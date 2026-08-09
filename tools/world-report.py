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

Usage:
    python tools/world-report.py [--json] [saves-dir]
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


def main():
    argv = [a for a in sys.argv[1:] if a != "--json"]
    as_json = "--json" in sys.argv
    root = argv[0] if argv else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "run", "saves")

    if not os.path.isdir(root):
        print("no saves directory at %s" % root)
        return 1

    saves = [os.path.join(root, d) for d in sorted(os.listdir(root))
             if os.path.isdir(os.path.join(root, d))]
    reports = [describe(s) for s in saves]

    if as_json:
        print(json.dumps(reports, indent=2, default=str))
    else:
        print("\n\n".join(render(r) for r in reports))
    return 0


if __name__ == "__main__":
    sys.exit(main())
