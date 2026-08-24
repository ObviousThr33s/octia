package com.serenity.octia.world;

/**
 * What a position knows and never says.
 *
 * <pre>
 * MYSTERY.OBJ(ANY) -&gt; ?
 * </pre>
 *
 * <p>That line is the whole specification and it was written by the gardener,
 * not derived here. Read it in three parts. <b>MYSTERY</b> is not a piece of
 * content; it is a type. <b>OBJ(ANY)</b> says the type is generic over its
 * subject - a wreck, a core, an obelisk, an arch, a panel in a floor, a
 * position with nothing on it at all can each carry one. And <b>-&gt;?</b> says
 * what it resolves to is the unknown itself: the mod computes a real answer,
 * every time, deterministically, and never states it.
 *
 * <p><b>This adds no secret to the world.</b> That is the point and it is worth
 * being blunt about, because the obvious way to build a mystery is to invent a
 * hidden thing and hide it. Nothing here is hidden and nothing here is new. The
 * lattice in {@link Sightlines} has been laying a node in every 512-block cell
 * since it was written, {@link ObeliskFeature} already stands its prism along
 * the leg and {@link ArchFeature} already squares itself across it - so the
 * world has been pointing at its own waypoints in stone for months. What it has
 * never done is point at them from anywhere else. This class is the bearing
 * that every position already has, handed to anything that wants to show it.
 *
 * <p>{@code EraEcho}'s javadoc states the standard this is trying to meet: <i>a
 * property nobody can perceive is a comment with a test attached.</i> The
 * bearing to a node was exactly that. It was computed, it was correct, and it
 * was thrown away everywhere except the two features standing on top of it.
 *
 * <p><b>Pure Java, for the reason {@link Sightlines} is.</b> No Minecraft on the
 * imports and none wanted. Features run on chunk-generation worker threads,
 * several at once, so anything they consult must be a function rather than a
 * lookup: no {@code SavedData}, no chunk reads, no shared mutable anything. A
 * pure function of (seed, position) is thread-safe by construction, answers the
 * same on a dedicated server and on a client, and is tested by JUnit in
 * milliseconds with no world. {@code MysteryTest} is that test.
 *
 * <p><b>Why the answer is one of eight and not a number of degrees.</b> Because
 * the mod already has an object with eight positions around a centre, and it is
 * the central object: the ring of frame panels that {@code ShipCoreBlock}
 * surveys. A bearing quantised to eight fits a ring exactly, which means the
 * answer can be <em>shown</em> without inventing a single new block, texture or
 * model - one panel of eight, lit. Three bits of information, in a place a
 * player is already looking.
 *
 * <p><b>The mark is mostly a fact about where you stand, and only partly a
 * secret.</b> A node wanders at most {@link Sightlines#JITTER} from the middle
 * of its 512-block cell, so from a position near the cell's edge the bearing is
 * dominated by the cell's own geometry and two different seeds will usually
 * agree. The seed only really decides the answer close in. Measured by
 * {@code MysteryTest} rather than argued.
 *
 * <p>That is not a flaw, and it is the thing that makes the rule learnable. Two
 * wrecks in one cell point at the same place, so their marks <em>cross</em>, and
 * a player who has noticed that much can walk to the intersection without ever
 * being told there is anything there. What is at the intersection is a node,
 * which is where the obelisk and the arch have been standing since long before
 * this class existed.
 *
 * <p><b>The cell boundary is visible, and it is not a bug.</b> Two wrecks fifty
 * blocks apart on opposite sides of a boundary answer about different nodes and
 * can point almost oppositely. The lattice has always been made of cells and the
 * obelisks have always turned at the same seams. Anyone tempted to smooth this
 * away should read {@code Sightlines.step} first: the discontinuity is the
 * structure.
 *
 * <p><b>What a player can actually do with this.</b> Nothing, at first. The
 * first marked thing teaches nothing at all, which is the test any of this has
 * to pass: one lit panel is decoration. The second is a coincidence. Somewhere
 * around the fifth, walked, the rule arrives on its own - <i>they all point the
 * same way from here</i> - and a wreck stops being a wreck and becomes a
 * compass needle. No text says so. Nothing in the game will ever say so.
 */
public final class Mystery {

    /**
     * How close to a node counts as standing on it.
     *
     * <p>Sized against {@link Sightlines#JITTER}, not chosen for taste: a node
     * wanders up to 96 blocks inside its cell, and something within 24 blocks of
     * it is unambiguously at the waypoint rather than near it. Wide enough that
     * a whole wreck and its ring of digs sit inside, narrow enough that it is
     * rare - a disc of this radius is about 0.7% of a cell.
     */
    public static final int ARRIVED = 24;

    /**
     * The eight offsets of a ring, clockwise from north.
     *
     * <p>Order is load-bearing: {@link #toward} indexes this array with a
     * bearing divided by 45, so a reordering silently rotates every mark in
     * every world. North first and clockwise is the same convention
     * {@code Sightlines.Leg.bearing()} uses and the same one the debug overlay
     * reads out.
     *
     * <p>Minecraft's north is negative Z, which is why north is {@code (0, -1)}
     * and not {@code (0, 1)}. That sign has been got wrong here once per
     * project since the beginning of time.
     */
    private static final int[][] RING = {
            {0, -1},   // N
            {1, -1},   // NE
            {1, 0},    // E
            {1, 1},    // SE
            {0, 1},    // S
            {-1, 1},   // SW
            {-1, 0},   // W
            {-1, -1},  // NW
    };

    private Mystery() {
    }

    /**
     * One of the eight positions around a centre.
     *
     * @param dx east-positive offset, -1, 0 or 1
     * @param dz south-positive offset, -1, 0 or 1
     */
    public record Mark(int dx, int dz) {

        /** Index into the ring, clockwise from north. Useful to a test, not to the world. */
        public int octant() {
            for (int i = 0; i < RING.length; i++) {
                if (RING[i][0] == dx && RING[i][1] == dz) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Which of the eight ring positions points at this cell's node, or
     * <b>null</b> if the position is standing on the node already.
     *
     * <p><b>Null is not a failure and it is not an absence of information - it
     * is the answer.</b> A mark points the way to somewhere; asked at that
     * somewhere, there is no way to point, and the honest reply is nothing. So a
     * wreck seated on a waypoint has no lit panel in its floor, and it is the
     * only kind that does not. That is the same shape {@code Landfall.groundIn}
     * uses and for the same reason: a function that can say no is a different
     * function from one that always answers.
     *
     * <p>This matters more than it looks. It means the rule a player induces has
     * an exception built into it before they ever meet one, so the exception is
     * a discovery rather than a bug - and the thing it discovers is the node,
     * which is the thing the whole lattice is about.
     */
    public static Mark toward(long seed, int x, int z) {
        Sightlines.Node node = Sightlines.node(seed, Sightlines.cell(x), Sightlines.cell(z));

        int dx = node.x() - x;
        int dz = node.z() - z;

        // Squared, so no square root and no floating point in the test that
        // decides which branch this takes. The comparison is exact.
        if ((long) dx * dx + (long) dz * dz <= (long) ARRIVED * ARRIVED) {
            return null;
        }

        return markFor(dx, dz);
    }

    /**
     * The ring position nearest a delta, without asking where any node is.
     *
     * <p>Public because the snap is useful to anything that already knows a
     * direction and wants it as one of eight - a core that has just moored knows
     * the leg under it, and should not have to re-derive a bearing this class
     * already knows how to quantise. It is also the only way to test the snap
     * itself: {@link #toward} takes a position, and a position 400 blocks from
     * its node may well be in the next cell and answering about a different one.
     *
     * @return the ring offset, or null if the delta is exactly zero
     */
    public static Mark markFor(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return null;
        }
        int[] offset = RING[octantOf(dx, dz)];
        return new Mark(offset[0], offset[1]);
    }

    /**
     * Bearing from a position to its node, degrees, north zero, clockwise
     * through east.
     *
     * <p>Exposed because a test that only ever compares two quantised answers
     * cannot tell a correct snap from a consistently wrong one. Nothing in the
     * world reads this.
     */
    public static double bearingToNode(long seed, int x, int z) {
        Sightlines.Node node = Sightlines.node(seed, Sightlines.cell(x), Sightlines.cell(z));
        return bearing(node.x() - x, node.z() - z);
    }

    /** Whether this position is close enough to its node that no mark can point. */
    public static boolean arrived(long seed, int x, int z) {
        return toward(seed, x, z) == null;
    }

    /**
     * The ring index a delta falls in.
     *
     * <p>Rounding rather than flooring, so each octant is centred on its
     * cardinal or diagonal instead of starting at it - a bearing of 1 degree is
     * north, not north-east. {@code Math.floorMod} rather than {@code %},
     * because a bearing just under 360 rounds to 8 and has to come back to 0.
     */
    private static int octantOf(int dx, int dz) {
        return Math.floorMod((int) Math.round(bearing(dx, dz) / 45.0), 8);
    }

    /** North zero, clockwise through east. Minecraft's north is negative Z. */
    private static double bearing(int dx, int dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return degrees < 0 ? degrees + 360 : degrees;
    }
}
