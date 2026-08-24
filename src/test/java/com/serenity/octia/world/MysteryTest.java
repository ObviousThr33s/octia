package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bearing every position already had, asserted without a world.
 *
 * <p>{@link Mystery} has no Minecraft imports, which is what lets these run
 * under plain JUnit - the same split {@code SightlinesTest}, {@code RingTest}
 * and {@code IsleTest} get. A mark that points the wrong way would show up in a
 * save only as "the wrecks feel random", which is exactly the kind of wrongness
 * nobody ever reports.
 *
 * <p><b>Two of these tests were wrong before the code was.</b> The first drafts
 * stood 400 and 500 blocks from a node to get a known bearing - which crosses a
 * cell boundary, so they were asking about a different node - and asserted that
 * two seeds broadly disagree, which is untrue of the design and not a defect in
 * it. Both are kept in their corrected form with the mistake written into the
 * comment, because the trap is going to be walked into again.
 */
class MysteryTest {

    private static final long SEED = -5123300595721284843L;

    /** Somewhere well away from the origin, so nothing rides on cell zero. */
    private static final int X = 4_617;
    private static final int Z = -12_204;

    @Test
    @DisplayName("a mark points at the node of the cell you are standing in")
    void aMarkPointsAtTheNode() {
        Mystery.Mark mark = Mystery.toward(SEED, X, Z);
        assertNotNull(mark, "this position is not on a node, so it must have a mark");

        Sightlines.Node node = Sightlines.node(SEED, Sightlines.cell(X), Sightlines.cell(Z));

        // The signs are the whole assertion. If the node is north of us the mark
        // must have a negative dz, because Minecraft's north is negative Z - the
        // one thing this file exists to stop somebody getting backwards.
        int dx = node.x() - X;
        int dz = node.z() - Z;
        if (Math.abs(dx) > Math.abs(dz) * 2) {
            assertEquals(Integer.signum(dx), mark.dx(), "the mark should lean the way the node is, on x");
        }
        if (Math.abs(dz) > Math.abs(dx) * 2) {
            assertEquals(Integer.signum(dz), mark.dz(), "the mark should lean the way the node is, on z");
        }
    }

    @Test
    @DisplayName("standing on the node, there is no way to point, and no mark")
    void onTheNodeThereIsNoMark() {
        Sightlines.Node node = Sightlines.node(SEED, 3, -7);

        assertNull(Mystery.toward(SEED, node.x(), node.z()),
                "at the node itself the answer is nothing, not a direction");
        assertTrue(Mystery.arrived(SEED, node.x(), node.z()));

        // Just inside the radius: still arrived. Just outside: a mark again.
        assertNull(Mystery.toward(SEED, node.x() + Mystery.ARRIVED - 1, node.z()));
        assertNotNull(Mystery.toward(SEED, node.x() + Mystery.ARRIVED + 1, node.z()));
    }

    @Test
    @DisplayName("every mark is one of the eight ring offsets, never the centre")
    void everyMarkIsOnTheRing() {
        int found = 0;
        for (int x = -3000; x <= 3000; x += 137) {
            for (int z = -3000; z <= 3000; z += 149) {
                Mystery.Mark mark = Mystery.toward(SEED, x, z);
                if (mark == null) {
                    continue;
                }
                found++;
                assertTrue(Math.abs(mark.dx()) <= 1 && Math.abs(mark.dz()) <= 1,
                        "a mark must be a ring offset: " + mark);
                assertTrue(mark.dx() != 0 || mark.dz() != 0,
                        "the centre is where the core goes; a mark can never be there");
                assertTrue(mark.octant() >= 0, "a mark must be findable in the ring: " + mark);
            }
        }
        assertTrue(found > 1000, "expected plenty of marks in a 6000-block sweep, got " + found);
    }

    @Test
    @DisplayName("the snap is centred on its cardinal, not started at it")
    void theSnapIsCentred() {
        // Bearings are quantised by rounding, so a hair east of due north is
        // still north. Flooring instead would put the boundary on the cardinal
        // itself and rotate every mark by half an octant - a wrongness that
        // looks plausible in a screenshot and is wrong in every world.
        //
        // Asserted through markFor and not through toward. The earlier draft
        // stood 400 blocks from a node to get a known bearing, which crossed the
        // cell boundary and asked about a different node entirely. A cell is 512
        // blocks wide; walking half of one is not a small step.
        assertEquals(0, Mystery.markFor(0, -100).octant(), "due north is north");
        assertEquals(0, Mystery.markFor(20, -100).octant(), "11 degrees off north is still north");
        assertEquals(0, Mystery.markFor(-20, -100).octant(), "and on the other side of it too");
        assertEquals(1, Mystery.markFor(100, -100).octant(), "due north-east is north-east");
        assertEquals(2, Mystery.markFor(100, 0).octant(), "due east is east");
        assertEquals(4, Mystery.markFor(0, 100).octant(), "due south is south");
        assertEquals(6, Mystery.markFor(-100, 0).octant(), "due west is west");

        // The wrap. A bearing a hair short of 360 rounds to 8 and has to come
        // back to 0, which is what the floorMod is for.
        assertEquals(0, Mystery.markFor(-1, -1000).octant(), "just west of north wraps to north");

        // No delta, no direction.
        assertNull(Mystery.markFor(0, 0));
    }

    @Test
    @DisplayName("bearing is north zero and runs clockwise through east")
    void bearingRunsClockwiseFromNorth() {
        // Pick a position, ask which node IT has, and assert the bearing agrees
        // with that node - rather than assuming a node stays put while the
        // position walks half a cell away from it.
        Sightlines.Node node = Sightlines.node(SEED, Sightlines.cell(X), Sightlines.cell(Z));
        assertEquals(bearingOf(node.x() - X, node.z() - Z),
                Mystery.bearingToNode(SEED, X, Z), 0.001);

        // And the convention itself, on deltas, where no cell can interfere.
        assertEquals(0.0, bearingOf(0, -100), 0.001);
        assertEquals(90.0, bearingOf(100, 0), 0.001);
        assertEquals(180.0, bearingOf(0, 100), 0.001);
        assertEquals(270.0, bearingOf(-100, 0), 0.001);
    }

    /** The convention under test, restated so the test does not trust the code for it. */
    private static double bearingOf(int dx, int dz) {
        double d = Math.toDegrees(Math.atan2(dx, -dz));
        return d < 0 ? d + 360 : d;
    }

    @Test
    @DisplayName("the seed decides the mark near a node, and barely does far from one")
    void theSeedMattersWhereItCan() {
        // The honest version of "a different seed lays different marks". The
        // first draft asserted broad disagreement and failed - correctly. A node
        // wanders at most JITTER=96 inside a 512-block cell, so from out near the
        // cell's edge the bearing is set by the cell's geometry and the seed can
        // hardly move it. That draft was asserting something untrue about the
        // design rather than something wrong with the code.
        long other = SEED ^ 0x9E3779B97F4A7C15L;

        int nearSame = 0;
        int nearTotal = 0;
        int farSame = 0;
        int farTotal = 0;

        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                int centreX = cx * Sightlines.SPACING + Sightlines.SPACING / 2;
                int centreZ = cz * Sightlines.SPACING + Sightlines.SPACING / 2;

                // Near the middle of the cell, where the jitter is most of the
                // vector and the seed therefore decides the answer.
                for (int r = Mystery.ARRIVED + 8; r < Mystery.ARRIVED + 72; r += 8) {
                    Mystery.Mark a = Mystery.toward(SEED, centreX + r, centreZ);
                    Mystery.Mark b = Mystery.toward(other, centreX + r, centreZ);
                    if (a == null || b == null) {
                        continue;
                    }
                    nearTotal++;
                    if (a.equals(b)) {
                        nearSame++;
                    }
                }

                // Out towards the corner of the cell, where it does not.
                Mystery.Mark fa = Mystery.toward(SEED, centreX + 240, centreZ + 240);
                Mystery.Mark fb = Mystery.toward(other, centreX + 240, centreZ + 240);
                if (fa != null && fb != null) {
                    farTotal++;
                    if (fa.equals(fb)) {
                        farSame++;
                    }
                }
            }
        }

        assertTrue(nearTotal > 100 && farTotal > 30,
                "expected a decent sample of both, got " + nearTotal + " and " + farTotal);

        double near = nearSame / (double) nearTotal;
        double far = farSame / (double) farTotal;

        assertTrue(near < 0.6,
                "close to a node the seed should be deciding the mark, but two seeds agreed "
                        + Math.round(near * 100) + "% of the time");
        assertTrue(far > near,
                "far from a node the cell geometry should dominate, so agreement should RISE: "
                        + "near " + Math.round(near * 100) + "%, far " + Math.round(far * 100) + "%");
    }

    @Test
    @DisplayName("the same seed and position always answer the same")
    void itIsAFunction() {
        // The whole reason this class has no Minecraft imports. A generation
        // worker consults it from several threads at once and must get the same
        // answer every time, or two chunks of one wreck disagree.
        for (int i = 0; i < 200; i++) {
            assertEquals(Mystery.toward(SEED, X + i, Z - i), Mystery.toward(SEED, X + i, Z - i));
        }
    }
}
