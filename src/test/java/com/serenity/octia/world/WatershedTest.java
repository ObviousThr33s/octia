package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The watershed's soul is pure arithmetic over a seed and a cell, with no
 * Minecraft on its imports, so it is tested here rather than as a GameTest -
 * the {@code SightlinesTest} reasoning. The properties below are the ones
 * {@code WatershedFeature} relies on while running on several
 * chunk-generation worker threads at once, and the one the charter names
 * first: same seed, same answer, every time.
 */
class WatershedTest {

    private static final int SEEDS = 6;
    private static final int SPAN = 12;

    /** The charter's "pure determinism" deliverable, measured rather than trusted. */
    @Test
    @DisplayName("the same seed and cell always answer the same watershed")
    void sameSeedSameAnswer() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    assertEquals(Watershed.springAt(seed, cx, cz),
                            Watershed.springAt(seed, cx, cz),
                            "springAt flickered at " + cx + "," + cz);
                    assertEquals(Watershed.fallLegs(seed, cx, cz),
                            Watershed.fallLegs(seed, cx, cz),
                            "fallLegs flickered at " + cx + "," + cz);
                }
            }
        }
    }

    /**
     * Both directions, so the gate cannot be replaced by a coin that happens
     * to agree half the time. The head is exposed for exactly this - a test
     * that only compares two gated answers cannot tell a correct gate from a
     * consistently wrong one, the {@code Mystery.bearingToNode} precedent.
     */
    @Test
    @DisplayName("a spring is the uphill end of its own leg, and nothing else is")
    void aSpringIsTheUphillEndOfItsOwnLeg() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    Sightlines.Leg leg = Sightlines.leg(seed, cx, cz);
                    boolean uphill = Watershed.head(seed, cx, cz)
                            > Watershed.head(seed,
                                    cx + leg.heading().dx(), cz + leg.heading().dz());
                    assertEquals(uphill, Watershed.springAt(seed, cx, cz),
                            "the gate disagreed with the heads at " + cx + "," + cz);
                }
            }
        }
    }

    /**
     * The fall re-walked by hand: the head strictly decreases at every hop,
     * the count is 1 to {@code MAX_FALL_LEGS} for a spring and 0 for anything
     * else, and it is the count {@code fallLegs} answers. Strict decrease is
     * what makes a cycle impossible, so the cap is a design bound and not
     * termination insurance - this test is where that claim is walked.
     */
    @Test
    @DisplayName("the fall never climbs and is bounded")
    void theFallNeverClimbsAndIsBounded() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    int answered = Watershed.fallLegs(seed, cx, cz);
                    if (!Watershed.springAt(seed, cx, cz)) {
                        assertEquals(0, answered,
                                "a non-spring fell at " + cx + "," + cz);
                        continue;
                    }

                    int walked = 0;
                    int wx = cx;
                    int wz = cz;
                    long at = Watershed.head(seed, wx, wz);
                    while (walked < Watershed.MAX_FALL_LEGS) {
                        Sightlines.Leg leg = Sightlines.leg(seed, wx, wz);
                        int tx = wx + leg.heading().dx();
                        int tz = wz + leg.heading().dz();
                        long below = Watershed.head(seed, tx, tz);
                        if (below >= at) {
                            break;  // the hand walk only ever moves strictly downhill
                        }
                        walked++;
                        wx = tx;
                        wz = tz;
                        at = below;
                    }

                    assertTrue(walked >= 1 && walked <= Watershed.MAX_FALL_LEGS,
                            "a spring fell " + walked + " legs at " + cx + "," + cz);
                    assertEquals(walked, answered,
                            "fallLegs disagreed with the walk at " + cx + "," + cz);
                }
            }
        }
    }

    /**
     * Not taste but a salt-degeneracy tripwire: a {@code SPRING_SALT}
     * colliding with another consumer of the seed would push the share off
     * one half, and this is where it would show first - the
     * measured-property idiom of {@code SightlinesTest} and
     * {@code BeamlineTest}.
     */
    @Test
    @DisplayName("about half the cells water")
    void aboutHalfTheCellsWater() {
        int springs = 0;
        int total = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    total++;
                    if (Watershed.springAt(seed, cx, cz)) {
                        springs++;
                    }
                }
            }
        }
        double share = (double) springs / total;
        assertTrue(share > 0.45 && share < 0.55,
                "springs open on " + share + " of cells; the salt has gone degenerate");
    }
}
