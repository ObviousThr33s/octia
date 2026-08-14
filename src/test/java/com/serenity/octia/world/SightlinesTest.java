package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The lattice is pure arithmetic over a seed and a position, with no Minecraft
 * on its imports, so it is tested here rather than as a GameTest. That is not a
 * convenience: the properties below - determinism, containment, and a heading
 * that is never ambiguous - are the ones {@link ObeliskFeature} relies on while
 * running on several chunk-generation worker threads at once, and a fast gate
 * that runs them over ten thousand cells is worth more than one in-world case.
 */
class SightlinesTest {

    private static final long SEED = 95512464L;

    @Test
    @DisplayName("the same seed and cell always answer the same node")
    void nodesAreDeterministic() {
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                assertEquals(Sightlines.node(SEED, cx, cz), Sightlines.node(SEED, cx, cz));
                assertEquals(Sightlines.leg(SEED, cx, cz).heading(),
                        Sightlines.leg(SEED, cx, cz).heading());
            }
        }
    }

    @Test
    @DisplayName("a different seed lays a different lattice")
    void theSeedMovesTheLattice() {
        int same = 0;
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                if (Sightlines.node(SEED, cx, cz).equals(Sightlines.node(SEED + 1, cx, cz))) {
                    same++;
                }
            }
        }
        // Not zero-or-bust: two 289-wide draws can collide by chance. A handful
        // out of 1681 is the lattice being reseeded; a hundred is not.
        assertTrue(same < 20, "the seed barely moved the lattice: " + same + " nodes identical");
    }

    @Test
    @DisplayName("every node lands inside the cell it belongs to")
    void nodesStayInTheirCells() {
        for (int cx = -50; cx <= 50; cx++) {
            for (int cz = -50; cz <= 50; cz++) {
                Sightlines.Node node = Sightlines.node(SEED, cx, cz);
                assertEquals(cx, Sightlines.cell(node.x()),
                        "node " + node + " escaped cell x " + cx);
                assertEquals(cz, Sightlines.cell(node.z()),
                        "node " + node + " escaped cell z " + cz);
            }
        }
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
     * apart can each wander JITTER, so the leg's cross-axis reaches 2*JITTER
     * while its along-axis shrinks to SPACING - 2*JITTER: the worst bend is
     * {@code atan(2J / (SPACING - 2J))}, which is why JITTER has to stay under a
     * quarter of SPACING. At or past 45 degrees a leg would sit exactly between
     * two cardinals and the prism's orientation would be a coin toss.
     */
    @Test
    @DisplayName("a leg never bends far enough to be ambiguous about its cardinal")
    void legsNeverReachFortyFiveDegrees() {
        double worst = 0;
        for (int cx = -30; cx <= 30; cx++) {
            for (int cz = -30; cz <= 30; cz++) {
                Sightlines.Leg leg = Sightlines.leg(SEED, cx, cz);
                double along = switch (leg.heading()) {
                    case NORTH, SOUTH -> Math.abs(leg.to().z() - leg.from().z());
                    case EAST, WEST -> Math.abs(leg.to().x() - leg.from().x());
                };
                double across = switch (leg.heading()) {
                    case NORTH, SOUTH -> Math.abs(leg.to().x() - leg.from().x());
                    case EAST, WEST -> Math.abs(leg.to().z() - leg.from().z());
                };
                worst = Math.max(worst, Math.toDegrees(Math.atan2(across, along)));
            }
        }
        assertTrue(worst < 35, "a leg bent " + worst + " degrees off its cardinal");
    }

    @Test
    @DisplayName("the leg steps to the neighbouring cell it names")
    void theStepAgreesWithTheNodeItReaches() {
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                Sightlines.Leg leg = Sightlines.leg(SEED, cx, cz);
                assertEquals(Sightlines.node(SEED, cx + leg.heading().dx(), cz + leg.heading().dz()),
                        leg.to());
                assertNotEquals(leg.from(), leg.to());
            }
        }
    }

    @Test
    @DisplayName("all four headings get used, and none of them dominates")
    void headingsAreSpread() {
        Map<Sightlines.Heading, Integer> counts = new HashMap<>();
        int total = 0;
        for (int cx = -50; cx <= 50; cx++) {
            for (int cz = -50; cz <= 50; cz++) {
                counts.merge(Sightlines.leg(SEED, cx, cz).heading(), 1, Integer::sum);
                total++;
            }
        }
        assertEquals(4, counts.size(), "a heading never came up: " + counts);
        for (Map.Entry<Sightlines.Heading, Integer> entry : counts.entrySet()) {
            double share = (double) entry.getValue() / total;
            assertTrue(share > 0.2 && share < 0.3,
                    entry.getKey() + " took " + share + " of the lattice");
        }
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
