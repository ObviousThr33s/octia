package com.serenity.octia;

import java.util.List;

import com.serenity.octia.item.BindleItem;
import com.serenity.octia.item.CubeItem;
import com.serenity.octia.traverse.SailRigItem;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTab;
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

    /**
     * A hand-made sail-frame: andesite ribs over cloth, held, not worn.
     *
     * <p>Stacks to one because it is equipment - a committed hand is the price
     * of the glide, and a stack of sails would be a stack of hands. No
     * durability and no enchantability: the rig is a decision, not a tool that
     * wears out. Everything it does lives in {@link SailRigItem}.
     */
    public static final SailRigItem SAIL_RIG = register("sail_rig",
            new SailRigItem(new Item.Properties().stacksTo(1)));

    /**
     * Sealed. Holds what its packer left, on its own stack, exactly as a bindle
     * does. Stacks to one because two of them in a stack would be two sets of
     * contents sharing one component.
     */
    public static final CubeItem RED_CUBE = register("red_cube",
            new CubeItem(CubeItem.Kind.SEALED, new Item.Properties().stacksTo(1)));

    /**
     * Yours. Every gold cube a player holds opens that player's one pocket in
     * {@code CubePockets}, so two in one hand are one bag with two doors.
     */
    public static final CubeItem GOLD_CUBE = register("gold_cube",
            new CubeItem(CubeItem.Kind.OWN, new Item.Properties().stacksTo(1)));

    /**
     * The road. One bag for the whole save; every purple cube anywhere opens it.
     * It moves items and never a player, which is why it is not a traversal
     * mechanic.
     */
    public static final CubeItem PURPLE_CUBE = register("purple_cube",
            new CubeItem(CubeItem.Kind.ROAD, new Item.Properties().stacksTo(1)));

    /**
     * The mod's own creative tab: everything Octia registers, in one place.
     *
     * <p><b>An addition, not a move.</b> The bindle and the sail-rig are still
     * filed into {@code TOOLS_AND_UTILITIES} by {@link #bootstrap()}, and the
     * two blocks are still filed into their vanilla tabs by
     * {@link OctiaBlocks#bootstrap()}. A player who learned where the bindle
     * lives finds it there tomorrow. This tab is the second door, not the
     * relocation: an item may appear in as many tabs as file it, and the cost of
     * appearing twice is a duplicate in a search, while the cost of moving is a
     * player looking for something that is no longer where they left it.
     *
     * <p><b>Why a field and not a line in bootstrap.</b> Registration belongs in
     * the static initialiser, beside the items, for the reason the class note
     * gives: it happens exactly once, on the first touch, and
     * {@link #bootstrap()} is what schedules that touch. The two lambdas below
     * are lazy, so this field may sit above or below the items it names without
     * changing anything - it reads better here, at the end of the list, because
     * it is about all of them.
     *
     * <p>The icon is the andesite frame panel: the one block this mod is made
     * of, the thing every ruin is built from, and the only sprite a player will
     * already recognise before they have found anything.
     */
    public static final CreativeModeTab ITEMS = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB, Octia.id("items"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(OctiaBlocks.ANDESITE_FRAME_PANEL))
                    .title(Component.translatable("itemGroup.octia.items"))
                    .displayItems((parameters, entries) -> {
                        // Blocks first, the way a builder reaches for them:
                        // the thing everything is made of, then the thing that
                        // makes it a ship.
                        entries.accept(OctiaBlocks.ANDESITE_FRAME_PANEL);
                        entries.accept(OctiaBlocks.SHIP_CORE);

                        // Then what a person carries. The bindle before the
                        // cubes because the cubes are bindles - see CubeItem.
                        entries.accept(BINDLE);
                        entries.accept(SAIL_RIG);
                        entries.accept(RED_CUBE);
                        entries.accept(GOLD_CUBE);
                        entries.accept(PURPLE_CUBE);
                    })
                    .build());

    private OctiaItems() {
    }

    private static <T extends Item> T register(String path, T item) {
        Registry.register(BuiltInRegistries.ITEM, Octia.id(path), item);
        return item;
    }

    /**
     * Forces class initialisation, then files the items into creative tabs.
     *
     * <p>Touching this class is what registers everything above, {@link #ITEMS}
     * included - the tab is a static field, so the mod's own tab exists the
     * moment this is called and not before.
     */
    static void bootstrap() {
        // Tools and utilities, beside the bundle it is worked like, rather than
        // in ingredients where an empty bag would read as a crafting component.
        //
        // These stay after ITEMS was added, and that is the decision rather than
        // an oversight: a mod tab is where a player looks for a mod's things,
        // and this is where a player who has been using the bindle since before
        // the tab existed will keep reaching. Both, not one.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    entries.accept(BINDLE);
                    entries.accept(SAIL_RIG);
                    entries.accept(RED_CUBE);
                    entries.accept(GOLD_CUBE);
                    entries.accept(PURPLE_CUBE);
                });
    }

    /**
     * A bindle packed the way somebody on the road would have packed it.
     *
     * <p>What a wayfarer leaves behind, and it has to read as a person's rather
     * than as a loot roll: something to eat, something to see by, and half the
     * time one thing off the road. Two or three stacks, never four - a full
     * bindle is one nobody was using.
     *
     * <p>This is the same road the store tables pack. Each of
     * {@code octia:chests/ruin_store}, {@code ruin_store_old} and
     * {@code station_store} carries a {@code set_contents} block that is this
     * list in JSON, and the three had drifted apart - the old store's bindle had
     * no food, the ruin store's had a 70 percent torch, and neither could ever
     * produce the piece of hull this list claims to carry. A change here is a
     * change owed to all three. See {@code OctiaLoot}.
     *
     * <p><b>Corrected [2026-08-24].</b> The list used to be four long while the
     * draw was {@code 2 + random.nextInt(Bindle.SLOTS - 2)}, which is 2 or 3. So
     * {@code road.get(3)} - the andesite frame panel - was unreachable and had
     * never been packed in any world, while the paragraph above promised it. The
     * superseded shape is kept here rather than deleted, because that is how
     * this repo records a fix:
     *
     * <pre>
     *     new ItemStack(random.nextBoolean() ? Items.STRING : Items.LEATHER, ...),
     *     new ItemStack(OctiaBlocks.ANDESITE_FRAME_PANEL, ...));
     *     int packed = 2 + random.nextInt(Bindle.SLOTS - 2);
     * </pre>
     *
     * <p>The third thing is now drawn rather than appended, so the list is three
     * long and every entry in it can actually be handed to somebody. The draw
     * count drops from six to five, which moves the exact items on a given seed;
     * {@code BindleGameTest.theRoadPacksTwoOrThree} asserts the slot count and
     * the item type only, and 2 or 3 is still what comes out.
     */
    public static ItemStack forTheRoad(RandomSource random) {
        ItemStack bindle = new ItemStack(BINDLE);

        List<ItemStack> road = List.of(
                new ItemStack(Items.BREAD, 1 + random.nextInt(3)),
                new ItemStack(Items.TORCH, 2 + random.nextInt(5)),
                // The third thing is one thing, drawn here rather than added
                // as three more slots a bindle does not have.
                switch (random.nextInt(3)) {
                    case 0 -> new ItemStack(Items.STRING, 1 + random.nextInt(2));
                    case 1 -> new ItemStack(Items.LEATHER, 1 + random.nextInt(2));
                    default -> new ItemStack(OctiaBlocks.ANDESITE_FRAME_PANEL, 1 + random.nextInt(2));
                });

        // road.size() rather than Bindle.SLOTS: the bound is a property of this
        // list, and the defect above was exactly the two disagreeing.
        int packed = 2 + random.nextInt(road.size() - 1);
        for (int i = 0; i < packed; i++) {
            BindleItem.add(bindle, road.get(i));
        }
        return bindle;
    }
}
