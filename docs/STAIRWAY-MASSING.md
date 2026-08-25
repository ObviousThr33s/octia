# STAIRWAY-MASSING.md - the prism and its two masked mouths

Set down 2026-08-24 from the owner's direction, given verbatim:

> *"use spheres and spheres with flat slices for face to face connection on the stairs.
> as in take any tall generated land mass and find the base at ground level and the top
> then gen rectangular prism then at the top and the bottom generate spheres to 'mask'
> the openings"*

This supersedes nothing yet - `StairwayFeature` as shipped is footing-verified switchback
stairs cut into a riser, and it stays exactly as it is until this lands. Corrections are
new entries; the switchbacks are not deleted, they are the fallback when the massing
refuses a site.

---

## I. The shape, in one paragraph

Find a tall landmass: a base at ground level and a top. Run a rectangular prism between
them - that is the shaft the stair climbs inside. Then put a sphere at each end, and cut
each sphere flat exactly where it meets the prism's end face, so sphere and prism join
**face to face** rather than intersecting into a mess. The two spheres are not decoration;
they are **masks**. A prism driven into a hillside ends in a raw rectangular mouth that
reads as a hole somebody cut. A sphere swallowing that junction reads as a place where the
ground opens.

Nothing here is a new block palette. It is a **volume test**, and the stair, the landings
and the andesite cladding are placed inside whatever volume it answers for.

## II. The volume, as arithmetic

The whole shape is three solids unioned, in coordinates relative to the centre of the
prism's **base** face - so `dy = 0` is the bottom face and `dy = height` is the top.

```
prism      : |dx| <= halfW  and  |dz| <= halfD  and  0 <= dy <= height
bottom cap : dy <= 0        and  dx*dx + dy*dy + dz*dz          <= r*r
top cap    : dy >= height   and  dx*dx + (dy-height)^2 + dz*dz  <= r*r
```

**The flat slice is the `dy <= 0` and `dy >= height` clause, and that is the entire trick.**
Each sphere is centred exactly on the prism's end face and then only its outer half is
kept. The cut plane and the prism's end face are the same plane, so the join is flush by
construction - there is no seam to tune, no overlap to clean up, and no case where a
sphere bulges back up inside the shaft.

Two constraints on `r` that are not taste:

- `r >= sqrt(halfW^2 + halfD^2)`, or the cap is narrower than the prism's own corner
  diagonal and the mouth pokes out of the thing meant to mask it. The cap must at minimum
  swallow the footprint's corners.
- `halfW + r <= 7` and `halfD + r <= 7` measured from the site centre, or the site leaves
  its owning chunk. One-site-one-chunk is a law, not a preference: a 16-wide chunk gives
  7 blocks of reach either side of centre once the centre column is spent.

Those two together bound the design: a 3x3 shaft (`halfW = halfD = 1`) admits `r` from 2
to 6; a 5x5 shaft admits `r` from 3 to 5. Wider than 5x5 has no legal cap and must fall
back to switchbacks.

## III. The class

A pure function with no Minecraft imports, tested by JUnit without a world - the
`Isle` / `Bindle` / `SailRig` / `Watershed` pattern, which is how every geometric claim in
this mod is made checkable.

```java
package com.serenity.octia.world;

/**
 * The volume a stair tower occupies: a prism between two ground levels, with a
 * sphere at each end cut flat against the prism's own end face.
 *
 * <p>The masks are the point. A prism driven into a hillside ends in a raw
 * rectangular mouth, which reads as a hole somebody cut; a sphere swallowing
 * that junction reads as a place where the ground opens. Cutting each sphere on
 * the plane of the face it sits against makes the join flush by construction,
 * so there is no seam to tune and no case where a cap bulges back inside the
 * shaft.
 *
 * <p>Coordinates are relative to the centre of the prism's base face, so
 * {@code dy == 0} is that face and {@code dy == height} is the top one.
 */
public final class Massing {

    private Massing() {
    }

    /** The largest half-extent a site may reach from its centre and stay in its chunk. */
    public static final int REACH = 7;

    /** Whether a cell is inside the prism or inside either masked mouth. */
    public static boolean holds(int dx, int dy, int dz,
            int halfW, int halfD, int height, int radius) {
        if (inPrism(dx, dy, dz, halfW, halfD, height)) {
            return true;
        }
        if (dy <= 0) {
            return inBall(dx, dy, dz, radius);
        }
        if (dy >= height) {
            return inBall(dx, dy - height, dz, radius);
        }
        return false;
    }

    static boolean inPrism(int dx, int dy, int dz, int halfW, int halfD, int height) {
        return dy >= 0 && dy <= height
                && Math.abs(dx) <= halfW && Math.abs(dz) <= halfD;
    }

    static boolean inBall(int dx, int dy, int dz, int radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * The smallest cap that can swallow this footprint's corners.
     *
     * <p>A cap narrower than the prism's own corner diagonal leaves the mouth
     * sticking out of the thing meant to hide it, so this is a floor, not a
     * suggestion.
     */
    public static int minRadius(int halfW, int halfD) {
        return (int) Math.ceil(Math.sqrt(halfW * halfW + halfD * halfD));
    }

    /** Whether a site of this size stays inside the chunk it is sited in. */
    public static boolean fitsChunk(int halfW, int halfD, int radius) {
        return halfW + radius <= REACH && halfD + radius <= REACH;
    }
}
```

## IV. Finding the landmass, without reading a heightmap

`RuinGround`'s header bans heightmap reads in features - the `_WG` maps are empty outside
natural generation, and this repo has already paid for that lesson once. So base and top
come from the sanctioned helpers:

- **base**: `RuinGround.surfaceNear(level, centre, ...)` at the site column, which is the
  same call every other feature sites itself with.
- **top**: walk up the column from the base while the terrain is solid, stopping at the
  first air with headroom. That is a read of blocks the feature already owns, not a
  heightmap.
- **refuse rather than level.** If the run from base to top is shorter than the minimum a
  tower is worth, or the column is interrupted by a cave, or either cap's volume would
  leave the chunk, the site is declined and the existing switchback path runs instead.
  `ObeliskFeature` refuses rather than levels; this does the same.

The survey-then-write contract from `WatershedFeature` is the shape to copy: survey the
whole volume read-only, return a fully validated plan or `null`, and write nothing at all
on a refusal. Zero partial towers.

## V. What the gametests assert

Pure, in `MassingTest` (JUnit, no world):

- every prism cell is held, and every cell outside prism and both balls is not
- **the flat slice holds**: no cell with `0 < dy < height` is held outside the prism, so a
  cap can never bulge back inside the shaft at any radius
- the two caps are mirror images: `holds(dx, -k, dz, ...) == holds(dx, height + k, dz, ...)`
- `minRadius` never returns a radius that leaves a footprint corner outside its cap
- `fitsChunk` refuses exactly the sizes that would cross a chunk border

In-world, in `StairwayGameTest`:

- a placed tower's every cell lies inside `Massing.holds` for its own parameters - the
  mask is the contract, and nothing is written outside it
- the tower stays inside its owning chunk
- the stair inside it still climbs monotonically and every step still rests on solid,
  which is what the existing switchback tests already assert and must keep asserting
- a site too wide for any legal cap places switchbacks and no tower

`[2026-08-24]`
