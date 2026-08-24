package com.serenity.octia.client;

import java.util.Optional;

import com.serenity.octia.Octia;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The world type the Octia switch chooses for you.
 *
 * <p><b>What the terrain actually is.</b> {@code data/octia/worldgen/world_preset/sky.json}
 * is the whole of Octia's terrain: the Overworld generated from
 * {@code minecraft:floating_islands} with the ordinary Overworld biome source
 * over it, so the islands are plains and savanna and snowy taiga rather than a
 * second End. No Java generates it and none should - a world preset is data,
 * and the moment it becomes code it stops being something a pack author can
 * borrow, override or turn off.
 *
 * <p><b>Why this class exists at all, then.</b> Because a preset nobody selects
 * generates nothing. Shipping the JSON and tagging it into
 * {@code minecraft:normal} puts <i>Octia Sky</i> on the world-type button and
 * leaves the terrain one undiscovered click away, which for the mod's own
 * switch is the wrong default: turning Octia on and getting vanilla hills is
 * the same broken promise the beacon exists to prevent.
 *
 * <p><b>What it will not do.</b> It never overrides a world type the player
 * chose. It moves {@code minecraft:normal} to {@code octia:sky} and moves
 * {@code octia:sky} back to {@code minecraft:normal}, and touches nothing else
 * - pick Amplified or Large Biomes or a datapack's own preset and the switch
 * leaves it alone, because at that point the player has said something more
 * specific than the switch has.
 */
final class SkyChoice {

    /** The preset shipped in this mod's data. Built through {@link Octia#id}, as everything is. */
    private static final ResourceKey<WorldPreset> SKY =
            ResourceKey.create(Registries.WORLD_PRESET, Octia.id("sky"));

    /** Vanilla's own, and the thing {@link #SKY} is swapped against. */
    private static final ResourceKey<WorldPreset> NORMAL =
            ResourceKey.create(Registries.WORLD_PRESET,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("normal"));

    /**
     * Logged once rather than every time the screen resizes. A missing preset
     * means the datapack did not load, which is worth one line and not a line
     * per frame of a window drag.
     */
    private static boolean complained;

    private SkyChoice() {
    }

    /**
     * Points the world type at the sky, or back at vanilla.
     *
     * @param on whether Octia is switched on for the world about to be made
     */
    static void follow(CreateWorldScreen screen, boolean on) {
        WorldCreationUiState state = screen.getUiState();
        ResourceKey<WorldPreset> current = keyOf(state.getWorldType());

        ResourceKey<WorldPreset> want = on ? SKY : NORMAL;
        ResourceKey<WorldPreset> from = on ? NORMAL : SKY;

        // Only ever the one swap. Anything else on the button is a deliberate
        // choice and outranks the switch.
        if (current == null || !current.equals(from)) {
            return;
        }

        // getNormalPresetList rather than a registry lookup: it is the list the
        // world-type button itself cycles, so an entry found here is by
        // definition selectable, already holds the Holder the ui state wants,
        // and cannot disagree with what the player sees.
        for (WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
            if (want.equals(keyOf(entry))) {
                state.setWorldType(entry);
                return;
            }
        }

        if (!complained && on) {
            complained = true;
            Octia.LOGGER.warn("Octia: {} is not among the world types on offer, so the switch "
                    + "cannot select it. The world will generate as ordinary terrain.", SKY.location());
        }
    }

    /** The registry key behind a world-type entry, or null for an unregistered one. */
    private static ResourceKey<WorldPreset> keyOf(WorldCreationUiState.WorldTypeEntry entry) {
        if (entry == null) {
            return null;
        }
        Optional<ResourceKey<WorldPreset>> key = entry.preset().unwrapKey();
        return key.orElse(null);
    }
}
