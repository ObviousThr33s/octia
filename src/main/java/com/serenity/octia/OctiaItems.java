package com.serenity.octia;

import java.util.List;

import com.serenity.octia.item.Bindle;
import com.serenity.octia.item.BindleItem;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every item this mod registers that is not a block, and the one place it
 * happens.
 *
 * <p>The sibling of {@link OctiaBlocks} and the same contract: registration runs
 * in the static initialiser, which fires when {@link #bootstrap()} is first
 * called from the entrypoint, and touching this class any earlier moves
 * registration to whatever phase touched it. The block items stay in
 * {@code OctiaBlocks} because they are made by the block funnel there; this file
 * is for items that are only ever items.
 */
public final class OctiaItems {

    /**
     * A wanderer's bindle. Four stacks, no screen, worked like a bundle.
     *
     * <p>Stacks to one, which is not a balance decision - a bindle carries its
     * contents in a data component, and two of them in a stack would be two
     * different sets of contents with one component between them. Vanilla holds
     * every container item to one for exactly this reason.
     */
    public static final BindleItem BINDLE = register("bindle",
            new BindleItem(new Item.Properties().stacksTo(1)));

    private OctiaItems() {
    }

    private static <T extends Item> T register(String path, T item) {
        Registry.register(BuiltInRegistries.ITEM, Octia.id(path), item);
        return item;
    }

    /** Forces class initialisation, then files the items into creative tabs. */
    static void bootstrap() {
        // Tools and utilities, beside the bundle it is worked like, rather than
        // in ingredients where an empty bag would read as a crafting component.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(BINDLE));
    }

    /**
     * A bindle packed the way somebody on the road would have packed it.
     *
     * <p>What a wayfarer leaves behind, and it has to read as a person's rather
     * than as a loot roll: something to eat, something to see by, and one thing
     * that says they had been at a dig. Two or three stacks, never four - a full
     * bindle is one nobody was using.
     */
    public static ItemStack forTheRoad(RandomSource random) {
        ItemStack bindle = new ItemStack(BINDLE);

        List<ItemStack> road = List.of(
                new ItemStack(Items.BREAD, 1 + random.nextInt(3)),
                new ItemStack(Items.TORCH, 2 + random.nextInt(5)),
                new ItemStack(random.nextBoolean() ? Items.STRING : Items.LEATHER, 1 + random.nextInt(2)),
                new ItemStack(OctiaBlocks.ANDESITE_FRAME_PANEL, 1 + random.nextInt(2)));

        int packed = 2 + random.nextInt(Bindle.SLOTS - 2);
        for (int i = 0; i < packed; i++) {
            BindleItem.add(bindle, road.get(i));
        }
        return bindle;
    }
}
