package com.serenity.octia.gametest;

import java.util.Optional;

import com.serenity.octia.Octia;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.TemplateRuinFeature;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * MILESTONE 2 - ruins authored as templates.
 *
 * <p>The pipeline's whole reason for existing is that a template can be careless
 * and the mechanics stay exact, so these test the seam rather than the shape:
 * that a placed template still yields a real hull, that erosion cannot take the
 * instruction that produces it, and that the instruction itself never survives
 * into the world.
 */
public class TemplateRuinGameTest implements FabricGameTest {

    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /** The starter template shipped in data/octia/structure/. */
    private static final String TEMPLATE = "waystation";

    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    /**
     * Places the template at the given integrity and returns where it went.
     *
     * @param integrity 1.0 for whole, lower to rot that fraction away
     */
    private static BlockPos place(GameTestHelper helper, float integrity) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(true);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(GROUND);

        boolean built = OctiaWorldgen.templateRuin().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(4242L),
                absolute,
                new TemplateRuinFeature.Config(Octia.id(TEMPLATE), integrity)));

        return built ? absolute : null;
    }

    /** Finds the one ship core the template's marker should have produced. */
    private static BlockPos findCore(ServerLevel level, BlockPos middle) {
        for (BlockPos p : BlockPos.betweenClosed(middle.offset(-6, -4, -6), middle.offset(6, 6, 6))) {
            if (level.getBlockState(p).getBlock() instanceof ShipCoreBlock) {
                return p.immutable();
            }
        }
        return null;
    }

    /** The template resolves and lands. If this fails the .nbt is missing. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTemplatePlaces(GameTestHelper helper) {
        BlockPos at = place(helper, 1.0f);
        if (at == null) {
            throw new AssertionError("the template ruin declined ground it should have accepted, "
                    + "or data/octia/structure/" + TEMPLATE + ".nbt did not resolve");
        }
        helper.succeed();
    }

    /**
     * A DATA marker becomes a real hull, by ShipCoreBlock's own rule.
     *
     * <p>This is the seam the pipeline is built around: the template carries an
     * instruction, and the hexahedron is written in code afterwards.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theCoreMarkerBecomesAHull(GameTestHelper helper) {
        BlockPos at = place(helper, 1.0f);
        if (at == null) {
            throw new AssertionError("the template ruin did not place");
        }

        ServerLevel level = helper.getLevel();
        BlockPos core = findCore(level, at);
        if (core == null) {
            throw new AssertionError("the template's core marker produced no ship core");
        }
        if (!ShipCoreBlock.hullIntact(level, core, null)) {
            throw new AssertionError("the stamped hull at " + core + " is not intact");
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> Erosion cannot eat the marker.
     *
     * <p>{@code filterBlocks} runs the processor chain, so reading markers
     * through the same settings that erode would let the rot roll delete the
     * instruction along with the fabric - and the ruin would come out as plain
     * masonry, sometimes, on a dice roll nobody would think to inspect. The
     * feature keeps a second, unprocessed settings object for exactly this. At
     * an integrity this brutal a shared one would almost never survive.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void erosionCannotEatTheMarker(GameTestHelper helper) {
        BlockPos at = place(helper, 0.05f);
        if (at == null) {
            throw new AssertionError("the template ruin did not place");
        }

        ServerLevel level = helper.getLevel();
        BlockPos core = findCore(level, at);
        if (core == null) {
            throw new AssertionError("at integrity 0.05 the core marker was rotted away - "
                    + "markers are being read through the eroding settings");
        }
        if (!ShipCoreBlock.hullIntact(level, core, null)) {
            throw new AssertionError("the stamped hull at " + core
                    + " is not intact, so erosion reached the ring");
        }
        helper.succeed();
    }

    /**
     * No structure block survives into the world.
     *
     * <p>{@code placeInWorld} writes them, so a marker left alone is a visible
     * instruction standing inside the ruin it was describing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noMarkerIsLeftStanding(GameTestHelper helper) {
        BlockPos at = place(helper, 1.0f);
        if (at == null) {
            throw new AssertionError("the template ruin did not place");
        }

        ServerLevel level = helper.getLevel();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -4, -8), at.offset(8, 8, 8))) {
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.STRUCTURE_BLOCK)) {
                throw new AssertionError("a structure block marker is still standing at " + p);
            }
        }
        helper.succeed();
    }

    /** The template is made of the mod's own block, so it read the palette. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTemplateIsBuiltFromAndesite(GameTestHelper helper) {
        BlockPos at = place(helper, 1.0f);
        if (at == null) {
            throw new AssertionError("the template ruin did not place");
        }

        ServerLevel level = helper.getLevel();
        int panels = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -2, -6), at.offset(6, 6, 6))) {
            if (level.getBlockState(p).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                panels++;
            }
        }
        // The shell is around a hundred panels; the stamped cube alone is 26.
        if (panels < 40) {
            throw new AssertionError("only " + panels + " frame panels placed - "
                    + "the template palette did not come through");
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoTemplateRuin(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(false);

        ServerLevel level = helper.getLevel();
        boolean built = OctiaWorldgen.templateRuin().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(4242L),
                helper.absolutePos(GROUND),
                new TemplateRuinFeature.Config(Octia.id(TEMPLATE), 1.0f)));

        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("a template ruin generated with Octia switched off");
        }
        helper.succeed();
    }
}
