package com.serenity.octia.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Whether Octia is switched on for this save, decided once on the world-create
 * screen and then fixed for the life of the world.
 *
 * <p><b>How the create-screen choice reaches the world.</b> The button on
 * {@code CreateWorldScreen} runs on the client, before any server or save
 * directory exists, so it has nowhere to write. It parks the answer in
 * {@link #pending} instead. The first time a save asks for this store there is
 * no {@code .dat} file, so {@code computeIfAbsent} calls the no-arg constructor
 * — and that constructor takes {@link #pending}. A world that already has the
 * file goes through {@link #load} instead and keeps whatever it was created
 * with. One field, two paths in, and no way for a later world to inherit an
 * earlier world's answer by accident.
 *
 * <p>Fetched through {@code server.overworld()} for the same reason
 * {@code ShipMoorings} is: that data storage is the save-root {@code data/}
 * folder, shared by every dimension. Per-dimension would mean the Nether could
 * disagree with the Overworld about whether the mod is on.
 */
public final class OctiaWorldOption extends SavedData {

    /** Becomes {@code <save>/data/octia_world.dat}. */
    private static final String FILE = "octia_world";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_RAISED = "beacon_raised";

    /**
     * Where the beacon was raised, packed. Absent on every save written before
     * this key existed - including worlds whose beacon is standing right now -
     * so absence means "not recorded", never "not raised". {@link #beaconRaised}
     * remains the only authority on whether it went up.
     */
    private static final String KEY_BEACON_AT = "beacon_at";

    /**
     * See the class note. Defaults to on: a player who installs a mod and never
     * touches the button expects the mod, not silence.
     *
     * <p>Volatile because the create screen writes it on the client thread and
     * the store reads it on the server thread.
     */
    private static volatile boolean pending = true;

    /** As above: for DataFixTypes, see the note in ShipMoorings. */
    private static final SavedData.Factory<OctiaWorldOption> FACTORY = new SavedData.Factory<>(
            OctiaWorldOption::new, OctiaWorldOption::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private boolean enabled;
    private boolean beaconRaised;

    /** Packed position of the beacon, or null if this save never recorded one. */
    private Long beaconAt;

    private OctiaWorldOption() {
        this.enabled = pending;
    }

    /** Set from the world-create screen. Has no effect on a world that already exists. */
    public static void setPending(boolean value) {
        pending = value;
    }

    public static boolean pending() {
        return pending;
    }

    /** The one store for the whole save. Never per-dimension. */
    public static OctiaWorldOption get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    private static OctiaWorldOption load(CompoundTag tag, HolderLookup.Provider registries) {
        OctiaWorldOption out = new OctiaWorldOption();
        out.enabled = tag.getBoolean(KEY_ENABLED);
        out.beaconRaised = tag.getBoolean(KEY_RAISED);
        // contains() rather than getLong(): getLong answers 0 for an absent key,
        // and 0 is a real position - the block at world origin, y=0.
        out.beaconAt = tag.contains(KEY_BEACON_AT) ? tag.getLong(KEY_BEACON_AT) : null;
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(KEY_ENABLED, enabled);
        tag.putBoolean(KEY_RAISED, beaconRaised);
        if (beaconAt != null) {
            tag.putLong(KEY_BEACON_AT, beaconAt);
        }
        return tag;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean beaconRaised() {
        return beaconRaised;
    }

    /**
     * Where the beacon stands, or null if this save has no record of it.
     *
     * <p>Null does not mean no beacon. Saves written before this was recorded
     * have one standing and cannot say where; ask {@link #beaconRaised()} for
     * that question and treat this purely as a map hint.
     */
    public BlockPos beaconAt() {
        return beaconAt == null ? null : BlockPos.of(beaconAt);
    }

    /** Called once, by the raiser, as the beacon goes up. */
    public void recordBeaconAt(BlockPos pos) {
        beaconAt = pos.asLong();
        setDirty();
    }

    /** @return true the first time only, so the beacon is raised once per save */
    public boolean claimBeacon() {
        if (beaconRaised) {
            return false;
        }
        beaconRaised = true;
        setDirty();
        return true;
    }
}
