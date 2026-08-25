package com.serenity.octia.entity;

/**
 * Where a void squid may be, and how far it moves in a tick.
 *
 * <p><b>Pure Java, for the reason {@code Sightlines}, {@code Watershed} and
 * {@code SailRig} are.</b> No Minecraft on the imports and none wanted. A band,
 * a drift and a refusal are sums, and sums are the half of a creature a JUnit
 * test can reach in milliseconds - {@code VoidSquidDriftTest} is that test. The
 * moment a method here wants a {@code Level} or an {@code Entity} it belongs in
 * {@link VoidSquid}, where a {@code @GameTest} can drive it against a real
 * world. That is the split the bindle and the sail-rig both use, verbatim, and
 * for the same reason: an invariant asserted without a world cannot be broken by
 * a world.
 *
 * <p><b>The band is stated in absolute world Y, and it has to be.</b>
 * {@code noise_settings/sky.json} runs its noise band from {@code min_y} 0 to
 * height 256, and the Overworld dimension floor is -64. So on this world
 * everything between -64 and 0 is open void with the continent's underside over
 * it, and nothing generates down there at all. {@link #BAND_FLOOR} and
 * {@link #BAND_CEILING} are cut out of that gap with {@link #CLEARANCE} to spare
 * at both ends, which is what {@code theBandSitsInsideTheVoid} holds.
 *
 * <p><b>Units.</b> Velocities are blocks per tick, the units
 * {@code Entity.getDeltaMovement} speaks. {@link #DRIFT} is 0.03, which is 0.6
 * blocks a second. A walk is 4.317 and the 8/23 flight that stalled the autosave
 * was 33. A squid is therefore slower than walking away from it, which is the
 * whole of its answer to the save-safety law: nobody will ever follow one
 * anywhere, and it cannot carry anybody.
 *
 * <p><b>Nothing here may be smaller than {@link #TRUNCATION}.</b>
 * {@code LivingEntity.aiStep} zeroes any velocity component under 0.003 before
 * it travels, so a drift tuned below that is silently no drift at all. The
 * consequence is visible in the shape of the motion rather than hidden: at
 * {@link #DRIFT} = 0.03 a heading within about six degrees of an axis loses its
 * small component and the squid drifts exactly along that axis for a while. That
 * is a wobble in the wander, not a defect, and it is written down so nobody
 * hunts it later.
 */
public final class VoidSquidDrift {

    /** The Overworld dimension floor. Stated, not read - this class reads nothing. */
    public static final int WORLD_FLOOR = -64;

    /**
     * The continent's underside: the floor of the noise band in
     * {@code noise_settings/sky.json}. Nothing generates below it, which is what
     * makes the gap under it a place to put a creature.
     */
    public static final int UNDERSIDE = 0;

    /**
     * How many blocks of open air a squid keeps around itself in every
     * direction it could go.
     *
     * <p>Two, and the number does more work than it looks. Vertically it is what
     * keeps a squid out of the two-block air seal over a watershed pool: that
     * seal is one block of water with air at +1 and +2, so a squid standing at
     * +1 has water one below and a squid at +2 has water two below, and both
     * fail. Standing at +3 passes, which is the charter's image exactly - held
     * over the mirror, never in it. See {@code VoidSquid.opensTo}.
     */
    public static final int CLEARANCE = 2;

    /** The highest a squid drifts. Far enough under the underside to be under it. */
    public static final int BAND_CEILING = -10;  // provisional - owner tunes by walking the world

    /** The lowest. Far enough over the dimension floor that nothing hangs off it. */
    public static final int BAND_FLOOR = -54;    // provisional - owner tunes by walking the world

    /** Horizontal drift, blocks per tick. 0.6 blocks a second. */
    public static final double DRIFT = 0.03;     // provisional - owner tunes by walking the world

    /** The gentle rise and fall inside the band, blocks per tick. */
    public static final double BOB = 0.02;       // provisional - owner tunes by walking the world

    /** How fast a squid outside the band returns to it, blocks per tick. */
    public static final double RECOVER = 0.05;   // provisional - owner tunes by walking the world

    /** Ticks for the heading to come all the way round. 2048 is about 102 seconds. */
    public static final int TURN_TICKS = 2048;   // provisional - owner tunes by walking the world

    /** Ticks in one rise and fall. 240 is twelve seconds. */
    public static final int BOB_TICKS = 240;     // provisional - owner tunes by walking the world

    /**
     * How long a squid stays somewhere it may not be before it is simply not
     * there any more.
     *
     * <p>Two seconds. It is not a death and it drops nothing - see
     * {@code VoidSquid.withdraw} for why a creature you can box in must not also
     * be a creature you can farm.
     */
    public static final int WITHDRAW_TICKS = 40;  // provisional - owner tunes by walking the world

    /** Vanilla's own velocity floor, from {@code LivingEntity.aiStep}. */
    public static final double TRUNCATION = 0.003;

    /** Keeps this draw off every other salt in the mod, the {@code Beamline} precedent. */
    private static final long HEADING_SALT = 0x1_0000_5_00_1DL;

    private VoidSquidDrift() {
    }

    /** One tick of intended velocity, as a plain triple. Blocks per tick. */
    public record Drift(double x, double y, double z) {
    }

    /** Horizontal magnitude. Public because the tests measure with it. */
    public static double horizontal(Drift drift) {
        return Math.sqrt(drift.x() * drift.x() + drift.z() * drift.z());
    }

    /** Whether this altitude is inside the band. Inclusive at both edges. */
    public static boolean inBand(double y) {
        return y >= BAND_FLOOR && y <= BAND_CEILING;
    }

    /**
     * Which neighbouring block a velocity component points at: -1, 0 or 1.
     *
     * <p>Public because {@link VoidSquid} asks the world about exactly three
     * neighbours per tick and this is how it picks them. Zero answers the cell
     * the squid is already in, which is always open by the time this is asked.
     */
    public static int step(double component) {
        if (component > 0) {
            return 1;
        }
        return component < 0 ? -1 : 0;
    }

    /**
     * The heading a squid is drifting on this tick, radians, from its own mark.
     *
     * <p>The mark is a squid's own number and not the world seed, so two squids
     * in one place wander apart instead of shoaling. It turns at a fixed rate
     * rather than being re-drawn, because a re-drawn heading is a jitter and a
     * turning one is a wander, and the second is what a drifting thing does.
     *
     * <p>{@code Math.floorMod} on the tick keeps the angle inside one turn
     * forever. Adding {@code tick * rate} unbounded would be correct for about a
     * fortnight of game time and then start losing precision, which is the kind
     * of fault nobody finds.
     */
    public static double heading(long mark, long tick) {
        long h = mix(mark ^ HEADING_SALT);
        double base = Math.floorMod(h, 3600) / 3600.0 * 2 * Math.PI;
        return base + Math.floorMod(tick, TURN_TICKS) * (2 * Math.PI / TURN_TICKS);
    }

    /**
     * The vertical part of a tick, and the only thing that owns the band.
     *
     * <p>Three answers, in order. Above the ceiling it sinks and nothing else -
     * so no input, no impulse and no argument can leave a squid climbing out of
     * the band. Below the floor it rises. Inside, it bobs, and the bob is
     * clamped to what is left of the band rather than being allowed to overshoot
     * and be corrected next tick: {@code y + rise(y, t)} is inside the band for
     * every {@code y} that was, which is a stronger statement than "it comes
     * back" and is the one {@code VoidSquidDriftTest} pins.
     */
    public static double rise(double y, long tick) {
        if (y >= BAND_CEILING) {
            return -RECOVER;
        }
        if (y <= BAND_FLOOR) {
            return RECOVER;
        }
        double bob = BOB * Math.sin(2 * Math.PI * Math.floorMod(tick, BOB_TICKS) / BOB_TICKS);
        return Math.max(BAND_FLOOR - y, Math.min(BAND_CEILING - y, bob));
    }

    /**
     * One tick of intended velocity: a slow turn horizontally, the band
     * vertically.
     *
     * <p>Set rather than added. The game applies air drag of 0.91 after the
     * move, so a velocity that were added to would settle somewhere the numbers
     * above do not name; setting it means the executed speed is exactly
     * {@link #DRIFT} and the ceiling is this file's number rather than the
     * drag's. That is {@code SailRig}'s argument in the other direction.
     */
    public static Drift drift(long mark, long tick, double y) {
        double angle = heading(mark, tick);
        return new Drift(DRIFT * Math.cos(angle), rise(y, tick), DRIFT * Math.sin(angle));
    }

    /**
     * Drops any component that points at a cell the squid may not occupy.
     *
     * <p>Dropping, never redirecting. A refusal that pushed back would be a
     * force, and a force can be stacked against another force until something
     * ends up where neither wanted it; a refusal that zeroes cannot. The
     * postcondition is the useful one and the tests state it: no component ever
     * changes sign, and none ever grows. So a squid that is sinking because it
     * is above the ceiling cannot be made to rise by anything in its way - it
     * only ever stops.
     */
    public static Drift refuse(Drift in, boolean openX, boolean openY, boolean openZ) {
        return new Drift(openX ? in.x() : 0.0,
                openY ? in.y() : 0.0,
                openZ ? in.z() : 0.0);
    }

    /**
     * Splitmix64's finaliser.
     *
     * <p>A second copy of {@code Sightlines.mix}, and the duplication is on
     * purpose rather than an oversight. That one is package-private in
     * {@code world} and is the lattice's mixer; this one hashes a creature's own
     * number and has nothing to do with the lattice. Reaching across for it
     * would tie a squid's wander to the threads, and then tuning either would
     * move both.
     */
    private static long mix(long value) {
        long v = value;
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= v >>> 33;
        return v;
    }
}
