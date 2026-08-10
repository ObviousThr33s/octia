package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.world.OctiaWorldgen;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * MILESTONE 2 - the obelisk.
 *
 * <p>An obelisk is masonry, not machinery, and the tests that matter are about
 * what it must <em>not</em> do. It carries no core, so it must never enter the
 * moorings store; a landmark that moored itself would put positions in the
 * spine that no player built and no player can take apart.
 */
public class ObeliskGameTest implements FabricGameTest {

    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /** Ground under the drop point, deep enough for the plinth to sit on. */
    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    /** Runs the feature and hands back the plinth centre, or null if declined. */
    private static BlockPos place(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(true);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(GROUND);

        boolean built = OctiaWorldgen.obelisk().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                absolute,
                NoneFeatureConfiguration.INSTANCE));

        return built ? absolute.below(1) : null;
    }

    /** It stands, on a plinth, out of andesite. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theObeliskStandsOnAPlinth(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(base.offset(dx, 0, dz)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("the plinth has a gap at " + base.offset(dx, 0, dz));
                }
            }
        }

        if (!level.getBlockState(base.above(2)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            throw new AssertionError("no column above the plinth at " + base);
        }
        helper.succeed();
    }

    /** The shaft is unbroken from the plinth to wherever it ends. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theShaftHasNoHoles(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        int height = 0;
        while (level.getBlockState(base.above(height + 1)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            height++;
        }
        if (height < 2) {
            throw new AssertionError("the obelisk is " + height + " blocks tall; that is a kerbstone");
        }

        // Nothing floating above the break. A snapped obelisk is snapped once.
        for (int y = height + 1; y <= height + 4; y++) {
            if (level.getBlockState(base.above(y)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                throw new AssertionError("a panel is floating above the break at " + base.above(y));
            }
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> An obelisk is not a ship.
     *
     * <p>It has no core, so nothing about it should ever reach
     * {@code ShipMoorings}. Give it one - because a lit spire that also moored
     * would look like a feature rather than a mistake - and this fails.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anObeliskIsNotAShip(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        ShipMoorings moorings = ShipMoorings.get(level.getServer());

        for (BlockPos p : BlockPos.betweenClosed(base.offset(-6, -2, -6), base.offset(6, 12, 6))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ShipCoreBlock) {
                throw new AssertionError("an obelisk generated a ship core at " + p);
            }
            if (moorings.isMoored(p)) {
                throw new AssertionError("an obelisk put " + p + " into the moorings store");
            }
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoObelisk(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(false);

        ServerLevel level = helper.getLevel();
        boolean built = OctiaWorldgen.obelisk().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                helper.absolutePos(GROUND),
                NoneFeatureConfiguration.INSTANCE));

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("an obelisk generated in a world with Octia switched off");
        }
        helper.succeed();
    }
}
