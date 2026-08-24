package com.serenity.octia.gametest;

import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.StairwayFeature;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

/**
 * GREENFIELD F2 - the switchback stairs.
 *
 * <p>A stairway has one job: it climbs, and every block a climber's foot lands
 * on is real. The tests here assert that decision - monotone climb, footing
 * under every step, solid landings, nothing floating, nothing leaking out of
 * the owning chunk - and never the particles around it.
 *
 * <p><b>These drive {@code StairwayFeature.raise} with axes the test chose.</b>
 * That is the {@code ArchFeature} license, quoted in its javadoc: where a site
 * is and what the shape looks like are different jobs, and the terrain gate
 * makes the shape untestable through {@code place}. Nothing here reads or
 * hard-codes a lattice heading - the axes below are the test's own, not the
 * world's. The two tests that do go through {@code place} are the ones about
 * the gates themselves: flat ground and the per-save switch.
 *
 * <p>The cliff the plot builds is sized so the survey passes by construction
 * at two flights: the face starts two blocks into the riser and the outer
 * flight's deepest hang off the ground is exactly the underpin. The floor is
 * kept to the {@code ObeliskGameTest} scale - a generous floor writes into the
 * next test.
 */
public class StairwayGameTest implements FabricGameTest {

    /** The foot of the stair: the free cell approaching feet stand in. */
    private static final BlockPos FOOT = new BlockPos(4, 3, 4);

    private static final Direction[] CARDINALS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * A cliff for the stair to climb: a 13x13 floor at the plot base, and a
     * face - every column two or more blocks toward {@code up} from the foot,
     * filled to ten above it. Stone rather than gravel throughout, so no
     * shoring lintel fires and every andesite block found afterwards is one
     * the geometry asked for.
     *
     * <p>With {@code drop}, the columns past the first landing's bay are cut
     * to nothing - the drop at the plot edge that the void-edge test needs.
     */
    private static void cliff(GameTestHelper helper, Direction up, Direction run0,
                              boolean drop) {
        int bay = StairwayFeature.flightSteps();
        for (int a = -6; a <= 6; a++) {
            for (int d = -6; d <= 6; d++) {
                BlockPos column = FOOT.relative(run0, a).relative(up, d);
                if (drop && a > bay) {
                    for (int dy = -4; dy <= 10; dy++) {
                        helper.setBlock(column.offset(0, dy, 0), Blocks.AIR);
                    }
                    continue;
                }
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(column.offset(0, dy, 0), Blocks.STONE);
                }
                if (d >= 2) {
                    for (int dy = 0; dy <= 10; dy++) {
                        helper.setBlock(column.offset(0, dy, 0), Blocks.STONE);
                    }
                }
            }
        }
    }

    /** Flat ground only - what the acceptance must refuse. */
    private static void flat(GameTestHelper helper) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(FOOT.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
    }

    /** Builds the cliff, drives {@code raise}, and hands back the anchor. */
    private static BlockPos raise(GameTestHelper helper, Direction up, Direction run0,
                                  int flights, boolean worn, boolean drop) {
        cliff(helper, up, run0, drop);
        OctiaWorldgen.setActive(true);

        BlockPos anchor = helper.absolutePos(FOOT);
        if (!StairwayFeature.raise(helper.getLevel(), anchor, up, run0, flights, worn)) {
            throw new AssertionError("the stairway declined ground it should have accepted");
        }
        return anchor;
    }

    /** Drives the full feature at the foot, the {@code ObeliskGameTest} idiom. */
    private static boolean place(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return OctiaWorldgen.stairway().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                helper.absolutePos(FOOT),
                NoneFeatureConfiguration.INSTANCE));
    }

    /**
     * <b>The climb is the decision.</b> Every step is a stair block exactly one
     * higher than the one before it, facing the way you walk while climbing -
     * a stairway that ever steps flat or down has failed its one job.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStairsClimbMonotonically(GameTestHelper helper) {
        Direction up = Direction.EAST;
        Direction run0 = up.getClockWise();
        BlockPos anchor = raise(helper, up, run0, 2, false, false);
        ServerLevel level = helper.getLevel();

        int steps = StairwayFeature.flightSteps();
        for (int k = 0; k < 2; k++) {
            Direction travel = (k % 2 == 0) ? run0 : run0.getOpposite();
            for (int i = 0; i < steps; i++) {
                BlockPos pos = StairwayFeature.stepAt(anchor, up, run0, k, i);
                if (pos.getY() != anchor.getY() + k * steps + i) {
                    throw new AssertionError("step " + k + "," + i + " sits at y="
                            + pos.getY() + "; the climb must be one block per step");
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.ANDESITE_STAIRS)) {
                    throw new AssertionError("no stair at " + pos);
                }
                if (state.getValue(StairBlock.FACING) != travel) {
                    throw new AssertionError("the stair at " + pos + " faces "
                            + state.getValue(StairBlock.FACING)
                            + "; climbing you face " + travel);
                }
            }
        }
        helper.succeed();
    }

    /** Footing law, per step, no exceptions - the underpin wrote what the terrain did not. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyStepRestsOnSolid(GameTestHelper helper) {
        Direction up = Direction.EAST;
        Direction run0 = up.getClockWise();
        BlockPos anchor = raise(helper, up, run0, 2, false, false);
        ServerLevel level = helper.getLevel();

        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < StairwayFeature.flightSteps(); i++) {
                BlockPos below = StairwayFeature.stepAt(anchor, up, run0, k, i).below();
                if (level.getBlockState(below).isAir()) {
                    throw new AssertionError("a step hangs over air at " + below);
                }
                if (!level.getFluidState(below).is(Fluids.EMPTY)) {
                    throw new AssertionError("a step stands in fluid at " + below);
                }
            }
        }
        helper.succeed();
    }

    /** The turn is walkable and there is no gap at the bay. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theLandingsAreSolidAndLevel(GameTestHelper helper) {
        Direction up = Direction.EAST;
        Direction run0 = up.getClockWise();
        BlockPos anchor = raise(helper, up, run0, 2, false, false);
        ServerLevel level = helper.getLevel();

        int steps = StairwayFeature.flightSteps();
        for (int k = 0; k < 2; k++) {
            for (int c = 0; c <= 2; c++) {
                BlockPos pos = StairwayFeature.landingAt(anchor, up, run0, k, c);
                if (pos.getY() != anchor.getY() + (k + 1) * steps - 1) {
                    throw new AssertionError("landing " + k + " cell " + c
                            + " sits at y=" + pos.getY() + "; it must be level with its flight");
                }
                if (!level.getBlockState(pos).is(Blocks.ANDESITE)) {
                    throw new AssertionError("the landing has a gap at " + pos);
                }
            }
        }
        helper.succeed();
    }

    /**
     * Writes stayed inside the plan and nothing hangs. Everything andesite in
     * the write box plus a one-block shell is inside the box proper, and every
     * such block has something under it.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void nothingFloatsAndNothingLeaks(GameTestHelper helper) {
        Direction up = Direction.EAST;
        Direction run0 = up.getClockWise();
        BlockPos anchor = raise(helper, up, run0, 2, false, false);
        ServerLevel level = helper.getLevel();

        BoundingBox box = StairwayFeature.writeBox(anchor, up, run0, 2);
        BlockPos min = new BlockPos(box.minX() - 1, box.minY() - 1, box.minZ() - 1);
        BlockPos max = new BlockPos(box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);

        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(p);
            if (!state.is(Blocks.ANDESITE) && !state.is(Blocks.ANDESITE_STAIRS)
                    && !state.is(Blocks.ANDESITE_SLAB)) {
                continue;
            }
            if (!box.isInside(p)) {
                throw new AssertionError("a write leaked outside the plan at " + p.immutable());
            }
            if (level.getBlockState(p.below()).isAir()) {
                throw new AssertionError("masonry floats over air at " + p.immutable());
            }
        }
        helper.succeed();
    }

    /**
     * <b>Containment is arithmetic; prove the arithmetic.</b> No writes here -
     * for corner-extreme origins in a positive and a negative chunk, across
     * every uphill axis and both runs, the clamped write box at full height
     * never leaves the origin's chunk columns.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theStairwayKeepsToItsChunk(GameTestHelper helper) {
        int flights = StairwayFeature.maxFlights();
        int[][] chunks = {{3, 5}, {-4, -7}};
        int[] corners = {0, 15};

        for (int[] chunk : chunks) {
            int minX = SectionPos.sectionToBlockCoord(chunk[0]);
            int minZ = SectionPos.sectionToBlockCoord(chunk[1]);
            for (int corner : corners) {
                BlockPos origin = new BlockPos(minX + corner, 64, minZ + corner);
                for (Direction up : CARDINALS) {
                    for (Direction run0 : new Direction[] {
                            up.getClockWise(), up.getCounterClockWise()}) {
                        BlockPos anchor = StairwayFeature.anchorFor(origin, up, run0, flights);
                        BoundingBox box = StairwayFeature.writeBox(anchor, up, run0, flights);
                        if (SectionPos.blockToSectionCoord(box.minX()) != chunk[0]
                                || SectionPos.blockToSectionCoord(box.maxX()) != chunk[0]
                                || SectionPos.blockToSectionCoord(box.minZ()) != chunk[1]
                                || SectionPos.blockToSectionCoord(box.maxZ()) != chunk[1]) {
                            throw new AssertionError("the write box left chunk "
                                    + chunk[0] + "," + chunk[1] + " for origin " + origin
                                    + ", up " + up + ", run " + run0);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Acceptance is the real gate: no riser, no stair. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void flatGroundGrowsNoStairs(GameTestHelper helper) {
        flat(helper);
        OctiaWorldgen.setActive(true);

        if (place(helper)) {
            throw new AssertionError("a stairway grew on flat ground");
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoStairway(GameTestHelper helper) {
        cliff(helper, Direction.EAST, Direction.SOUTH, false);
        OctiaWorldgen.setActive(false);

        boolean built = place(helper);

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("a stairway generated in a world with Octia switched off");
        }
        helper.succeed();
    }

    /**
     * <b>Wear truncates height, never soundness.</b> A worn stub of one flight
     * still climbs on whole stairs and still ends on a landing; only the far
     * lip crumbles to a slab, and even the slab has solid under it.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aWornStubStillEndsOnALanding(GameTestHelper helper) {
        Direction up = Direction.SOUTH;
        Direction run0 = up.getClockWise();
        BlockPos anchor = raise(helper, up, run0, 1, true, false);
        ServerLevel level = helper.getLevel();

        for (int i = 0; i < StairwayFeature.flightSteps(); i++) {
            BlockPos pos = StairwayFeature.stepAt(anchor, up, run0, 0, i);
            if (!level.getBlockState(pos).is(Blocks.ANDESITE_STAIRS)) {
                throw new AssertionError("the worn stub is missing a stair at " + pos);
            }
        }
        for (int c = 0; c <= 1; c++) {
            BlockPos pos = StairwayFeature.landingAt(anchor, up, run0, 0, c);
            if (!level.getBlockState(pos).is(Blocks.ANDESITE)) {
                throw new AssertionError("the stub's landing has a gap at " + pos);
            }
        }
        BlockPos lip = StairwayFeature.landingAt(anchor, up, run0, 0, 2);
        if (!level.getBlockState(lip).is(Blocks.ANDESITE_SLAB)) {
            throw new AssertionError("the worn lip at " + lip + " is not a slab");
        }
        if (level.getBlockState(lip.below()).isAir()) {
            throw new AssertionError("the worn lip hangs over air at " + lip.below());
        }
        helper.succeed();
    }

    /**
     * <b>The landing at the edge of nothing is solid or the site refuses.</b>
     * With the ground cut away past the bay, every landing cell stands on
     * masonry all the way down to footing - no air anywhere in the column,
     * because a glider arrives on exactly this block.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aVoidEdgeLandingIsSolid(GameTestHelper helper) {
        Direction up = Direction.EAST;
        Direction run0 = up.getCounterClockWise();
        BlockPos anchor = raise(helper, up, run0, 2, false, true);
        ServerLevel level = helper.getLevel();

        int reach = StairwayFeature.underpin() + 1;
        for (int k = 0; k < 2; k++) {
            for (int c = 0; c <= 2; c++) {
                BlockPos p = StairwayFeature.landingAt(anchor, up, run0, k, c).below();
                for (int depth = 0; depth < reach; depth++) {
                    BlockState state = level.getBlockState(p);
                    if (state.isAir()) {
                        throw new AssertionError("air under a landing at " + p);
                    }
                    if (!state.is(Blocks.ANDESITE)) {
                        break;
                    }
                    p = p.below();
                }
            }
        }
        helper.succeed();
    }
}
