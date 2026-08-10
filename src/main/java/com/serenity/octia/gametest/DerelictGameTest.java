package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.ship.ShipStatus;
import com.serenity.octia.world.OctiaWorldgen;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * MILESTONE 2 - the derelict.
 *
 * <p>These place the feature directly rather than waiting for a chunk to roll
 * it, because a rarity filter of one in four hundred is not a thing a test can
 * wait for. What is under test is the feature's own contract: that what it
 * builds is a real hull by {@code ShipCoreBlock}'s rule, that its dig is inside
 * the call radius, and that it deliberately leaves the moorings store alone.
 */
public class DerelictGameTest implements FabricGameTest {

    /**
     * Where the feature is handed its surface.
     *
     * <p>y=3 leaves room for the floor beneath: the wreck sinks two blocks and
     * then refuses ground that is air one block further down, so there has to be
     * something under it and it cannot be the bottom of the box.
     */
    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /**
     * Solid ground around the drop point, four deep.
     *
     * <p>Kept to a five-block reach on purpose. Gametests are laid out thirteen
     * blocks apart and {@code helper.setBlock} does not bounds-check, so a
     * generous floor is not harmless - it writes into the next test's plot. Five
     * clears the derelict's own footprint and stops well short of the neighbour.
     */
    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    /**
     * Runs the feature at a spot in the test box and hands back the core's
     * position, or null if the feature declined to build there.
     *
     * <p>The floor is laid first: the feature refuses ground it would hang over,
     * which is correct behaviour and would otherwise make every test here a
     * false negative in an empty structure block.
     */
    private static BlockPos place(GameTestHelper helper, BlockPos where) {
        ServerLevel level = helper.getLevel();

        floor(helper, where);

        OctiaWorldgen.setActive(true);
        BlockPos absolute = helper.absolutePos(where);

        boolean built = OctiaWorldgen.derelict().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(1234L),
                absolute,
                NoneFeatureConfiguration.INSTANCE));

        if (!built) {
            return null;
        }

        // The feature sinks the hull relative to the floor it found, so the core
        // is not at the position it was handed. Find it rather than assume it.
        for (int dy = -6; dy <= 2; dy++) {
            BlockPos candidate = absolute.offset(0, dy, 0);
            if (level.getBlockState(candidate).getBlock() instanceof ShipCoreBlock) {
                return candidate;
            }
        }
        return null;
    }

    /** The wreck is a genuine hull, not scenery shaped like one. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void derelictBuildsARealHull(GameTestHelper helper) {
        BlockPos core = place(helper, GROUND);
        if (core == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        if (!ShipCoreBlock.hullIntact(helper.getLevel(), core, null)) {
            throw new AssertionError("a derelict generated without an intact hull at " + core
                    + " - it would read as scenery, not as a ship");
        }
        helper.succeed();
    }

    /**
     * The dig is inside the call radius, so the wreck answers the hook it was
     * built to illustrate. A derelict whose own site cannot call it would be the
     * mod contradicting itself in the terrain.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void derelictGeneratesItsOwnDig(GameTestHelper helper) {
        BlockPos core = place(helper, GROUND);
        if (core == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        if (!ShipCoreBlock.digSiteInRange(helper.getLevel(), core)) {
            throw new AssertionError("no dig within " + ShipCoreBlock.CALL_RADIUS
                    + " blocks of the derelict core at " + core);
        }

        if (helper.getLevel().getBlockState(core).getValue(ShipCoreBlock.STATUS) != ShipStatus.CALLED) {
            throw new AssertionError("the derelict has a hull and a dig but does not read CALLED");
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> The core is placed after its own evidence.
     *
     * <p>These tests run against a live {@code ServerLevel}, so every write here
     * fires {@code onPlace} and the core surveys itself the moment it lands -
     * exactly as it does under {@code /place feature}. Natural generation gets a
     * {@code WorldGenRegion} and no survey at all. The feature must produce the
     * same wreck either way, and it only does if the digs are in the ground
     * before the core is.
     *
     * <p>Reorder {@code place()} so the core goes down first and this fails: the
     * survey runs over undisturbed ground, answers MOORED, and a wreck built on
     * top of a dig site quietly stops claiming to have been called.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPlacedDerelictSurveysAsCalled(GameTestHelper helper) {
        BlockPos core = place(helper, GROUND);
        if (core == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        if (helper.getLevel().getBlockState(core).getValue(ShipCoreBlock.STATUS) != ShipStatus.CALLED) {
            throw new AssertionError("placed into a live level, the derelict at " + core
                    + " did not survey as CALLED - the core was written before its dig site");
        }
        if (!ShipMoorings.get(helper.getLevel().getServer()).isMoored(core)) {
            throw new AssertionError("a derelict placed into a live level should moor on placement, "
                    + "the same as any hand-placed core; " + core + " is absent from the store");
        }
        helper.succeed();
    }

    /** A survey is what enters it in the store. Discovery is registration. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void surveyingADerelictMoorsIt(GameTestHelper helper) {
        BlockPos core = place(helper, GROUND);
        if (core == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        helper.useBlock(helper.relativePos(core), helper.makeMockPlayer(GameType.SURVIVAL));

        if (!ShipMoorings.get(helper.getLevel().getServer()).isMoored(core)) {
            throw new AssertionError("right-clicking a derelict core did not moor " + core);
        }
        helper.succeed();
    }

    /** The switch reaches the terrain: a disabled save generates nothing. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoDerelict(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The shared floor, not a wider hand-rolled one: its note explains why
        // the reach is capped at five, and this test used seven.
        floor(helper, GROUND);

        OctiaWorldgen.setActive(false);
        boolean built = OctiaWorldgen.derelict().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(1234L),
                helper.absolutePos(GROUND),
                NoneFeatureConfiguration.INSTANCE));

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("a derelict generated in a world with Octia switched off");
        }
        helper.succeed();
    }

    /**
     * The ship is a cube, and erosion is not allowed to eat the ring.
     *
     * <p>The top course weathers at random, which is what keeps wrecks from
     * looking stamped from one die. The core's own slice is exempt, and this is
     * the test that says so: lose one panel from {@code dy == 0} and
     * {@code hullIntact} fails, the core reads ADRIFT, and the ruin has quietly
     * stopped being a ship. Widening the erosion roll to all courses is a
     * one-character change that would do exactly that.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theHullIsACubeAndTheRingSurvives(GameTestHelper helper) {
        BlockPos core = place(helper, GROUND);
        if (core == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();

        // The middle slice: all eight, no exceptions.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!level.getBlockState(core.offset(dx, 0, dz)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("erosion took " + core.offset(dx, 0, dz)
                            + " out of the core's ring - the derelict is no longer a hull");
                }
            }
        }

        // The buried course is untouched too, so the cube has a floor.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(core.offset(dx, -1, dz)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("the cube has a hole in its bottom course at "
                            + core.offset(dx, -1, dz));
                }
            }
        }
        helper.succeed();
    }

    /**
     * A dig is in the ground within the call radius. Not that it carries loot,
     * which is the half of the name this cannot reach.
     *
     * <p>Every suspicious gravel or sand block is an {@code EntityBlock}, so its
     * {@code BrushableBlockEntity} exists the instant the block is written -
     * {@code RuinGround.dig} depends on exactly that to set the loot table at
     * all. The {@code instanceof} below therefore follows from the block check
     * above it and can never be the thing that fails, which leaves this test
     * asserting what {@code derelictGeneratesItsOwnDig} already asserts through
     * {@code digSiteInRange}. The loot table itself is never read back.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theDigCarriesLoot(GameTestHelper helper) {
        BlockPos core = place(helper, GROUND);
        if (core == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        int radius = ShipCoreBlock.CALL_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(
                core.offset(-radius, -radius, -radius), core.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(p);
            if (!state.is(Blocks.SUSPICIOUS_GRAVEL) && !state.is(Blocks.SUSPICIOUS_SAND)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof BrushableBlockEntity) {
                helper.succeed();
                return;
            }
        }
        throw new AssertionError("the derelict's dig has no brushable block entity to loot");
    }
}
