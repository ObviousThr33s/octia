package com.serenity.octia.world;

import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * What Octia adds to the terrain, and the one honest place the world switch can
 * still be applied once it does.
 *
 * <p><b>The problem this class exists to solve.</b> {@code OctiaBeacon} raises a
 * mast at spawn instead of generating anything, and its note says why: Fabric's
 * biome modifications are registered once per game launch and apply to every
 * world that launch opens, so they cannot be gated on a per-save flag. That is
 * still true. What is also true, and is the way through, is that a biome
 * modification only ever schedules a <em>feature</em> - and the feature runs with
 * a level in hand and can decline. So the modification is global and the
 * placement is conditional, which produces exactly the per-save behaviour the
 * switch promises without pretending the registration is per-save.
 *
 * <p><b>Why the flag is cached rather than read.</b> The obvious implementation
 * is for the feature to ask {@code OctiaWorldOption.get(server)} directly. That
 * is a {@code SavedData} lookup against {@code DimensionDataStorage}, which is a
 * plain map owned by the server thread - and features run on chunk-generation
 * worker threads, several at once. Reading it there is a data race that would
 * surface as an intermittent crash under exactly the conditions nobody tests: a
 * fresh world generating many chunks at speed. So the server thread publishes
 * the answer once, at world load, through a volatile, and the workers only read.
 *
 * <p>The flag is cleared on server stop so that a second world opened in the
 * same launch cannot inherit the first one's answer. That is the bug this shape
 * would otherwise introduce, and it is the reason the reset is not optional.
 */
public final class OctiaWorldgen {

    /**
     * Registry path for both the feature type and the two worldgen JSON files
     * that configure and place it. They must agree; this constant is why they do.
     */
    private static final String DERELICT = "derelict";

    /**
     * Written by the server thread at world load, read by generation workers.
     * Volatile rather than synchronised: it is one boolean, written rarely and
     * read constantly, and the only guarantee needed is visibility.
     */
    private static volatile boolean active;

    /** Held from registration so nothing has to cast it back out of the registry. */
    private static DerelictFeature derelict;

    private OctiaWorldgen() {
    }

    /** Registers the feature type and schedules it into Overworld biomes. */
    public static void bootstrap() {
        derelict = Registry.register(BuiltInRegistries.FEATURE, Octia.id(DERELICT),
                new DerelictFeature(NoneFeatureConfiguration.CODEC));

        // SURFACE_STRUCTURES rather than a later step: the derelict sits on the
        // ground and wants to be there before grass, flowers and trees decorate
        // over it, so the wreck looks weathered into the landscape rather than
        // dropped on top of it.
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                ResourceKey.create(Registries.PLACED_FEATURE, Octia.id(DERELICT)));
    }

    /**
     * Publishes whether this save wants Octia's terrain. Server thread only,
     * called once per world load before any chunk can generate.
     */
    public static void setActive(boolean value) {
        active = value;
    }

    /** Whether the currently open save wants Octia's terrain. Safe off-thread. */
    public static boolean active() {
        return active;
    }

    /** The registered feature itself, for tests that place one directly. */
    public static DerelictFeature derelict() {
        return derelict;
    }

    /** The placed feature key, for {@code /place feature} and for tests. */
    public static ResourceKey<PlacedFeature> placedKey() {
        return ResourceKey.create(Registries.PLACED_FEATURE, Octia.id(DERELICT));
    }
}
