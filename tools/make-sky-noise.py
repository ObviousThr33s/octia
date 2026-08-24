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


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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

    ap.add_argument("--out", default=DEFAULT_OUT)
    args = ap.parse_args()

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

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(settings, f, indent=2)
        f.write("\n")
    print("wrote %s  (%,d bytes)".replace("%,d", "%d")
          % (os.path.relpath(args.out, REPO), os.path.getsize(args.out)))


if __name__ == "__main__":
    main()
