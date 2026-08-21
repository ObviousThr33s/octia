package com.serenity.octia.world;

/**
 * The threads: a lattice of waypoints, and the legs between them.
 *
 * <p><b>Why this had to be invented rather than discovered.</b> The two saves in
 * {@code worlds/} were read for it - every structure start in them, by type, with
 * its cell and its offset inside that cell. Vanilla lays each structure type on
 * its own jittered grid: one start per cell of N chunks, dropped somewhere in the
 * first N-minus-separation of it. Trial chambers in the amplified save sit in
 * four distinct 34-chunk cells at offsets 16, 18, 1 and 13; ruined portals in
 * four distinct 40-chunk cells at offsets no larger than 23. It holds for every
 * type in the save.
 *
 * <p>Which answers the question that started this and answers it in the negative:
 * <b>nothing in a vanilla world points at anything else.</b> Two structures of
 * the same type have a minimum distance between them and nothing more - no
 * alignment, no bearing, no order. The grid is a spacing rule, not a route. So a
 * thread between landmarks is not a property of the terrain waiting to be read
 * out; it is a thing this mod has to lay down itself.
 *
 * <p><b>What is laid down.</b> The same jittered grid vanilla uses, at 512 blocks
 * a cell, with one <em>node</em> per cell - and then the one thing vanilla never
 * does: each node is given a <em>next</em> node, one cell away, chosen by the same
 * hash. That makes the nodes a directed chain rather than a scattering, and the
 * segment between two of them is a {@link Leg}: a line across the world with a
 * direction along it. {@link ObeliskFeature} reads the leg under its feet and
 * stands its prism along it.
 *
 * <p><b>Pure Java, on purpose.</b> No Minecraft on this class's imports and none
 * wanted. Features run on chunk-generation worker threads, several at once, so
 * anything they consult must be a function rather than a lookup - no
 * {@code SavedData}, no chunk reads, no shared mutable anything. A pure function
 * of (seed, position) is thread-safe by construction, gives the same answer on a
 * dedicated server and a client, and can be tested by JUnit in milliseconds
 * without a world. {@code SightlinesTest} is that test.
 *
 * <p>Steps are the four cardinals and never a diagonal. That is what makes
 * {@link Leg#heading()} meaningful: two nodes a step apart can each wander
 * {@link #JITTER}, so the worst a cardinal leg bends is
 * {@code atan(2J / (SPACING - 2J))} - about 31 degrees at the constants below -
 * and the nearest cardinal to a leg is never in doubt. A diagonal step would sit
 * at 45 degrees, exactly between two of them, and the prism's long axis would be
 * a coin toss. {@code SightlinesTest} measures the worst bend over four thousand
 * cells rather than trusting that arithmetic.
 */
public final class Sightlines {

    /**
     * Blocks between one node and the next. Chosen against the yardstick in
     * {@code docs/WORLDS.md}: vanilla structures in these saves land between 60
     * and 400 blocks apart, and a leg wants to be longer than the things it
     * passes so that following one is a journey rather than a step.
     */
    public static final int SPACING = 512;

    /**
     * How far a node wanders from the middle of its cell.
     *
     * <p><b>Must stay under a quarter of {@link #SPACING}</b>, and it is the one
     * constant here with a hard ceiling rather than a taste. Two nodes a step
     * apart wander independently, so the leg's cross-axis reaches {@code 2J}
     * while its along-axis shrinks to {@code SPACING - 2J}; at {@code J =
     * SPACING / 4} those are equal, the leg sits at 45 degrees, and
     * {@link Leg#heading()} stops naming the axis the prism should lie along.
     * At 96 against 512 the worst bend is 31 degrees, with room to spare.
     */
    public static final int JITTER = 96;

    /**
     * Half-width of the corridor either side of a leg. Not a filter on where an
     * obelisk may stand - see {@link ObeliskFeature}, which uses it to decide how
     * likely one is to have stayed up, not whether it exists.
     *
     * <p><b>[2026-08-21] Widened from 32 to 128, which is KEG's ruling on the
     * first of the three doors ROADMAP XII left open.</b> At 32 the corridor was
     * 12.66% of the ground and the route was a thin ribbon almost nobody would
     * meet; at 128 it is <b>50.45%</b>, measured by {@code Sweep} at this test's
     * own constants and 50.47% over sixteen million points.
     *
     * <p>What the widening buys and what it costs, since both are real. A
     * standing obelisk goes from <b>20% to 64%</b> likely to actually be on a
     * thread, and the share of obelisks left standing rises from 55% to 69% - so
     * the route becomes something a player meets and can follow. But its
     * <em>lift over chance</em> falls from <b>1.60x to 1.27x</b>: at 12.66% a
     * standing obelisk was weak evidence of a strong kind, and at 50.45% it is
     * stronger evidence of a weaker kind. XII called this "stops the corridor
     * being a corridor", and that is the trade in one number.
     *
     * <p>The width is now half the cell, since {@code 2 * 128} is
     * {@link #SPACING} / 2. There is no natural stopping point past this:
     * {@code Sweep}'s histogram is flat at about 3.16% of the world per 8-block
     * bucket out to 136, so width buys share linearly at roughly 0.395% per
     * block. Any future move is a matter of taste, not of finding a knee.
     */
    public static final int CORRIDOR = 128;

    /** Keeps each draw off the world seed's other consumers, and off each other. */
    private static final long NODE_SALT = 0x51_6117_11E5L;
    private static final long STEP_SALT = 0x7_47EAD_5L;
    private static final long REPICK_SALT = 0x2C_0107_3EDL;

    private Sightlines() {
    }

    /** One of the four cardinals, as a unit step in cells. */
    public enum Heading {

        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        private final int dx;
        private final int dz;

        Heading(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }
    }

    /** A waypoint. Block coordinates, horizontal only - the ground decides Y. */
    public record Node(int x, int z) {
    }

    /**
     * The segment from one node to the next.
     *
     * @param from the node of the cell you are standing in
     * @param to   the node it points at
     * @param step which cardinal was taken to get there
     */
    public record Leg(Node from, Node to, Heading step) {

        /** Bearing along the leg, degrees, north zero and clockwise through east. */
        public double bearing() {
            // atan2(dx, -dz), not the textbook order: Minecraft's north is
            // negative Z. Same convention as the debug overlay's readout.
            return Math.toDegrees(Math.atan2(to.x() - from.x(), -(to.z() - from.z())));
        }

        /**
         * The cardinal the prism lies along. This is {@link #step} rather than
         * anything recomputed from {@link #bearing()}, and deliberately so: the
         * step is what the lattice actually chose, and rounding the bearing back
         * to a cardinal can only either agree with it or be wrong.
         */
        public Heading heading() {
            return step;
        }

        /**
         * Perpendicular distance from a position to the infinite line through the
         * leg. The line rather than the segment: a position past the far node is
         * still on the thread as far as anything reading this cares, and the
         * segment form would put a discontinuity at each node for no gain.
         *
         * <p>Answers zero for a degenerate leg, which cannot happen with the
         * jitter bounded as it is but is not worth crashing over if it ever can.
         */
        public double distanceToLine(int x, int z) {
            double lx = to.x() - from.x();
            double lz = to.z() - from.z();
            double length = Math.sqrt(lx * lx + lz * lz);
            if (length == 0) {
                return 0;
            }
            return Math.abs(lz * (x - from.x()) - lx * (z - from.z())) / length;
        }
    }

    /** Which cell a block coordinate falls in. Negative coordinates included. */
    public static int cell(int coord) {
        return Math.floorDiv(coord, SPACING);
    }

    /** The node of one cell. A pure function of the seed and the cell. */
    public static Node node(long seed, int cellX, int cellZ) {
        long h = hash(seed, cellX, cellZ, NODE_SALT);
        int spread = 2 * JITTER + 1;
        int dx = Math.floorMod(h, spread) - JITTER;
        int dz = Math.floorMod(h >>> 32, spread) - JITTER;
        return new Node(cellX * SPACING + SPACING / 2 + dx,
                cellZ * SPACING + SPACING / 2 + dz);
    }

    /** The leg leaving one cell's node. */
    public static Leg leg(long seed, int cellX, int cellZ) {
        Heading step = step(seed, cellX, cellZ);
        return new Leg(node(seed, cellX, cellZ),
                node(seed, cellX + step.dx(), cellZ + step.dz()),
                step);
    }

    /** The draw, before the two-cycle is excluded. */
    private static Heading rawStep(long seed, int cellX, int cellZ) {
        return Heading.values()[Math.floorMod(hash(seed, cellX, cellZ, STEP_SALT), 4)];
    }

    /**
     * A cardinal step always flips the parity of {@code cellX + cellZ}, so the
     * lattice is a checkerboard and every leg runs black to white or white to
     * black. That is the whole trick below.
     */
    private static boolean black(int cellX, int cellZ) {
        return ((cellX + cellZ) & 1) == 0;
    }

    /** Whether the cell one step this way answers by stepping straight back. */
    private static boolean pointsBack(long seed, int cellX, int cellZ, Heading step) {
        int tx = cellX + step.dx();
        int tz = cellZ + step.dz();
        Heading theirs = rawStep(seed, tx, tz);
        return tx + theirs.dx() == cellX && tz + theirs.dz() == cellZ;
    }

    /**
     * The step out of a cell, with the immediate doubling-back excluded.
     *
     * <p><b>What was wrong.</b> Each cell drew from four cardinals independently,
     * so the cell it stepped to stepped straight back one time in four - measured
     * at <b>25.08%</b> over 2,040,200 cells. A thread therefore ran about
     * <b>4.65 legs, 2,380 blocks</b>, before returning on itself, which is not far
     * enough to be a route. Nobody chose that; it fell out of the draw being
     * uniform over four options.
     *
     * <p><b>Why the obvious fix does not work.</b> This is a pure function of one
     * cell, and a node may be pointed at by anywhere from none to all four of its
     * neighbours, so "do not step back" is not locally defined - and a cell that
     * re-draws because its target answers back can land on another neighbour that
     * also answers back. Re-drawing until it is happy recurses, and on a lattice
     * with no boundary that is not a terminating argument.
     *
     * <p><b>What breaks the recursion.</b> The parity in {@link #black}. Black
     * cells keep their draw and never adjust; only white cells look at their
     * neighbours, and every neighbour of a white cell is black and therefore
     * already final. One side moving and the other standing still is what makes
     * this terminate in one pass.
     *
     * <p><b>Cost.</b> One extra hash in the common case, since three white cells
     * in four are happy with the draw they got. Four in the worst case. Measured
     * expectation is 1.75.
     *
     * <p><b>It does not reach zero, and cannot.</b> When all four of a white
     * cell's neighbours answer back there is no legal step left and the draw has
     * to stand. That is <b>0.3866%</b> of cells, measured - and the residual
     * two-cycle rate after this fix is <b>0.3867%</b>, i.e. exactly the stranded
     * cells and nothing else. So the rule is as good as a local rule gets.
     *
     * <p><b>What it buys.</b> Two-cycles fall from 25.08% to 0.39%, and a thread
     * now runs <b>11.15 legs, 5,709 blocks</b> before it returns on itself - 2.4
     * times further. The worst bend is untouched at 30.116 degrees, and the four
     * cardinals stay balanced within a twentieth of a percent, because nothing
     * here prefers a direction.
     *
     * <p><b>This moves the lattice.</b> A save generated before this change and
     * extended after it will disagree about every white cell that re-drew, so
     * arches and obelisk bearings will not line up across the seam. That is
     * acceptable at an alpha and would not be later.
     */
    private static Heading step(long seed, int cellX, int cellZ) {
        Heading raw = rawStep(seed, cellX, cellZ);
        if (black(cellX, cellZ) || !pointsBack(seed, cellX, cellZ, raw)) {
            return raw;
        }

        Heading[] legal = new Heading[4];
        int count = 0;
        for (Heading candidate : Heading.values()) {
            if (!pointsBack(seed, cellX, cellZ, candidate)) {
                legal[count++] = candidate;
            }
        }
        if (count == 0) {
            return raw;
        }
        return legal[Math.floorMod(hash(seed, cellX, cellZ, REPICK_SALT), count)];
    }

    /** The leg under a block position. */
    public static Leg legAt(long seed, int x, int z) {
        return leg(seed, cell(x), cell(z));
    }

    /**
     * Splitmix64's finaliser over the cell, salted and folded with the seed.
     *
     * <p>The two multipliers on the cell are Minecraft's own large-feature
     * constants, kept because they are known to spread adjacent chunk
     * coordinates well; the mixing after them is what stops the low bits of a
     * cell index showing through into a heading.
     */
    private static long hash(long seed, int cellX, int cellZ, long salt) {
        return mix(seed ^ mix(cellX * 341873128712L + cellZ * 132897987541L + salt));
    }

    private static long mix(long value) {
        long v = value;
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= v >>> 33;
        return v;
    }
}
