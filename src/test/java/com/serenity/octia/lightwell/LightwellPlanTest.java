package com.serenity.octia.lightwell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.serenity.octia.tower.TowerPlan;

/**
 * A lightwell is four numbers, so almost everything that can go wrong with one
 * is arithmetic - and arithmetic is exactly what a headless gate can prove.
 *
 * <p>Tested here rather than as a GameTest for the same reason
 * {@code TowerPlanTest} is: {@link LightwellPlan} has no Minecraft on its
 * imports on purpose, so that the half of the feature which is decidable is
 * decided here and only the look of the thing is left to the world.
 *
 * <p>The tests that matter most are the two invariants. One, that the block
 * count the cap is enforced against is the count actually produced - a cap that
 * disagrees with the thing it caps is worse than no cap. Two, that the shaft is
 * clear from grade to apex, because that is the only claim the whole structure
 * exists to make.
 */
class LightwellPlanTest {

    /** A well on the default step: mouth 23, shaft 3, so five levels. */
    private static LightwellPlan well() {
        return LightwellPlan.of(23, 3);
    }

    @Test
    @DisplayName("depth is derived from the envelope, never chosen")
    void levelsFallOutOfTheGeometry() {
        LightwellPlan w = well();
        // (23-1)/2 = 11 half-width, (3-1)/2 = 1 shaft half, 10 to step down by 5
        assertEquals(5, w.inset());
        assertEquals(3, w.levels(), "ten to cross in steps of five is two steps, plus the apex");
        assertEquals(11, w.outerHalf(0));
        assertEquals(6, w.outerHalf(1));
        assertEquals(1, w.outerHalf(2), "the last level has arrived at the shaft");
        assertEquals(0, w.ringWidth(2), "no ring left is the definition of the apex");
    }

    @Test
    @DisplayName("the envelope narrows going down and the shaft does not")
    void thePyramidInverts() {
        LightwellPlan w = LightwellPlan.of(43, 3, 5, 4);
        int previous = Integer.MAX_VALUE;
        for (int i = 0; i < w.levels(); i++) {
            assertTrue(w.outerHalf(i) < previous, "level " + i + " must be inside the one above");
            previous = w.outerHalf(i);
        }
        assertEquals(3, w.shaft(), "the shaft is the one measurement that does not taper");
    }

    /**
     * The claim the whole structure is built to make. If a single block lands
     * inside the shaft above the apex, the well is a cellar with a hole in it
     * and every argument in the class javadoc is void.
     */
    @Test
    @DisplayName("nothing stands in the shaft between grade and the apex")
    void theShaftIsClearAllTheWayDown() {
        // 45 and 5: half-width 22 less shaft-half 2 is 20, which five divides.
        // 43 with a shaft of 5 leaves 19 and is refused, correctly - that was
        // this test's first draft, and the class caught it.
        LightwellPlan w = LightwellPlan.of(45, 5, 5, 4);
        int shaftHalf = (w.shaft() - 1) / 2;
        int apexFloor = w.floorY(w.levels() - 1);

        for (LightwellPlan.Block b : w.blocks()) {
            boolean inShaft = Math.max(Math.abs(b.x()), Math.abs(b.z())) <= shaftHalf;
            if (!inShaft) {
                continue;
            }
            assertTrue(b.y() <= apexFloor + 1,
                    "a block at y=" + b.y() + " stands in the shaft, which must be open"
                            + " from grade down to the apex floor at " + apexFloor);
        }
    }

    /** The measurements the invariants are swept over, including awkward ones. */
    private static final int[][] SPREAD = {
            {23, 3, 5, 3},
            {43, 3, 5, 4},
            {13, 3, 5, 8},
            {21, 5, 2, 3},
            {9, 3, 1, 3},
            {11, 5, 3, 3},
            {15, 3, 6, 5},
    };

    /**
     * A cap that disagrees with the thing it caps is worse than no cap. The
     * analytic count and the generated list are two derivations of one number,
     * so they are checked against each other rather than each against itself.
     */
    @Test
    @DisplayName("the projected block count is the count that is built")
    void theCapAgreesWithTheBuild() {
        for (int[] c : SPREAD) {
            LightwellPlan w = LightwellPlan.of(c[0], c[1], c[2], c[3]);
            assertEquals(w.blocks().size(), w.projectedBlocks(),
                    "mouth " + c[0] + " shaft " + c[1] + " inset " + c[2] + " storey " + c[3]);
        }
    }

    /**
     * One count worked out by hand, so the two derivations above cannot drift
     * together. mouth 9, shaft 3, inset 1, storey 3 gives four levels:
     *
     * <pre>
     * level 0  outer 4  floor 9x9 - 3x3 = 72  wall 8*4*2 = 64  kerb 16  = 152
     * level 1  outer 3  floor 7x7 - 3x3 = 40  wall 8*3*2 = 48  kerb 16  = 104
     * level 2  outer 2  floor 5x5 - 3x3 = 16  wall 8*2*2 = 32  kerb  0  =  48
     * level 3  outer 1  apex, floor 3x3 =  9  no wall        kerb  0  =    9
     * anchorage                                                         =   1
     *                                                                    ---
     *                                                                    314
     * </pre>
     *
     * <p>Two of those zeroes were bugs before they were rules. Level 2's ring is
     * one deep, so its lip and its retaining wall are the same cells and only
     * one may be placed. Level 3 is the apex, where the envelope has arrived at
     * the shaft and a wall would stand in the shaft itself. At the default inset
     * of five neither level occurs, so the obvious well would have hidden both.
     */
    @Test
    @DisplayName("a well counted by hand comes to what it is built as")
    void oneWellCountedByHand() {
        LightwellPlan w = LightwellPlan.of(9, 3, 1, 3);
        assertEquals(4, w.levels());
        assertEquals(314, w.blocks().size());
        assertEquals(314L, w.projectedBlocks());
    }

    @Test
    @DisplayName("no two blocks are placed at the same spot")
    void nothingIsPlacedTwice() {
        for (int[] c : SPREAD) {
            LightwellPlan w = LightwellPlan.of(c[0], c[1], c[2], c[3]);
            Set<String> seen = new HashSet<>();
            for (LightwellPlan.Block b : w.blocks()) {
                String at = b.x() + "," + b.y() + "," + b.z();
                assertTrue(seen.add(at), "two blocks at " + at + " in mouth " + c[0]
                        + " shaft " + c[1] + " inset " + c[2]
                        + "; a well that writes a cell twice is doing work it cannot account for");
            }
        }
    }

    @Test
    @DisplayName("there is exactly one anchorage and it is at the bottom")
    void oneCoreAtTheApex() {
        LightwellPlan w = well();
        List<LightwellPlan.Block> cores = w.blocks().stream()
                .filter(b -> b.cell() == TowerPlan.Cell.CORE)
                .toList();
        assertEquals(1, cores.size(), "a well is a place, so it has one place of arrival");
        LightwellPlan.Block core = cores.get(0);
        assertEquals(0, core.x());
        assertEquals(0, core.z());
        assertEquals(w.floorY(w.levels() - 1) + 1, core.y(),
                "the anchorage stands on the apex floor, not at grade");
    }

    @Test
    @DisplayName("y is zero at grade and negative downward")
    void theFrameIsDugNotStacked() {
        LightwellPlan w = well();
        assertTrue(w.floorY(0) < 0, "the first floor is below grade");
        assertTrue(w.floorY(1) < w.floorY(0), "each floor is below the one before it");
        for (LightwellPlan.Block b : w.blocks()) {
            assertTrue(b.y() < 0, "a dug well places nothing at or above grade");
        }
    }

    // ---- Refusals ------------------------------------------------------

    @Test
    @DisplayName("an even width has no centre and is refused")
    void evenWidthsAreRefused() {
        assertThrows(LightwellPlan.Malformed.class, () -> LightwellPlan.of(24, 3));
        assertThrows(LightwellPlan.Malformed.class, () -> LightwellPlan.of(23, 4));
    }

    /**
     * The refusal has to name a way out. A message that says only "no" leaves
     * the author guessing at arithmetic the class has already done.
     */
    @Test
    @DisplayName("an envelope that misses the shaft is refused, and the refusal names a fix")
    void aMissedShaftNamesTheNearestFit() {
        LightwellPlan.Malformed e = assertThrows(LightwellPlan.Malformed.class,
                () -> LightwellPlan.of(25, 3, 5, 3));
        assertTrue(e.getMessage().contains("23") || e.getMessage().contains("33"),
                "the refusal should name a mouth that works, was: " + e.getMessage());
        // and the ones it names must actually work
        LightwellPlan.of(23, 3, 5, 3);
        LightwellPlan.of(33, 3, 5, 3);
    }

    @Test
    @DisplayName("a shaft wider than the mouth is refused")
    void aShaftCannotExceedItsMouth() {
        assertThrows(LightwellPlan.Malformed.class, () -> LightwellPlan.of(9, 11));
    }

    @Test
    @DisplayName("a step of nothing never reaches the bottom")
    void zeroInsetIsRefused() {
        assertThrows(LightwellPlan.Malformed.class, () -> LightwellPlan.of(23, 3, 0, 3));
    }

    @Test
    @DisplayName("storeys out of range are refused at both ends")
    void storeyRangeIsEnforced() {
        assertThrows(LightwellPlan.Malformed.class, () -> LightwellPlan.of(23, 3, 5, 2));
        assertThrows(LightwellPlan.Malformed.class, () -> LightwellPlan.of(23, 3, 5, 9));
    }

    /**
     * The cap has to bite before the allocation, not after.
     *
     * <p>This test earned its keep: the ceiling was first set to 200,000, and
     * this failed because the largest well the other caps allow is 77,674
     * blocks - so the guard could never fire. A cap that cannot refuse anything
     * is a comment wearing the costume of a safeguard.
     */
    @Test
    @DisplayName("an oversized well is refused before it is built")
    void theCapRefusesBeforeAllocating() {
        LightwellPlan.Malformed e = assertThrows(LightwellPlan.Malformed.class,
                () -> LightwellPlan.of(65, 3, 1, 8));
        assertTrue(e.getMessage().contains("ceiling"), e.getMessage());
    }

    /**
     * And the ceiling must still be reachable from below, or it is the opposite
     * mistake: a guard so tight that ordinary wells cannot be built.
     */
    @Test
    @DisplayName("the ceiling refuses the largest wells and admits ordinary ones")
    void theCeilingSitsBetweenTheTwo() {
        LightwellPlan ordinary = LightwellPlan.of(43, 3, 5, 4);
        assertTrue(ordinary.projectedBlocks() < LightwellPlan.MAX_BLOCKS,
                "a 43-wide well on the default step is ordinary and must be allowed, was "
                        + ordinary.projectedBlocks());
    }

    // ---- Daylight, which is advisory ------------------------------------

    @Test
    @DisplayName("a ring deeper than light can cross is reported, not refused")
    void wideRingsAreReportedNotRefused() {
        // mouth 43, shaft 3: the top ring is 20 deep, well past the reach of sky
        LightwellPlan w = LightwellPlan.of(43, 3, 5, 4);
        assertEquals(0, w.estimatedLightAtWall(0), "twenty blocks in is dark");
        List<LightwellPlan.Finding> found = w.daylight();
        assertFalse(found.isEmpty(), "a twenty-deep ring should be reported");
        assertEquals(0, found.get(0).level());
        assertFalse(w.fullyLit());
    }

    @Test
    @DisplayName("a well on the brightest step is lit to its edges at every level")
    void aNarrowRingIsFullyLit() {
        // rings of 6 reach exactly GROWTH_LIGHT at the wall
        assertEquals(6, LightwellPlan.brightestRing());
        LightwellPlan w = LightwellPlan.of(15, 3, LightwellPlan.brightestRing(), 3);
        assertTrue(w.fullyLit(), "every ring is at the growth threshold: " + w.daylight());
        assertEquals(TowerPlan.GROWTH_LIGHT, w.estimatedLightAtWall(0));
    }

    @Test
    @DisplayName("the apex has no ring, so it is never reported as dim")
    void theApexIsNotAFinding() {
        LightwellPlan w = LightwellPlan.of(43, 3, 5, 4);
        for (LightwellPlan.Finding f : w.daylight()) {
            assertTrue(w.ringWidth(f.level()) > 0, "the apex has no ring to light");
        }
    }

    /**
     * Depth is free and width is not. This is the design claim of the whole
     * form, so it is asserted rather than left in a comment: a deeper well is
     * not a darker one.
     */
    @Test
    @DisplayName("going deeper costs no light; going wider does")
    void depthIsFreeAndWidthIsNot() {
        LightwellPlan shallow = LightwellPlan.of(15, 3, 6, 3);
        LightwellPlan deep = LightwellPlan.of(15, 3, 6, 8);
        assertEquals(shallow.estimatedLightAtWall(0), deep.estimatedLightAtWall(0),
                "a taller storey digs deeper and changes no ring width");
        assertTrue(deep.depth() > shallow.depth());

        LightwellPlan wide = LightwellPlan.of(27, 3, 12, 3);
        assertTrue(wide.estimatedLightAtWall(0) < shallow.estimatedLightAtWall(0),
                "a wider ring is a darker one");
    }

    // ---- The section ----------------------------------------------------

    @Test
    @DisplayName("the section reads as a pyramid pointing down")
    void theSectionNarrows() {
        LightwellPlan w = LightwellPlan.of(23, 3);
        List<String> section = w.section();
        assertEquals(w.levels(), section.size());
        int previous = Integer.MAX_VALUE;
        for (String row : section) {
            int solid = row.trim().length();
            assertTrue(solid < previous, "each row is narrower than the one above it: " + section);
            previous = solid;
        }
    }

    @Test
    @DisplayName("the section is drawn in the tower's own vocabulary")
    void theSectionSharesTheVocabulary() {
        for (String row : LightwellPlan.of(23, 3).section()) {
            for (char c : row.toCharArray()) {
                if (c == ' ') {
                    continue;
                }
                assertTrue(TowerPlan.Cell.of(c) != null,
                        "'" + c + "' is not in the vocabulary the tower already owns");
            }
        }
    }
}
