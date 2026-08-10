"""
Read a Minecraft save from outside the game and report what is in it.

Why Python and not PowerShell, in a tools/ folder that is otherwise .ps1: this
needs an NBT reader and a region-file reader, and both are byte-level work with
signed big-endian integers and two different compression schemes. PowerShell can
do it; it cannot do it in a way anyone would want to read again.

Why read the save at all, rather than asking the game: the game only knows about
the world it currently has open, one at a time, and answering "what is in each of
the three worlds" by loading each one costs three launches and changes the saves
just by opening them. This touches nothing - every file is opened read-only.

Reports, per save:
  * identity      - level name, seed, spawn, in-game day
  * octia         - enabled, beacon raised, where, and every mooring
  * exploration   - how many chunks exist and the block extent they cover
  * structures    - every vanilla structure start the world has generated
  * ruins         - where Octia's own features landed, and how far apart

Usage:
    python tools/world-report.py [--json|--ruins|--catalogue] [save-or-saves-dir]

--ruins      density and spacing of Octia's ruins, for tuning against a real
             world rather than a guess.
--catalogue  a docs/WORLDS.md section per save, headed with the KEG block,
             ready to append to the register. Called by tools/new-world.ps1 so
             that a world is catalogued the moment it exists.
"""

import gzip
import json
import os
import struct
import sys
import zlib
from collections import defaultdict

# ---- NBT ------------------------------------------------------------------

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE = 0, 1, 2, 3, 4, 5, 6
BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = 7, 8, 9, 10, 11, 12


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        b = self.d[self.i:self.i + n]
        if len(b) != n:
            raise EOFError("truncated NBT")
        self.i += n
        return b

    def num(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        n = self.num(">H")
        # Modified UTF-8. Plain utf-8 with replacement is close enough for the
        # keys and registry ids this tool reads, and never raises on odd data.
        return self.take(n).decode("utf-8", "replace")

    def payload(self, kind):
        if kind == BYTE:
            return self.num(">b")
        if kind == SHORT:
            return self.num(">h")
        if kind == INT:
            return self.num(">i")
        if kind == LONG:
            return self.num(">q")
        if kind == FLOAT:
            return self.num(">f")
        if kind == DOUBLE:
            return self.num(">d")
        if kind == BYTE_ARRAY:
            return self.take(self.num(">i"))
        if kind == STRING:
            return self.string()
        if kind == LIST:
            inner = self.num(">b")
            n = self.num(">i")
            return [self.payload(inner) for _ in range(max(0, n))]
        if kind == COMPOUND:
            out = {}
            while True:
                t = self.num(">b")
                if t == END:
                    return out
                # Name into a local first. `out[self.string()] = self.payload(t)`
                # looks equivalent and is not: Python evaluates the right-hand
                # side before the subscript, so the value would be read off the
                # wire ahead of the name it belongs to and every field after it
                # would be shifted.
                name = self.string()
                out[name] = self.payload(t)
        if kind == INT_ARRAY:
            n = self.num(">i")
            return list(struct.unpack(">%di" % n, self.take(4 * n)))
        if kind == LONG_ARRAY:
            n = self.num(">i")
            return list(struct.unpack(">%dq" % n, self.take(8 * n)))
        raise ValueError("unknown NBT tag %d" % kind)


def parse_nbt(raw):
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    kind = r.num(">b")
    if kind != COMPOUND:
        raise ValueError("root is not a compound")
    r.string()
    return r.payload(COMPOUND)


def load_dat(path):
    if not os.path.isfile(path):
        return None
    with open(path, "rb") as f:
        return parse_nbt(f.read())


# ---- BlockPos -------------------------------------------------------------

def signed(value, bits):
    """Two's-complement sign extension for a field narrower than 64 bits."""
    mask = 1 << (bits - 1)
    return (value & (mask - 1)) - (value & mask)


def unpack_pos(packed):
    """Inverse of BlockPos.asLong(): X in the top 26, Z in the next 26, Y in 12."""
    return (
        signed(packed >> 38, 26),
        signed(packed, 12),
        signed(packed >> 12, 26),
    )


# ---- Region files ---------------------------------------------------------

SECTOR = 4096


def read_region(path):
    """Yield the parsed NBT of every chunk present in one .mca file."""
    size = os.path.getsize(path)
    if size < SECTOR:
        return
    with open(path, "rb") as f:
        header = f.read(SECTOR)
        for slot in range(1024):
            entry = header[slot * 4:slot * 4 + 4]
            offset = int.from_bytes(entry[:3], "big")
            if offset == 0:
                continue
            start = offset * SECTOR
            if start + 5 > size:
                continue
            f.seek(start)
            length = int.from_bytes(f.read(4), "big")
            if length < 1:
                continue
            scheme = f.read(1)[0]
            body = f.read(length - 1)
            try:
                if scheme == 1:
                    body = gzip.decompress(body)
                elif scheme == 2:
                    body = zlib.decompress(body)
                elif scheme != 3:
                    continue
                yield parse_chunk(body)
            except Exception:
                # A half-written chunk is a fact about the save, not a crash.
                continue


def parse_chunk(raw):
    """Chunk NBT, already decompressed. Same shape as a root tag, no gzip."""
    r = Reader(raw)
    kind = r.num(">b")
    if kind != COMPOUND:
        raise ValueError("chunk root is not a compound")
    r.string()
    return r.payload(COMPOUND)


def scan_regions(save):
    """Chunk count, block extent, and every generated structure start."""
    folder = os.path.join(save, "region")
    out = {
        "chunks": 0,
        "min_x": None, "max_x": None, "min_z": None, "max_z": None,
        "structures": defaultdict(list),
        "cores": [],
    }
    if not os.path.isdir(folder):
        return out

    for name in sorted(os.listdir(folder)):
        if not name.endswith(".mca"):
            continue
        for chunk in read_region(os.path.join(folder, name)):
            cx, cz = chunk.get("xPos"), chunk.get("zPos")
            if cx is None or cz is None:
                continue
            out["chunks"] += 1
            for key, value in (("min_x", cx), ("max_x", cx), ("min_z", cz), ("max_z", cz)):
                cur = out[key]
                if cur is None:
                    out[key] = value
                elif key.startswith("min"):
                    out[key] = min(cur, value)
                else:
                    out[key] = max(cur, value)

            # Octia's own ruins are not structure starts - they are features,
            # and a feature leaves no record of itself anywhere in the save
            # except the blocks it wrote. So they are found the only way they
            # can be: by the section palette naming one of our blocks. A core
            # in a palette means a hull somewhere in that 16-cube.
            for section in (chunk.get("sections") or []):
                palette = ((section.get("block_states") or {}).get("palette") or [])
                for entry in palette:
                    if not isinstance(entry, dict):
                        continue
                    if entry.get("Name") == "octia:ship_core":
                        out["cores"].append((cx * 16 + 8, (section.get("Y") or 0) * 16, cz * 16 + 8))
                        break

            starts = (chunk.get("structures") or {}).get("starts") or {}
            for sid, start in starts.items():
                # A start whose id is INVALID is the game recording that it
                # considered this structure here and declined. Not a structure.
                if not isinstance(start, dict):
                    continue
                if str(start.get("id", "")).upper() == "INVALID":
                    continue
                sx, sz = start.get("ChunkX"), start.get("ChunkZ")
                if sx is None or sz is None:
                    continue
                pos = (sx * 16 + 8, sz * 16 + 8)
                if pos not in out["structures"][sid]:
                    out["structures"][sid].append(pos)

    out["structures"] = {k: sorted(v) for k, v in sorted(out["structures"].items())}
    out["cores"] = sorted(set(out["cores"]))
    return out


# ---- Report ---------------------------------------------------------------

def describe(save):
    name = os.path.basename(save)
    report = {"folder": name}

    level = load_dat(os.path.join(save, "level.dat")) or {}
    data = level.get("Data", {})
    report["level_name"] = data.get("LevelName")
    report["spawn"] = [data.get("SpawnX"), data.get("SpawnY"), data.get("SpawnZ")]
    report["day"] = (data.get("Time") or 0) // 24000
    settings = data.get("WorldGenSettings") or {}
    report["seed"] = settings.get("seed", data.get("RandomSeed"))

    # The Overworld's noise preset is the single most load-bearing fact about
    # what the terrain looks like - amplified is a different planet from default
    # and the difference does not show up anywhere else in this report.
    overworld = (settings.get("dimensions") or {}).get("minecraft:overworld") or {}
    report["generator"] = (overworld.get("generator") or {}).get("settings")
    report["features"] = bool(settings.get("generate_features", 1))

    world = (load_dat(os.path.join(save, "data", "octia_world.dat")) or {}).get("data", {})
    report["octia"] = {
        "enabled": bool(world.get("enabled", 0)),
        "beacon_raised": bool(world.get("beacon_raised", 0)),
        "beacon_at": list(unpack_pos(world["beacon_at"])) if "beacon_at" in world else None,
    }

    moorings = (load_dat(os.path.join(save, "data", "octia_moorings.dat")) or {}).get("data", {})
    report["moorings"] = sorted(unpack_pos(p) for p in (moorings.get("moorings") or []))

    report.update(scan_regions(save))
    return report


def render(r):
    lines = []
    lines.append("== %s" % r["folder"])
    lines.append("   name    : %s" % r["level_name"])
    lines.append("   seed    : %s" % r["seed"])
    lines.append("   terrain : %s%s" % (r["generator"], "" if r["features"] else "  (features OFF)"))
    lines.append("   spawn   : %s" % " ".join(str(v) for v in r["spawn"]))
    lines.append("   day     : %d" % r["day"])

    o = r["octia"]
    beacon = "not recorded" if o["beacon_at"] is None else " ".join(str(v) for v in o["beacon_at"])
    lines.append("   octia   : enabled=%s raised=%s beacon=%s"
                 % (o["enabled"], o["beacon_raised"], beacon))

    if r["moorings"]:
        lines.append("   moorings: %d" % len(r["moorings"]))
        for pos in r["moorings"]:
            lines.append("             %s" % " ".join(str(v) for v in pos))
    else:
        lines.append("   moorings: none")

    if r["chunks"]:
        lines.append("   explored: %d chunks, x %d..%d  z %d..%d (blocks)"
                     % (r["chunks"], r["min_x"] * 16, r["max_x"] * 16 + 15,
                        r["min_z"] * 16, r["max_z"] * 16 + 15))
    else:
        lines.append("   explored: nothing generated")

    if r["structures"]:
        lines.append("   structures:")
        for sid, spots in r["structures"].items():
            where = "  ".join("(%d,%d)" % p for p in spots[:6])
            more = "  +%d more" % (len(spots) - 6) if len(spots) > 6 else ""
            lines.append("     %-38s %s%s" % (sid, where, more))
    else:
        lines.append("   structures: none generated")

    return "\n".join(lines)


def render_ruins(r):
    """Where Octia's own ruins landed, and how far apart."""
    lines = ["== %s  ruins" % r["folder"]]

    spawn = r["spawn"]
    cores = r["cores"]
    if not cores:
        lines.append("   no ship cores found in %d generated chunks" % r["chunks"])
        return "\n".join(lines)

    def flat(a, b):
        return round(((a[0] - b[0]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5)

    spawn_xz = (spawn[0] or 0, 0, spawn[2] or 0)
    ranked = sorted(cores, key=lambda c: flat(c, spawn_xz))

    lines.append("   %d ship core(s) in %d chunks  (1 per %d chunks)"
                 % (len(cores), r["chunks"], r["chunks"] // max(1, len(cores))))
    # Chunk resolution, and said so. A palette names what is somewhere in a
    # 16-cube, not where - unpacking the block data would give exact positions
    # and is not worth it for a density measurement. Do not read these as
    # coordinates to walk to; read them as somewhere-in-that-chunk.
    lines.append("   positions are chunk-centres, +/-8 blocks")
    lines.append("   nearest to spawn: ~%s  (~%db)"
                 % (" ".join(str(v) for v in ranked[0]), flat(ranked[0], spawn_xz)))

    for core in ranked[:12]:
        lines.append("     %-22s %db from spawn"
                     % (" ".join(str(v) for v in core), flat(core, spawn_xz)))
    if len(ranked) > 12:
        lines.append("     ... and %d more" % (len(ranked) - 12))

    # Nearest-neighbour spacing is the number that decides whether a density
    # reads as litter or as absence. One-per-N-chunks hides clumping entirely.
    if len(cores) > 1:
        gaps = sorted(min(flat(a, b) for b in cores if b != a) for a in cores)
        lines.append("   nearest-neighbour spacing: min %db, median %db, max %db"
                     % (gaps[0], gaps[len(gaps) // 2], gaps[-1]))
    return "\n".join(lines)


def render_catalogue(r, act="ACT TWO", milestone="MILESTONE 2", scope="|ALL|"):
    """
    A docs/WORLDS.md section for one save, headed with the KEG block.

    This FORMATS the notation. It must never parse it. `com.serenity.octia.codex`
    is the canonical implementation and NotationTest pins it against the physical
    sign at (-112, 67, -149) - the world is the source of truth, not the docs. A
    second parser written here would drift from the Java one and nothing would
    catch it, because nothing would be testing it. So the act, milestone and
    scope arrive as arguments, and the world's name is copied, never read.
    """
    artifact = r["folder"].replace(".", "_")
    beacon = r["octia"]["beacon_at"]
    out = [
        "```",
        act,
        milestone,
        artifact,
        "SEEK KEG %s" % scope,
        "```",
        "",
        "## %s" % (r["level_name"] or r["folder"]),
        "",
        "**Read from the save**",
        "",
        "| | |",
        "|---|---|",
        "| seed | `%s` |" % r["seed"],
        "| terrain | `%s` |" % r["generator"],
        "| spawn | `%s` |" % " ".join(str(v) for v in r["spawn"]),
        "| age | day %d |" % r["day"],
        "| explored | %d chunks |" % r["chunks"],
        "| octia | enabled=%s raised=%s |" % (r["octia"]["enabled"], r["octia"]["beacon_raised"]),
        "| beacon | %s |" % ("not recorded" if beacon is None
                             else "`%s`" % " ".join(str(v) for v in beacon)),
        "| moorings | %d |" % len(r["moorings"]),
        "| ship cores | %d |" % len(r["cores"]),
        "",
    ]

    if r["structures"]:
        out += ["**Structures**", "", "| structure | at |", "|---|---|"]
        for sid, spots in r["structures"].items():
            where = ", ".join("(%d,%d)" % p for p in spots[:5])
            if len(spots) > 5:
                where += ", +%d more" % (len(spots) - 5)
            out.append("| %s | %s |" % (sid, where))
        out.append("")

    out += [
        "**Seen in world**",
        "",
        "- _nothing recorded - nobody has walked this one yet_",
        "",
    ]
    return "\n".join(out)


def main():
    flags = {"--json", "--ruins", "--catalogue"}
    argv = [a for a in sys.argv[1:] if a not in flags]
    as_json = "--json" in sys.argv
    as_ruins = "--ruins" in sys.argv
    as_catalogue = "--catalogue" in sys.argv
    root = argv[0] if argv else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "run", "saves")

    if not os.path.isdir(root):
        print("no directory at %s" % root)
        return 1

    # Accept either one save or a folder of them, because the two callers want
    # different things: a human runs this over run/saves, and new-world.ps1 runs
    # it over the single world it just made.
    if os.path.isfile(os.path.join(root, "level.dat")):
        saves = [root]
    else:
        saves = [os.path.join(root, d) for d in sorted(os.listdir(root))
                 if os.path.isdir(os.path.join(root, d))]
        if not saves:
            print("no saves under %s" % root)
            return 1

    reports = [describe(s) for s in saves]

    if as_json:
        print(json.dumps(reports, indent=2, default=str))
    elif as_catalogue:
        print("\n---\n\n".join(render_catalogue(r) for r in reports))
    elif as_ruins:
        print("\n\n".join(render_ruins(r) for r in reports))
    else:
        print("\n\n".join(render(r) for r in reports))
    return 0


if __name__ == "__main__":
    sys.exit(main())
