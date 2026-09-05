package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.lightwell.LightwellPlan;
import com.serenity.octia.world.LightwellFeature;
import com.serenity.octia.world.OctiaWorldgen;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The lightwell, in the ground.
 *
 * <p>{@code LightwellPlanTest} already proves the geometry - that the shaft is
 * clear, that nothing is placed twice, that the count is the count. None of
 * that is repeated here. What a headless unit test cannot say is whether the
 * earth actually moved, and that is the whole subject of this file: a well that
 * lays every block correctly inside a solid hillside is not a well, it is a
 * fossil.
 *
 * <p>So the tests below are about the hole. Is the shaft open sky to floor. Is
 * a terrace something a person could stand on. And - the one that matters most,
 * because this is the only feature in the mod that removes blocks - does it
 * refuse ground it should refuse, rather than cutting a twenty-eight block pit
 * through somebody's village.
 */
public class LightwellGameTest implements FabricGameTest {

    /**
     * Where the mouth opens. High enough in the plot that the whole well fits
     * beneath it, since a truncated well would fail these tests for the wrong
     * reason.
     */
    private static final BlockPos GRADE = new BlockPos(9, 40, 9);

    /** Solid stone for the well to be cut out of, wider and deeper than it is. */
    private static void hillside(GameTestHelper helper, BlockPos grade) {
        LightwellPlan plan = LightwellFeature.plan();
        int reach = LightwellFeature.reach() + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -1; dy >= -(plan.depth() + 4); dy--) {
                    helper.setBlock(grade.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
    }

    /** Cuts a well and hands back the grade position, or null if it declined. */
    private static BlockPos cut(GameTestHelper helper) {
        hillside(helper, GRADE);
        OctiaWorldgen.setActive(true);
        BlockPos grade = helper.absolutePos(GRADE);
        return LightwellFeature.sink(helper.getLevel(), grade) ? grade : null;
    }

    /**
     * <b>The claim the whole structure exists to make.</b> The shaft is open
     * from the mouth to the apex floor. If a single block stands in it, every
     * terrace below that block is a cellar and the form has failed.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theShaftIsOpenFromGradeToTheApex(GameTestHelper helper) {
        BlockPos grade = cut(helper);
        if (grade == null) {
            throw new AssertionError("the well declined ground it should have taken");
        }

        ServerLevel level = helper.getLevel();
        LightwellPlan plan = LightwellFeature.plan();
        int shaftHalf = (plan.shaft() - 1) / 2;
        int apexFloor = plan.floorY(plan.levels() - 1);

        for (int y = -1; y > apexFloor; y--) {
            for (int x = -shaftHalf; x <= shaftHalf; x++) {
                for (int z = -shaftHalf; z <= shaftHalf; z++) {
                    // The anchorage stands on the apex floor, in the middle of
                    // the shaft, and is the one thing that belongs there - it is
                    // the bottom of the well, not an obstruction in it.
                    // LightwellPlanTest.oneCoreAtTheApex pins that position, and
                    // its own shaft-is-clear test permits exactly this block for
                    // the same reason. Anything ELSE here is a real blockage.
                    if (y == apexFloor + 1 && x == 0 && z == 0) {
                        continue;
                    }
                    BlockPos pos = grade.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        throw new AssertionError("the shaft is blocked at " + pos
                                + " by " + level.getBlockState(pos));
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * <b>The earth actually moved.</b> Every terrace has headroom over it, which
     * is the difference between a well and a diagram of one. Checked at the
     * outer edge of each ring, because that is the last place the excavation
     * reaches and the first place a bad loop bound would leave stone.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyTerraceIsHollowedOut(GameTestHelper helper) {
        BlockPos grade = cut(helper);
        if (grade == null) {
            throw new AssertionError("the well declined ground it should have taken");
        }

        ServerLevel level = helper.getLevel();
        LightwellPlan plan = LightwellFeature.plan();

        for (int i = 0; i < plan.levels(); i++) {
            if (plan.ringWidth(i) == 0) {
                continue; // the apex is floor, not gallery
            }
            int inner = (plan.shaft() - 1) / 2 + 1;
            int outer = plan.outerHalf(i) - 1;
            int floor = plan.floorY(i);

            for (int y = floor + 2; y < floor + plan.storey(); y++) {
                for (int d = inner; d <= outer; d++) {
                    BlockPos pos = grade.offset(d, y, 0);
                    if (!level.getBlockState(pos).isAir()) {
                        throw new AssertionError("level " + i + " is not hollowed at " + pos
                                + "; found " + level.getBlockState(pos));
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Every terrace has a floor under it, or it is a shelf nobody can stand on. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyTerraceHasAFloor(GameTestHelper helper) {
        BlockPos grade = cut(helper);
        if (grade == null) {
            throw new AssertionError("the well declined ground it should have taken");
        }

        ServerLevel level = helper.getLevel();
        LightwellPlan plan = LightwellFeature.plan();

        for (int i = 0; i < plan.levels(); i++) {
            if (plan.ringWidth(i) == 0) {
                continue;
            }
            int d = (plan.shaft() - 1) / 2 + 1;
            BlockPos pos = grade.offset(d, plan.floorY(i), 0);
            if (!level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                throw new AssertionError("level " + i + " has no floor at " + pos);
            }
        }
        helper.succeed();
    }

    /** The anchorage is at the bottom of the well, where every level can see it. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theAnchorageStandsAtTheApex(GameTestHelper helper) {
        BlockPos grade = cut(helper);
        if (grade == null) {
            throw new AssertionError("the well declined ground it should have taken");
        }

        LightwellPlan plan = LightwellFeature.plan();
        BlockPos core = grade.offset(0, plan.floorY(plan.levels() - 1) + 1, 0);
        if (!helper.getLevel().getBlockState(core).is(OctiaBlocks.SHIP_CORE)) {
            throw new AssertionError("no anchorage at the bottom of the well, at " + core);
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing refusal.</b> This is the only feature in the mod that
     * removes blocks, so the thing worth defending is what it will not do. Open
     * air under the rim is a cliff edge or a lake, and a well cut there opens
     * out of its own side or fills with water.
     *
     * <p>Nothing is built first: the point is that the refusal happens before a
     * single block moves, so the plot is asserted untouched afterwards.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aWellRefusesGroundItCannotStandOn(GameTestHelper helper) {
        OctiaWorldgen.setActive(true);
        BlockPos grade = helper.absolutePos(GRADE);

        // A footing with a hole in it: solid everywhere but one corner of the rim.
        hillside(helper, GRADE);
        helper.setBlock(GRADE.offset(LightwellFeature.reach(), -1, LightwellFeature.reach()),
                Blocks.AIR);

        if (LightwellFeature.sink(helper.getLevel(), grade)) {
            throw new AssertionError("the well cut into ground that was not there");
        }
        // And it stopped before it dug: the middle of the plot is still stone.
        if (!helper.getLevel().getBlockState(grade.below(1)).is(Blocks.STONE)) {
            throw new AssertionError("the well began excavating and then refused");
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldCutsNoWell(GameTestHelper helper) {
        hillside(helper, GRADE);
        OctiaWorldgen.setActive(false);

        BlockPos grade = helper.absolutePos(GRADE);
        boolean built = OctiaWorldgen.lightwell().place(
                new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                        java.util.Optional.empty(),
                        helper.getLevel(),
                        helper.getLevel().getChunkSource().getGenerator(),
                        net.minecraft.util.RandomSource.create(1129L),
                        grade,
                        net.minecraft.world.level.levelgen.feature.configurations
                                .NoneFeatureConfiguration.INSTANCE));

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("a well was cut in a world with Octia switched off");
        }
        helper.succeed();
    }
}
