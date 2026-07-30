package com.serenity.octioid;

import com.serenity.octioid.block.AndesiteFramePanelBlock;

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
 */
public final class OctioidBlocks {

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

    private OctioidBlocks() {
    }

    /** Registers a block and its matching item under the same path. */
    private static <T extends Block> T register(String path, T block) {
        Registry.register(BuiltInRegistries.BLOCK, Octioid.id(path), block);
        Registry.register(BuiltInRegistries.ITEM, Octioid.id(path), new BlockItem(block, new Item.Properties()));
        return block;
    }

    /** Forces class initialisation, then files the blocks into a creative tab. */
    static void bootstrap() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(entries -> entries.accept(ANDESITE_FRAME_PANEL));
    }
}
