package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The lattice is pure arithmetic over a seed and a position, with no Minecraft
 * on its imports, so it is tested here rather than as a GameTest. That is not a
 * convenience: the properties below are the ones {@link ObeliskFeature} relies
 * on while running on several chunk-generation worker threads at once.
 *
 * <p><b>Sampled, not spot-checked.</b> An earlier version of this file walked a
 * few thousand cells of one seed. That is enough to catch a lattice that is
 * broken and nowhere near enough to characterise one that works - a heading that
 * quietly favours one cardinal, or a worst-case bend that only shows up once in
 * two million cells, would both have passed it. Every invariant here now runs
 * over {@value #SEEDS} seeds, and the bounds asserted were chosen from a much
 * larger sweep: {@code tools/sightline-map/Sweep.java}, which is the same
 * measurement without a runtime budget. When a bound here looks arbitrary, that
 * tool is where the number came from.
 */
class SightlinesTest {

    /** Seeds swept per invariant. Enough to be a distribution, fast enough to gate. */
    private static final int SEEDS = 60;

    /** Cells either side of the origin, per seed. 60 x 41 x 41 is ~100k cells. */
    private static final int REACH = 20;

    /** The amplified dev save, for the cases that pin one concrete answer. */
    private static final long SEED = 95512464L;

    /** Spreads run indices so consecutive runs are not consecutive seeds. */
    private static long seedOf(int index) {
        long v = index * 0x9E3779B97F4A7C15L;
        v ^= v >>> 30;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 27;
        return v;
    }

    /** Runs a body over every cell of every swept seed. */
    private static void sweep(CellCheck check) {
        for (int s = 1; s <= SEEDS; s++) {
            long seed = seedOf(s);
            for (int cx = -REACH; cx <= REACH; cx++) {
                for (int cz = -REACH; cz <= REACH; cz++) {
                    check.at(seed, cx, cz);
                }
            }
        }
    }

    private interface CellCheck {
        void at(long seed, int cellX, int cellZ);
    }

    @Test
    @DisplayName("the same seed and cell always answer the same node")
    void nodesAreDeterministic() {
        sweep((seed, cx, cz) -> {
            assertEquals(Sightlines.node(seed, cx, cz), Sightlines.node(seed, cx, cz));
            assertEquals(Sightlines.leg(seed, cx, cz).heading(),
                    Sightlines.leg(seed, cx, cz).heading());
        });
    }

    @Test
    @DisplayName("a different seed lays a different lattice")
    void theSeedMovesTheLattice() {
        int same = 0;
        int total = 0;
        for (int cx = -40; cx <= 40; cx++) {
            for (int cz = -40; cz <= 40; cz++) {
                total++;
                if (Sightlines.node(SEED, cx, cz).equals(Sightlines.node(SEED + 1, cx, cz))) {
                    same++;
                }
            }
        }
        // Not zero-or-bust: two 193-wide draws collide by chance about once in
        // 37,000 cells. A handful is the lattice being reseeded; hundreds is not.
        assertTrue(same < total / 100.0 + 5,
                "the seed barely moved the lattice: " + same + " of " + total + " nodes identical");
    }

    /**
     * <b>Must hold for every cell of every seed.</b> A node that escapes its own
     * cell can cross a leg it is not part of, and {@link Sightlines#legAt} would
     * hand a position the leg of a cell whose node is somewhere else entirely.
     * The full sweep found zero escapes in 2,040,200 cells.
     */
    @Test
    @DisplayName("every node lands inside the cell it belongs to")
    void nodesStayInTheirCells() {
        sweep((seed, cx, cz) -> {
            Sightlines.Node node = Sightlines.node(seed, cx, cz);
            assertEquals(cx, Sightlines.cell(node.x()), "node " + node + " escaped cell x " + cx);
            assertEquals(cz, Sightlines.cell(node.z()), "node " + node + " escaped cell z " + cz);
        });
    }

    @Test
    @DisplayName("cells divide the axis at negative coordinates too")
    void cellsCoverNegativeSpace() {
        assertEquals(0, Sightlines.cell(0));
        assertEquals(0, Sightlines.cell(Sightlines.SPACING - 1));
        assertEquals(-1, Sightlines.cell(-1));
        assertEquals(-1, Sightlines.cell(-Sightlines.SPACING));
        assertEquals(-2, Sightlines.cell(-Sightlines.SPACING - 1));
    }

    /**
     * The property the prism's long axis rests on. Two nodes a cardinal step
     * apart wander independently, so the leg's cross-axis reaches {@code 2J}
     * while its along-axis shrinks to {@code SPACING - 2J}: the arithmetic bound
     * is {@code atan(2J / (SPACING - 2J))}, 30.96 degrees at 96 against 512.
     *
     * <p>The sweep measured a worst case of <b>30.007 degrees</b> over two
     * million cells, with exactly one leg past 30. So the bound is not merely
     * satisfied, it is nearly tight - which is the useful thing to know, because
     * it says the margin to 45 is real and not luck. 40 is asserted here as the
     * line between "comfortable" and "the constants moved".
     */
    @Test
    @DisplayName("a leg never bends far enough to be ambiguous about its cardinal")
    void legsNeverReachFortyFiveDegrees() {
        double[] worst = {0};
        sweep((seed, cx, cz) -> {
            Sightlines.Leg leg = Sightlines.leg(seed, cx, cz);
            long dx = leg.to().x() - leg.from().x();
            long dz = leg.to().z() - leg.from().z();
            boolean flat = leg.heading() == Sightlines.Heading.EAST
                    || leg.heading() == Sightlines.Heading.WEST;
            double along = Math.abs(flat ? dx : dz);
            double across = Math.abs(flat ? dz : dx);
            worst[0] = Math.max(worst[0], Math.toDegrees(Math.atan2(across, along)));
        });
        assertTrue(worst[0] < 40, "a leg bent " + worst[0] + " degrees off its cardinal");
    }

    @Test
    @DisplayName("the leg steps to the neighbouring cell it names")
    void theStepAgreesWithTheNodeItReaches() {
        sweep((seed, cx, cz) -> {
            Sightlines.Leg leg = Sightlines.leg(seed, cx, cz);
            assertEquals(Sightlines.node(seed, cx + leg.heading().dx(), cz + leg.heading().dz()),
                    leg.to());
            assertNotEquals(leg.from(), leg.to());
        });
    }

    /**
     * Balanced to well inside a percent across a hundred thousand cells. The
     * full sweep put every cardinal between 24.93% and 25.04%.
     */
    @Test
    @DisplayName("all four headings get used, and none of them dominates")
    void headingsAreSpread() {
        Map<Sightlines.Heading, Integer> counts = new EnumMap<>(Sightlines.Heading.class);
        int[] total = {0};
        sweep((seed, cx, cz) -> {
            counts.merge(Sightlines.leg(seed, cx, cz).heading(), 1, Integer::sum);
            total[0]++;
        });
        assertEquals(4, counts.size(), "a heading never came up: " + counts);
        for (Map.Entry<Sightlines.Heading, Integer> entry : counts.entrySet()) {
            double share = (double) entry.getValue() / total[0];
            assertTrue(share > 0.245 && share < 0.255,
                    entry.getKey() + " took " + share + " of the lattice");
        }
    }

    /**
     * <b>The number the design was wrong about.</b> The corridor was described
     * as though it divided the world roughly in half. It does not: a leg is a
     * line through a cell 512 blocks across, so a 32-block corridor covers about
     * an eighth of the ground, and the rest of the world is "off thread". The
     * full sweep measured <b>12.66%</b> over sixteen million points, which makes
     * the expected share of broken obelisks 45%, not the quarter the prose
     * implied.
     *
     * <p>Pinned here so the number cannot move without somebody noticing, since
     * it is the one constant that decides how much of the world reads as route.
     *
     * <p>Note what is measured: distance to the position's <em>own cell's</em>
     * leg, which is what {@link ObeliskFeature} computes. Measuring the nearest
     * leg anywhere would give a kinder number that nothing in the game uses.
     */
    @Test
    @DisplayName("the corridor covers about an eighth of the world, not half of it")
    void theCorridorIsAThinRibbon() {
        Random dice = new Random(20260815L);
        int inside = 0;
        int points = 0;
        for (int s = 1; s <= SEEDS; s++) {
            long seed = seedOf(s);
            for (int cx = -REACH; cx <= REACH; cx++) {
                for (int cz = -REACH; cz <= REACH; cz++) {
                    Sightlines.Leg leg = Sightlines.leg(seed, cx, cz);
                    for (int i = 0; i < 4; i++) {
                        int x = cx * Sightlines.SPACING + dice.nextInt(Sightlines.SPACING);
                        int z = cz * Sightlines.SPACING + dice.nextInt(Sightlines.SPACING);
                        points++;
                        if (leg.distanceToLine(x, z) <= Sightlines.CORRIDOR) {
                            inside++;
                        }
                    }
                }
            }
        }
        double share = (double) inside / points;
        assertTrue(share > 0.11 && share < 0.145,
                "the corridor now covers " + share + " of the world; the docs say an eighth");
    }

    @Test
    @DisplayName("distance to the line is zero on it and grows off it")
    void distanceMeasuresFromTheLine() {
        Sightlines.Leg leg = new Sightlines.Leg(
                new Sightlines.Node(0, 0), new Sightlines.Node(0, 512), Sightlines.Heading.SOUTH);

        assertEquals(0, leg.distanceToLine(0, 0), 1e-9);
        assertEquals(0, leg.distanceToLine(0, 256), 1e-9);
        // Past the far node: the line, not the segment. A thread does not stop
        // being a direction because you walked beyond the waypoint.
        assertEquals(0, leg.distanceToLine(0, 9000), 1e-9);
        assertEquals(32, leg.distanceToLine(32, 100), 1e-9);
        assertEquals(32, leg.distanceToLine(-32, 100), 1e-9);
    }

    @Test
    @DisplayName("a position reads the leg of the cell it stands in")
    void legAtFindsTheCell() {
        assertEquals(Sightlines.leg(SEED, 0, 0), Sightlines.legAt(SEED, 10, 10));
        assertEquals(Sightlines.leg(SEED, -1, -1), Sightlines.legAt(SEED, -10, -10));
        assertEquals(Sightlines.leg(SEED, 3, -2),
                Sightlines.legAt(SEED, 3 * Sightlines.SPACING + 5, -2 * Sightlines.SPACING + 5));
    }

    /**
     * Bearing is north-zero and clockwise through east, matching the debug
     * overlay's readout rather than the textbook atan2 order. Minecraft's north
     * is negative Z, and getting this backwards flips every thread on the map.
     */
    @Test
    @DisplayName("bearing is north zero, clockwise through east")
    void bearingUsesMinecraftNorth() {
        Sightlines.Node origin = new Sightlines.Node(0, 0);
        assertEquals(0, new Sightlines.Leg(origin, new Sightlines.Node(0, -100),
                Sightlines.Heading.NORTH).bearing(), 1e-9);
        assertEquals(90, new Sightlines.Leg(origin, new Sightlines.Node(100, 0),
                Sightlines.Heading.EAST).bearing(), 1e-9);
        assertEquals(180, new Sightlines.Leg(origin, new Sightlines.Node(0, 100),
                Sightlines.Heading.SOUTH).bearing(), 1e-9);
        assertEquals(-90, new Sightlines.Leg(origin, new Sightlines.Node(-100, 0),
                Sightlines.Heading.WEST).bearing(), 1e-9);
    }
}
