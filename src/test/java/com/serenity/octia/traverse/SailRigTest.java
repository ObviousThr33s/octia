package com.serenity.octia.traverse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sums a sail does, checked without a world.
 *
 * <p>These pin the two invariants the whole feature is - the cap and the band -
 * as postconditions over adversarial input, plus the truth table that decides
 * when the sail is open at all. The other half, where the same math is driven
 * through a real player's inventory tick, is {@code SailRigGameTest}.
 */
class SailRigTest {

    /** Fixed seed for the adversarial draws, its own constant. */
    private static final long DRAW_SEED = 0x5A11_A16L;

    /** A draw in {@code [-span, span]}. */
    private static double spread(Random random, double span) {
        return (random.nextDouble() * 2 - 1) * span;
    }

    /** Unsigned angle between two motions' horizontal components, radians. */
    private static double angleBetween(SailRig.Motion a, SailRig.Motion b) {
        double dot = a.x() * b.x() + a.z() * b.z();
        double cross = a.x() * b.z() - a.z() * b.x();
        return Math.abs(Math.atan2(cross, dot));
    }

    @Test
    @DisplayName("no input leaves a glide tick above the cap")
    void theCapHoldsForAnyInput() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < 10_000; i++) {
            SailRig.Motion in = new SailRig.Motion(
                    spread(random, 40), spread(random, 40), spread(random, 40));
            SailRig.Motion out = SailRig.glide(in, spread(random, 1), spread(random, 1));
            assertTrue(SailRig.horizontal(out.x(), out.z()) <= SailRig.HARD_CAP + 1e-9,
                    "the cap broke on draw " + i);
        }
    }

    @Test
    @DisplayName("the band holds, and an upward input never nets positive")
    void theBandHoldsAndNeverNetsPositive() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < 10_000; i++) {
            SailRig.Motion in = new SailRig.Motion(
                    spread(random, 40), spread(random, 40), spread(random, 40));
            SailRig.Motion out = SailRig.glide(in, spread(random, 1), spread(random, 1));
            assertTrue(out.y() >= -SailRig.SINK_MAX - 1e-9, "the arrest broke on draw " + i);
            assertTrue(out.y() <= -SailRig.SINK_MIN + 1e-9, "the climb clamp broke on draw " + i);
        }

        // A hard upward impulse comes out at exactly the gentle sink - the
        // sail refuses updrafts, which is the no-net-climb law itself.
        SailRig.Motion up = SailRig.glide(new SailRig.Motion(0.1, 40.0, 0.1), 1.0, 0.0);
        assertEquals(-SailRig.SINK_MIN, up.y());
    }

    @Test
    @DisplayName("a chain of ticks only ever sinks, whatever is re-injected")
    void aChainOfTicksOnlyEverSinks() {
        SailRig.Motion motion = new SailRig.Motion(40.0, 5.0, -40.0);
        double fell = 0;
        for (int tick = 0; tick < 200; tick++) {
            if (tick % 3 == 0) {
                motion = new SailRig.Motion(motion.x(), motion.y() + 1.0, motion.z());
            }
            double angle = tick * 0.7;
            motion = SailRig.glide(motion, Math.cos(angle), Math.sin(angle));
            assertTrue(motion.y() <= -SailRig.SINK_MIN + 1e-9,
                    "an executed tick moved up at tick " + tick);
            fell += motion.y();
        }
        assertTrue(fell < -200 * SailRig.SINK_MIN + 1e-6,
                "200 ticks did not fall 200 gentle sinks: " + fell);
    }

    @Test
    @DisplayName("pushing along the look from the cap stays at the cap")
    void thePushCannotBeatTheCap() {
        double lookX = 0.6;
        double lookZ = 0.8;
        SailRig.Motion in = new SailRig.Motion(
                SailRig.HARD_CAP * lookX, -0.1, SailRig.HARD_CAP * lookZ);
        SailRig.Motion out = SailRig.glide(in, lookX, lookZ);
        assertEquals(SailRig.HARD_CAP, SailRig.horizontal(out.x(), out.z()), 1e-9);
    }

    @Test
    @DisplayName("a vertical look pushes nothing")
    void aVerticalLookPushesNothing() {
        SailRig.Motion in = new SailRig.Motion(0.2, -0.5, -0.1);

        SailRig.Motion straightDown = SailRig.glide(in, 0.0, 0.0);
        assertEquals(in.x(), straightDown.x());
        assertEquals(in.z(), straightDown.z());

        SailRig.Motion nearlyDown = SailRig.glide(in,
                SailRig.LOOK_EPSILON / 2, -SailRig.LOOK_EPSILON / 2);
        assertEquals(in.x(), nearlyDown.x());
        assertEquals(in.z(), nearlyDown.z());
    }

    @Test
    @DisplayName("the sail opens only in a plain fall, and only past the threshold")
    void theSailOpensOnlyInAPlainFall() {
        float past = SailRig.DEPLOY_FALL + 0.01F;

        assertTrue(SailRig.deploys(false, false, false, past));
        assertFalse(SailRig.deploys(true, false, false, past), "it opened on the ground");
        assertFalse(SailRig.deploys(false, true, false, past), "it opened in a fluid");
        assertFalse(SailRig.deploys(false, false, true, past), "it opened while carried");
        assertFalse(SailRig.deploys(false, false, false, SailRig.DEPLOY_FALL),
                "at exactly the threshold the sail must still be stowed");
        assertFalse(SailRig.deploys(false, false, false, 0.0F));
    }

    @Test
    @DisplayName("the ground remembers at most a soft fall, and the memory keeps the latch")
    void theGroundRemembersAtMostASoftFall() {
        assertEquals(SailRig.SOFT_FALL, SailRig.remembered(40.0F));
        assertEquals(1.0F, SailRig.remembered(1.0F), 0.0F);
        assertTrue(SailRig.remembered(SailRig.SOFT_FALL) > SailRig.DEPLOY_FALL,
                "the capped memory fell below the deploy threshold - the latch is broken");
    }

    @Test
    @DisplayName("the constants keep their promises to each other")
    void theConstantsKeepTheirPromises() {
        assertTrue(SailRig.DEPLOY_FALL < SailRig.SOFT_FALL,
                "the latch flaps open-shut mid-glide");
        assertTrue(SailRig.SOFT_FALL + 2 * SailRig.SINK_MAX <= 3.0F,
                "a landing under sail stings");
        assertTrue(0 < SailRig.SINK_MIN && SailRig.SINK_MIN <= SailRig.SINK_MAX,
                "the band inverts");
        assertTrue(SailRig.GLIDE_PUSH * 0.91 / 0.09 > SailRig.HARD_CAP,
                "drag, not the cap, owns top speed");
        assertTrue(SailRig.HARD_CAP * 20.0 <= 9.0 + 1.0E-9,
                "the save-safety law's own number is broken");
    }

    @Test
    @DisplayName("steering preserves speed, respects the budget, and runs the thread both ways")
    void steeringPreservesSpeed() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < 2_000; i++) {
            SailRig.Motion in = new SailRig.Motion(spread(random, 1), -0.1, spread(random, 1));
            double budget = random.nextDouble() * 0.5;
            SailRig.Motion out = SailRig.steer(in, spread(random, 1), spread(random, 1), budget);

            assertEquals(SailRig.horizontal(in.x(), in.z()),
                    SailRig.horizontal(out.x(), out.z()), 1e-9,
                    "the speed moved on draw " + i);
            assertEquals(in.y(), out.y());
            assertTrue(angleBetween(in, out) <= budget + 1e-9,
                    "the turn beat its budget on draw " + i);
        }

        // A velocity nearer the reversed axis steers toward the reversal - the
        // thread runs both ways, and an assist that turned a westbound glide
        // eastward would be a rudder fighting the sail.
        SailRig.Motion west = new SailRig.Motion(-1.0, -0.1, 0.05);
        SailRig.Motion nudged = SailRig.steer(west, 1.0, 0.0, 0.02);
        assertTrue(nudged.x() < 0, "the assist reversed the glide");
        assertTrue(Math.abs(nudged.z()) < Math.abs(west.z()),
                "the assist turned away from the thread");

        // The three refusals hand back the input itself.
        SailRig.Motion still = new SailRig.Motion(0.0, -0.1, 0.0);
        assertSame(still, SailRig.steer(still, 1.0, 0.0, 0.5));
        SailRig.Motion moving = new SailRig.Motion(0.2, -0.1, 0.1);
        assertSame(moving, SailRig.steer(moving, 0.0, 0.0, 0.5));
        assertSame(moving, SailRig.steer(moving, 1.0, 0.0, 0.0));
    }
}
