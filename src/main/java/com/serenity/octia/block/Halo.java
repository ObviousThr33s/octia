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
     * What the light is doing at this hour.
     *
     * <p>Asked for as "psychedelic tracer mornings then calm hazy nights". The
     * answer is not a rooster: a chicken is a <b>neolithic</b> animal, and
     * TRAJECTORY.traj XII rules the neolithic out - the same trap the enchanting
     * table sets. And VoidSquid says there are no {@code SoundEvent}s in this
     * mod, so a dawn that announced itself would have to borrow a vanilla sound
     * to say a thing this world has no word for.
     *
     * <p>So the day turns in <b>light</b>, which is the one thing this mod
     * already speaks in - {@code PanelLight}, {@code Luminaries},
     * {@code FirstLight}. Nothing is added to the world; what is already lit
     * behaves differently depending on the hour.
     */
    public enum Hour {

        /** Just after sunrise. Twice the motes, thrown wide - they read as tracers. */
        MORNING,

        /** Ordinary daylight. What shipped in 0.2.0-alpha.1, unchanged. */
        DAY,

        /** Held close to the block, so the halo reads as a haze rather than a spray. */
        NIGHT
    }

    /** A Minecraft day, in ticks. */
    public static final long DAY_LENGTH = 24000L;

    /** Sunrise is 0. The tracer window closes here. */
    public static final long MORNING_ENDS = 2000L;

    /** Dusk. Between here and the next sunrise the halo settles. */
    public static final long NIGHT_BEGINS = 13000L;

    /** Sunrise proper, and the end of the night window. */
    public static final long NIGHT_ENDS = 23000L;

    /**
     * Which hour a level's day time falls in.
     *
     * <p>Takes the raw {@code getDayTime()} and wraps it itself, because that
     * counter runs for the life of a world and is not bounded to one day. Negative
     * is handled too - {@code /time set} accepts anything and a world that has had
     * its clock wound backwards should still have a morning.
     */
    public static Hour hour(long dayTime) {
        long t = Math.floorMod(dayTime, DAY_LENGTH);
        // Morning WRAPS, and the first draft did not. Sunrise is at 23000, not
        // at 0, so a window of 0..2000 left 23000..23999 falling through to DAY -
        // an hour of ordinary daylight sitting in the dark, right where the sun
        // actually comes up. The three windows now tile the whole day with no
        // gap, which is what theThreeHoursLandWhereTheSkyDoes exists to hold.
        if (t >= NIGHT_ENDS || t < MORNING_ENDS) {
            return Hour.MORNING;
        }
        if (t >= NIGHT_BEGINS) {
            return Hour.NIGHT;
        }
        return Hour.DAY;
    }

    /**
     * How many motes a luminary is worth at this hour.
     *
     * <p>Morning doubles the count and nothing else changes it. Night is
     * deliberately <b>not</b> reduced: the halo is most visible in the dark, and
     * thinning it there would take the effect away exactly when it can be seen.
     * Night is made calm by pulling the motes in, not by having fewer of them.
     */
    public static int motes(int lightLevel, Hour hour) {
        int base = motes(lightLevel);
        return hour == Hour.MORNING ? base * 2 : base;
    }

    /**
     * The {@code out} roll, bent toward the shell this hour wants.
     *
     * <p>An enchant mote is given a destination and falls to it, so how far out
     * it starts is how far it travels, which is how long its streak is. Morning
     * pushes the roll toward {@link #OUTER} - long falls, and they read as
     * tracers. Night pulls it toward {@link #INNER} - short falls that sit on the
     * block as a haze.
     *
     * <p>Both curves are their own inverse in the sense that matters here: they
     * map 0 to 0 and 1 to 1, so no mote ever leaves the shell that
     * {@link #at} already guarantees. The hour changes where motes crowd, never
     * where they are allowed to be.
     */
    public static double bend(Hour hour, double out) {
        return switch (hour) {
            case MORNING -> 1.0 - (1.0 - out) * (1.0 - out);
            case NIGHT -> out * out;
            case DAY -> out;
        };
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
