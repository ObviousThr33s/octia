package com.serenity.octia.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.serenity.octia.block.Halo.Hour;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The day turning, in light.
 *
 * <p>Asked for as "psychedelic tracer mornings then calm hazy nights". There is
 * no rooster in it and there cannot be: a chicken is neolithic and
 * TRAJECTORY.traj XII rules the neolithic out, and VoidSquid records that this
 * mod owns no {@code SoundEvent}s at all. So the hour is carried by the halo,
 * and the halo's arithmetic is the only part of a particle effect a headless
 * gate can hold to anything - which is the same reason {@link Halo} exists.
 */
@DisplayName("the day turns in light, not in a bird")
class HourTest {

    @Test
    @DisplayName("the three hours land where the sky does")
    void hoursMatchTheSky() {
        assertEquals(Hour.MORNING, Halo.hour(0), "sunrise");
        assertEquals(Hour.MORNING, Halo.hour(1999));
        assertEquals(Hour.DAY, Halo.hour(Halo.MORNING_ENDS), "the tracer window is half-open at the top");
        assertEquals(Hour.DAY, Halo.hour(6000), "noon");
        assertEquals(Hour.DAY, Halo.hour(12999));
        assertEquals(Hour.NIGHT, Halo.hour(Halo.NIGHT_BEGINS), "dusk, and night is half-open at the bottom");
        assertEquals(Hour.NIGHT, Halo.hour(18000), "midnight");
        assertEquals(Hour.NIGHT, Halo.hour(22999));
        assertEquals(Hour.MORNING, Halo.hour(Halo.NIGHT_ENDS), "sunrise proper, straight back into morning");
    }

    @Test
    @DisplayName("the clock runs for the life of a world, so it is wrapped here")
    void theClockIsWrapped() {
        // getDayTime() is not bounded to one day - it counts up forever - and a
        // world four hundred days old must still have a morning.
        for (long day = 0; day < 400; day++) {
            long base = day * Halo.DAY_LENGTH;
            assertEquals(Hour.MORNING, Halo.hour(base), "day " + day + " lost its morning");
            assertEquals(Hour.NIGHT, Halo.hour(base + 18000), "day " + day + " lost its night");
        }
    }

    @Test
    @DisplayName("a clock wound backwards still has a morning")
    void negativeTimeStillTurns() {
        // /time set accepts anything, and floorMod is why this holds where a
        // plain % would hand back a negative and fall through to DAY forever.
        assertEquals(Hour.MORNING, Halo.hour(-Halo.DAY_LENGTH));
        assertEquals(Hour.NIGHT, Halo.hour(-Halo.DAY_LENGTH + 18000));
        assertEquals(Hour.NIGHT, Halo.hour(-2000), "two thousand ticks before 0 is 22000, still night");
        // -1000 wraps to 23000, which is sunrise itself and therefore morning.
        // This is the boundary the first draft dropped on the floor.
        assertEquals(Hour.MORNING, Halo.hour(-1000));
    }

    @Test
    @DisplayName("morning doubles the motes and no other hour touches the count")
    void morningDoubles() {
        for (int light = 1; light <= 15; light++) {
            int base = Halo.motes(light);
            assertEquals(base * 2, Halo.motes(light, Hour.MORNING));
            assertEquals(base, Halo.motes(light, Hour.DAY));
            assertEquals(base, Halo.motes(light, Hour.NIGHT),
                    "night is made calm by pulling motes in, not by having fewer");
        }
    }

    @Test
    @DisplayName("a dark block stays dark at every hour")
    void darkIsDarkAllDay() {
        for (Hour hour : Hour.values()) {
            assertEquals(0, Halo.motes(0, hour));
            assertEquals(0, Halo.motes(-1, hour));
        }
    }

    @Test
    @DisplayName("bending the roll never lets a mote off the shell")
    void bendStaysOnTheShell() {
        // The hour decides where motes crowd, never where they may be. If this
        // fails, a morning mote is inside the andesite or outside the halo.
        for (Hour hour : Hour.values()) {
            for (int i = 0; i <= 1000; i++) {
                double out = i / 1000.0;
                double bent = Halo.bend(hour, out);
                assertTrue(bent >= 0.0 && bent <= 1.0,
                        hour + " bent " + out + " to " + bent + ", which is off the shell");

                Halo.Mote mote = Halo.at(0.3, 0.7, bent);
                double r = mote.radius();
                assertTrue(r >= Halo.INNER - 1e-9 && r <= Halo.OUTER + 1e-9,
                        hour + " put a mote at radius " + r);
            }
        }
    }

    @Test
    @DisplayName("both ends are fixed, so no hour can lose the whole shell")
    void theEndsAreFixed() {
        for (Hour hour : Hour.values()) {
            assertEquals(0.0, Halo.bend(hour, 0.0), 1e-12);
            assertEquals(1.0, Halo.bend(hour, 1.0), 1e-12);
        }
    }

    @Test
    @DisplayName("morning throws wide and night holds close, on the same roll")
    void theHoursPullOppositeWays() {
        // The claim the effect actually rests on. An enchant mote is given a
        // destination and falls to it, so how far out it starts is how long its
        // streak is: far is a tracer, near is a haze.
        for (int i = 1; i < 1000; i++) {
            double out = i / 1000.0;
            double morning = Halo.bend(Hour.MORNING, out);
            double day = Halo.bend(Hour.DAY, out);
            double night = Halo.bend(Hour.NIGHT, out);

            assertTrue(morning > day, "morning did not throw wider than day at " + out);
            assertTrue(night < day, "night did not hold closer than day at " + out);
        }
    }

    @Test
    @DisplayName("on average, a morning mote really does start further out than a night one")
    void theAveragesSeparate() {
        // Swept rather than sampled, in HaloTest's register: a thousand even
        // rolls, not a handful of seeds that might have been kind.
        double morning = 0;
        double night = 0;
        int n = 1000;
        for (int i = 0; i < n; i++) {
            double out = (i + 0.5) / n;
            morning += Halo.at(0.1, 0.5, Halo.bend(Hour.MORNING, out)).radius();
            night += Halo.at(0.1, 0.5, Halo.bend(Hour.NIGHT, out)).radius();
        }
        morning /= n;
        night /= n;

        // The gap is analytic, not a guess. Mean of 1-(1-u)^2 over [0,1] is 2/3
        // and of u^2 is 1/3, so the two hours sit a third of the shell's depth
        // apart: (OUTER - INNER) / 3 = 0.2. Asserted as the number rather than as
        // "big enough", so that widening the shell has to come past this test.
        assertEquals((Halo.OUTER - Halo.INNER) / 3.0, morning - night, 0.01,
                "morning " + morning + " against night " + night);
        assertTrue(night < (Halo.INNER + Halo.OUTER) / 2.0, "night is not holding close");
        assertTrue(morning > (Halo.INNER + Halo.OUTER) / 2.0, "morning is not throwing wide");
    }
}
