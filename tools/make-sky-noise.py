"""
Derive octia:sky's noise settings from vanilla's floating_islands, and change
the one thing that decides what an island feels like.

WHY DERIVE RATHER THAN AUTHOR. The vanilla file is 94 KB, and almost all of it
is the surface rule - the several-hundred-branch sequence that decides grass
over dirt over stone, sand in deserts, snow at altitude, the whole overworld
palette. None of that wants changing; getting it wrong would make the islands
the right SHAPE out of the wrong MATERIAL. So this reads the vanilla settings
out of the Minecraft jar, replaces the density term, and writes the result.
Everything not named below is vanilla's, byte for byte.

WHAT WAS WRONG WITH THE VANILLA SHAPE, measured rather than felt. Generated
seed 1 at radius 16 and read the blocks back with tools/chunk-probe.py. The
island at spawn is:

    151  dirt
    152  grass_block

Two blocks. Not an island - a lily pad. And it is not a rare bad draw: about
three quarters of chunks carry material in most 16-block bands from y=16 to
y=176. So the sky manages to be crowded and flimsy at once - too much stuff to
let the void read as void, and each piece too thin to read as land.

WHY IT HAPPENS. Vanilla's expression reduces, at mid heights where both
vertical tapers are at 1, to exactly:

    density = end/base_3d_noise

That noise sits near zero with modest amplitude, so it crosses the solid
threshold shallowly and often - many thin sheets rather than few thick masses.

GAIN ALONE IS A NO-OP, and this file exists partly to stop anyone rediscovering
that. The first attempt multiplied the noise by 2.5, regenerated seed 1, and got
terrain IDENTICAL to vanilla block for block - the spawn column matched to the
line. The reason is arithmetic, not a bug: a block is solid where density is
above zero, and multiplying by a positive constant never moves a zero crossing.
k*N > 0 exactly when N > 0. Scaling a field that is about to be thresholded
changes nothing at all.

What moves a threshold is a BIAS. What makes some regions land and others open
sky is a term that varies slowly across the world. So the noise is replaced by:

    N  ->  gain * N  +  bias  +  swell * <low-frequency field>

    bias    lifts the whole sky toward solid. Positive means thicker islands
            AND more of them, because every marginal cell tips solid. On its
            own it fills the world in, which is the opposite of the goal.
    swell   the composition term, and the one that matters. A slow field over
            x and z, so whole regions run positive - substantial land, islands
            with mass - and whole regions run negative - open gulf, nothing at
            all for hundreds of blocks. This is what makes an archipelago
            instead of an even scatter, and it is what gives the eye somewhere
            to rest.
    gain    kept because it is not useless once a bias is present: with the
            field offset, gain decides how sharply land gives way to sky.

The arithmetic survives the tapers untouched. Vanilla computes
-23.4375 + G_top * (23.4375 + N); substituting for N and leaving both offsets
alone keeps the top and bottom fade exactly as vanilla shaped them. The offsets
are structural, not tuning.

Density is also CLAMPED before it is used - squeeze() takes clamp(x, -1, 1) -
so terms far outside that range are wasted. Keep bias and swell within about
a unit of each other and of the noise.

Usage:
  python tools/make-sky-noise.py --bias 0.6 --swell 0.9
  python tools/make-sky-noise.py --gain 1.0 --bias 0 --swell 0     # vanilla exactly
"""

import argparse
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

VANILLA = "data/minecraft/worldgen/noise_settings/floating_islands.json"

# The leaf this file exists to wrap. It appears exactly once in vanilla's
# floating-islands router, as a bare string reference.
ISLAND_NOISE = "minecraft:end/base_3d_noise"

DEFAULT_OUT = os.path.join(
    REPO, "src", "main", "resources", "data", "octia",
    "worldgen", "noise_settings", "sky.json")


def find_jar():
    """The deobfuscated 1.21.1 jar Loom already unpacked for the build.

    Looked up rather than configured: it is a build artifact with a hashed
    path, and a hardcoded one would rot on the next Loom or Minecraft bump.
    """
    roots = [
        os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom"),
        os.path.join(REPO, ".gradle", "loom-cache"),
    ]
    best = None
    for root in roots:
        for dirpath, _dirs, files in os.walk(root):
            for f in files:
                if f.endswith(".jar") and "minecraft-merged" in f and "sources" not in f:
                    p = os.path.join(dirpath, f)
                    try:
                        with zipfile.ZipFile(p) as z:
                            z.getinfo(VANILLA)
                    except Exception:
                        continue
                    size = os.path.getsize(p)
                    if best is None or size > best[0]:
                        best = (size, p)
    if not best:
        sys.exit("could not find a Minecraft jar containing %s" % VANILLA)
    return best[1]


def load_vanilla():
    jar = find_jar()
    with zipfile.ZipFile(jar) as z:
        with z.open(VANILLA) as f:
            return json.load(f), jar


def swell_field(scale):
    """A slow field over x and z, and nothing at all over y.

    minecraft:continentalness is borrowed rather than a new noise invented: it
    is already registered, already has a long wavelength, and is already the
    noise vanilla uses to decide where land is at all. y_scale is 0 on purpose -
    a term that varied with height would make islands thicker at some altitudes
    rather than making some REGIONS land and others sky, and the region is the
    thing the eye reads.
    """
    return {
        "type": "minecraft:shifted_noise",
        "noise": "minecraft:continentalness",
        "shift_x": "minecraft:shift_x",
        "shift_y": 0.0,
        "shift_z": "minecraft:shift_z",
        "xz_scale": scale,
        "y_scale": 0.0,
    }


def reshape(node, gain, bias, swell, scale, seen):
    """Replace every island-noise reference with gain*N + bias + swell*field.

    Walks the whole router rather than the one path known to contain it, so a
    Minecraft version that reshapes the expression still gets the treatment -
    and so `seen` can prove it was applied at all. A silent zero-replacement
    would write a file identical to vanilla, and the terrain would be unchanged
    for reasons nobody could see. That already happened once with gain.
    """
    if isinstance(node, str):
        if node != ISLAND_NOISE:
            return node
        seen.append(1)

        term = {"type": "minecraft:mul", "argument1": gain, "argument2": node} if gain != 1.0 else node
        if swell:
            term = {
                "type": "minecraft:add",
                "argument1": term,
                "argument2": {"type": "minecraft:mul",
                              "argument1": swell,
                              "argument2": swell_field(scale)},
            }
        if bias:
            term = {"type": "minecraft:add", "argument1": bias, "argument2": term}
        return term
    if isinstance(node, list):
        return [reshape(v, gain, bias, swell, scale, seen) for v in node]
    if isinstance(node, dict):
        return {k: reshape(v, gain, bias, swell, scale, seen) for k, v in node.items()}
    return node


def pattern_knobs(word):
    """A thread's ring, as generator settings. See docs/THREADS.md.

    A ring of legs is a word in base 8 - one digit per leg, each the leg's
    heading snapped to one of eight, which is what Mystery.markFor already
    answers. This turns that word into terrain, so every pattern HAS a world and
    "does it exist" becomes "has anyone opened it".

    THIS FILE DOES NOT COMPUTE THE WORD, and must not learn how. Ring.from and
    Sightlines.step are Java, and ROADMAP records what happened the last time a
    Python tool transposed the lattice: sightline-map.py drew a different lattice
    than the game generated for 12.7% of cells, and the check that was supposed
    to catch it compared four cells. The word comes in as an argument, read off
    the world by something that IS the lattice.

    Bounded on purpose. make-sky-noise's own note says density is clamped to
    [-1, 1] before use, so terms far outside that are wasted; every knob below
    stays inside the range the sky settings were tuned in.
    """
    digits = [int(c) for c in word]
    if not digits or any(d > 7 for d in digits):
        sys.exit("a pattern is a word in base 8, one digit per leg: e.g. 2604")

    # A ring has no start, so the same circuit entered at a different cell would
    # otherwise name a different world. Canonical = least rotation.
    rotations = [tuple(digits[i:] + digits[:i]) for i in range(len(digits))]
    canon = min(rotations)
    if tuple(digits) != canon:
        print("pattern %s canonicalises to %s (a ring has no start)"
              % (word, "".join(str(d) for d in canon)))
    digits = list(canon)

    turns = sum(1 for i in range(len(digits))
                if digits[i] != digits[i - 1])

    return {
        "word": "".join(str(d) for d in digits),
        "gain": 1.0,
        "bias": round((sum(digits) % 5) * 0.05, 4),
        "swell": round(0.2 + (turns % 5) * 0.15, 4),
        "swell_scale": round(0.06 + (len(digits) % 4) * 0.02, 4),
        # Dry, every one of them, and this is a constraint rather than a taste.
        # octia:sky put sea_level 96 over a band whose min_y is 0 on a generator
        # with no floor, and the sea drains out of the bottom of the world -
        # measured, docs/ISLANDS.md X. Until that has a floor, a pattern world
        # does not get water. A generator that ships a known leak by the
        # thousand is worse than one that ships none.
        "sea_level": -64,
        "aquifers": False,
    }


def write_preset(word, repo):
    """The world_preset that points at a pattern's noise settings.

    Structurally sky.json's, which is proven to load. The Nether and the End are
    left exactly as vanilla wrote them, for the reason ISLANDS.md IX gives: this
    changes where you arrive, not the era stack under it.
    """
    preset = {
        "_comment": ("DERIVED - do not hand-edit, regenerate. "
                     "python tools/make-sky-noise.py --pattern %s" % word),
        "dimensions": {
            "minecraft:overworld": {
                "type": "minecraft:overworld",
                "generator": {
                    "type": "minecraft:noise",
                    "biome_source": {"type": "minecraft:multi_noise",
                                     "preset": "minecraft:overworld"},
                    "settings": "octia:thread_%s" % word,
                },
            },
            "minecraft:the_nether": {
                "type": "minecraft:the_nether",
                "generator": {
                    "type": "minecraft:noise",
                    "biome_source": {"type": "minecraft:multi_noise",
                                     "preset": "minecraft:nether"},
                    "settings": "minecraft:nether",
                },
            },
            "minecraft:the_end": {
                "type": "minecraft:the_end",
                "generator": {
                    "type": "minecraft:noise",
                    "biome_source": {"type": "minecraft:the_end"},
                    "settings": "minecraft:end",
                },
            },
        },
    }
    out = os.path.join(repo, "src", "main", "resources", "data", "octia",
                       "worldgen", "world_preset", "thread_%s.json" % word)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(preset, f, indent=2)
        f.write("\n")
    return out


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pattern", default=None, metavar="WORD",
                    help="a thread's ring as a base-8 word, one digit per leg "
                         "(e.g. 2604). Derives every knob below from it and "
                         "writes octia:thread_WORD - both the noise settings "
                         "and the world preset. See docs/THREADS.md.")
    ap.add_argument("--gain", type=float, default=1.0,
                    help="multiplier on the island noise. On its own this does NOTHING - "
                         "see the note at the top of this file.")
    ap.add_argument("--bias", type=float, default=0.0,
                    help="constant offset. Positive fills the sky in; this is the knob "
                         "that actually moves the solid/air threshold.")
    ap.add_argument("--swell", type=float, default=0.0,
                    help="amplitude of the slow regional field: land here, open sky there.")
    ap.add_argument("--swell-scale", type=float, default=0.12,
                    help="xz_scale of that field. Smaller is slower and larger-featured.")

    # Water. floating_islands ships with sea_level -64 and aquifers off, which is
    # why an octia:sky world contains no water of any kind - not a lake, not a
    # spring, not a waterfall. An ocean or river world cannot exist until these
    # two move, and whether they CAN move without the islands drowning is a
    # question for a generated world rather than an argument.
    ap.add_argument("--sea-level", type=int, default=None,
                    help="y of the sea. Vanilla floating_islands is -64, i.e. no sea at all.")
    ap.add_argument("--aquifers", dest="aquifers", action="store_true", default=None,
                    help="let the generator carve water bodies inside the terrain.")
    ap.add_argument("--no-aquifers", dest="aquifers", action="store_false")

    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    # --pattern overrides every knob, because a pattern world whose terrain did
    # not follow from its pattern would be a lie told in data.
    pattern = None
    if args.pattern is not None:
        k = pattern_knobs(args.pattern)
        pattern = k["word"]
        args.gain, args.bias = k["gain"], k["bias"]
        args.swell, args.swell_scale = k["swell"], k["swell_scale"]
        args.sea_level, args.aquifers = k["sea_level"], k["aquifers"]
        if args.out is None:
            args.out = os.path.join(
                REPO, "src", "main", "resources", "data", "octia",
                "worldgen", "noise_settings", "thread_%s.json" % pattern)
        print("pattern %s -> gain=%g bias=%g swell=%g scale=%g sea=%d aquifers=%s"
              % (pattern, args.gain, args.bias, args.swell, args.swell_scale,
                 args.sea_level, args.aquifers))
    if args.out is None:
        args.out = DEFAULT_OUT

    settings, jar = load_vanilla()
    print("vanilla from: %s" % os.path.basename(jar))

    seen = []
    settings["noise_router"] = reshape(settings["noise_router"], args.gain, args.bias,
                                       args.swell, args.swell_scale, seen)
    if not seen:
        sys.exit("the island noise reference was not found - vanilla's expression has changed, "
                 "and reshaping it blindly would be worse than failing here")
    print("applied at %d reference(s): gain=%.3f bias=%+.3f swell=%.3f @ scale %.3f"
          % (len(seen), args.gain, args.bias, args.swell, args.swell_scale))

    if args.sea_level is not None:
        print("sea_level %d -> %d" % (settings["sea_level"], args.sea_level))
        settings["sea_level"] = args.sea_level
    if args.aquifers is not None:
        print("aquifers_enabled %s -> %s" % (settings["aquifers_enabled"], args.aquifers))
        settings["aquifers_enabled"] = args.aquifers
    if args.gain != 1.0 and not args.bias and not args.swell:
        print("  WARNING: gain with no bias and no swell is a no-op. "
              "It cannot move a zero crossing.")

    # Provenance, written into the artifact rather than remembered beside it.
    #
    # This file emits ~2,250 lines of DERIVED data, and for a day the inputs
    # that produced the shipped one were unrecoverable from the repo: they had
    # to be read back out of the expression tree by hand. The usage example at
    # the top of THIS file names different values than what shipped, so a
    # reader following the docs regenerated a different world. A derived file
    # that cannot say what derived it is a file nobody can safely change.
    #
    # An unknown key is safe here: Mojang's settings codec reads the fields it
    # names and ignores the rest, which is why datapacks have used _comment for
    # years. Verified by generating a world with this key present and reading
    # the blocks back - see worlds/slices/README.md.
    if pattern is not None:
        # The pattern IS the provenance. Recording the derived knobs instead
        # would invite somebody to edit one of them, and then the terrain would
        # no longer follow from the word it is named after.
        flags = ["--pattern %s" % pattern]
    else:
        flags = ["--gain %g" % args.gain, "--bias %g" % args.bias,
                 "--swell %g" % args.swell, "--swell-scale %g" % args.swell_scale]
        if args.sea_level is not None:
            flags.append("--sea-level %d" % args.sea_level)
        if args.aquifers is True:
            flags.append("--aquifers")
        elif args.aquifers is False:
            flags.append("--no-aquifers")

    settings = {
        "_comment": ("DERIVED - do not hand-edit, regenerate. "
                     "python tools/make-sky-noise.py %s  "
                     "(from vanilla %s in %s)"
                     % (" ".join(flags), VANILLA, os.path.basename(jar))),
        **settings,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(settings, f, indent=2)
        f.write("\n")
    # relpath throws across drives on Windows - "path is on mount 'C:', start on
    # mount 'D:'" - which made --out to any other drive crash AFTER writing the
    # file correctly. The path is cosmetic here; the write is not.
    try:
        shown = os.path.relpath(args.out, REPO)
    except ValueError:
        shown = args.out
    print("wrote %s  (%d bytes)" % (shown, os.path.getsize(args.out)))

    if pattern is not None:
        preset = write_preset(pattern, REPO)
        try:
            shown = os.path.relpath(preset, REPO)
        except ValueError:
            shown = preset
        print("wrote %s" % shown)
        print("open it with: tools\\new-world.ps1 -Type thread_%s" % pattern)
        print("NOTE: not tagged into minecraft:normal, so it does NOT appear on")
        print("      the world-type button. That is deliberate - a pattern world")
        print("      is reached by finding its thread, not by scrolling a list.")


if __name__ == "__main__":
    main()
