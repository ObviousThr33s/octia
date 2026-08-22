package com.serenity.octia.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The halo's arithmetic, swept rather than sampled.
 *
 * <p>Particles cannot be tested by the gates - {@code animateTick} runs on a
 * client and both gates are headless - so the only part of the halo that can be
 * held to anything is where the motes are and how many. That is exactly why the
 * sums were split out of {@link Luminaries} into {@link Halo}.
 */
class HaloTest {

    @Test
    @DisplayName("a dark block is worth no motes at all")
    void darkCostsNothing() {
        assertEquals(0, Halo.motes(0));
        assertEquals(0, Halo.motes(-1));
    }

    /**
     * Flat on purpose. A styled panel is worth one more mote than a generic
     * one, not twice the spectacle - see the note on {@code Halo.motes}.
     */
    @Test
    @DisplayName("a lit block is worth one mote, a bright one two")
    void lightIsWorthLittle() {
        assertEquals(1, Halo.motes(1));
        assertEquals(1, Halo.motes(7));
        assertEquals(2, Halo.motes(8));
        assertEquals(2, Halo.motes(15));
    }

    /**
     * The claim the shell rests on: no mote is ever drawn inside the cube it
     * belongs to, in any direction. Swept over the whole sphere because the
     * failure is directional - a shell at 0.75 is outside the faces and inside
     * every corner, which sampling a few seeds would very likely miss.
     */
    @Test
    @DisplayName("no mote on the shell lands inside the block")
    void nothingInsideTheBlock() {
        for (int a = 0; a <= 40; a++) {
            for (int u = 0; u <= 40; u++) {
                for (int o = 0; o <= 4; o++) {
                    Halo.Mote mote = Halo.at(a / 40.0, u / 40.0, o / 4.0);
                    double furthest = Math.max(Math.abs(mote.x()),
                            Math.max(Math.abs(mote.y()), Math.abs(mote.z())));
                    assertTrue(furthest > 0.5,
                            "a mote at " + mote + " is inside the block it falls toward");
                }
            }
        }
    }

    @Test
    @DisplayName("every mote sits between the inner and outer shells")
    void onTheShell() {
        for (int a = 0; a <= 20; a++) {
            for (int u = 0; u <= 20; u++) {
                for (int o = 0; o <= 10; o++) {
                    double radius = Halo.at(a / 20.0, u / 20.0, o / 10.0).radius();
                    assertTrue(radius >= Halo.INNER - 1e-9 && radius <= Halo.OUTER + 1e-9,
                            "a mote sat at " + radius);
                }
            }
        }
    }

    /**
     * The inner shell clears a corner, which is the number the whole design
     * hangs on and the one somebody will later try to tighten.
     */
    @Test
    @DisplayName("the inner shell clears the corner of the cube, not just the face")
    void innerClearsTheCorner() {
        assertTrue(Halo.INNER > Math.sqrt(3) / 2.0,
                "an inner shell at " + Halo.INNER + " puts diagonal motes inside the andesite");
        assertTrue(Halo.OUTER > Halo.INNER);
    }
}
