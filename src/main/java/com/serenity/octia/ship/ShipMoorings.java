package com.serenity.octia.ship;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The spine: every moored ship, keyed by {@link BlockPos} and nothing else.
 *
 * <p>This is the claim the whole design rests on - a ship exists at the same
 * coordinates across the era stack. It is true here because <b>the dimension is
 * not part of the key</b>. A position moored from the Overworld reads as moored
 * from the Nether, the End, or any era layer added later, because there is one
 * store and one file for the entire save.
 *
 * <p>That is also why this is fetched through {@code server.overworld()}: the
 * Overworld's data storage is the save-root {@code data/} folder, shared by
 * every dimension. Fetching it from the current level would silently produce
 * one store per dimension and quietly break the only property that matters.
 * {@code ShipGameTest.mooringsAreDimensionAgnostic} pins this.
 */
public final class ShipMoorings extends SavedData {

    /** Becomes {@code <save>/data/octia_moorings.dat}. */
    private static final String FILE = "octia_moorings";

    private static final String KEY = "moorings";

    /**
     * A DataFixTypes is mandatory even though this data has no vanilla fixers.
     * {@code DimensionDataStorage.readSavedData} passes it straight to
     * {@code readTagFromDisk} with no null check, inside a try/catch that only
     * logs - so a null here would not crash, it would discard every moored ship
     * on world reload and leave one line in the log. RANDOM_SEQUENCES is chosen
     * because its schema is minimal, and fixers only run at all when a world is
     * opened in a later game version than it was written by.
     */
    private static final SavedData.Factory<ShipMoorings> FACTORY = new SavedData.Factory<>(
            ShipMoorings::new, ShipMoorings::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private final Set<Long> moored = new HashSet<>();

    private ShipMoorings() {
    }

    /** The one store for the whole save. Never per-dimension. */
    public static ShipMoorings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    private static ShipMoorings load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipMoorings out = new ShipMoorings();
        for (long packed : tag.getLongArray(KEY)) {
            out.moored.add(packed);
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(KEY, moored.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    /** @return true if this position was not already moored */
    public boolean moor(BlockPos pos) {
        if (moored.add(pos.asLong())) {
            setDirty();
            return true;
        }
        return false;
    }

    /** @return true if this position had been moored */
    public boolean unmoor(BlockPos pos) {
        if (moored.remove(pos.asLong())) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean isMoored(BlockPos pos) {
        return moored.contains(pos.asLong());
    }

    public int count() {
        return moored.size();
    }

    /** Every moored position. Unordered - the store is a set, not a route. */
    public Set<BlockPos> positions() {
        return Collections.unmodifiableSet(
                moored.stream().map(BlockPos::of).collect(Collectors.toSet()));
    }
}
