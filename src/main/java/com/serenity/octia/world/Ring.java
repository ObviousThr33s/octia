package com.serenity.octia.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ring a thread falls into, and how big it is.
 *
 * <p><b>Every thread ends in a ring. That is not a design choice, it is
 * arithmetic.</b> {@link Sightlines} gives each cell exactly one outgoing step,
 * so following the legs is iterating a function - and a function iterated on a
 * finite set of visited cells must eventually repeat one. The walk therefore has
 * the shape of a rho: a lead-in from wherever you started, and then a closed
 * circuit it never leaves. The circuit is the interesting half and nothing has
 * ever looked at it.
 *
 * <p>{@code Sightlines.step} already measured the length of the whole rho -
 * 11.15 legs before a thread returns on itself - but a rho's length is a lead
 * plus a ring and that number does not say which is which. This class separates
 * them.
 *
 * <p><b>Pure, like everything else in the lattice.</b> No Minecraft on the
 * imports. A ring is a function of (seed, cell) exactly as a leg is, so it can
 * be computed on a generation worker, on a client, or in a JUnit test in
 * microseconds - see {@code RingTest}.
 *
 * <p><b>What it is for.</b> A closed circuit of legs is a storage ring: a route
 * that goes nowhere and comes back, which is a useless road and a very good
 * machine. See docs/TRAJECTORY.traj XIV. Nothing in the mod reads this yet.
 */
public final class Ring {

    /**
     * How far to walk before giving up on a ring.
     *
     * <p>Generous by two orders of magnitude. The measured rho is eleven legs and
     * the longest ring found over a hundred thousand starts is far inside this,
     * so a walk that reaches the limit is a bug in the lattice rather than a big
     * ring - which is exactly what a limit should be for.
     */
    public static final int LIMIT = 4096;

    private Ring() {
    }

    /**
     * A closed circuit of legs, and the walk that reached it.
     *
     * @param cells         the cells of the ring in order, each as {@code {x, z}};
     *                      the first is where the walk first entered it
     * @param lead          how many legs were walked before the ring was entered
     * @param circumference the distance around it in blocks, node to node
     */
    public record Circuit(List<int[]> cells, int lead, double circumference) {

        /** How many legs go around. Always even - see {@code RingTest}. */
        public int legs() {
            return cells.size();
        }

        /** Whether the walk started on the ring rather than off it. */
        public boolean startedOnIt() {
            return lead == 0;
        }
    }

    /**
     * Walks the thread out of a cell until it closes, and reports the circuit.
     *
     * @return the circuit, or null if {@link #LIMIT} legs were walked without one
     */
    public static Circuit from(long seed, int cellX, int cellZ) {
        Map<Long, Integer> seen = new HashMap<>();
        List<int[]> walk = new ArrayList<>();

        int x = cellX;
        int z = cellZ;
        for (int step = 0; step < LIMIT; step++) {
            Long key = pack(x, z);
            Integer before = seen.get(key);
            if (before != null) {
                return circuit(seed, walk.subList(before, walk.size()), before);
            }
            seen.put(key, step);
            walk.add(new int[] {x, z});

            Sightlines.Heading heading = Sightlines.leg(seed, x, z).step();
            x += heading.dx();
            z += heading.dz();
        }
        return null;
    }

    /** The ring as a circuit, with its circumference measured node to node. */
    private static Circuit circuit(long seed, List<int[]> ring, int lead) {
        List<int[]> cells = List.copyOf(ring);
        double around = 0;
        for (int i = 0; i < cells.size(); i++) {
            int[] here = cells.get(i);
            int[] next = cells.get((i + 1) % cells.size());
            Sightlines.Node a = Sightlines.node(seed, here[0], here[1]);
            Sightlines.Node b = Sightlines.node(seed, next[0], next[1]);
            around += Math.hypot(b.x() - a.x(), b.z() - a.z());
        }
        return new Circuit(cells, lead, around);
    }

    /** Two ints in one long, so a cell can be a map key without allocating one. */
    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
