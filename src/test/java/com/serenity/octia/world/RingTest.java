package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every thread ends in a ring, and the ring is a property of the lattice rather
 * than of where you started walking.
 *
 * <p>These are the claims docs/TRAJECTORY.traj XIV rests on. They are arithmetic
 * rather than opinion - a function iterated on a finite visited set must repeat -
 * but the arithmetic is only as true as the lattice's one-step-per-cell rule, and
 * that rule lives in another file that somebody may one day change. If a future
 * {@code Sightlines} ever gives a cell two exits, or none, this is the test that
 * says so.
 */
class RingTest {

    private static final int SEEDS = 8;
    private static final int SPAN = 20;

    @Test
    @DisplayName("every walk closes, and well inside the limit")
    void everyWalkCloses() {
        int longest = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    Ring.Circuit circuit = Ring.from(seed, cx, cz);
                    assertNotNull(circuit, "no ring from " + cx + "," + cz + " on seed " + seed);
                    longest = Math.max(longest, circuit.legs() + circuit.lead());
                }
            }
        }
        assertTrue(longest < Ring.LIMIT / 8,
                "the longest rho was " + longest + " legs, which is close enough to the limit "
                        + "that the limit may be hiding something rather than catching it");
    }

    /**
     * The lattice is a checkerboard and every leg crosses it, so a circuit has to
     * come back on an even number of legs. An odd ring would mean a leg that did
     * not change parity, which is a diagonal, which the lattice does not have.
     */
    @Test
    @DisplayName("a ring is an even number of legs, and no cell twice")
    void ringsAreEvenAndSimple() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx += 3) {
                for (int cz = -SPAN; cz <= SPAN; cz += 3) {
                    Ring.Circuit circuit = Ring.from(seed, cx, cz);
                    assertEquals(0, circuit.legs() % 2,
                            "an odd ring of " + circuit.legs() + " legs means a diagonal step");
                    assertEquals(circuit.legs(), cells(circuit).size(),
                            "a cell appeared twice in one ring");
                }
            }
        }
    }

    /**
     * The ring belongs to the lattice, not to the walk. Start anywhere on one and
     * you get the same set of cells and no lead-in - which is what makes it
     * addressable at all: a ring can be named by any cell on it.
     */
    @Test
    @DisplayName("starting on a ring gives back that same ring, with no lead")
    void ringsAreStable() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            Ring.Circuit found = Ring.from(seed, 0, 0);
            Set<String> expected = cells(found);

            for (int[] cell : found.cells()) {
                Ring.Circuit again = Ring.from(seed, cell[0], cell[1]);
                assertTrue(again.startedOnIt(),
                        "starting on the ring still reported a lead of " + again.lead());
                assertEquals(expected, cells(again), "the same ring came back with different cells");
                assertEquals(found.legs(), again.legs());
            }
        }
    }

    /**
     * Circumference is measured node to node, and nodes wander up to the lattice
     * jitter inside their cells, so a leg is somewhere between spacing minus two
     * jitters and spacing plus two. Anything outside that means the measurement is
     * reading a different lattice than the one being walked.
     */
    @Test
    @DisplayName("circumference is the legs times a plausible leg")
    void circumferenceIsPlausible() {
        double shortest = Sightlines.SPACING - 2.0 * Sightlines.JITTER;
        double longest = Sightlines.SPACING + 2.0 * Sightlines.JITTER;

        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx += 5) {
                for (int cz = -SPAN; cz <= SPAN; cz += 5) {
                    Ring.Circuit circuit = Ring.from(seed, cx, cz);
                    double perLeg = circuit.circumference() / circuit.legs();
                    assertTrue(perLeg >= shortest && perLeg <= longest,
                            "a leg of " + perLeg + " blocks is not a leg of this lattice");
                }
            }
        }
    }

    private static Set<String> cells(Ring.Circuit circuit) {
        Set<String> out = new HashSet<>();
        for (int[] cell : circuit.cells()) {
            out.add(cell[0] + "," + cell[1]);
        }
        return out;
    }
}
