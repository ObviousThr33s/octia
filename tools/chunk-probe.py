"""
Read the actual blocks out of a save, without launching the game.

world-report.py answers "what is in this world" - chunk counts, structure
starts, where the ruins landed. This answers the narrower question that comes
before it: "is the terrain the terrain I think it is." It opens the region
files, walks the section palettes, and prints the vertical truth of a column.

Why this had to exist. octia:sky says it generates the Overworld from
minecraft:floating_islands. A dedicated server resolves level-type through the
WORLD_PRESET registry, and when that lookup fails it FALLS BACK TO NORMAL and
says nothing a person would notice - same log lines, same "world ready", and a
save full of ordinary hills. Reading level.dat only proves what the save was
asked for. Reading the blocks proves what it got.

The tell is unambiguous and needs no judgement:

  a sky world      has nothing below y=0 and bedrock nowhere
  an ordinary one  has bedrock at -64 and stone continuously beneath you

Usage:
  python tools/chunk-probe.py <save>                  overview + a column at spawn
  python tools/chunk-probe.py <save> --column X Z     the column at X,Z
  python tools/chunk-probe.py <save> --profile        fill-by-height over every chunk
"""

import argparse
import collections
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_report():
    """Borrow the NBT reader from world-report.py rather than writing a second one.

    Its filename has a hyphen in it, so it cannot be imported by name. Two
    parsers for one format is how they drift, and this file is a probe - it
    should have no opinions of its own about what NBT means.
    """
    path = os.path.join(HERE, "world-report.py")
    spec = importlib.util.spec_from_file_location("world_report", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


R = _load_report()


# ---- section decoding -----------------------------------------------------

def section_palette(section):
    bs = section.get("block_states")
    if not isinstance(bs, dict):
        return None, None
    palette = [p.get("Name", "?") for p in bs.get("palette", [])]
    return palette, bs.get("data")


def block_at(palette, data, x, y, z):
    """One block inside a 16x16x16 section. x, y, z are section-local 0..15.

    Single-palette sections carry NO data array at all - the whole section is
    that one block. That is how air is stored, and reading it as an empty array
    is the mistake that makes a void look like a parse failure.
    """
    if palette is None:
        return None
    if not palette:
        return None
    if len(palette) == 1 or not data:
        return palette[0]

    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    index = y * 256 + z * 16 + x
    long_index = index // per_long
    if long_index >= len(data):
        return None
    offset = (index % per_long) * bits
    raw = data[long_index] & 0xFFFFFFFFFFFFFFFF
    value = (raw >> offset) & ((1 << bits) - 1)
    if value >= len(palette):
        return None
    return palette[value]


def chunk_sections(chunk):
    """{section_y: (palette, data)} for one chunk, lowest first."""
    out = {}
    for s in chunk.get("sections", []):
        y = s.get("Y")
        if y is None:
            continue
        if y > 127:
            y -= 256                      # NBT byte, signed
        palette, data = section_palette(s)
        if palette:
            out[y] = (palette, data)
    return dict(sorted(out.items()))


def regions(save):
    d = os.path.join(save, "region")
    if not os.path.isdir(d):
        sys.exit("no region directory under %s" % save)
    return [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.endswith(".mca")]


# ---- the three readings ---------------------------------------------------

def overview(save):
    """Which Y bands hold anything at all, across every chunk in the save."""
    band = collections.Counter()
    solid_band = collections.Counter()
    blocks = collections.Counter()
    chunks = 0

    for path in regions(save):
        for chunk in R.read_region(path):
            if chunk.get("Status") not in ("minecraft:full", "full"):
                continue
            chunks += 1
            for y, (palette, data) in chunk_sections(chunk).items():
                real = [p for p in palette if p != "minecraft:air"]
                band[y] += 1
                if real:
                    solid_band[y] += 1
                    for p in real:
                        blocks[p] += 1

    print("== %s" % os.path.basename(save.rstrip("\\/")))
    print("   %d full chunks" % chunks)
    if not chunks:
        return

    lo = min(solid_band) * 16 if solid_band else None
    hi = (max(solid_band) + 1) * 16 - 1 if solid_band else None
    print("   sections holding anything but air: y %s to %s" % (lo, hi))
    print()
    print("   section  chunks-with-solid   bar")
    for y in sorted(band):
        n = solid_band.get(y, 0)
        if n == 0:
            continue
        bar = "#" * max(1, int(40 * n / chunks))
        print("   %6d   %5d/%-5d       %s" % (y * 16, n, chunks, bar))
    print()
    print("   commonest blocks:")
    for name, n in blocks.most_common(10):
        print("     %-34s %d section(s)" % (name, n))

    print()
    bedrock = any("bedrock" in b for b in blocks)
    below_zero = [y for y in solid_band if y < 0]
    print("   VERDICT")
    print("     bedrock present     : %s" % ("YES - this is not a sky world" if bedrock else "no"))
    print("     solid below y=0     : %s" % ("YES - not a sky world" if below_zero else "no"))
    if not bedrock and not below_zero:
        print("     -> floating islands over void, as asked for.")


def column(save, cx, cz):
    """Every non-air block in one column, with the gaps named."""
    rx, rz = cx >> 9, cz >> 9
    path = os.path.join(save, "region", "r.%d.%d.mca" % (rx, rz))
    if not os.path.isfile(path):
        sys.exit("no region file for %d,%d (%s)" % (cx, cz, os.path.basename(path)))

    want_cx, want_cz = cx >> 4, cz >> 4
    lx, lz = cx & 15, cz & 15

    for chunk in R.read_region(path):
        if chunk.get("xPos") != want_cx or chunk.get("zPos") != want_cz:
            continue
        print("== column at x=%d z=%d  (chunk %d,%d)" % (cx, cz, want_cx, want_cz))
        runs = []
        for sy, (palette, data) in chunk_sections(chunk).items():
            for y in range(16):
                b = block_at(palette, data, lx, y, lz)
                world_y = sy * 16 + y
                b = b or "minecraft:air"
                if runs and runs[-1][0] == b:
                    runs[-1][2] = world_y
                else:
                    runs.append([b, world_y, world_y])
        for name, y0, y1 in runs:
            if name == "minecraft:air":
                print("   %4d..%-4d  %s" % (y0, y1, "- air -" if y1 - y0 > 2 else "air"))
            else:
                print("   %4d..%-4d  %s" % (y0, y1, name))
        return
    sys.exit("chunk %d,%d is not generated in this save" % (want_cx, want_cz))


def profile(save):
    """How much of each chunk is solid, as a distribution. Islands are sparse."""
    fills = []
    for path in regions(save):
        for chunk in R.read_region(path):
            if chunk.get("Status") not in ("minecraft:full", "full"):
                continue
            solid = 0
            for y, (palette, data) in chunk_sections(chunk).items():
                if len(palette) == 1:
                    if palette[0] != "minecraft:air":
                        solid += 4096
                    continue
                for i in range(4096):
                    bits = max(4, (len(palette) - 1).bit_length())
                    per_long = 64 // bits
                    li = i // per_long
                    if not data or li >= len(data):
                        break
                    raw = data[li] & 0xFFFFFFFFFFFFFFFF
                    v = (raw >> ((i % per_long) * bits)) & ((1 << bits) - 1)
                    if v < len(palette) and palette[v] != "minecraft:air":
                        solid += 1
            fills.append(solid)
    if not fills:
        sys.exit("no full chunks")
    fills.sort()
    total = 384 * 256
    print("== solid blocks per chunk, over %d chunks" % len(fills))
    for label, v in (("min", fills[0]),
                     ("p25", fills[len(fills) // 4]),
                     ("median", fills[len(fills) // 2]),
                     ("p75", fills[3 * len(fills) // 4]),
                     ("max", fills[-1])):
        print("   %-7s %8d  (%.2f%% of a full column stack)" % (label, v, 100.0 * v / total))
    empty = sum(1 for f in fills if f == 0)
    print("   completely empty chunks: %d (%.1f%%)" % (empty, 100.0 * empty / len(fills)))


# ---- seeing it ------------------------------------------------------------

# Written with zlib and struct only. Pillow is not a dependency of this repo and
# adding one so a diagnostic can draw a picture would be a poor trade.
def write_png(path, width, height, rows):
    import struct
    import zlib
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    blob = b"\x89PNG\r\n\x1a\n"
    blob += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    blob += chunk(b"IDAT", zlib.compress(raw, 9))
    blob += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(blob)


# Ordered, because the first match wins and "grass_block" must be tested before
# "block", "deepslate_coal_ore" before "coal_ore".
PALETTE = [
    ("octia:ship_core", (255, 196, 60)),
    ("octia:andesite_frame_panel", (232, 120, 40)),
    ("air", None),
    ("water", (38, 82, 158)),
    ("ice", (170, 210, 235)),
    ("seagrass", (48, 120, 90)),
    ("kelp", (48, 120, 90)),
    ("grass_block", (96, 152, 72)),
    ("snow", (238, 244, 248)),
    ("sand", (214, 202, 152)),
    ("gravel", (128, 124, 120)),
    ("dirt", (122, 88, 60)),
    ("clay", (150, 156, 162)),
    ("granite", (150, 106, 92)),
    ("diorite", (196, 196, 192)),
    ("andesite", (136, 138, 136)),
    ("deepslate", (66, 66, 72)),
    ("tuff", (90, 92, 84)),
    ("copper", (150, 110, 78)),
    ("iron_ore", (176, 152, 124)),
    ("coal_ore", (54, 54, 58)),
    ("gold", (200, 168, 72)),
    ("lava", (220, 110, 30)),
    ("log", (92, 70, 44)),
    ("leaves", (66, 110, 54)),
    ("stone", (118, 118, 120)),
]

SKY_TOP = (16, 18, 26)      # the void above
SKY_BOT = (8, 9, 14)        # and below - darker, so down reads as down


def colour_of(name):
    for key, rgb in PALETTE:
        if key in name:
            return rgb
    return (200, 60, 200)   # unmapped: loud on purpose, so it gets noticed


def slice_view(save, z, x0, x1, y0, y1, scale, out):
    """A vertical cross-section, rendered. The one view that shows a sky world."""
    cz = z >> 4
    lz = z & 15

    # Load every chunk the slice crosses, once.
    wanted = {}
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        wanted[cx] = None
    for path in regions(save):
        for chunk in R.read_region(path):
            if chunk.get("zPos") != cz:
                continue
            cx = chunk.get("xPos")
            if cx in wanted and wanted[cx] is None:
                wanted[cx] = chunk_sections(chunk)

    have = sum(1 for v in wanted.values() if v)
    if not have:
        sys.exit("no generated chunks along z=%d in that x range" % z)

    width = (x1 - x0) * scale
    height = (y1 - y0) * scale
    rows = []
    for py in range(height):
        y = y1 - 1 - (py // scale)
        row = bytearray()
        for px in range(width):
            x = x0 + (px // scale)
            sections = wanted.get(x >> 4)
            rgb = None
            if sections:
                sy, ly = divmod(y, 16)
                cell = sections.get(sy)
                if cell:
                    name = block_at(cell[0], cell[1], x & 15, ly, lz)
                    if name:
                        rgb = colour_of(name)
            if rgb is None:
                t = py / float(height)
                rgb = tuple(int(SKY_TOP[i] + (SKY_BOT[i] - SKY_TOP[i]) * t) for i in range(3))
            row += bytes(rgb)
        rows.append(row)

    write_png(out, width, height, rows)
    print("wrote %s  (%dx%d, %d chunks along the slice, x %d..%d at z=%d, y %d..%d)"
          % (out, width, height, have, x0, x1, z, y0, y1))


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("save")
    ap.add_argument("--column", nargs=2, type=int, metavar=("X", "Z"))
    ap.add_argument("--profile", action="store_true")
    ap.add_argument("--slice", type=int, metavar="Z",
                    help="render a vertical cross-section at this z, as a PNG")
    ap.add_argument("--x", nargs=2, type=int, default=[-128, 128], metavar=("X0", "X1"))
    ap.add_argument("--y", nargs=2, type=int, default=[0, 240], metavar=("Y0", "Y1"))
    ap.add_argument("--scale", type=int, default=3)
    ap.add_argument("--out", default="slice.png")
    args = ap.parse_args()

    if args.slice is not None:
        slice_view(args.save, args.slice, args.x[0], args.x[1],
                   args.y[0], args.y[1], args.scale, args.out)
    elif args.column:
        column(args.save, args.column[0], args.column[1])
    elif args.profile:
        profile(args.save)
    else:
        overview(args.save)


if __name__ == "__main__":
    main()
