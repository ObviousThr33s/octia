package com.serenity.octia.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sums a void squid does, checked without a world.
 *
 * <p>These pin the two things the creature is - the band it cannot leave and the
 * refusal that cannot push it anywhere - as postconditions over adversarial
 * input, plus the lattice of promises the constants make to each other. The
 * other half, where the same math is driven through a real entity in a real
 * level, is {@code VoidSquidGameTest}.
 *
 * <p><b>The band is the half that only lives here.</b> It is stated in absolute
 * world Y and a gametest plot is never in it, so no test with a world in it can
 * ever watch a squid bob between the floor and the ceiling. This file is the
 * only place that claim is made good.
 *
 * <p><b>Corrected [2026-08-24]: a gametest plot can be in it, and one was.</b>
 * The runner placed a plot at world Y -60, two blocks under
 * {@link VoidSquidDrift#BAND_FLOOR}; a squid spawned there recovered into the
 * band and bobbed, and {@code VoidSquidGameTest.itStaysInsideItsBand} failed
 * because it was still applying the below-the-floor clause. The correction is
 * written out in that test. What is kept from the paragraph above is the reason
 * this file exists: a plot reaches whichever two or three blocks of the band it
 * happens to land beside, so it can watch a bob but it cannot walk a band from
 * floor to ceiling, and {@link #theBandCannotBeSteppedOutOf} does that in
 * twenty thousand draws with no world at all. This file's tests were green
 * through the failure and were right to be - {@code rise} is asked here about
 * the altitude it is about to move from, which is what the gametest had stopped
 * doing.
 */
class VoidSquidDriftTest {

    /** Fixed seed for the adversarial draws, its own constant. */
    private static final long DRAW_SEED = 0x5_0_1D_1DL;

    /** How many random squids each sweep walks. */
    private static final int SQUIDS = 500;

    /** A draw in {@code [-span, span]}. */
    private static double spread(Random random, double span) {
        return (random.nextDouble() * 2 - 1) * span;
    }

    @Test
    @DisplayName("the band sits inside the void, with clearance at both ends")
    void theBandSitsInsideTheVoid() {
        assertTrue(VoidSquidDrift.BAND_FLOOR < VoidSquidDrift.BAND_CEILING,
                "the band is upside down");

        // The squid keeps CLEARANCE blocks of air under it, so a floor set less
        // than that above the world's own floor is a squid hanging off the
        // bottom of the world with nothing under it to check.
        assertTrue(VoidSquidDrift.WORLD_FLOOR + VoidSquidDrift.CLEARANCE
                        < VoidSquidDrift.BAND_FLOOR,
                "the band's floor is too near the bottom of the world");

        // And the same over its head: the continent's underside is the floor of
        // the noise band, so a ceiling within CLEARANCE of it means a squid can
        // never legally be at its own ceiling.
        assertTrue(VoidSquidDrift.BAND_CEILING + VoidSquidDrift.CLEARANCE
                        < VoidSquidDrift.UNDERSIDE,
                "the band's ceiling is too near the continent's underside");
    }

    @Test
    @DisplayName("nothing the squid does is smaller than vanilla's velocity floor")
    void nothingIsBelowTheTruncation() {
        // LivingEntity.aiStep zeroes any component under 0.003 before it
        // travels, so a speed tuned below that is silently no speed at all.
        assertTrue(VoidSquidDrift.DRIFT > VoidSquidDrift.TRUNCATION, "the drift is invisible");
        assertTrue(VoidSquidDrift.BOB > VoidSquidDrift.TRUNCATION, "the bob is invisible");
        assertTrue(VoidSquidDrift.RECOVER > VoidSquidDrift.TRUNCATION,
                "a squid outside the band would never come back");
    }

    @Test
    @DisplayName("a squid is slower than walking away from one")
    void theDriftIsNotAWayToTravel() {
        // The save-safety law is about what a mechanic invites. A walk is 4.317
        // blocks a second and the 8/23 flight that stalled the autosave was 33;
        // at one block a second nobody follows a squid anywhere.
        assertTrue(VoidSquidDrift.DRIFT * 20 <= 1.0,
                "a void squid is fast enough to be worth following");
        assertTrue(VoidSquidDrift.RECOVER * 20 <= 2.0,
                "the return to the band is fast enough to be worth riding");
    }

    @Test
    @DisplayName("the drift is always exactly one speed, whatever the squid or the tick")
    void theDriftIsOneSpeed() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < SQUIDS; i++) {
            long mark = random.nextLong();
            for (int tick = 0; tick < 40; tick++) {
                VoidSquidDrift.Drift drift =
                        VoidSquidDrift.drift(mark, random.nextInt(100_000), spread(random, 200));
                assertEquals(VoidSquidDrift.DRIFT, VoidSquidDrift.horizontal(drift), 1.0E-12,
                        "the horizontal speed moved on draw " + i + "/" + tick);
            }
        }
    }

    @Test
    @DisplayName("a squid inside the band cannot step out of it")
    void theBandCannotBeSteppedOutOf() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < 20_000; i++) {
            double y = VoidSquidDrift.BAND_FLOOR
                    + random.nextDouble() * (VoidSquidDrift.BAND_CEILING - VoidSquidDrift.BAND_FLOOR);
            long tick = random.nextInt(100_000);
            double after = y + VoidSquidDrift.rise(y, tick);
            // A nanometre of slack, and it is arithmetic rather than leniency.
            // Near an edge the clamp aims at the edge exactly, and y + (edge -
            // y) is only within an ulp of the edge in IEEE754 - so a strict
            // comparison would fail on rounding a squid cannot feel. The band
            // is stated in whole blocks.
            assertTrue(after <= VoidSquidDrift.BAND_CEILING + 1.0E-9
                            && after >= VoidSquidDrift.BAND_FLOOR - 1.0E-9,
                    "one tick left the band, from " + y + " to " + after);
        }

        // The two edges by hand, since a random draw will not land on them.
        assertTrue(VoidSquidDrift.inBand(VoidSquidDrift.BAND_CEILING
                + VoidSquidDrift.rise(VoidSquidDrift.BAND_CEILING, 0)));
        assertTrue(VoidSquidDrift.inBand(VoidSquidDrift.BAND_FLOOR
                + VoidSquidDrift.rise(VoidSquidDrift.BAND_FLOOR, 0)));
    }

    @Test
    @DisplayName("above the ceiling it only ever sinks, below the floor it only ever rises")
    void outsideTheBandItOnlyComesBack() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < 20_000; i++) {
            long tick = random.nextInt(100_000);

            double above = VoidSquidDrift.BAND_CEILING + random.nextDouble() * 400;
            assertTrue(VoidSquidDrift.rise(above, tick) <= 0.0,
                    "a squid at " + above + " was told to climb");

            double below = VoidSquidDrift.BAND_FLOOR - random.nextDouble() * 400;
            assertTrue(VoidSquidDrift.rise(below, tick) >= 0.0,
                    "a squid at " + below + " was told to sink");
        }
    }

    @Test
    @DisplayName("a refusal only ever zeroes a component - never grows one, never flips one")
    void aRefusalOnlyZeroes() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < 20_000; i++) {
            VoidSquidDrift.Drift in = new VoidSquidDrift.Drift(
                    spread(random, 40), spread(random, 40), spread(random, 40));
            boolean openX = random.nextBoolean();
            boolean openY = random.nextBoolean();
            boolean openZ = random.nextBoolean();

            VoidSquidDrift.Drift out = VoidSquidDrift.refuse(in, openX, openY, openZ);

            assertEquals(openX ? in.x() : 0.0, out.x(), "x was not left alone or zeroed");
            assertEquals(openY ? in.y() : 0.0, out.y(), "y was not left alone or zeroed");
            assertEquals(openZ ? in.z() : 0.0, out.z(), "z was not left alone or zeroed");
        }

        // The clause the band leans on: a squid that has been told to sink
        // cannot be made to rise by anything standing in its way. It only ever
        // stops.
        VoidSquidDrift.Drift sinking = new VoidSquidDrift.Drift(0.03, -0.05, 0.03);
        assertTrue(VoidSquidDrift.refuse(sinking, false, false, false).y() <= 0.0);
        assertTrue(VoidSquidDrift.refuse(sinking, true, false, true).y() <= 0.0);
    }

    @Test
    @DisplayName("the same squid at the same tick answers the same, always")
    void theWanderIsDeterministic() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < SQUIDS; i++) {
            long mark = random.nextLong();
            long tick = random.nextInt(100_000);
            double y = spread(random, 200);

            VoidSquidDrift.Drift first = VoidSquidDrift.drift(mark, tick, y);
            VoidSquidDrift.Drift again = VoidSquidDrift.drift(mark, tick, y);
            assertEquals(first, again, "a squid answered twice on draw " + i);
        }
    }

    @Test
    @DisplayName("the heading comes all the way round and then repeats exactly")
    void theHeadingIsPeriodic() {
        Random random = new Random(DRAW_SEED);
        for (int i = 0; i < SQUIDS; i++) {
            long mark = random.nextLong();
            long tick = random.nextInt(100_000);
            assertEquals(VoidSquidDrift.heading(mark, tick),
                    VoidSquidDrift.heading(mark, tick + VoidSquidDrift.TURN_TICKS), 1.0E-12,
                    "the heading did not repeat after a full turn on draw " + i);
        }
    }

    @Test
    @DisplayName("two squids in one place do not shoal")
    void squidsWanderApart() {
        // A mark is a squid's own number rather than the world seed, so a
        // handful of them in one cell drift in every direction rather than
        // leaving together. Measured as coverage of the eight octants rather
        // than asserted about any one pair.
        Random random = new Random(DRAW_SEED);
        boolean[] octants = new boolean[8];
        for (int i = 0; i < SQUIDS; i++) {
            double angle = VoidSquidDrift.heading(random.nextLong(), 0);
            octants[(int) (angle / (2 * Math.PI) * 8) % 8] = true;
        }
        for (int i = 0; i < octants.length; i++) {
            assertTrue(octants[i], "no squid in " + SQUIDS + " ever set off toward octant " + i);
        }
    }

    @Test
    @DisplayName("a component picks the neighbour it points at")
    void theStepPicksOneNeighbour() {
        assertEquals(1, VoidSquidDrift.step(0.03));
        assertEquals(-1, VoidSquidDrift.step(-0.03));
        assertEquals(0, VoidSquidDrift.step(0.0));
        assertEquals(1, VoidSquidDrift.step(Double.MIN_VALUE));
        assertEquals(-1, VoidSquidDrift.step(-Double.MIN_VALUE));
    }
}
