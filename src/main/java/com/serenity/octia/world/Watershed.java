package com.serenity.octia.world;

/**
 * Water as a second reading of the same lattice the obelisks sight down.
 *
 * <p><b>Why the springs are seeded and not surveyed.</b> A spring wants to rise
 * where the ground is high and run to where it is low, and the honest way to
 * know that is to read the terrain. This class deliberately does not. Features
 * run on chunk-generation worker threads, several at once, and the height of
 * the ground at another cell's node is a chunk read this function must never
 * make - the neighbouring cell may not exist yet, and asking for it from a
 * worker is the deadlock {@code RuinGround.clearOfStructures} documents. So
 * every cell is given a seeded <em>head</em> instead: a potential, drawn from
 * the same splitmix64 mixer the lattice itself is drawn from, under a salt of
 * its own. The water then obeys the head the way real water obeys height, and
 * the head is a pure function, so the whole watershed is decided before a
 * single block is read.
 *
 * <p><b>The gate.</b> A cell's node opens as a spring exactly when its head is
 * strictly greater than the head of the node its own leg points at - when the
 * node is the uphill end of its own leg. About half of all cells pass, and the
 * strictness is load-bearing twice over: a tie is no spring, and a walk that
 * only ever moves to a strictly smaller head can never revisit a cell, so the
 * fall below terminates without any cycle-breaking of its own.
 *
 * <p><b>The fall.</b> From a spring, follow successive legs downhill while the
 * head strictly decreases, capped at {@link #MAX_FALL_LEGS}. The length of that
 * walk, 1 to {@value #MAX_FALL_LEGS}, is the pool budget the body may render -
 * how far this watershed is allowed to matter. The residual two-cycles in
 * {@link Sightlines} - 0.39% of cells - cannot trap the walk, because a cycle
 * would need a head below itself; the cap is a design bound, not termination
 * insurance.
 *
 * <p><b>Pure Java, for the reason {@link Sightlines} and {@link Mystery} are.</b>
 * No Minecraft on the imports and none wanted. Anything a generation worker
 * consults must be a function rather than a lookup: no {@code SavedData}, no
 * chunk reads, no shared mutable anything. A pure function of (seed, cell) is
 * thread-safe by construction, answers the same on a dedicated server and a
 * client, and is tested by JUnit in milliseconds without a world.
 * {@code WatershedTest} is that gate.
 */
public final class Watershed {

    /**
     * How many legs a fall may run before the watershed stops mattering.
     * A cap on reach, not a termination guarantee - the strict decrease is
     * the termination guarantee.
     */
    public static final int MAX_FALL_LEGS = 4;  // provisional - owner tunes by walking the world

    /**
     * Hexspeak "cascade". A new salt with its own constant, keeping this draw
     * off NODE, STEP, REPICK, STATION and SPAWN - one mixer in one place, many
     * salts, the {@link Beamline} precedent.
     */
    private static final long SPRING_SALT = 0xCA5CADEL;

    private Watershed() {
    }

    /**
     * The head of one cell: its seeded potential.
     *
     * <p>Exposed for the test only, the {@link Mystery#bearingToNode} precedent:
     * a test that only compares two gated answers cannot tell a correct gate
     * from a consistently wrong one, so the number under the gate has to be
     * readable. Nothing in the world reads it. Comparison is signed, here and
     * everywhere below - that is the convention, and the test re-walks it.
     */
    public static long head(long seed, int cellX, int cellZ) {
        return Sightlines.hash(seed, cellX, cellZ, SPRING_SALT);
    }

    /**
     * Whether this cell's node opens as a spring.
     *
     * <p>True exactly when the cell's head is strictly greater than the head of
     * the cell its own leg points at - the node is the uphill end of its own
     * leg. Strict, so a tie (hash-equal, astronomically rare) is no spring;
     * strictness is also what makes {@link #fallLegs} terminate.
     */
    public static boolean springAt(long seed, int cellX, int cellZ) {
        Sightlines.Leg leg = Sightlines.leg(seed, cellX, cellZ);
        return head(seed, cellX, cellZ)
                > head(seed, cellX + leg.heading().dx(), cellZ + leg.heading().dz());
    }

    /**
     * The length of the downhill walk from this cell, or 0 if it is no spring.
     *
     * <p>From the cell, repeatedly take the current cell's own leg to its
     * target while the head strictly decreases, counting legs, capped at
     * {@link #MAX_FALL_LEGS}. For a spring the answer is 1 to
     * {@value #MAX_FALL_LEGS} - the first leg is downhill by the spring gate
     * itself. Strict decrease makes a cycle impossible: the residual 0.39%
     * two-cycles in {@link Sightlines} cannot trap this walk, so the cap is a
     * statement of how far a watershed is allowed to matter, not insurance.
     */
    public static int fallLegs(long seed, int cellX, int cellZ) {
        if (!springAt(seed, cellX, cellZ)) {
            return 0;
        }
        int legs = 0;
        int cx = cellX;
        int cz = cellZ;
        long at = head(seed, cx, cz);
        while (legs < MAX_FALL_LEGS) {
            Sightlines.Leg leg = Sightlines.leg(seed, cx, cz);
            int tx = cx + leg.heading().dx();
            int tz = cz + leg.heading().dz();
            long below = head(seed, tx, tz);
            if (below >= at) {
                break;
            }
            legs++;
            cx = tx;
            cz = tz;
            at = below;
        }
        return legs;
    }
}
