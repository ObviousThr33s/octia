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
    }
}
