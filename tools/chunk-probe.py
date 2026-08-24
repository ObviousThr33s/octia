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
    rock_band = collections.Counter()
    fluid_band = collections.Counter()
    blocks = collections.Counter()
    chunks = 0
    deep_chunks = 0

    for path in regions(save):
        for chunk in R.read_region(path):
            if chunk.get("Status") not in ("minecraft:full", "full"):
                continue
            chunks += 1
            deep_rock = False
            for y, (palette, data) in chunk_sections(chunk).items():
                if y < 0 and 2 in [_kind(p) for p in palette]:
                    deep_rock = True
                # _kind, not `!= "minecraft:air"`. That test passes cave_air and
                # void_air, so every cave counted as material.
                kinds = [_kind(p) for p in palette]
                real = [p for p, k in zip(palette, kinds) if k]
                band[y] += 1
                if real:
                    solid_band[y] += 1
                    if 2 in kinds:
                        rock_band[y] += 1
                    if 1 in kinds:
                        fluid_band[y] += 1
                    for p in real:
                        blocks[p] += 1
            if deep_rock:
                deep_chunks += 1

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
    # The tell, corrected 2026-08-23, and it was wrong twice over.
    #
    # It used to read "anything below y=0 means this is not a sky world". That
    # called BOTH a genuine octia:sky save and a genuine vanilla
    # floating_islands save "not a sky world", for two different reasons, and
    # the two are opposites:
    #
    #   fluid below the band  - WATER, 3,756 blocks, in exactly the 25 chunks
    #     the server had ticked out of 169 generated. Generation touched all of
    #     them equally, so this is not generation: the sea is DRAINING out of
    #     the bottom of the world. octia:sky sets sea_level 96 over a band whose
    #     min_y is 0, and floating_islands has no floor, so water at the bottom
    #     of the band has nothing to sit on. Vanilla sets sea_level to -64 for
    #     exactly this reason - so that no water is ever placed at all.
    #
    #   rock below the band   - a TRIAL CHAMBER, tuff bricks and waxed copper,
    #     at y -16 to -47, hanging in the void. On VANILLA floating_islands,
    #     nothing to do with this mod: structures place in dimension space
    #     (-64..320) rather than in the noise band, so one anchored near the
    #     floor extends straight through it.
    #
    # So presence proves nothing, and the honest discriminator is a RATIO. An
    # ordinary overworld has rock under essentially every chunk; a floating one
    # has it under the few that caught a structure - 19 of 1,089 here, 1.7%.
    bedrock = any("bedrock" in b for b in blocks)
    fluid_below = sorted(y for y in fluid_band if y < 0)
    share = 100.0 * deep_chunks / max(1, chunks)
    continuous = share > 50.0

    print("   VERDICT")
    print("     bedrock present     : %s"
          % ("YES - this is not a sky world" if bedrock else "no"))
    if deep_chunks == 0:
        note = "  - none"
    elif continuous:
        note = "  - continuous, so ordinary terrain"
    else:
        note = "  - scattered, i.e. structures reaching below the band"
    print("     chunks with rock below y=0 : %d/%d (%.1f%%)%s"
          % (deep_chunks, chunks, share, note))

    sky = not bedrock and not continuous
    if not sky:
        # The leak test below only means anything on a floorless band. An
        # ordinary overworld generates down to -64, so water under y=0 there is
        # an ocean doing its job - and reporting it as a drain made every normal
        # save cry wolf.
        print("     fluid below y=0     : not asked - this band reaches below 0 anyway")
    elif fluid_below:
        n = sum(fluid_band[y] for y in fluid_below)
        print("     fluid below y=0     : YES in %d section(s) - THE SEA IS DRAINING." % n)
        print("                           Nothing GENERATES below the band, so this")
        print("                           leaked. Check sea_level against noise.min_y.")
    else:
        print("     fluid below y=0     : no")
    if sky:
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


# Fluids are counted apart from rock, and the split is the whole point of this
# reading. "Solid" used to mean "not air", so a flooded cavern counted exactly
# like a cavern filled with stone - and on a world whose settings turned
# aquifers on and moved sea level from -64 to 96, that is the difference
# between an archipelago and a bathtub. A number that cannot tell those apart
# cannot answer the question this file exists to answer.
FLUIDS = ("water", "lava", "bubble_column")

AIR_NAMES = frozenset(("minecraft:air", "minecraft:cave_air", "minecraft:void_air"))


def _kind(name):
    """0 air, 1 fluid, 2 rock."""
    if name in AIR_NAMES:
        return 0
    for f in FLUIDS:
        if f in name:
            return 1
    return 2


def _indices(palette, data):
    """Every one of a section's 4096 palette indices, or None if uniform.

    Each long is decoded once instead of once per block. The old loop
    recomputed bits and per_long inside the 4096-iteration body and re-read the
    same long up to sixteen times, which is why a profile over a played save
    took minutes. Indices never span a long - Minecraft leaves the high bits of
    each long unused rather than straddling - so this is a flat walk.
    """
    if len(palette) == 1 or not data:
        return None
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for raw in data:
        raw &= 0xFFFFFFFFFFFFFFFF
        for k in range(per_long):
            out.append((raw >> (k * bits)) & mask)
            if len(out) == 4096:
                return out
    return out


def strata(save):
    """What is at each depth, so "deeper" can be asked whether it means anything.

    ROADMAP XIII wants stratigraphy - "deeper digs draw from older loot tables,
    archaeology that means something vertically instead of being flat
    everywhere" - and that is only buildable if the rock itself already changes
    with depth. An ordinary overworld does: dirt, then stone, then deepslate at
    0, then bedrock. A generator derived from floating_islands may not, because
    its band is 256 tall and vanilla's depth-dependent surface rules were
    written against a 384-tall column that starts at -64.

    This is the reading Dig Dug, Motherload and Terraria all depend on: that
    going down is going somewhere. If every band answers the same, depth is
    scenery and the loot table is the only thing that could ever carry it.
    """
    bands = collections.defaultdict(collections.Counter)
    kinds = collections.defaultdict(collections.Counter)
    chunks = 0

    for path in regions(save):
        for chunk in R.read_region(path):
            if chunk.get("Status") not in ("minecraft:full", "full"):
                continue
            chunks += 1
            for sy, (palette, data) in chunk_sections(chunk).items():
                lo = sy * 16
                kind = [_kind(p) for p in palette]
                idx = _indices(palette, data)
                if idx is None:
                    bands[lo][palette[0]] += 4096
                    kinds[lo][kind[0]] += 4096
                    continue
                for v, n in collections.Counter(idx).items():
                    if v < len(palette):
                        bands[lo][palette[v]] += n
                        kinds[lo][kind[v]] += n

    if not chunks:
        sys.exit("no full chunks")

    print("== strata over %d chunks, in 16-block bands" % chunks)
    print("   %-11s %6s %6s %6s   %s"
          % ("band", "rock", "fluid", "air", "commonest rock, by share of the rock there"))
    for lo in sorted(bands, reverse=True):
        total = sum(kinds[lo].values())
        if not total:
            continue
        rock = kinds[lo][2]
        top = [(p, n) for p, n in bands[lo].most_common() if _kind(p) == 2][:3]
        named = ", ".join("%s %.0f%%" % (p.split(":")[-1], 100.0 * n / max(1, rock))
                          for p, n in top)
        print("   %5d..%-5d %5.1f%% %5.1f%% %5.1f%%   %s"
              % (lo, lo + 15,
                 100.0 * rock / total,
                 100.0 * kinds[lo][1] / total,
                 100.0 * kinds[lo][0] / total,
                 named or "-"))

    # The question the table exists to answer, answered rather than left to the
    # eye: does the rock change with depth at all? Compared as the set of the
    # three commonest rocks in each band that has any.
    fills = [lo for lo in sorted(bands)
             if kinds[lo][2] and 100.0 * kinds[lo][2] / max(1, sum(kinds[lo].values())) > 1.0]
    signatures = set()
    for lo in fills:
        rock = kinds[lo][2]
        top = tuple(p for p, _n in bands[lo].most_common() if _kind(p) == 2)[:3]
        signatures.add(top)
    print()
    print("   %d band(s) hold more than 1%% rock, and they show %d distinct"
          % (len(fills), len(signatures)))
    print("   top-three rock signature(s).")
    if len(signatures) <= 1:
        print("   -> DEPTH MEANS NOTHING HERE. The same rock all the way down, so")
        print("      nothing in the terrain can carry a stratum. Anything vertical")
        print("      would have to be put there.")


def profile(save):
    """How much of each chunk is rock, and how much is fluid. Islands are sparse."""
    rocks = []
    fluids = []
    for path in regions(save):
        for chunk in R.read_region(path):
            if chunk.get("Status") not in ("minecraft:full", "full"):
                continue
            rock = fluid = 0
            for _y, (palette, data) in chunk_sections(chunk).items():
                kinds = [_kind(p) for p in palette]
                idx = _indices(palette, data)
                if idx is None:
                    if kinds[0] == 1:
                        fluid += 4096
                    elif kinds[0] == 2:
                        rock += 4096
                    continue
                for v, n in collections.Counter(idx).items():
                    if v >= len(kinds):
                        continue
                    if kinds[v] == 1:
                        fluid += n
                    elif kinds[v] == 2:
                        rock += n
            rocks.append(rock)
            fluids.append(fluid)
    if not rocks:
        sys.exit("no full chunks")

    # The full overworld column, 384 tall, deliberately - not the 256-tall band
    # octia:sky generates in. Keeping one denominator is what lets a sky save's
    # number be read against an ordinary save's.
    total = 384 * 256
    n = len(rocks)
    solids = sorted(r + f for r, f in zip(rocks, fluids))
    rocks.sort()
    fluids.sort()

    print("== per chunk, over %d chunks  (%% of a full 384-tall column stack)" % n)
    print("            %10s %10s %10s" % ("rock", "fluid", "rock+fluid"))
    for label, i in (("min", 0), ("p25", n // 4), ("median", n // 2),
                     ("p75", 3 * n // 4), ("max", n - 1)):
        print("   %-7s  %7d    %7d    %7d      %5.2f%% rock, %5.2f%% total"
              % (label, rocks[i], fluids[i], solids[i],
                 100.0 * rocks[i] / total, 100.0 * solids[i] / total))

    empty = sum(1 for s in solids if s == 0)
    dry = sum(1 for f in fluids if f == 0)
    print("   completely empty chunks : %d (%.1f%%)" % (empty, 100.0 * empty / n))
    print("   chunks with no fluid    : %d (%.1f%%)" % (dry, 100.0 * dry / n))


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


# Air is matched EXACTLY, and never as a substring. This is not fussiness.
#
# "air" was an ordinary substring rule here, and "air" is a substring of
# "stairs" - so minecraft:stone_brick_stairs, and every one of the trial
# chamber's waxed_*_cut_copper_stairs, rendered as VOID. In the one tool whose
# whole purpose is answering "is there void under this", solid stone read back
# as a hole. Any key three letters long is a trap in a substring table; the
# defence is to take air out of the table entirely.
AIR = frozenset(("air", "cave_air", "void_air"))

# Ordered, because the first match wins. Two orderings below are load-bearing
# and a reorder is a silent recolour:
#   "redstone" before "stone"     - redstone_ore ends in "stone"
#   "campfire" before "fire"      - campfire ends in "fire"
#   "mossy" before "moss_"        - and moss_ carries the underscore so it
#                                   cannot take mossy_cobblestone, which is
#                                   mostly cobble and should read as stone
# "deepslate" stays ahead of the ore keys, so deepslate_iron_ore reads as the
# depth it is at rather than as the ore in it - a terrain probe is asking about
# rock, not about mining.
PALETTE = [
    ("octia:ship_core", (255, 196, 60)),
    ("octia:andesite_frame_panel", (232, 120, 40)),

    # water and what lives in it
    ("bubble_column", (110, 160, 215)),
    ("water", (38, 82, 158)),
    ("ice", (170, 210, 235)),
    ("seagrass", (48, 120, 90)),
    ("kelp", (48, 120, 90)),

    # surface
    ("grass_block", (96, 152, 72)),
    ("short_grass", (110, 164, 80)),
    ("tall_grass", (110, 164, 80)),
    ("moss_", (78, 128, 56)),
    ("glow_lichen", (108, 138, 108)),
    ("snow", (238, 244, 248)),
    ("sand", (214, 202, 152)),
    ("gravel", (128, 124, 120)),
    ("dirt", (122, 88, 60)),
    ("clay", (150, 156, 162)),

    # flowers, fungi, crops - all one green, because on a 2px slice they are
    # one thing: something growing
    ("poppy", (172, 74, 74)),
    ("dandelion", (208, 196, 84)),
    ("dead_bush", (128, 100, 62)),
    ("mushroom", (168, 116, 104)),
    ("pumpkin", (206, 126, 42)),
    ("flower_pot", (140, 92, 74)),
    ("potted", (140, 92, 74)),

    # rock
    ("granite", (150, 106, 92)),
    ("diorite", (196, 196, 192)),
    ("andesite", (136, 138, 136)),
    ("calcite", (224, 226, 220)),
    ("smooth_basalt", (58, 58, 66)),
    ("basalt", (72, 70, 78)),
    ("deepslate", (66, 66, 72)),
    ("tuff", (90, 92, 84)),
    ("obsidian", (28, 20, 42)),
    ("magma_block", (176, 82, 34)),
    ("netherrack", (110, 50, 50)),
    ("soul_soil", (76, 60, 52)),

    # ore, and the geode
    ("redstone", (156, 40, 40)),
    ("lapis", (36, 72, 156)),
    ("diamond", (96, 200, 202)),
    ("emerald", (60, 172, 96)),
    ("amethyst", (150, 104, 200)),
    ("copper", (150, 110, 78)),
    ("iron_ore", (176, 152, 124)),
    ("coal_ore", (54, 54, 58)),
    ("gold", (200, 168, 72)),
    ("lava", (220, 110, 30)),

    # trees
    ("log", (92, 70, 44)),
    ("leaves", (66, 110, 54)),

    # things a person made - mineshafts, villages, trial chambers, ruins
    ("planks", (162, 130, 78)),
    ("fence", (140, 112, 68)),
    ("ladder", (140, 112, 68)),
    ("barrel", (132, 104, 64)),
    ("chest", (168, 124, 58)),
    ("table", (150, 118, 72)),
    ("button", (140, 112, 68)),
    ("bone_block", (222, 218, 198)),
    ("rail", (128, 112, 96)),
    ("chain", (74, 76, 82)),
    ("cobweb", (216, 218, 222)),
    ("tripwire", (150, 150, 140)),
    ("spawner", (46, 66, 78)),
    ("vault", (58, 78, 92)),
    ("dispenser", (112, 112, 114)),
    ("cauldron", (62, 62, 66)),
    ("decorated_pot", (176, 112, 84)),
    ("terracotta", (152, 94, 68)),
    ("glass", (206, 214, 220)),
    ("wool", (228, 228, 228)),
    ("concrete", (216, 216, 216)),
    ("bed", (188, 72, 72)),

    # light, which is what a ruin is found by
    ("campfire", (232, 152, 56)),
    ("fire", (236, 130, 40)),
    ("torch", (248, 208, 108)),
    ("lantern", (248, 208, 108)),
    ("candle", (242, 224, 168)),

    ("stone", (118, 118, 120)),
]

SKY_TOP = (16, 18, 26)      # the void above
SKY_BOT = (8, 9, 14)        # and below - darker, so down reads as down

UNMAPPED = (200, 60, 200)   # loud on purpose, so it gets noticed

# Every name that fell through, and how often. Reported at exit rather than
# only coloured, because "somebody will notice the magenta" is how a palette
# hole survives for weeks - and a hole in a thin seam is a few pixels nobody
# ever sees.
_unmapped = collections.Counter()


def colour_of(name):
    path = name.split(":")[-1]
    if path in AIR:
        return None
    for key, rgb in PALETTE:
        if key in name:
            return rgb
    _unmapped[name] += 1
    return UNMAPPED


def report_unmapped():
    """What the palette could not name. To stderr, so it cannot be piped away
    with the picture."""
    if not _unmapped:
        return
    total = sum(_unmapped.values())
    print("\n%d block(s) in %d kind(s) had no palette entry and were drawn "
          "magenta:" % (total, len(_unmapped)), file=sys.stderr)
    for name, n in _unmapped.most_common():
        print("   %-46s %d" % (name, n), file=sys.stderr)


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
    ap.add_argument("--strata", action="store_true",
                    help="what rock is at each depth, and whether depth means "
                         "anything at all in this world")
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
        report_unmapped()
    elif args.column:
        column(args.save, args.column[0], args.column[1])
    elif args.profile:
        profile(args.save)
    elif args.strata:
        strata(args.save)
    else:
        overview(args.save)


if __name__ == "__main__":
    main()
