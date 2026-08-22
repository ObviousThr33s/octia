package com.serenity.octia.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A wreck at the far end of a beamline.
 *
 * <p>{@link Beamline} says where a bend in the lattice throws its tangent and
 * how far along it the station sits. This is what puts something there, and the
 * something is an ordinary derelict - the same hull, dig and dressing
 * {@link DerelictFeature} builds anywhere else. What changes is only that the
 * site was chosen by the lattice rather than by a coin flip.
 *
 * <p><b>Why it matters that it is this ruin and not a new one.</b> An obelisk
 * lies along its leg with a slot bored down its length, and a station is exactly
 * where that line goes. Sight through the slot, walk, and there is a wreck. The
 * sighting has existed since the lattice landed with nothing at the end of it;
 * this is the end of it.
 *
 * <p><b>One chunk, one station, and that is what makes it safe.</b> A generation
 * worker may only write near its own chunk, so a feature cannot answer "there is
 * a station 300 blocks that way" by building there. Instead the chunk that
 * <em>contains</em> a station is the one that builds it - see
 * {@link Beamline#inChunk}. No two chunks can claim the same station, nothing
 * needs to remember what has been built, and every write stays where it belongs.
 *
 * <p><b>The placement modifiers do not choose the column here.</b> They only
 * bring this class a chunk; {@code derelict_station.json} asks once per chunk
 * with no rarity filter at all, because the rarity is in the lattice - a station
 * lands in about one chunk in 1,575, measured. The column is then the station's
 * own, dropped to the surface heightmap and handed to
 * {@link DerelictFeature#raise} exactly as the wild wreck's column is.
 *
 * <p>It yields to structures like the wild one does. A wreck at a station is
 * meant, but not enough to put it in somebody's village square - and the tangent
 * that pointed at it is still true whether or not the wreck could take the site.
 */
public class BeamlineDerelictFeature extends Feature<NoneFeatureConfiguration> {

    public BeamlineDerelictFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Same gate as every other Octia feature, and for the same reason: the
        // biome modification is per-launch, the switch is per-save, and the
        // moment of placement is the only honest place to apply it.
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Beamline.Station station = Beamline.inChunk(level.getSeed(),
                origin.getX() >> 4, origin.getZ() >> 4);
        if (station == null) {
            return false;
        }

        // WORLD_SURFACE_WG, matching the heightmap the wild derelict's placement
        // modifier uses. Everything below the surface is DerelictFeature's job:
        // it walks down through air and fluid to real ground and then sinks by
        // the wreck's age, and doing any of that here would be a second
        // definition of where a hull sits.
        int x = station.at().x();
        int z = station.at().z();
        BlockPos column = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);

        return DerelictFeature.raise(level, context.random(), column, true);
    }
}
