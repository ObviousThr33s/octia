package com.serenity.octia.crew;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who has been met, and where.
 *
 * <p>This is the whole reason strangers are worth building. Anyone can spawn a
 * wanderer; a wanderer who turns up a second time and opens with <i>the place</i>
 * rather than with your name is a different thing entirely - it means the world
 * kept something, and that the first meeting mattered after it ended.
 *
 * <p>Deliberately thin: a name and a position, nothing else. No conversation
 * history, no relationship score, no disposition. What a traveller remembers
 * about a stranger on a road is roughly where they were standing, and a richer
 * model would be a promise the rest of the design cannot keep.
 *
 * <p>Fetched through {@code server.overworld()} for the same reason
 * {@code ShipMoorings} is: that data storage is the save-root {@code data/}
 * folder, shared by every dimension. A person met in the Nether is the same
 * person met in the Overworld.
 */
public final class WayfarerLedger extends SavedData {

    /** Becomes {@code <save>/data/octia_wayfarers.dat}. */
    private static final String FILE = "octia_wayfarers";

    /** For the DataFixTypes choice, see the note in ShipMoorings. */
    private static final SavedData.Factory<WayfarerLedger> FACTORY = new SavedData.Factory<>(
            WayfarerLedger::new, WayfarerLedger::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    /** Seat name to the packed position of the last meeting. */
    private final Map<String, Long> met = new HashMap<>();

    private WayfarerLedger() {
    }

    public static WayfarerLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    private static WayfarerLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        WayfarerLedger out = new WayfarerLedger();
        for (String name : tag.getAllKeys()) {
            out.met.put(name, tag.getLong(name));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        met.forEach(tag::putLong);
        return tag;
    }

    /** Where this person was last met, or null if never. */
    public BlockPos where(String name) {
        Long packed = met.get(name);
        return packed == null ? null : BlockPos.of(packed);
    }

    public boolean hasMet(String name) {
        return met.containsKey(name);
    }

    /** Records a meeting, replacing any earlier one. The latest place is the one. */
    public void remember(String name, BlockPos where) {
        met.put(name, where.asLong());
        setDirty();
    }

    public int count() {
        return met.size();
    }
}
