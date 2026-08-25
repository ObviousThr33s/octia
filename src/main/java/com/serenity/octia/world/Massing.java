package com.serenity.octia.world;

/**
 * The volume a stair tower occupies: a prism between two ground levels, with a
 * sphere at each end cut flat against the prism's own end face.
 *
 * <p><b>The masks are the point.</b> A prism driven into a hillside ends in a raw
 * rectangular mouth, which reads as a hole somebody cut. A sphere swallowing that
 * junction reads as a place where the ground opens. The owner gave the shape
 * directly - "at the top and the bottom generate spheres to mask the openings" -
 * and the flat slice is the half that makes it buildable.
 *
 * <p><b>Why the slice is the whole trick.</b> Each sphere is centred exactly on the
 * prism's end face and then only its outer half is kept, so the cut plane and the
 * face are the same plane. The join is flush by construction: there is no seam to
 * tune, no overlap to clean up, and no radius at which a cap can bulge back up
 * inside the shaft. That last claim is not an intention - it is asserted by
 * exhaustion in {@code MassingTest}, over every legal shaft and radius.
 *
 * <p>Coordinates are relative to the centre of the prism's <em>base</em> face, so
 * {@code dy == 0} is that face and {@code dy == height} is the top one. Nothing
 * here reads a world: this is a shape, and the feature that uses it decides which
 * cells become stair, landing or cladding. Pure, like {@code Isle} and
 * {@code Sightlines}, for the same reason - a shape that can be checked under
 * JUnit is a shape nobody has to go and look at in a save.
 */
public final class Massing {

    private Massing() {
    }

    /**
     * The largest half-extent a site may reach from its centre and stay in its chunk.
     *
     * <p>A chunk is sixteen wide, and a site spends its centre column, which leaves
     * seven either side. One-site-one-chunk is a law here, not a preference, so a
     * shape that cannot answer this bound is refused rather than clipped.
     */
    public static final int REACH = 7;

    /** Whether a cell is inside the prism or inside either masked mouth. */
    public static boolean holds(int dx, int dy, int dz,
            int halfW, int halfD, int height, int radius) {
        if (inPrism(dx, dy, dz, halfW, halfD, height)) {
            return true;
        }
        // Below the base face, and above the top one, and nowhere between: this
        // pair of guards IS the flat slice.
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
     * suggestion. It is also tight: one less always leaves a corner outside.
     */
    public static int minRadius(int halfW, int halfD) {
        return (int) Math.ceil(Math.sqrt((double) halfW * halfW + (double) halfD * halfD));
    }

    /**
     * Whether a site of this size stays inside the chunk it is sited in.
     *
     * <p>The bound is the cap's, not the prism's: the caps are the widest part of
     * the shape, being at least the corner diagonal by {@link #minRadius}. A 3x3
     * shaft admits radii two through six and a 5x5 admits three through five;
     * anything wider has no legal cap at all and wants the switchbacks instead.
     */
    public static boolean fitsChunk(int halfW, int halfD, int radius) {
        return halfW + radius <= REACH && halfD + radius <= REACH;
    }
}
