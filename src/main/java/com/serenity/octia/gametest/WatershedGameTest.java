package com.serenity.octia.gametest;

import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.Sightlines;
import com.serenity.octia.world.Watershed;
import com.serenity.octia.world.WatershedFeature;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The watershed: the gate and the body, tested through different doors.
 *
 * <p><b>Why two doors.</b> Where the springs are is a function of the world
 * seed, and a gametest cannot choose one - the {@code BeamlineDerelictGameTest}
 * argument. So the gate is tested as {@code place()}, which must decline a
 * plot the lattice opened no spring under, and the body is tested through
 * {@link WatershedFeature#carve}, which can be offered a course directly.
 * The arithmetic half of the soul - determinism, the uphill rule, the
 * bounded fall - is {@code WatershedTest}, where it costs milliseconds.
 *
 * <p><b>Nothing here hard-codes a heading.</b> Every carve call reads the
 * flow off the leg under the plot, the way the feature itself does, so the
 * course runs a different way on every seed and the asserts have to hold on
 * all of them. The legs argument is fixed at 2 - a budget is a body input,
 * not a heading.
 *
 * <p><b>The floor is the test fixture and the hazard at once.</b> Gravel at
 * radius 5, the BeamlineDerelictGameTest floor, and deliberately one ring
 * under the obelisk's 6 even though 6 is the stated ceiling: plots are
 * thirteen apart, so two radius-6 floors from neighbouring plots sit flush
 * with no air between them - and unlike the obelisk, this feature walks.
 * A trough offered a bridge of gravel would cross the seam and carve its
 * basin in the next test's plot, which is exactly the "generous floor"
 * failure the ceiling exists to prevent. At radius 5 there is guaranteed
 * void beyond the edge whatever the neighbour laid, the walk EDGE-stops at
 * offset 6, and every write stays inside offset 5. The plot-floor edge IS
 * the void edge, so the invariant under test - no source on an open edge -
 * is also what keeps the build inside the plot.
 */
public class WatershedGameTest implements FabricGameTest {

    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /** Ground under the drop point, deep enough for a basin's liner to sit in. */
    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    /** The flow at the plot, read the way the feature reads it - never assumed. */
    private static Sightlines.Heading flowAt(GameTestHelper helper, BlockPos absolute) {
        return Sightlines.legAt(helper.getLevel().getSeed(),
                absolute.getX(), absolute.getZ()).heading();
    }

    /** Solid in the seal's sense: not air, and holding no fluid. */
    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).isAir()
                && level.getFluidState(pos).is(Fluids.EMPTY);
    }

    /** Every fluid cell over the plot floor, in a fixed scan order. */
    private static List<BlockPos> waterCells(GameTestHelper helper) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = -2; x <= 10; x++) {
            for (int z = -2; z <= 10; z++) {
                for (int y = 0; y <= 6; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    if (!helper.getLevel().getFluidState(pos).isEmpty()) {
                        cells.add(pos);
                    }
                }
            }
        }
        return cells;
    }

    /** Runs the body at the plot centre with the seed's own flow and a budget of 2. */
    private static void carve(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(true);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(GROUND);
        if (!WatershedFeature.carve(level, absolute, flowAt(helper, absolute), 2)) {
            // The degenerate-course rule makes a flat 11x11 always buildable,
            // so false here is not "no site" - it is a refusal of ground the
            // survey should have accepted.
            throw new AssertionError("the carve declined ground it should have accepted");
        }
    }

    /**
     * The soul's gate gates. On a plot whose cell opened no spring, the
     * feature must decline before it reads a block; on one that did, the
     * course may outgrow the plot, so the body is not asserted here - it has
     * its own doors below. The odds of each branch are honestly about 1/2,
     * rather than the station test's 1/1575, and the taken branch is decided
     * by the seed, not by this test.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSpringGateGates(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(true);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(GROUND);
        int legs = Watershed.fallLegs(level.getSeed(),
                Sightlines.cell(absolute.getX()), Sightlines.cell(absolute.getZ()));

        if (legs != 0) {
            // A spring is open under this plot. Said out loud rather than
            // silently passed over.
            helper.succeed();
            return;
        }

        boolean built = OctiaWorldgen.watershed().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                absolute,
                NoneFeatureConfiguration.INSTANCE));
        if (built) {
            throw new AssertionError("built where the lattice opened no spring");
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoWatershed(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(false);

        ServerLevel level = helper.getLevel();
        boolean built = OctiaWorldgen.watershed().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                helper.absolutePos(GROUND),
                NoneFeatureConfiguration.INSTANCE));

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("a watershed generated in a world with Octia switched off");
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> Every water cell is a still source in a
     * sealed bowl: solid below, solid or same-level source on all four sides,
     * and it stays exactly where it was put. The static half walks the seal
     * cell by cell; the dynamic half rides the live level for free - the
     * carve ran on a ServerLevel, where setBlock at flag 2 still schedules
     * fluid ticks (the {@code RuinGround.put} javadoc), so ten ticks later
     * the water set must be unchanged and nothing may be flowing. That
     * asserts "cannot spread" as a decision, not a particle.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyPoolIsSealed(GameTestHelper helper) {
        carve(helper);

        ServerLevel level = helper.getLevel();
        List<BlockPos> before = waterCells(helper);
        for (BlockPos pos : before) {
            if (!level.getFluidState(pos).isSource()) {
                throw new AssertionError("a water cell is not a source at " + pos);
            }
            if (!solid(level, pos.below())) {
                throw new AssertionError("a source stands on nothing at " + pos);
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                // Solid, or another source at the same Y - a horizontal
                // neighbour shares the Y by construction.
                if (!solid(level, next) && !level.getFluidState(next).isSource()) {
                    throw new AssertionError("a source is unsealed toward " + side + " at " + pos);
                }
            }
        }

        helper.runAfterDelay(10, () -> {
            List<BlockPos> after = waterCells(helper);
            if (!after.equals(before)) {
                throw new AssertionError("the water moved: " + before.size()
                        + " cells became " + after.size());
            }
            for (BlockPos pos : after) {
                if (!helper.getLevel().getFluidState(pos).isSource()) {
                    throw new AssertionError("flowing water appeared at " + pos);
                }
            }
            helper.succeed();
        });
    }

    /**
     * The charter's edge law, walked rather than assumed: from every source,
     * in every cardinal, solid is met before any open column - air at water Y
     * over air. The plot edge supplies the void every run, on every seed,
     * whichever way the leg points, and the reach walked is
     * {@link WatershedFeature#edgeGap()} + 1 so the law fails the day the
     * constant and the carve disagree.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noSourceSitsOnAnOpenEdge(GameTestHelper helper) {
        carve(helper);

        ServerLevel level = helper.getLevel();
        List<BlockPos> water = waterCells(helper);
        if (water.isEmpty()) {
            throw new AssertionError("a watershed with no mirror built nothing");
        }

        for (BlockPos pos : water) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int out = 1; out <= WatershedFeature.edgeGap() + 1; out++) {
                    BlockPos column = pos.relative(side, out);
                    if (solid(level, column)) {
                        break;  // the seal, met before anything open
                    }
                    if (level.getBlockState(column).isAir()
                            && level.getBlockState(column.below()).isAir()) {
                        throw new AssertionError("a source sits on an open island edge at "
                                + pos + " toward " + side);
                    }
                    // Another water cell: keep walking outward.
                }
            }
        }
        helper.succeed();
    }
}
