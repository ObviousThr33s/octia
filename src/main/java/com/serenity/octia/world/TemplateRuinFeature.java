package com.serenity.octia.world;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.serenity.octia.Octia;
import com.serenity.octia.ship.ShipCoreBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A ruin authored in the world and saved to an {@code .nbt}, placed as terrain.
 *
 * <p><b>Why this exists.</b> The derelict and the obelisk are shapes written in
 * Java, and every new one is more Java. This is the other route: build a ruin
 * in game with blocks, save it with a Structure Block, drop the file in
 * {@code data/octia/structure/}, and it generates. Level design stops being a
 * programming task, which for a world-building game is the correct direction.
 *
 * <p><b>The core is stamped in code, and that is the whole trick.</b> Erosion is
 * a {@link BlockRotProcessor}, which deletes blocks at random - exactly what a
 * ruin wants, and exactly what a hull must not have. {@link ShipCoreBlock#hullIntact}
 * needs all eight of the core's horizontal neighbours; lose one to the rot roll
 * and the ruin quietly stops being a ship. So a template does not contain a
 * core. It contains a <b>DATA marker</b> saying where one goes, and the
 * hexahedron is written afterwards by {@link DerelictFeature#stamp}, out of
 * reach of the processor. The template author gets to be careless with a
 * chisel; the mechanics stay exact.
 *
 * <p><b>Markers are read from an unprocessed copy of the settings.</b> Vanilla's
 * {@code filterBlocks} runs the processor chain, so asking the placed settings
 * for markers would let the rot roll delete the instruction along with the
 * fabric - and a ruin that was supposed to carry a hull would come out as
 * masonry, sometimes, depending on a dice roll nobody would think to look at.
 * Two settings objects: one that erodes and places, one that only rotates and
 * is asked where things are.
 *
 * <p>The structure blocks themselves are placed into the world by
 * {@code placeInWorld} and must be cleared afterwards, or every ruin contains a
 * floating instruction to itself.
 */
public class TemplateRuinFeature extends Feature<TemplateRuinFeature.Config> {

    /** Metadata on the one marker that means "a ship was here". */
    private static final String MARKER_CORE = "core";

    /** How far the template sinks below the surface it was dropped on. */
    private static final int SINK = 1;

    /** Digs ring the ruin, outside its footprint and inside the call radius. */
    private static final int DIG_MIN = 2;
    private static final int DIG_MAX = 5;

    /**
     * What a template ruin needs to know, from the configured feature JSON.
     *
     * @param template  the {@code .nbt} under {@code data/<ns>/structure/}
     * @param integrity 1.0 places the template whole; lower rots that fraction
     *                  away at random. Markers are exempt - see the class note.
     */
    public record Config(ResourceLocation template, float integrity) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("template").forGetter(Config::template),
                Codec.floatRange(0.0f, 1.0f).optionalFieldOf("integrity", 1.0f).forGetter(Config::integrity)
        ).apply(instance, Config::new));
    }

    public TemplateRuinFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();

        Optional<StructureTemplate> found =
                level.getLevel().getStructureManager().get(config.template());
        if (found.isEmpty()) {
            // Loud, because the alternative is a feature that silently never
            // generates and a datapack path nobody rechecks.
            Octia.LOGGER.warn("Octia: no structure template {} - nothing placed.", config.template());
            return false;
        }
        StructureTemplate template = found.get();

        Rotation rotation = Rotation.getRandom(random);
        Mirror mirror = random.nextBoolean() ? Mirror.NONE : Mirror.FRONT_BACK;

        // Two settings from one shape. `shape` rotates and nothing else, and is
        // what the markers are read through. `placed` also erodes.
        StructurePlaceSettings shape = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(true);

        StructurePlaceSettings placed = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(true)
                .setRandom(random);
        if (config.integrity() < 1.0f) {
            placed.addProcessor(new BlockRotProcessor(config.integrity()));
        }

        // placeInWorld takes the corner, not the middle. Centre the footprint on
        // the origin the placement modifier chose, or every ruin sits offset to
        // the north-west of the spot that was actually surveyed for it.
        Vec3i size = template.getSize(rotation);
        BlockPos corner = context.origin()
                .offset(-size.getX() / 2, -SINK, -size.getZ() / 2);
        BlockPos middle = corner.offset(size.getX() / 2, 0, size.getZ() / 2);

        int reach = Math.max(size.getX(), size.getZ()) / 2;
        if (!RuinGround.hasFooting(level, corner.below(1), reach)) {
            return false;
        }
        if (!RuinGround.isDry(level, middle, reach, 1, size.getY())) {
            return false;
        }

        if (!template.placeInWorld(level, corner, corner, placed, random, 2)) {
            return false;
        }

        markers(level, random, template, shape, corner, middle);

        // After the markers, so any core stamped by one is standing before
        // Habitation looks for hull rings to keep out of.
        Habitation.dress(level, random, middle, RuinAge.roll(random));

        // Recorded under the template's own path, so a save that ships three
        // authored ruins can be asked about each of them separately without
        // anyone editing an enum.
        RuinRegistry.report(level.getLevel(), config.template().getPath(), middle);
        return true;
    }

    /**
     * Acts on every DATA marker, then clears them all.
     *
     * <p>Clearing is not tidying. {@code placeInWorld} writes the structure
     * blocks into the world, so a marker left alone is a visible instruction
     * standing inside the ruin it was describing.
     */
    private static void markers(WorldGenLevel level, RandomSource random, StructureTemplate template,
                                StructurePlaceSettings shape, BlockPos corner, BlockPos middle) {
        List<StructureTemplate.StructureBlockInfo> found =
                template.filterBlocks(corner, shape, Blocks.STRUCTURE_BLOCK);

        for (StructureTemplate.StructureBlockInfo info : found) {
            String metadata = info.nbt() == null ? "" : info.nbt().getString("metadata");
            String mode = info.nbt() == null ? "" : info.nbt().getString("mode");

            // Clear first. Whatever the marker asked for is written over the top
            // of air, and an unrecognised one leaves no trace of itself.
            RuinGround.put(level, info.pos(), Blocks.AIR.defaultBlockState());

            if (!"DATA".equals(mode) || !MARKER_CORE.equals(metadata)) {
                continue;
            }

            // Digs before the core, for the reason DerelictFeature spells out:
            // against a live level the core surveys itself the instant it lands,
            // so the evidence has to be in the ground first or /place and
            // natural generation disagree about whether the ship was called.
            int digs = RuinGround.dig(level, random, info.pos(), DIG_MIN, DIG_MAX, 3 + random.nextInt(3));
            DerelictFeature.stamp(level, random, info.pos(), digs > 0);
        }

        if (found.isEmpty()) {
            // Not an error. A template with no marker is masonry, like the
            // obelisk, and masonry is a legitimate kind of ruin.
            Octia.LOGGER.debug("Octia: template ruin at {} carries no core marker.", middle);
        }
    }
}
