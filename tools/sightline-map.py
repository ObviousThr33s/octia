"""
Draw the sightlines lattice over a real save, as one self-contained HTML file.

Why this exists: `Sightlines` is a pure function of the world seed, so the
lattice it lays can be computed outside the game entirely - and a picture of it
over the structure starts already in a save is the only way to judge whether the
legs read as routes or as noise. The alternative is launching the game and
flying, which is the thing `world-report.py` was written to avoid.

Why the arithmetic is duplicated here rather than shared: it cannot be shared.
The authority is `Sightlines.java` and it runs in the JVM. What this file owes
is a *match*, and `--probe` is how that is checked - it prints the same handful
of nodes the Java prints, so the two can be diffed by eye or by pipe:

    javac -d /tmp/sl src/main/java/com/serenity/octia/world/Sightlines.java

If they ever disagree, this file is wrong. It was wrong once already: it carried
JITTER=144 after the Java moved to 96, and every node it drew was in the wrong
place while looking entirely plausible.

Usage:
    python tools/sightline-map.py [save-or-saves-dir] [-o out.html] [--range N]
    python tools/sightline-map.py --probe

The output opens in any browser, needs no network, and embeds its own data.
"""

import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PARTS = os.path.join(HERE, "sightline-map")

# ---- the lattice, transposed from Sightlines.java --------------------------

MASK = (1 << 64) - 1

SPACING = 512
JITTER = 96
CORRIDOR = 128
SPREAD = 2 * JITTER + 1

NODE_SALT = 0x51_6117_11E5
STEP_SALT = 0x7_47EAD_5
REPICK_SALT = 0x2C_0107_3ED
STATION_SALT = 0x5_7A7_10_5

# Beamlines, transposed from Beamline.java.
DRIFT_MIN = 160
DRIFT_MAX = 384

# Order matters: the Java indexes Heading.values() by the hash modulo four.
HEADINGS = [("NORTH", 0, -1), ("EAST", 1, 0), ("SOUTH", 0, 1), ("WEST", -1, 0)]


def mix(value):
    """splitmix64's finaliser, in 64-bit unsigned arithmetic."""
    v = value & MASK
    v ^= v >> 33
    v = (v * 0xFF51AFD7ED558CCD) & MASK
    v ^= v >> 33
    v = (v * 0xC4CEB9FE1A85EC53) & MASK
    v ^= v >> 33
    return v


def signed(u):
    """Java's long. Needed because Math.floorMod takes the sign into account."""
    return u - (1 << 64) if u >= (1 << 63) else u


def hash_cell(seed, cell_x, cell_z, salt):
    return mix((seed & MASK) ^ mix((cell_x * 341873128712 + cell_z * 132897987541 + salt) & MASK))


def node(seed, cell_x, cell_z):
    u = hash_cell(seed, cell_x, cell_z, NODE_SALT)
    # floorMod on the signed value for the low half, on the unsigned shift for
    # the high half - exactly as the Java spells it, and not interchangeable.
    dx = (signed(u) % SPREAD) - JITTER
    dz = ((u >> 32) % SPREAD) - JITTER
    return [cell_x * SPACING + SPACING // 2 + dx, cell_z * SPACING + SPACING // 2 + dz]


def raw_step(seed, cell_x, cell_z):
    return HEADINGS[signed(hash_cell(seed, cell_x, cell_z, STEP_SALT)) % 4]


def black(cell_x, cell_z):
    """A cardinal step always flips this parity, so the lattice is a board."""
    return (cell_x + cell_z) & 1 == 0


def points_back(seed, cell_x, cell_z, heading):
    _, dx, dz = heading
    tx, tz = cell_x + dx, cell_z + dz
    _, bx, bz = raw_step(seed, tx, tz)
    return tx + bx == cell_x and tz + bz == cell_z


def step(seed, cell_x, cell_z):
    """
    The step out of a cell, with the immediate doubling-back excluded.

    Black cells keep their draw; only white cells look at their neighbours, and
    every neighbour of a white cell is black and therefore already final. That
    asymmetry is what makes the rule terminate in one pass - see the long note
    on Sightlines.step, which is the authority this transposes.

    This was missing here until 2026-08-22 and the map drew a different lattice
    than the game generated for 12.7% of cells. --probe now sweeps a grid rather
    than four cells, which is what would have caught it.
    """
    raw = raw_step(seed, cell_x, cell_z)
    if black(cell_x, cell_z) or not points_back(seed, cell_x, cell_z, raw):
        return raw
    legal = [h for h in HEADINGS if not points_back(seed, cell_x, cell_z, h)]
    if not legal:
        return raw
    return legal[signed(hash_cell(seed, cell_x, cell_z, REPICK_SALT)) % len(legal)]


def legs(seed, reach):
    out = []
    for cell_x in range(-reach, reach + 1):
        for cell_z in range(-reach, reach + 1):
            name, dx, dz = step(seed, cell_x, cell_z)
            out.append({
                "c": [cell_x, cell_z],
                "a": node(seed, cell_x, cell_z),
                "b": node(seed, cell_x + dx, cell_z + dz),
                "h": name,
            })
    return out


def stations(seed, reach):
    """
    The tangents, transposed from Beamline.java.

    A node whose incoming leg arrives on one heading and whose outgoing leg
    leaves on another is a bend, and a bend throws light straight on: the leg's
    own direction carried past the node, not the cardinal nearest to it.
    """
    out = []
    for cell_x in range(-reach, reach + 1):
        for cell_z in range(-reach, reach + 1):
            leaving = step(seed, cell_x, cell_z)
            here = node(seed, cell_x, cell_z)
            for arriving in HEADINGS:
                name, dx, dz = arriving
                fx, fz = cell_x - dx, cell_z - dz
                if step(seed, fx, fz)[0] != name or name == leaving[0]:
                    continue
                salt = STATION_SALT + HEADINGS.index(arriving)
                drift = DRIFT_MIN + signed(hash_cell(seed, cell_x, cell_z, salt)) % (
                    DRIFT_MAX - DRIFT_MIN + 1)
                came = node(seed, fx, fz)
                ax, az = here[0] - came[0], here[1] - came[1]
                length = (ax * ax + az * az) ** 0.5 or 1.0
                out.append({
                    "c": [cell_x, cell_z],
                    "n": here,
                    "s": [here[0] + round(ax / length * drift),
                          here[1] + round(az / length * drift)],
                    "h": name,
                    "d": drift,
                })
    return out


# ---- vanilla's own grids ---------------------------------------------------

# Recalled, NOT read out of the 1.21.1 jar - see docs/SIGHTLINES.md. Used only
# to show that the save's starts fit them; nothing in the mod depends on them.
VANILLA = {
    "trial_chambers": (34, 12),
    "ruined_portal": (40, 15),
    "ocean_ruin_cold": (20, 8),
    "village_plains": (34, 8),
    "village_taiga": (34, 8),
    "trail_ruins": (34, 8),
}


def proof(structures):
    """Each start with the cell it landed in and its offset inside that cell."""
    out = []
    for sid, spots in structures.items():
        chunks = sorted(((x - 8) // 16, (z - 8) // 16) for x, z in spots)
        spacing, separation = VANILLA.get(sid, (0, 0))
        rows = []
        for cx, cz in chunks:
            if not spacing:
                rows.append({"chunk": [cx, cz], "cell": None, "off": None, "ok": None})
                continue
            cell = [cx // spacing, cz // spacing]
            off = [cx - cell[0] * spacing, cz - cell[1] * spacing]
            rows.append({"chunk": [cx, cz], "cell": cell, "off": off,
                         "ok": max(off) <= spacing - separation - 1})
        out.append({
            "id": sid,
            "spacing": spacing,
            "sep": separation,
            "window": spacing - separation - 1 if spacing else None,
            "rows": rows,
        })
    return out


def mirrored(structures):
    """The mineshaft oddity: starts that pair up as exact negations."""
    seen = {((x - 8) // 16, (z - 8) // 16) for x, z in structures.get("mineshaft", [])}
    pairs = sorted({tuple(sorted([c, (-c[0], -c[1])])) for c in seen if (-c[0], -c[1]) in seen})
    return {"pairs": [[list(a), list(b)] for a, b in pairs], "n": len(seen)}


# ---- the page --------------------------------------------------------------

def read_save(where):
    raw = subprocess.run([sys.executable, os.path.join(HERE, "world-report.py"), "--json", where],
                         capture_output=True, text=True, check=True).stdout
    saves = json.loads(raw)
    if not isinstance(saves, list):
        saves = [saves]
    if not saves:
        raise SystemExit("no saves under " + where)
    # The most explored one, since an empty save draws an empty map.
    return max(saves, key=lambda s: sum(len(v) for v in s["structures"].values()))


def build(save, reach):
    structures = {k.split(":")[-1]: v for k, v in save["structures"].items()}
    return {
        "seed": save["seed"],
        "spacing": SPACING,
        "jitter": JITTER,
        "corridor": CORRIDOR,
        "range": reach,
        "spawn": save["spawn"],
        "generator": save["generator"],
        "name": save["level_name"],
        "chunks": save["chunks"],
        "extent": [save["min_x"], save["max_x"], save["min_z"], save["max_z"]],
        "legs": legs(save["seed"], reach),
        "stations": stations(save["seed"], reach),
        "structures": structures,
        "proof": proof(structures),
        "mirror": mirrored(structures),
    }


def part(name):
    with open(os.path.join(PARTS, name), encoding="utf-8") as f:
        return f.read()


def render(data):
    return "".join([
        part("page.css.html"),
        part("page.body.html"),
        "<script>window.__DATA__=", json.dumps(data, separators=(",", ":")), ";</script>\n",
        "<script>\n", part("page.js"), "\n</script>\n",
    ])


def probe(seed):
    """
    What to diff against the Java. Keep both in step.

    Four named cells, then a sweep. The four alone are not enough and that is
    not a hypothetical: three white cells in four are happy with their raw
    draw, so a missing re-pick rule hid behind four cells for weeks while the
    map drew a lattice the game does not have. The digest below is over 41x41
    cells and would have failed on the first run.
    """
    for cell_x, cell_z in [(0, 0), (-6, -6), (3, -2), (-1, 4)]:
        n = node(seed, cell_x, cell_z)
        name, dx, dz = step(seed, cell_x, cell_z)
        to = node(seed, cell_x + dx, cell_z + dz)
        print("%d,%d node=%d,%d step=%s to=%d,%d"
              % (cell_x, cell_z, n[0], n[1], name, to[0], to[1]))

    order = [h[0] for h in HEADINGS]
    digest = 0
    counts = {name: 0 for name in order}
    for cell_x in range(-20, 21):
        for cell_z in range(-20, 21):
            name = step(seed, cell_x, cell_z)[0]
            counts[name] += 1
            digest = (digest * 31 + order.index(name)) % (1 << 32)
    print("sweep 41x41 digest=%d %s stations=%d"
          % (digest, " ".join("%s=%d" % (k, counts[k]) for k in sorted(counts)),
             len(stations(seed, 20))))



def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("save", nargs="?", default=os.path.join(os.path.dirname(HERE), "worlds"))
    ap.add_argument("-o", "--out", default="sightlines.html")
    ap.add_argument("--range", type=int, default=6, help="cells drawn either side of origin")
    ap.add_argument("--probe", action="store_true", help="print nodes to diff against Sightlines.java")
    ap.add_argument("--node", nargs=2, type=int, metavar=("X", "Z"),
                    help="print the node and leg for a world position, for /tp")
    ap.add_argument("--seed", type=int, default=95512464, help="seed for --probe and --node")
    args = ap.parse_args()

    if args.probe:
        probe(args.seed)
        return

    if args.node:
        # An arch stands on the node, so this is the coordinate to teleport to.
        # There is no command that finds one in game and there should not be -
        # the whole point is that you arrive at one by following the obelisks.
        x, z = args.node
        cell_x, cell_z = x // SPACING, z // SPACING
        here = node(args.seed, cell_x, cell_z)
        name, dx, dz = step(args.seed, cell_x, cell_z)
        there = node(args.seed, cell_x + dx, cell_z + dz)
        print("seed %d, position %d %d" % (args.seed, x, z))
        print("  cell   %d %d" % (cell_x, cell_z))
        print("  node   %d %d      /tp @s %d ~ %d" % (here[0], here[1], here[0], here[1]))
        print("  leg    %s, to %d %d" % (name.lower(), there[0], there[1]))
        # Same formula as Sightlines.Leg.distanceToLine: the infinite line, not
        # the segment, because that is what ObeliskFeature asks.
        lx, lz = there[0] - here[0], there[1] - here[1]
        d = abs(lz * (x - here[0]) - lx * (z - here[1])) / (lx * lx + lz * lz) ** 0.5
        print("  you are %.0f blocks off that line (%s the %d-block corridor)"
              % (d, "inside" if d <= CORRIDOR else "outside", CORRIDOR))
        return

    save = read_save(args.save)
    page = render(build(save, args.range))
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(page)
    print("%s  (%d bytes, seed %d, %d starts)"
          % (args.out, len(page), save["seed"],
             sum(len(v) for v in save["structures"].values())))


if __name__ == "__main__":
    main()
