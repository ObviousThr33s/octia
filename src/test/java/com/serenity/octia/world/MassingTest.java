package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stair tower's shape, asserted without a world.
 *
 * <p>{@link Massing} has no Minecraft imports, so the design's one load-bearing
 * claim - that a sphere cut on the plane of the face it sits against can never
 * bulge back inside the shaft - is settled here by exhaustion rather than by
 * looking at a save. Every legal shaft and every legal radius is walked, which is
 * a few hundred thousand cells and still instant.
 */
class MassingTest {

    /** Every shaft and radius worth checking, walked by the sweeps below. */
    private static final int[] HALVES = {1, 2, 3};
    private static final int[] HEIGHTS = {4, 7, 12, 20};
    private static final int MAX_RADIUS = 7;

    @Test
    @DisplayName("the flat slice holds: no cap ever bulges into the shaft")
    void theFlatSliceHolds() {
        sweep((halfW, halfD, height, radius) -> {
            int span = Math.max(halfW, halfD) + radius + 2;
            // Strictly between the two end faces - the region only the prism may own.
            for (int dy = 1; dy < height; dy++) {
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        if (!Massing.holds(dx, dy, dz, halfW, halfD, height, radius)) {
                            continue;
                        }
                        // Supplier form throughout the sweeps: these loops run into
                        // the millions, and an eagerly concatenated message would
                        // cost more than the arithmetic being asserted.
                        int cx = dx;
                        int cy = dy;
                        int cz = dz;
                        assertTrue(Massing.inPrism(dx, dy, dz, halfW, halfD, height),
                                () -> "a cell between the faces is held but is not prism: "
                                        + cx + "," + cy + "," + cz
                                        + " in " + halfW + "x" + halfD
                                        + " h" + height + " r" + radius);
                    }
                }
            }
        });
    }

    @Test
    @DisplayName("the two mouths are mirrors, so a tower reads the same from either end")
    void theTwoMouthsAreMirrors() {
        sweep((halfW, halfD, height, radius) -> {
            int span = Math.max(halfW, halfD) + radius + 2;
            for (int out = 0; out <= radius + 1; out++) {
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        boolean below = Massing.holds(dx, -out, dz, halfW, halfD, height, radius);
                        boolean above = Massing.holds(dx, height + out, dz,
                                halfW, halfD, height, radius);
                        assertEquals(below, above,
                                "the caps disagree " + out + " out at "
                                        + dx + "," + dz);
                    }
                }
            }
        });
    }

    @Test
    @DisplayName("the prism is held whole, and nothing beyond the caps is")
    void thePrismIsHeldWhole() {
        sweep((halfW, halfD, height, radius) -> {
            for (int dy = 0; dy <= height; dy++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfD; dz <= halfD; dz++) {
                        assertTrue(Massing.holds(dx, dy, dz, halfW, halfD, height, radius),
                                "a prism cell is not held: " + dx + "," + dy + "," + dz);
                    }
                }
            }
            // One block past the cap's reach, straight out from each face's centre.
            assertFalse(Massing.holds(0, -(radius + 1), 0, halfW, halfD, height, radius),
                    "a cell past the bottom cap is held");
            assertFalse(Massing.holds(0, height + radius + 1, 0,
                    halfW, halfD, height, radius),
                    "a cell past the top cap is held");
        });
    }

    @Test
    @DisplayName("the smallest legal cap swallows the corners, and one less does not")
    void theSmallestCapSwallowsTheCorners() {
        for (int halfW : HALVES) {
            for (int halfD : HALVES) {
                int radius = Massing.minRadius(halfW, halfD);

                for (int sx = -halfW; sx <= halfW; sx += Math.max(1, halfW * 2)) {
                    for (int sz = -halfD; sz <= halfD; sz += Math.max(1, halfD * 2)) {
                        assertTrue(Massing.inBall(sx, 0, sz, radius),
                                "corner " + sx + "," + sz + " is outside its own cap "
                                        + "at r" + radius);
                    }
                }

                // Tight, or the floor is not a floor: one less must leave a corner out.
                assertFalse(Massing.inBall(halfW, 0, halfD, radius - 1),
                        "r" + (radius - 1) + " already covers " + halfW + "x" + halfD
                                + ", so minRadius is loose");
            }
        }
    }

    @Test
    @DisplayName("a site that fits the chunk never reaches past the chunk")
    void aFittingSiteNeverReachesPast() {
        sweep((halfW, halfD, height, radius) -> {
            if (!Massing.fitsChunk(halfW, halfD, radius)) {
                return;
            }
            int span = Math.max(halfW, halfD) + radius + 2;
            for (int dy = -radius - 1; dy <= height + radius + 1; dy++) {
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        if (!Massing.holds(dx, dy, dz, halfW, halfD, height, radius)) {
                            continue;
                        }
                        assertTrue(Math.abs(dx) <= Massing.REACH
                                        && Math.abs(dz) <= Massing.REACH,
                                "a fitting site reaches " + dx + "," + dz
                                        + " which is past " + Massing.REACH);
                    }
                }
            }
        });
    }

    @Test
    @DisplayName("the legal design space is three wide and five wide, and no wider")
    void theLegalDesignSpace() {
        assertEquals(java.util.List.of(2, 3, 4, 5, 6), legalRadii(1),
                "a 3x3 shaft's legal radii");
        assertEquals(java.util.List.of(3, 4, 5), legalRadii(2),
                "a 5x5 shaft's legal radii");
        assertTrue(legalRadii(3).isEmpty(),
                "a 7x7 shaft has no legal cap and must fall back to switchbacks");
    }

    private static java.util.List<Integer> legalRadii(int half) {
        java.util.List<Integer> legal = new java.util.ArrayList<>();
        for (int radius = 1; radius <= 8; radius++) {
            if (radius >= Massing.minRadius(half, half)
                    && Massing.fitsChunk(half, half, radius)) {
                legal.add(radius);
            }
        }
        return legal;
    }

    /** Walks every shaft, height and radius worth asserting over. */
    private static void sweep(Case body) {
        for (int halfW : HALVES) {
            for (int halfD : HALVES) {
                for (int height : HEIGHTS) {
                    for (int radius = 1; radius <= MAX_RADIUS; radius++) {
                        body.check(halfW, halfD, height, radius);
                    }
                }
            }
        }
    }

    private interface Case {
        void check(int halfW, int halfD, int height, int radius);
    }
}
