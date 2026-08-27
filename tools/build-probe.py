"""
Find where someone BUILT in a save, and say where to stand to see it.

world-report.py answers "what is in this world". chunk-probe.py answers "is the
terrain the terrain I think it is". Neither answers the question you actually
have in front of a 44,576-chunk save: OF ALL THIS, WHICH BITS ARE MINE.

Landoak is 8 GB and 47.8 hours of play. The handiwork in it occupies a few
dozen chunks. Opening the game and flying around looking for it is the method
this file exists to replace.

Why not a list of "player blocks". That was the first design and it is wrong on
day one. These saves run four hundred mods; any hand-written list of what a
person places is out of date before it is saved, and the two failure modes are
both fatal - a modded decoration block is missed, or a modded ORE is called
handiwork and every cave lights up.

So the test is statistical and self-calibrating. Manufactured-looking blocks are
counted per chunk, and then compared AGAINST THE WORLD'S OWN DISTRIBUTION. A
badlands save is full of terracotta and a nether save is full of bricks; both
raise that world's baseline evenly, and a build still stands far above its own
world's ninetieth percentile. The pattern list below therefore does not need to
be right, only consistent - it is a ruler, not a judgement.

The second correction is structure starts. Villages place planks, stairs, doors
and beds; mineshafts place rails and fences; strongholds place stone brick and
bookshelves. Called handiwork, every one of them is a false positive, and in a
ctov/betterdungeons pack there are hundreds. But the game WRITES DOWN where it
generated each of them, in the chunk's own `structures.starts`. So they are not
guessed at - they are read, and excluded by footprint.

What survives both is a place a person made.

Usage:
  python tools/build-probe.py <save>                  ranked places, with /tp lines
  python tools/build-probe.py <save> --top 5          only the best five
  python tools/build-probe.py <save> --radius 128     widen structure exclusion
  python tools/build-probe.py <save> --keep-structures  do not exclude them
  python tools/build-probe.py <save> --json           machine-readable
"""

import argparse
import collections
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def _load(name, filename):
    """Borrow chunk-probe's readers rather than writing a third set.

    chunk-probe imports world-report for the same reason, so loading it here
    brings both. Their filenames have hyphens and cannot be imported by name.
    """
    path = os.path.join(HERE, filename)
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


P = _load("chunk_probe", "chunk-probe.py")     # section decoding, regions()
R = P.R                                        # world-report, via chunk-probe


# ---- the ruler ------------------------------------------------------------
#
# Checked in order: anything matching NATURAL is never manufactured, whatever
# else it looks like. `grass_block` ends in _block and `deepslate_bricks` looks
# like masonry; both are the ground.

NATURAL = (
    "air", "grass_block", "dirt", "podzol", "mycelium", "mud", "clay",
    "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite",
    "gravel", "sand", "sandstone", "netherrack", "basalt", "blackstone",
    "obsidian", "magma", "bedrock", "ore", "_log", "_wood", "leaves", "sapling",
    "water", "lava", "ice", "snow", "moss", "vine", "flower", "grass", "fern",
    "kelp", "coral", "seagrass", "sculk", "amethyst", "dripstone", "soul_",
    "bone_block", "packed_mud", "rooted_dirt", "farmland", "path",
    # Badlands is made of terracotta - white, orange, yellow, brown, red and
    # light grey, in bands, by the hundred thousand. Counted as manufactured it
    # outscores every real build on the map: Landoak's top two "places" were one
    # chunk of white_terracotta each, at 19,221 and 16,481 blocks, against a
    # genuine wool-and-bookshelf build's 561. The glazed kind is still crafted
    # and is let through above.
    "terracotta",
)

MADE = (
    "_planks", "_stairs", "_slab", "_fence", "_gate", "_door", "_trapdoor",
    "_wall", "_pane", "_carpet", "wool", "brick", "polished_", "chiseled_",
    "smooth_", "cut_", "glazed_", "concrete", "glass", "lantern", "torch",
    "redstone", "piston", "hopper", "dropper", "dispenser", "observer",
    "rail", "ladder", "chain", "_bed", "sign", "banner", "barrel", "chest",
    "furnace", "crafting", "anvil", "bookshelf", "terracotta", "quartz",
    "lamp", "wire", "cable", "pipe", "conduit", "casing", "machine", "panel",
    "scaffolding", "shulker_box", "beacon", "candle",
)


def manufactured(name):
    short = name.split(":")[-1]
    # Glazed terracotta is always crafted and no biome makes it, so it is
    # checked before the natural list swallows every "*_terracotta".
    if "glazed_terracotta" in short:
        return True
    for n in NATURAL:
        if n in short:
            return False
    for m in MADE:
        if m in short:
            return True
    return False


# ---- one pass over the save ----------------------------------------------

def survey(save):
    """Per-chunk manufactured-palette hits, plus every structure start."""
    score = {}                                   # (cx, cz) -> hits
    palette_of = collections.defaultdict(collections.Counter)
    starts = []                                  # (block_x, block_z)
    chunks = 0

    for path in P.regions(save):
        for chunk in R.read_region(path):
            cx, cz = chunk.get("xPos"), chunk.get("zPos")
            if cx is None or cz is None:
                continue
            chunks += 1

            for sid, start in ((chunk.get("structures") or {}).get("starts") or {}).items():
                if not isinstance(start, dict):
                    continue
                if str(start.get("id", "")).upper() == "INVALID":
                    continue
                sx, sz = start.get("ChunkX"), start.get("ChunkZ")
                if sx is not None and sz is not None:
                    starts.append((sx * 16 + 8, sz * 16 + 8))

            # Blocks, not palette entries. Counting entries was the first
            # version and it silently caps: a section lists each block type
            # once, so the busiest chunk anyone can build tops out near sixty
            # and a 3x-p90 threshold becomes unreachable. Test - 2,918 blocks
            # placed by hand - scored 0 places under that metric.
            hits = 0
            for section in (chunk.get("sections") or []):
                palette, data = P.section_palette(section)
                if not palette:
                    continue
                made = {i for i, nm in enumerate(palette) if manufactured(nm)}
                if not made:
                    continue                      # skips every natural section
                idx = P._indices(palette, data)
                if idx is None:
                    # Uniform section - no data array, the whole 16-cube is
                    # palette[0]. A solid 4096 of anything is worth knowing.
                    if 0 in made:
                        hits += 4096
                        palette_of[(cx, cz)][palette[0]] += 4096
                    continue
                counts = collections.Counter(idx)
                for i in made:
                    n = counts.get(i, 0)
                    if n:
                        hits += n
                        palette_of[(cx, cz)][palette[i]] += n
            if hits:
                score[(cx, cz)] = hits

    return chunks, score, palette_of, sorted(set(starts))


# ---- thresholds and clustering -------------------------------------------

def percentile(values, q):
    if not values:
        return 0
    s = sorted(values)
    i = int(round((len(s) - 1) * q))
    return s[i]


def structure_chunks(starts, radius):
    """Every chunk coord within `radius` blocks of a generated structure.

    Structures are excluded by footprint, not by chunk. A ctov village start is
    one coordinate but the village is a hundred blocks across, so a
    chunk-for-chunk match would exclude the middle and leave the outskirts
    looking like handiwork.

    Built as a set rather than tested per chunk against every start: Endsal has
    eight hundred starts over forty-five thousand chunks, and the pairwise form
    is thirty-six million distance checks to answer a question a lookup answers.
    """
    span = radius // 16 + 1
    covered = set()
    for sx, sz in starts:
        scx, scz = sx >> 4, sz >> 4
        for dx in range(-span, span + 1):
            for dz in range(-span, span + 1):
                if (dx * 16) ** 2 + (dz * 16) ** 2 <= radius * radius + 128:
                    covered.add((scx + dx, scz + dz))
    return covered


def cluster(chunks, gap=3):
    """Connected components over chunk coords, joining across small gaps.

    A build is rarely a solid rectangle of chunks - a tower with a path to it
    reads as two islands two chunks apart. Joining across a gap keeps one place
    from being reported as five.
    """
    todo = set(chunks)
    out = []
    while todo:
        seed = todo.pop()
        group = [seed]
        edge = [seed]
        while edge:
            cx, cz = edge.pop()
            for dx in range(-gap, gap + 1):
                for dz in range(-gap, gap + 1):
                    n = (cx + dx, cz + dz)
                    if n in todo:
                        todo.discard(n)
                        group.append(n)
                        edge.append(n)
        out.append(group)
    return out


# ---- report ---------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("save")
    ap.add_argument("--top", type=int, default=20)
    ap.add_argument("--radius", type=int, default=96,
                    help="structure exclusion footprint, blocks (default 96)")
    ap.add_argument("--gap", type=int, default=3,
                    help="chunks of gap still counted as one place (default 3)")
    ap.add_argument("--keep-structures", action="store_true",
                    help="do not exclude generated structures")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    save = args.save.rstrip("\\/")
    if not os.path.isdir(os.path.join(save, "region")):
        # A world kept only as SimpleBackups zips has no region directory of its
        # own. That is not a broken path and should not read like one.
        zips = [f for f in os.listdir(save) if f.endswith(".zip")] \
            if os.path.isdir(save) else []
        if zips:
            sys.exit("%s holds no live save - only %d backup zip(s).\n"
                     "Unzip the newest one somewhere and probe that:\n"
                     "  %s" % (os.path.basename(save), len(zips), sorted(zips)[-1]))
        sys.exit("no region directory under %s" % save)

    chunks, score, palette_of, starts = survey(save)
    if not score:
        print("== %s" % os.path.basename(save))
        print("   %d chunks, nothing manufactured anywhere. Nobody built here." % chunks)
        return

    # Generated structures come out BEFORE the baseline is measured, not after.
    # Measuring first was the second bug and it was subtle: percentiles taken
    # over every chunk holding anything manufactured are dominated by villages,
    # so the bar becomes "busier than a typical village" and real handiwork sits
    # underneath it. Test's own redstone works - 546 blocks in their best chunk
    # - were lost to a threshold of 954 set by the fifty villages that its
    # superflat preset generates. Exclude first and the baseline is what it
    # should have been all along: the empty world, not the furnished one.
    # The radius has to yield to the pack. 96 blocks is right for a vanilla save
    # and catastrophic on Landoak, which has 1,801 structure starts: at 96 they
    # cover 68,801 chunks in a world that only HAS 65,689, so the exclusion eats
    # the entire map and every build with it. An exclusion that removes almost
    # everything has stopped being an exclusion. So it shrinks until it leaves a
    # world to measure - and says which radius it settled on, because that
    # number is a fact about the pack worth knowing.
    radius = args.radius
    covered = set()
    if not args.keep_structures and starts:
        while True:
            covered = structure_chunks(starts, radius)
            inside = sum(1 for c in score if c in covered)
            if inside <= 0.70 * len(score) or radius <= 16:
                break
            radius //= 2

    free = {c: n for c, n in score.items() if c not in covered}
    structural = {c: n for c, n in score.items() if c in covered}

    values = list(free.values()) or list(score.values())
    p50, p90 = percentile(values, 0.50), percentile(values, 0.90)
    # 3x the ninetieth percentile, but never below 150 BLOCKS - on a save where
    # almost nothing is built p90 can be 2, and 6 would call a village well a
    # town. 150 is about a small hut, which is the smallest thing worth flying to.
    threshold = max(150, p90 * 3)

    hot = [c for c, n in free.items() if n >= threshold]
    excluded = sum(1 for n in structural.values() if n >= threshold)

    # Second signal: materials nobody generated.
    #
    # Density alone cannot find every build, and Test is the proof. Its redstone
    # works are SPARSER than the villages around them - 546 manufactured blocks
    # in their best chunk against a village chunk's 954 - so no threshold set
    # from density separates them, in either direction, ever. Raising the bar
    # loses the build; lowering it returns fifty villages.
    #
    # But `morered:red_alloy_wire` is a mod block that no structure in that save
    # generates, and it appears in a handful of chunks. A manufactured material
    # that is rare across the whole world AND absent from every structure
    # footprint was carried there by a person. The chunk holding it is a place,
    # however thinly built - so it is admitted regardless of score.
    in_chunks = collections.Counter()
    in_structures = collections.Counter()
    for c, mats in palette_of.items():
        for m in mats:
            in_chunks[m] += 1
            if c in covered:
                in_structures[m] += 1
    rare_max = max(4, int(0.02 * len(score)))
    signature = {m for m, n in in_chunks.items()
                 if n <= rare_max and in_structures[m] == 0}
    by_signature = [c for c, mats in palette_of.items()
                    if c not in covered and not set(mats).isdisjoint(signature)]
    hot = sorted(set(hot) | set(by_signature))

    places = []
    for group in cluster(hot, args.gap):
        total = sum(score[c] for c in group)
        mats = collections.Counter()
        for c in group:
            mats.update(palette_of[c])
        xs = [c[0] for c in group]
        zs = [c[1] for c in group]
        cx = (min(xs) + max(xs)) // 2
        cz = (min(zs) + max(zs)) // 2
        near = None
        if starts:
            bx, bz = cx * 16 + 8, cz * 16 + 8
            sx, sz = min(starts, key=lambda s: (s[0] - bx) ** 2 + (s[1] - bz) ** 2)
            near = int(((sx - bx) ** 2 + (sz - bz) ** 2) ** 0.5)
        places.append({
            "x": cx * 16 + 8, "z": cz * 16 + 8,
            "chunks": len(group), "score": total,
            "materials": [m for m, _ in mats.most_common(5)],
            "signature": sorted(set(mats) & signature)[:4],
            "nearest_structure_blocks": near,
        })
    places.sort(key=lambda p: -p["score"])

    if args.json:
        print(json.dumps({
            "save": os.path.basename(save), "chunks": chunks,
            "p50": p50, "p90": p90, "threshold": threshold,
            "structure_starts": len(starts), "excluded_chunks": excluded,
            "places": places[:args.top],
        }, indent=2))
        return

    print("== %s" % os.path.basename(save))
    print("   %d full chunks, %d with anything manufactured" % (chunks, len(score)))
    print("   baseline (manufactured blocks per chunk): p50 %d, p90 %d" % (p50, p90))
    print("   threshold %d  -  %d chunk(s) over it" % (threshold, len(hot) + excluded))
    if starts:
        print("   %d structure start(s) on file covering %d chunk(s); %d over the bar"
              % (len(starts), len(covered), excluded))
        print("   ...excluded as generated, not built.  (radius %d%s)"
              % (radius, ", shrunk to fit" if radius != args.radius else ""))
    print()

    if not places:
        # Two different nothings, and saying the wrong one sends you looking in
        # the wrong place. Either nobody cleared the bar, or everybody who did
        # was a village.
        print("   NO HANDIWORK FOUND.")
        if excluded:
            print("   %d chunk(s) cleared the threshold and every one of them sat"
                  % excluded)
            print("   inside a generated structure. --keep-structures shows those.")
        else:
            print("   Nothing came near the threshold - the busiest chunk held %d"
                  % max(values))
            print("   manufactured block(s) against a bar of %d. Nobody built here."
                  % threshold)
        return

    print("   PLACES (%d found, best first)" % len(places))
    for i, p in enumerate(places[:args.top], 1):
        print()
        print("   %2d. x %-7d z %-7d   %d chunk(s), score %d"
              % (i, p["x"], p["z"], p["chunks"], p["score"]))
        print("       materials: %s" % ", ".join(p["materials"]))
        if p["signature"]:
            print("       carried in: %s" % ", ".join(p["signature"]))
        if p["nearest_structure_blocks"] is not None:
            print("       nearest generated structure: %d blocks"
                  % p["nearest_structure_blocks"])
        print("       /tp @s %d ~ %d" % (p["x"], p["z"]))


if __name__ == "__main__":
    main()
