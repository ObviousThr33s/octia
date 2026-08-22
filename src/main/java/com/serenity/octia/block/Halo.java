package com.serenity.octia.block;

/**
 * Where a mote of light sits, and how many of them a luminary is worth.
 *
 * <p>The sums behind the halo, kept free of Minecraft types so a JUnit test can
 * reach them - see {@code HaloTest}. What needs the game is one call to
 * {@code addParticle}, and that lives in {@link Luminaries}.
 *
 * <p><b>The shell is the whole idea.</b> Motes are drawn on a spherical shell
 * around the block and flown inward, so the light reads as gathering rather
 * than as leaking. How that is asked for is {@link Luminaries}' problem and it
 * is not obvious - an enchant mote is given a destination and an offset, not a
 * velocity, and the first version had it exactly backwards. That only works if no mote ever spawns inside the cube it is
 * falling toward: a particle drawn at the centre of a solid block is invisible,
 * and half a dozen invisible particles a tick is a performance cost with
 * nothing to show for it.
 *
 * <p>Hence {@link #INNER} at 0.9 rather than at 0.5. Half a block is the
 * distance to a <i>face</i>; the distance to a <i>corner</i> is 0.866, and a
 * shell any tighter than that puts every diagonal mote inside the andesite.
 * That is the one number here worth defending, and {@code HaloTest} pins it by
 * sweeping the sphere rather than by trusting the arithmetic.
 */
public final class Halo {

    /** Inside this and a diagonal mote would be inside the block. See the class note. */
    public static final double INNER = 0.9;

    /** Outside this and the halo stops reading as belonging to the block. */
    public static final double OUTER = 1.5;

    private Halo() {
    }

    /** One mote's offset from the centre of the block it belongs to. */
    public record Mote(double x, double y, double z) {

        /** How far out it sits. */
        public double radius() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    /**
     * How many motes a luminary is worth this tick.
     *
     * <p>Dark blocks are worth none, which is what makes this safe to call from
     * every frame panel in render distance: an unlit panel does no work. Above
     * that it is deliberately flat - one for a plain lamp, two for a dressed
     * one. A count that scaled properly with light would make a styled panel
     * twice the spectacle of a generic one, and a wall of them a fog.
     *
     * @param lightLevel the block's own light emission, 0-15
     */
    public static int motes(int lightLevel) {
        if (lightLevel <= 0) {
            return 0;
        }
        return 1 + lightLevel / 8;
    }

    /**
     * A point on the shell, from three rolls of a die between 0 and 1.
     *
     * <p>Takes its randomness as arguments rather than holding a generator,
     * which is what lets a test sweep the whole sphere instead of hoping the
     * seeds it tried were representative.
     *
     * @param around  turn about the vertical, 0-1
     * @param up      height on the sphere, 0-1; uniform in cosine so the poles
     *                do not crowd
     * @param out     how far out between {@link #INNER} and {@link #OUTER}, 0-1
     */
    public static Mote at(double around, double up, double out) {
        double theta = around * Math.PI * 2.0;
        double cosPhi = 2.0 * up - 1.0;
        double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
        double radius = INNER + out * (OUTER - INNER);
        return new Mote(
                Math.cos(theta) * sinPhi * radius,
                cosPhi * radius,
                Math.sin(theta) * sinPhi * radius);
    }
}
