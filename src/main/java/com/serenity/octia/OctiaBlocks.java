package com.serenity.octia;

import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.ship.ShipCoreBlock;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Every block this mod registers, and the one place it happens.
 *
 * <p>Registration runs in the static initialiser, which fires when
 * {@link #bootstrap()} is first called from the mod entrypoint. That ordering
 * matters: touching this class before Minecraft's registries are open throws,
 * and calling it from {@code onInitialize} is the guaranteed-safe moment.
 *
 * <p><b>The invariant, written down because the JVM will not enforce it.</b>
 * Class initialisation fires on the first <i>active use</i> by anyone, and
 * reading {@link #ANDESITE_FRAME_PANEL} counts exactly as much as calling
 * {@link #bootstrap()}. The safety above therefore rests on nothing touching
 * this class before {@code Octia.onInitialize} does. Any later hook that reads
 * a field here from an earlier phase - a mixin, a client-only initialiser, a
 * static field in some other class - moves registration to that phase without
 * a word of warning.
 */
public final class OctiaBlocks {

    /**
     * Andesite framing around a panel that cycles dark, generic, styled.
     *
     * <p>The light level is wired here rather than inside the block class
     * because {@code Properties} is frozen once the block is constructed —
     * this lambda is the only hook the game offers for a state-dependent
     * light, and it is consulted every time the blockstate changes.
     */
    public static final AndesiteFramePanelBlock ANDESITE_FRAME_PANEL = register(
            "andesite_frame_panel",
            new AndesiteFramePanelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(AndesiteFramePanelBlock.LIGHT).lightLevel())));

    /**
     * The ship's anchor. Ring it with frame panels to moor it; leave a dig
     * nearby and it is called.
     *
     * <p>Harder than the panel and blast-resistant on purpose - a core is
     * infrastructure, and a creeper should not unmoor a ship.
     */
    public static final ShipCoreBlock SHIP_CORE = register(
            "ship_core",
            new ShipCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.0f, 1200.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ShipCoreBlock.STATUS).lightLevel())));

    private OctiaBlocks() {
    }

    /**
     * Registers a block and its matching item under the same path.
     *
     * <p>One funnel, one {@link Octia#id(String)} call per registry: that is
     * what keeps the block and item registries from ever disagreeing about a
     * path, and what keeps a namespaced string literal out of this file.
     *
     * <p><b>Seam.</b> This assumes every block wants exactly one plain
     * {@code BlockItem} with default {@code Item.Properties}. The first block
     * that wants none (a purely technical block), or a stack limit, or a
     * {@code BlockItem} subclass, is the one that needs a second path through
     * here. Add it then; adding it now would be an abstraction with no caller.
     */
    private static <T extends Block> T register(String path, T block) {
        Registry.register(BuiltInRegistries.BLOCK, Octia.id(path), block);
        Registry.register(BuiltInRegistries.ITEM, Octia.id(path), new BlockItem(block, new Item.Properties()));
        return block;
    }

    /** Forces class initialisation, then files the blocks into creative tabs. */
    static void bootstrap() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(entries -> entries.accept(ANDESITE_FRAME_PANEL));
        // The core is machinery, not masonry.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> entries.accept(SHIP_CORE));
    }
}
