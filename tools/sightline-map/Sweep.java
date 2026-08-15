import com.serenity.octia.world.Sightlines;

import java.util.Random;

/**
 * Measures the lattice at scale, because the properties it is relied on for
 * were only ever spot-checked.
 *
 * <p><b>Why this exists.</b> {@code SightlinesTest} walked a few thousand cells
 * of one seed. That is enough to catch a lattice that is broken and nowhere near
 * enough to characterise one that works: a worst-case bend, a heading that
 * quietly favours one cardinal, a corridor that turns out to cover almost
 * nothing. Sightlines is a pure function, so there is no reason to guess at any
 * of it - sample it until the numbers stop moving.
 *
 * <p><b>The one that matters is the last.</b> Everything above the corridor
 * section is a correctness check with a pass or a fail. The corridor split is a
 * design number: {@link ObeliskFeature} breaks an obelisk 1 in 8 inside the
 * corridor and 4 in 8 outside it, so the share of the world that is actually
 * inside decides what fraction of obelisks stand. That share was never measured
 * before this, and the design was being described as though it were about half.
 *
 * <p>It samples exactly what the feature computes - distance to <em>its own
 * cell's</em> leg, not to the nearest leg anywhere. A position can sit right on
 * a neighbouring cell's line and still be "far", because the feature never looks
 * at that leg. Measuring the nearest leg instead would flatter the design with a
 * number nothing in the game uses.
 *
 * <pre>
 * javac -d /tmp/sl src/main/java/com/serenity/octia/world/Sightlines.java \
 *                  tools/sightline-map/Sweep.java
 * java -cp /tmp/sl Sweep [seeds] [reach] [samplesPerCell]
 * </pre>
 *
 * <p>No Gradle and no Minecraft: {@code Sightlines} has neither on its imports.
 * Not a test - {@code SightlinesTest} is the gate, and it asserts the bounds
 * this tool is used to choose.
 */
public final class Sweep {

    private Sweep() {
    }

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int reach = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        int samples = args.length > 2 ? Integer.parseInt(args[2]) : 8;

        long cells = 0;
        long[] headings = new long[4];
        long escaped = 0;
        long twoCycles = 0;
        double worstBend = 0;
        long bendOver30 = 0;
        long minLength = Long.MAX_VALUE;
        long maxLength = 0;
        double sumLength = 0;

        long inside = 0;
        long points = 0;
        // Distance to the line, in 8-block buckets out to 128, then a tail.
        long[] hist = new long[18];

        Random dice = new Random(20260815L);

        for (int s = 1; s <= seeds; s++) {
            long seed = mixSeed(s);
            for (int cx = -reach; cx <= reach; cx++) {
                for (int cz = -reach; cz <= reach; cz++) {
                    Sightlines.Leg leg = Sightlines.leg(seed, cx, cz);
                    cells++;
                    headings[leg.heading().ordinal()]++;

                    Sightlines.Node from = leg.from();
                    if (Sightlines.cell(from.x()) != cx || Sightlines.cell(from.z()) != cz) {
                        escaped++;
                    }

                    // A -> B -> A. Legal and even pleasant - two arches facing
                    // each other across half a kilometre - but worth counting,
                    // because a lattice that is mostly two-cycles is a field of
                    // pairs rather than a network of routes.
                    Sightlines.Leg back = Sightlines.leg(seed,
                            cx + leg.heading().dx(), cz + leg.heading().dz());
                    if (back.to().equals(from)) {
                        twoCycles++;
                    }

                    long dx = leg.to().x() - from.x();
                    long dz = leg.to().z() - from.z();
                    long along = Math.abs(horizontal(leg) ? dx : dz);
                    long across = Math.abs(horizontal(leg) ? dz : dx);
                    double bend = Math.toDegrees(Math.atan2(across, along));
                    worstBend = Math.max(worstBend, bend);
                    if (bend > 30) {
                        bendOver30++;
                    }

                    long length = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                    minLength = Math.min(minLength, length);
                    maxLength = Math.max(maxLength, length);
                    sumLength += length;

                    for (int i = 0; i < samples; i++) {
                        int x = cx * Sightlines.SPACING + dice.nextInt(Sightlines.SPACING);
                        int z = cz * Sightlines.SPACING + dice.nextInt(Sightlines.SPACING);
                        double d = leg.distanceToLine(x, z);
                        points++;
                        if (d <= Sightlines.CORRIDOR) {
                            inside++;
                        }
                        hist[Math.min(hist.length - 1, (int) (d / 8))]++;
                    }
                }
            }
        }

        System.out.printf("seeds=%d  cells=%,d  points=%,d%n", seeds, cells, points);
        System.out.println();

        System.out.println("-- correctness ---------------------------------------");
        System.out.printf("nodes outside their own cell   %d   (must be 0)%n", escaped);
        System.out.printf("worst bend off the cardinal    %.3f deg   (must be < 45)%n", worstBend);
        System.out.printf("legs bending past 30 deg       %,d  (%.4f%%)%n",
                bendOver30, 100.0 * bendOver30 / cells);
        System.out.println();

        System.out.println("-- shape ---------------------------------------------");
        String[] names = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            System.out.printf("%-6s %,12d  %6.3f%%%n", names[i], headings[i], 100.0 * headings[i] / cells);
        }
        System.out.printf("two-cycles (A->B->A)           %,d  (%.3f%%, chance says 25%%)%n",
                twoCycles, 100.0 * twoCycles / cells);
        System.out.printf("leg length  min %d  mean %.1f  max %d%n",
                minLength, sumLength / cells, maxLength);
        System.out.println();

        System.out.println("-- the corridor, which is a design number ------------");
        double near = (double) inside / points;
        System.out.printf("inside the %d-block corridor    %.4f%%   of the world%n",
                Sightlines.CORRIDOR, 100.0 * near);
        System.out.printf("expected obelisks broken       %.2f%%   (1 in 8 near, 4 in 8 far)%n",
                100.0 * (near * 1 / 8.0 + (1 - near) * 4 / 8.0));
        System.out.println();
        System.out.println("distance to own leg, share of world:");
        for (int i = 0; i < hist.length; i++) {
            String label = i == hist.length - 1 ? String.format("%4d+   ", i * 8)
                    : String.format("%4d-%-3d", i * 8, i * 8 + 7);
            System.out.printf("  %s %6.3f%%  %s%n", label, 100.0 * hist[i] / points,
                    bar(100.0 * hist[i] / points));
        }
    }

    private static boolean horizontal(Sightlines.Leg leg) {
        return leg.heading() == Sightlines.Heading.EAST || leg.heading() == Sightlines.Heading.WEST;
    }

    /** Spreads the run index so consecutive runs are not consecutive seeds. */
    private static long mixSeed(int index) {
        long v = index * 0x9E3779B97F4A7C15L;
        v ^= v >>> 30;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 27;
        return v;
    }

    private static String bar(double percent) {
        int n = (int) Math.round(percent);
        return "#".repeat(Math.max(0, Math.min(60, n)));
    }
}
