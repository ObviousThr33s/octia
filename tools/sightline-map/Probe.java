/**
 * Prints the same four cells tools/sightline-map.py --probe prints, so the
 * Python transposition of the lattice can be diffed against the authority.
 *
 * <p>It compiles against Sightlines alone - that class has no Minecraft on its
 * imports, which is what makes this possible without the whole toolchain:
 *
 * <pre>
 * javac -d /tmp/sl src/main/java/com/serenity/octia/world/Sightlines.java \
 *                  tools/sightline-map/Probe.java
 * diff &lt;(python tools/sightline-map.py --probe) &lt;(java -cp /tmp/sl Probe)
 * </pre>
 *
 * <p>Not a test and not shipped in the jar - it lives beside the map it guards.
 * The real gate on the lattice is SightlinesTest; this only pins that the
 * picture is drawn from the same numbers the world is.
 *
 * <p><b>Four cells were not enough.</b> Three white cells in four keep their raw
 * draw, so the re-pick rule that Sightlines.step applies to the other quarter
 * hid behind these four cells while the Python drew a different lattice for
 * 12.7% of cells. The sweep below is over 41x41 and would have caught it on the
 * first run; the four cells are kept because a digest that disagrees says
 * nothing about where.
 */
public final class Probe {

    /** The amplified dev save, so the output matches the committed map. */
    private static final long SEED = 95512464L;

    private static final int[][] CELLS = {{0, 0}, {-6, -6}, {3, -2}, {-1, 4}};

    private Probe() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : SEED;
        for (int[] cell : CELLS) {
            com.serenity.octia.world.Sightlines.Node node =
                    com.serenity.octia.world.Sightlines.node(seed, cell[0], cell[1]);
            com.serenity.octia.world.Sightlines.Leg leg =
                    com.serenity.octia.world.Sightlines.leg(seed, cell[0], cell[1]);
            System.out.println(cell[0] + "," + cell[1]
                    + " node=" + node.x() + "," + node.z()
                    + " step=" + leg.heading()
                    + " to=" + leg.to().x() + "," + leg.to().z());
        }
        sweep(seed);
    }

    /** The digest the Python prints, over the same window and in the same order. */
    private static void sweep(long seed) {
        long digest = 0;
        int[] counts = new int[com.serenity.octia.world.Sightlines.Heading.values().length];
        int stations = 0;
        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                com.serenity.octia.world.Sightlines.Heading heading =
                        com.serenity.octia.world.Sightlines.leg(seed, cellX, cellZ).heading();
                counts[heading.ordinal()]++;
                digest = (digest * 31 + heading.ordinal()) % (1L << 32);
                stations += com.serenity.octia.world.Beamline.thrownBy(seed, cellX, cellZ).size();
            }
        }
        StringBuilder tally = new StringBuilder();
        for (String name : new String[] {"EAST", "NORTH", "SOUTH", "WEST"}) {
            com.serenity.octia.world.Sightlines.Heading heading =
                    com.serenity.octia.world.Sightlines.Heading.valueOf(name);
            tally.append(name).append('=').append(counts[heading.ordinal()]).append(' ');
        }
        System.out.println("sweep 41x41 digest=" + digest + " " + tally.toString().trim()
                + " stations=" + stations);
    }
}
