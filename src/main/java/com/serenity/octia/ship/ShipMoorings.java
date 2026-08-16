package com.serenity.octia.ship;

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
    /**
     * <b>FROZEN. Do not rename this with the mod.</b>
     *
     * <p>This is not a namespace, it is a filename: it becomes
     * {@code <save>/data/octia_moorings.dat}. Every save ever written by this
     * mod has one. Change the string and {@code computeIfAbsent} finds no file,
     * calls the no-arg constructor, and hands back an empty store - so every
     * mooring a player made disappears from the F6 map, with no exception, no
     * log line, and nothing in the save actually deleted. It looks exactly like
     * a world that was never moored.
     *
     * <p>The mod's name may move. This may not, until somebody writes the
     * migration that reads the old file and writes the new one.
     */
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
     *
     * <p><b>Borrowing a vanilla type is not free.</b> {@code SavedData.save}
     * stamps this file with the current DataVersion, so vanilla's fixers for
     * this type will run over the mod's own NBT on any Minecraft bump. V99
     * registers it as {@code DSL.remainder()}, which is exactly where
     * {@code moorings} sits - a future fixer shaped like
     * {@code RandomSequenceSettingsFix}, which rewrites {@code data} into
     * {@code data.sequences}, would carry it out of reach. {@link #load} would
     * then read an absent long array and return an empty set: no crash, no log
     * line, every moored ship forgotten. docs/UPGRADING.md holds the checkpoint
     * to run before that bump.
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

    /**
     * Every moored position, as a detached snapshot rather than a view. It is
     * built fresh per call, so writing to it changes nothing and holding it
     * shows nothing new - which is why it is not wrapped unmodifiable: there is
     * no shared set to protect. Unordered: the store is a set, not a route.
     */
    public Set<BlockPos> positions() {
        return moored.stream().map(BlockPos::of).collect(Collectors.toSet());
    }
}
