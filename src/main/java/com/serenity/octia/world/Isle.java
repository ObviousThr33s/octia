package com.serenity.octia.world;

/**
 * The shape of an island, as arithmetic.
 *
 * <p>No Minecraft imports on purpose, for the same reason {@link Sightlines}
 * has none: a shape that can be asserted without a world is a shape that can be
 * got wrong in a unit test rather than in a save. What builds one is
 * {@link Landfall}; what says how big it is at each depth is here.
 *
 * <p><b>Why an island shape exists in a mod that does not generate islands.</b>
 * It does not generate them - {@code minecraft:floating_islands} does, and the
 * {@code octia:sky} world preset is the whole of that. This is the one island
 * Octia makes itself, and it is made only when the terrain failed to put one
 * where a player is about to stand. See {@link Landfall} for when that happens
 * and why refusing to build it is not an option.
 *
 * <p><b>The profile.</b> Flat on top for the soil, then falling away fast:
 *
 * <pre>
 *   depth 0     ##############   grass, full radius
 *   depth 1-2   ##############   dirt,  full radius
 *   depth 3       ##########     stone, R-2
 *   depth 4         ######       stone, R-4
 *   depth 5           ##         stone, R-6
 * </pre>
 *
 * A cone would read as a spike and a cylinder as a plug. Two blocks of taper
 * per layer under a flat cap is the cheapest thing that reads as *floating*
 * from underneath, which is the only angle that has to sell it - you arrive on
 * the top, and you only ever see the bottom on the way past it.
 */
public final class Isle {

    /**
     * Layers of soil under the grass, all at full radius.
     *
     * <p>Two, so a shovel does not reach stone on the first block and so
     * anything that plants itself has somewhere to go.
     */
    public static final int SOIL = 2;

    /** How much narrower each layer gets once the taper starts. */
    private static final int TAPER = 2;

    private Isle() {
    }

    /**
     * Radius of the disc at this depth, or negative once the island has ended.
     *
     * @param radius the island's radius at the surface
     * @param depth  0 for the grass layer, increasing downward
     * @return the radius of that layer; negative means there is no such layer
     */
    public static int radiusAt(int radius, int depth) {
        if (depth < 0) {
            return -1;
        }
        if (depth <= SOIL) {
            return radius;
        }
        return radius - (depth - SOIL) * TAPER;
    }

    /**
     * How many layers thick an island of this radius is, grass included.
     *
     * <p>Derived from {@link #radiusAt} rather than stated separately, because
     * two constants that have to agree are one constant and a bug waiting for
     * somebody to change only the other one.
     */
    public static int thickness(int radius) {
        int depth = 0;
        while (radiusAt(radius, depth) >= 0) {
            depth++;
        }
        return depth;
    }

    /**
     * Whether a column this far from the centre is part of the island at this
     * depth.
     *
     * <p>Circular by squared distance, not by {@code Math.hypot} - the same
     * answer, no floating point, and it is asked once per block of the island.
     */
    public static boolean holds(int radius, int depth, int dx, int dz) {
        int r = radiusAt(radius, depth);
        if (r < 0) {
            return false;
        }
        return dx * dx + dz * dz <= r * r;
    }
}
