package com.serenity.octia.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The insides that are not on any stack.
 *
 * <p>A bindle and a red cube carry what they hold on the item itself, which is
 * why a hopper, a death and a chest all move the contents without this file
 * existing. The other two cubes are the opposite claim: a gold cube is a door
 * onto <em>your</em> one pocket and a purple cube is a door onto the save's one
 * pocket, so what they hold cannot live on the door. Two gold cubes in one hand
 * are one pocket seen twice, and that is only true if the pocket is kept
 * somewhere neither of them is.
 *
 * <p><b>Fetched through {@code server.overworld()}, deliberately.</b> That data
 * storage is the save-root {@code data/} folder, shared by every dimension -
 * the same reason {@code ShipMoorings} and {@code WayfarerLedger} reach for it.
 * Fetching from the current level would give one store per dimension, and a
 * purple cube would then open a different bag in the Nether, which is the only
 * property the purple cube has.
 *
 * <p><b>Keys.</b> A player's pocket is keyed by the string form of their UUID;
 * the road's is keyed by {@link #ROAD}. Those cannot collide - a UUID's string
 * form is thirty-six characters with four hyphens in it and is never the word
 * {@code road} - so one flat map holds both without a discriminator.
 *
 * <p><b>Nothing here is a container the world can see.</b> No block entity, no
 * menu, no {@code Container}. A pocket is a list of stacks that only
 * {@link CubeItem} ever reads, through {@link #read} and {@link #write}, and
 * every list handed in or out is a copy - the store's own list is never lent
 * out, for the reason {@link BindleItem#contents} copies too.
 */
public final class CubePockets extends SavedData {

    /** Becomes {@code <save>/data/octia_pockets.dat}. */
    private static final String FILE = "octia_pockets";

    /**
     * The one key every purple cube in the save opens.
     *
     * <p>A word rather than a UUID because it is not anybody's, and because a
     * person reading the {@code .dat} should be able to tell the shared bag
     * from the personal ones without a lookup table.
     */
    public static final String ROAD = "road";

    /** For the DataFixTypes choice, see the note in {@code ShipMoorings}. */
    private static final SavedData.Factory<CubePockets> FACTORY = new SavedData.Factory<>(
            CubePockets::new, CubePockets::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    /** Pocket key to what is in it, in the order it went in. */
    private final Map<String, List<ItemStack>> pockets = new HashMap<>();

    private CubePockets() {
    }

    /** The one store for the whole save. Never per-dimension. */
    public static CubePockets get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    /**
     * Reads the file back.
     *
     * <p>A stack that will not parse is dropped rather than throwing, because
     * the alternative to losing one item to a removed mod is losing every
     * pocket in the save to it. The loss is silent here and loud nowhere, which
     * is the honest trade for a bag: the pocket still opens.
     */
    private static CubePockets load(CompoundTag tag, HolderLookup.Provider registries) {
        CubePockets out = new CubePockets();
        for (String key : tag.getAllKeys()) {
            List<ItemStack> held = new ArrayList<>();
            for (Tag entry : tag.getList(key, Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, entry).ifPresent(held::add);
            }
            out.pockets.put(key, held);
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        pockets.forEach((key, held) -> {
            ListTag list = new ListTag();
            for (ItemStack stack : held) {
                // ItemStack.save throws on an empty stack rather than writing
                // one, so the guard is not tidiness - it is the difference
                // between a saved world and a stack trace in the autosave.
                if (!stack.isEmpty()) {
                    list.add(stack.save(registries));
                }
            }
            tag.put(key, list);
        });
        return tag;
    }

    /** What is in a pocket, as a mutable copy. Never the store's own list. */
    public List<ItemStack> read(String key) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : pockets.getOrDefault(key, List.of())) {
            out.add(stack.copy());
        }
        return out;
    }

    /**
     * Writes a pocket back, dropping any empties so the slot count stays
     * honest - the same rule {@link BindleItem} keeps on a stack, for the same
     * reason: an empty stack in the list is a slot nobody can use.
     */
    public void write(String key, List<ItemStack> held) {
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : held) {
            if (!stack.isEmpty()) {
                kept.add(stack.copy());
            }
        }
        pockets.put(key, kept);
        setDirty();
    }

    /** How many pockets have been opened. Useful to a test, not to the world. */
    public int count() {
        return pockets.size();
    }
}
