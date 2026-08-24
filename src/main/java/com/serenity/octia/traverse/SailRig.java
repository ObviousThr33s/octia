package com.serenity.octia.traverse;

/**
 * The arithmetic of the sail-rig, kept away from anything that needs a world.
 *
 * <p>A glide is a push, a cap, a band, and a memory, and all four are sums.
 * Sums are the half of this that a JUnit test can reach in milliseconds - see
 * {@code SailRigTest}. The moment a method here wants an {@code ItemStack} or a
 * {@code Player} it belongs in {@link SailRigItem} instead, where a
 * {@code @GameTest} can drive it against a real entity. That is the split the
 * bindle uses, verbatim, and for the same reason.
 *
 * <p><b>Units.</b> Velocities are blocks per tick, the units
 * {@code Entity.getDeltaMovement} speaks. {@link #HARD_CAP} is written as
 * {@code 9.0 / 20.0} so the owner's number is the 9.0 - blocks per second - and
 * the 20 is ticks. Nine a second is boat pace, 0.56 chunks a second, against
 * the 33 metres a second of the 8/23 chunk-storm that stalled the autosave.
 *
 * <p><b>The cap owns top speed, not the drag.</b> Vanilla air drag multiplies
 * horizontal velocity by 0.91 each tick, so a push of {@link #GLIDE_PUSH}
 * settles unclamped at {@code 0.05 * 0.91 / 0.09 = 0.506} - deliberately above
 * the cap. That way the ceiling is this file's number, and a version bump that
 * moves the drag cannot move the top speed. Full sail from rest arrives in
 * about 23 ticks.
 *
 * <p><b>The band.</b> While deployed, vertical velocity is clamped into
 * {@code [-SINK_MAX, -SINK_MIN]}. The upper edge is the charter's no-net-climb
 * clamp - never positive, never even zero, which is what makes the sail refuse
 * updrafts. The lower edge is the sail's arrest: gravity drives the vertical
 * below the band between clamps, so a steady glide executes at exactly
 * {@link #SINK_MAX} down - 2.4 blocks a second of descent, a glide ratio of
 * {@code HARD_CAP / SINK_MAX = 3.75:1}.
 *
 * <p><b>The constants promise each other things.</b> The latch stays latched
 * only while {@code DEPLOY_FALL < SOFT_FALL}; a landing under sail is free only
 * while {@code SOFT_FALL + 2 * SINK_MAX} stays under vanilla's damage threshold
 * of 3.0; the save-safety law holds only while {@code HARD_CAP * 20 <= 9}.
 * {@code SailRigTest.theConstantsKeepTheirPromises} holds the whole lattice, so
 * an owner tune that breaks the mechanism fails the build instead of the world.
 */
public final class SailRig {

    /** Horizontal ceiling, blocks per tick. The 9.0 is blocks per second. */
    public static final double HARD_CAP  = 9.0 / 20.0;  // provisional - owner tunes by walking the world

    /** The gentlest the sail ever sinks. The band's upper edge, negated. */
    public static final double SINK_MIN  = 0.05;        // provisional - owner tunes by walking the world

    /** The sail's arrest, and the steady-state descent. The band's lower edge, negated. */
    public static final double SINK_MAX  = 0.12;        // provisional - owner tunes by walking the world

    /** Forward push along the look, per tick. See the class note on the drag. */
    public static final double GLIDE_PUSH = 0.05;       // provisional - owner tunes by walking the world

    /** How far a fall must have run before the sail opens. Strictly greater-than. */
    public static final float  DEPLOY_FALL = 2.0F;      // provisional - owner tunes by walking the world

    /**
     * The most fall the ground is ever told about after a deployed tick.
     *
     * <p>Above {@link #DEPLOY_FALL}, so capping the memory cannot un-latch an
     * open sail, and low enough that {@code SOFT_FALL + 2 * SINK_MAX = 2.74}
     * stays under vanilla's damage threshold of 3.0 - a landing under sail is
     * always free, and a glide bailed mid-air resumes its charge from here.
     */
    public static final float  SOFT_FALL   = 2.5F;      // derived - see the inequality lattice below

    /** Below this much horizontal look there is no direction to push in. */
    public static final double LOOK_EPSILON = 1.0E-4;

    /**
     * The optional steering assist's turn budget, radians per tick. Zero means
     * off, and it ships at zero - see {@link #steer} for what it would do and
     * {@link SailRigItem#sail} for why turning it on is not a one-liner.
     */
    public static final double STEER_ASSIST_RADIANS = 0.0;  // optional assist, shipped off - see steer

    private SailRig() {
    }

    /** A velocity, as a plain triple. Blocks per tick. */
    public record Motion(double x, double y, double z) {
    }

    /** Horizontal magnitude. Public because the tests measure with it. */
    public static double horizontal(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /**
     * Whether the sail is open this tick.
     *
     * <p>Derived, never stored. True only in a plain fall: not on the ground,
     * not in a fluid, not carried by any other traversal, and past
     * {@link #DEPLOY_FALL} of accumulated fall. Strictly greater-than - at
     * exactly {@code DEPLOY_FALL} the sail is still stowed, so an ordinary jump,
     * which peaks near 1.25, never opens it on flat ground.
     *
     * <p>The latch is free. While gliding, fallDistance keeps accumulating and
     * {@link #remembered} caps it each tick to {@link #SOFT_FALL}, which sits
     * above {@code DEPLOY_FALL} - so once open the sail stays open until
     * ground, water, or stowing closes it, with nothing written anywhere.
     */
    public static boolean deploys(boolean onGround, boolean inFluid, boolean otherwiseCarried,
            float fallDistance) {
        return !onGround && !inFluid && !otherwiseCarried && fallDistance > DEPLOY_FALL;
    }

    /**
     * One deployed tick's whole velocity transform.
     *
     * <p>In this order: push, cap, band. Push-then-cap, never cap-then-push -
     * the other order can exit a tick above the cap, and the cap is the law.
     * The push is {@link #GLIDE_PUSH} along the horizontal look; a look with
     * less than {@link #LOOK_EPSILON} of horizontal in it pushes nowhere, so
     * looking straight down glides nowhere. The cap rescales the horizontal
     * pair together, keeping the direction. The band then clamps the vertical.
     *
     * <p>Postconditions, for any input at all: {@code horizontal(out) <=
     * HARD_CAP} and {@code -SINK_MAX <= out.y <= -SINK_MIN}. That is why an
     * upward impulse mid-glide - wind charge, explosion, levitation - dies at
     * the next tick's clamp, and why a burst of stowed-dive momentum is
     * discarded rather than converted: dive-chaining and launch-conversion
     * exploits end here, deliberately.
     */
    public static Motion glide(Motion in, double lookX, double lookZ) {
        double x = in.x();
        double y = in.y();
        double z = in.z();

        double n = horizontal(lookX, lookZ);
        if (n > LOOK_EPSILON) {
            x += GLIDE_PUSH * lookX / n;
            z += GLIDE_PUSH * lookZ / n;
        }

        double h = horizontal(x, z);
        if (h > HARD_CAP) {
            x *= HARD_CAP / h;
            z *= HARD_CAP / h;
        }

        y = Math.min(y, -SINK_MIN);
        y = Math.max(y, -SINK_MAX);

        return new Motion(x, y, z);
    }

    /**
     * What the ground is told a deployed fall amounted to.
     *
     * <p>Never raises - a fall shorter than {@link #SOFT_FALL} is remembered as
     * itself. And because {@code SOFT_FALL > DEPLOY_FALL}, remembering cannot
     * un-latch the sail: the capped memory still clears the deploy threshold,
     * which is the whole latch.
     */
    public static float remembered(float fallDistance) {
        return Math.min(fallDistance, SOFT_FALL);
    }

    /**
     * The optional assist: turn the horizontal velocity toward a thread.
     *
     * <p>Rotates the horizontal component of {@code in} toward the nearer of
     * the two directions along the line {@code (alongX, alongZ)} - the axis is
     * flipped when the dot product is negative, because a thread runs both
     * ways - by at most {@code maxTurnRadians}. Horizontal magnitude is
     * preserved exactly and {@code y} is untouched, so this can violate
     * neither the cap nor the band regardless of where it is called, including
     * after {@link #glide}. That is what makes even a future enabling safe.
     *
     * <p>Zero horizontal velocity, a zero axis, or a non-positive budget hand
     * back {@code in} unchanged - there is either nothing to turn or nothing
     * to turn toward.
     */
    public static Motion steer(Motion in, double alongX, double alongZ, double maxTurnRadians) {
        if (maxTurnRadians <= 0) {
            return in;
        }
        if (alongX == 0 && alongZ == 0) {
            return in;
        }
        if (in.x() == 0 && in.z() == 0) {
            return in;
        }

        double ax = alongX;
        double az = alongZ;
        double dot = in.x() * ax + in.z() * az;
        if (dot < 0) {
            ax = -ax;
            az = -az;
            dot = -dot;
        }

        // The signed angle from the velocity to the axis. atan2 does not care
        // about either vector's length, which is why nothing is normalised.
        double cross = in.x() * az - in.z() * ax;
        double turn = Math.atan2(cross, dot);
        turn = Math.max(-maxTurnRadians, Math.min(maxTurnRadians, turn));

        double sin = Math.sin(turn);
        double cos = Math.cos(turn);
        return new Motion(in.x() * cos - in.z() * sin,
                in.y(),
                in.x() * sin + in.z() * cos);
    }
}
