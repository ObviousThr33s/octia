package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The island shape, asserted without a world.
 *
 * <p>{@link Isle} has no Minecraft imports, which is what lets these run under
 * plain JUnit - the same split {@code SightlinesTest} and {@code RingTest} get,
 * and for the same reason: a shape that can be checked here is a shape nobody
 * has to go and look at in a save.
 */
class IsleTest {

    private static final int R = 7;

    @Test
    @DisplayName("the cap is flat: grass and its soil are all full width")
    void theCapIsFlat() {
        for (int depth = 0; depth <= Isle.SOIL; depth++) {
            assertEquals(R, Isle.radiusAt(R, depth),
                    "depth " + depth + " is soil and should be full width");
        }
        assertTrue(Isle.radiusAt(R, Isle.SOIL + 1) < R,
                "the taper has to start the layer after the soil, or the island is a plug");
    }

    @Test
    @DisplayName("it tapers to nothing, and thickness is where that happens")
    void itTapersToNothing() {
        int thickness = Isle.thickness(R);

        assertTrue(Isle.radiusAt(R, thickness - 1) >= 0,
                "the last layer thickness counts must still exist");
        assertTrue(Isle.radiusAt(R, thickness) < 0,
                "the layer past the last one must not");

        // Monotonic, so nothing bulges out again on the way down. A layer wider
        // than the one above it would be an overhang the terrain never makes.
        for (int depth = 1; depth < thickness; depth++) {
            assertTrue(Isle.radiusAt(R, depth) <= Isle.radiusAt(R, depth - 1),
                    "depth " + depth + " is wider than the layer above it");
        }
    }

    @Test
    @DisplayName("thickness is derived from radiusAt, not stated beside it")
    void thicknessAgreesWithTheProfile() {
        // The point of the test is the agreement, so it is checked across sizes
        // rather than at the one radius the game happens to use. Two constants
        // that have to match are one constant and a bug waiting to happen.
        for (int radius = 0; radius <= 24; radius++) {
            int thickness = Isle.thickness(radius);
            assertTrue(thickness >= 1, "even a one-block island is one layer thick");
            assertTrue(Isle.radiusAt(radius, thickness) < 0,
                    "radius " + radius + " claims " + thickness + " layers and has more");
        }
    }

    @Test
    @DisplayName("a column is held if it is inside the circle for its depth")
    void holdsIsCircular() {
        // On the axis at exactly the radius: in. One past: out.
        assertTrue(Isle.holds(R, 0, R, 0));
        assertFalse(Isle.holds(R, 0, R + 1, 0));

        // The diagonal corner of the bounding square is outside the circle,
        // which is the whole difference between an island and a slab.
        assertFalse(Isle.holds(R, 0, R, R));

        assertTrue(Isle.holds(R, 0, 0, 0), "the centre is always island");
    }

    @Test
    @DisplayName("below the last layer nothing is held, at any offset")
    void nothingIsHeldPastTheBottom() {
        int past = Isle.thickness(R);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                assertFalse(Isle.holds(R, past, dx, dz),
                        "depth " + past + " is past the bottom and held (" + dx + ", " + dz + ")");
            }
        }
    }

    @Test
    @DisplayName("a negative depth is not a layer")
    void negativeDepthIsNotALayer() {
        // raise() counts downward from zero and never asks this, but a shape
        // that answers a nonsense question with a plausible number is how a
        // later caller gets a layer above the grass.
        assertTrue(Isle.radiusAt(R, -1) < 0);
        assertFalse(Isle.holds(R, -1, 0, 0));
    }
}
