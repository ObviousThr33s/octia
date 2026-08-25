package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.ship.ShipStatus;
import com.serenity.octia.world.DerelictFeature;
import com.serenity.octia.world.OctiaLoot;
import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.RuinAge;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
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
     * A wreck settles on a seabed, and refuses the waterline.
     *
     * <p>Derelicts used to refuse water outright, which kept them off the one
     * place a ship that was called and never arrived most belongs. They walk
     * down through it now. What they still refuse is straddling the surface: a
     * hull with its floor in the sea and its lid in the air is neither a
     * shipwreck nor a ruin.
     *
     * <p>Two columns of water over a solid bed is the deep case; one is the
     * shallow one, where the cube would break the surface.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aWreckSettlesOnASeabedButNotAtTheWaterline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Deep: bed at y-1, water for six courses above it.
        floor(helper, GROUND);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    helper.setBlock(GROUND.offset(dx, dy, dz), Blocks.WATER);
                }
            }
        }

        OctiaWorldgen.setActive(true);
        BlockPos top = helper.absolutePos(GROUND.above(5));
        boolean deep = OctiaWorldgen.derelict().place(new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(),
                RandomSource.create(31L), top, NoneFeatureConfiguration.INSTANCE));

        if (!deep) {
            throw new AssertionError("a derelict refused a seabed under six courses of water - "
                    + "the walk-down is not reaching the floor, or the submerged check is too strict");
        }

        BlockPos core = null;
        for (int dy = 2; dy >= -8; dy--) {
            BlockPos p = top.offset(0, dy, 0);
            if (level.getBlockState(p).getBlock() instanceof ShipCoreBlock) {
                core = p;
                break;
            }
        }
        if (core == null) {
            throw new AssertionError("the submerged derelict placed no core");
        }
        if (!ShipCoreBlock.hullIntact(level, core, null)) {
            throw new AssertionError("the submerged hull at " + core + " is not intact");
        }
        helper.succeed();
    }

    /**
     * A wreck stamped from a marker is a ship, ring and all.
     *
     * <p>{@code stamp} is the path a template's core marker takes and the same
     * one age-stripping runs through. An ancient derelict loses its whole top
     * course and sinks a further block, so what survives is the ring and the
     * floor - a hull worn down to precisely the part that still makes it one.
     * That is a good object and a dangerous one: it sits one careless edit away
     * from {@code hullIntact} failing, and widening the strip from {@code dy > 0}
     * to {@code dy >= 0} would turn every ancient wreck into masonry with no
     * error raised anywhere.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStampedHullIsAlwaysAShip(GameTestHelper helper) {
        floor(helper, GROUND);
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(GROUND);

        DerelictFeature.stamp(level, RandomSource.create(9L), core, true);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!level.getBlockState(core.offset(dx, 0, dz)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("the ring is incomplete at " + core.offset(dx, 0, dz));
                }
            }
        }
        if (!ShipCoreBlock.hullIntact(level, core, null)) {
            throw new AssertionError("a stamped hull at " + core + " is not intact");
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

    // ---------------------------------------------------------------------
    // The last lit panel.
    // ---------------------------------------------------------------------

    /** How many of a hull's twenty-six panels are burning. */
    private static int litPanels(ServerLevel level, BlockPos core) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = level.getBlockState(core.offset(dx, dy, dz));
                    if (state.is(OctiaBlocks.ANDESITE_FRAME_PANEL)
                            && state.getValue(AndesiteFramePanelBlock.LIGHT) != PanelLight.NONE) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * A wreck's age is readable in its light before it is readable in its moss.
     *
     * <p>The owner walked the world and said "cool but too dark!". The answer
     * was not a brighter block, it was that how much of a hull is still burning
     * says how long ago it was left - so the thing under test is the ordering,
     * not a number of lamps. Two wrecks are stamped from the same seed and
     * differ only in the age handed to them, which is why this goes through the
     * five-argument {@code stamp}: {@code place} rolls its own age, and a test
     * that placed twice and hoped for one of each would be a coin toss with an
     * assertion on it.
     *
     * <p>It also refuses zero outright. A count that is merely greater than
     * another count is satisfied by one lamp against none, and one lamp against
     * none is still a dark wreck.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aRecentWreckKeepsMoreLightThanAnAncientOne(GameTestHelper helper) {
        floor(helper, GROUND);
        ServerLevel level = helper.getLevel();

        BlockPos recent = helper.absolutePos(GROUND.offset(-3, -1, 0));
        BlockPos ancient = helper.absolutePos(GROUND.offset(3, -1, 0));

        DerelictFeature.stamp(level, RandomSource.create(5150L), recent, false, RuinAge.RECENT);
        DerelictFeature.stamp(level, RandomSource.create(5150L), ancient, false, RuinAge.ANCIENT);

        int lit = litPanels(level, recent);
        int dark = litPanels(level, ancient);

        if (lit <= dark) {
            throw new AssertionError("a recent wreck lit " + lit + " panels and an ancient one lit "
                    + dark + "; age is supposed to be read by light before it is read by moss");
        }
        if (lit == 0) {
            throw new AssertionError("a recent wreck carries no lit panel at all, so the walked "
                    + "world's 'too dark' is unanswered");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // The open working.
    // ---------------------------------------------------------------------

    /**
     * Where the working tests are cut, and why they get ground of their own.
     *
     * <p>The shared {@link #floor} cannot serve them, for two separate reasons
     * and both are load-bearing. Its strata are uniform, so the inversion the
     * spoil heap exists to show would be invisible - a heap of gravel lying on
     * gravel proves nothing about which end came out first. And it is gravel:
     * gravel standing over the open side of a fresh cut falls within ticks on a
     * live {@code ServerLevel}, which is precisely the trap that got the
     * collapsing-gravel variant ruled out before it was written. Dirt over stone
     * is layered and it stays where it is put.
     *
     * <p>Raised one course over the shared ground so the deepest cut still lands
     * inside the plot rather than under it.
     */
    private static final BlockPos WORKING_GROUND = new BlockPos(5, 4, 5);

    /** Two courses of dirt over two of stone, with the air above it cleared. */
    private static void layeredFloor(GameTestHelper helper) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    helper.setBlock(WORKING_GROUND.offset(dx, dy, dz), Blocks.AIR);
                }
                helper.setBlock(WORKING_GROUND.offset(dx, -1, dz), Blocks.DIRT);
                helper.setBlock(WORKING_GROUND.offset(dx, -2, dz), Blocks.DIRT);
                helper.setBlock(WORKING_GROUND.offset(dx, -3, dz), Blocks.STONE);
                helper.setBlock(WORKING_GROUND.offset(dx, -4, dz), Blocks.STONE);
            }
        }
    }

    /** The topmost solid block of that ground, in world coordinates. */
    private static BlockPos gradeTop(GameTestHelper helper) {
        return helper.absolutePos(WORKING_GROUND.below(1));
    }

    /**
     * A core seated the way a wreck of this age would have settled on that
     * ground.
     *
     * <p>Read out of the feature rather than written down here. A wreck sinks by
     * its age, the working derives the ground surface by adding that back, and a
     * test that hard-coded one or two would be holding the pit to a constant it
     * had copied - which passes until the day the sink moves and then fails
     * somewhere else entirely.
     */
    private static BlockPos coreFor(GameTestHelper helper, RuinAge age) {
        return gradeTop(helper).below(DerelictFeature.sink(age) - 1);
    }

    /** Cuts a working on that ground and refuses to continue if the site declined it. */
    private static DerelictFeature.Working excavate(GameTestHelper helper, RuinAge age,
                                                    long seed, int digAttempts) {
        layeredFloor(helper);
        DerelictFeature.Working working = DerelictFeature.excavate(helper.getLevel(),
                RandomSource.create(seed), coreFor(helper, age), age, digAttempts);
        if (working == null) {
            throw new AssertionError("the working declined flat, layered, dry ground at "
                    + coreFor(helper, age) + " - a site that refuses this refuses everything");
        }
        return working;
    }

    /**
     * The cut is a real hole: its floor stands well below the ground beside it.
     *
     * <p>Measured against the working's own lip rather than against a constant,
     * so the assertion survives the depth table being retuned. Two is the floor
     * of the requirement - anything shallower is a scuff in the grass rather
     * than a dig somebody stood in.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWorkingFloorSitsWellBelowGrade(GameTestHelper helper) {
        DerelictFeature.Working working = excavate(helper, RuinAge.RECENT, 4242L, 0);
        List<BlockPos> benches = working.benches();

        BlockPos deepest = benches.get(benches.size() - 1);
        int drop = working.lip().getY() - deepest.getY();
        if (drop < 2) {
            throw new AssertionError("the working's floor is only " + drop
                    + " below the ground beside it; a dig site has to be a hole");
        }
        helper.succeed();
    }

    /**
     * Every bench is reachable from the one above it, so the pit can be walked
     * out of.
     *
     * <p>A stepped cut that drops two at any point is a pit a player falls into
     * and mines their way out of, which is the opposite of what an already-dug
     * site is for. The benches are walked in the order the feature handed them
     * back - lip first - and each is asked three things: exactly one lower,
     * exactly one block along, and standing on something.
     *
     * <p>Cut at RECENT and with no digs, deliberately. An older working has a
     * block off its wall lying on the floor and a dig seated on each step, both
     * of which a player steps over without noticing and neither of which is the
     * geometry under test here.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWorkingIsAConnectedDescendingRamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DerelictFeature.Working working = excavate(helper, RuinAge.RECENT, 8080L, 0);
        List<BlockPos> benches = working.benches();

        if (benches.size() < 2) {
            throw new AssertionError("a working with " + benches.size()
                    + " bench is not a ramp; there is nothing to walk down");
        }

        for (int i = 0; i < benches.size(); i++) {
            BlockPos step = benches.get(i);
            if (!level.getBlockState(step).isAir()) {
                throw new AssertionError("the bench at " + step + " is not standing room");
            }
            if (level.getBlockState(step.below()).isAir()) {
                throw new AssertionError("the bench at " + step + " has nothing under it");
            }
            if (i == 0) {
                continue;
            }
            BlockPos above = benches.get(i - 1);
            if (step.getY() != above.getY() - 1) {
                throw new AssertionError("the step from " + above + " to " + step
                        + " drops " + (above.getY() - step.getY()) + "; a bench is one block");
            }
            // One across and one down, and nothing else. Manhattan says both at
            // once without this test having to know which way the cut faces.
            if (above.distManhattan(step) != 2) {
                throw new AssertionError("the bench at " + step + " is not adjacent to "
                        + above + " - the ramp is not connected");
            }
        }
        helper.succeed();
    }

    /**
     * <b>The tell.</b> The spoil lies beside the cut with its strata upside
     * down.
     *
     * <p>Material comes out of a hole top-first and goes on the pile in that
     * order, so a real spoil heap has the deepest thing on top of it - stone
     * over dirt, beside a hole with dirt over stone. That is the one detail that
     * makes a dug site read as dug rather than as decoration, and it is exactly
     * what the layered ground here exists to make visible: the cut goes through
     * dirt into stone, so the heap must be stone over dirt or the inversion did
     * not happen.
     *
     * <p>RECENT, because that is the age whose cap is the raw material. The
     * years lay a skin over an older one - which is the next test.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSpoilHeapLiesBesideTheCutWithItsStrataInverted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DerelictFeature.Working working = excavate(helper, RuinAge.RECENT, 1717L, 0);

        BlockPos cap = working.heapCap();
        if (cap == null) {
            throw new AssertionError("no spoil heap beside a working on flat open ground - "
                    + "the material that came out of the hole went nowhere");
        }
        if (!level.getBlockState(cap).is(Blocks.STONE)) {
            throw new AssertionError("the heap is capped with " + level.getBlockState(cap)
                    + "; the deepest thing out of the hole has to be the top of the pile");
        }
        if (!level.getBlockState(cap.below()).is(Blocks.DIRT)) {
            throw new AssertionError("under the heap's cap is " + level.getBlockState(cap.below())
                    + "; the shallowest thing out of the hole has to be under the deepest");
        }
        // And it is beside the cut, not in it.
        if (working.pit().isInside(cap)) {
            throw new AssertionError("the spoil heap at " + cap + " is inside its own pit");
        }
        helper.succeed();
    }

    /**
     * The years lie on top of the heap, and the age says which years.
     *
     * <p>Raw spoil, then weather, then the ground taking it back. Three ages in
     * one plot because they are one table and a table wants reading across; the
     * ground is re-laid between them, which also proves the cut leaves nothing
     * behind that a fresh floor cannot bury.
     *
     * <p>The inversion underneath is untouched at every age - that is the other
     * test's business, and it stays true here.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSpoilHeapCapMatchesTheAge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        capIs(helper, level, RuinAge.RECENT, Blocks.STONE,
                "raw spoil, still the deepest thing out of the hole");
        capIs(helper, level, RuinAge.WEATHERED, Blocks.COARSE_DIRT,
                "weather has worked it over without burying it");
        capIs(helper, level, RuinAge.ANCIENT, Blocks.GRASS_BLOCK,
                "the ground has taken it back");

        helper.succeed();
    }

    private static void capIs(GameTestHelper helper, ServerLevel level, RuinAge age,
                              Block expected, String why) {
        DerelictFeature.Working working = excavate(helper, age, 606L, 0);
        BlockPos cap = working.heapCap();
        if (cap == null) {
            throw new AssertionError("no spoil heap for a " + age + " working");
        }
        if (!level.getBlockState(cap).is(expected)) {
            throw new AssertionError("a " + age + " heap is capped with "
                    + level.getBlockState(cap) + " and should be " + expected + " - " + why);
        }
    }

    /**
     * The site's digs are seated in the working, not scattered in the grass
     * beside it.
     *
     * <p>This is the whole reason the working was worth cutting rather than
     * drawn on. A core that reads CALLED is pointing at a dig, and the readout
     * now gives a bearing to it - so where that dig is decides whether the
     * bearing points into a story or at a patch of gravel that happens to be
     * nearby. Down the steps of an open cut is a story.
     *
     * <p>The loot table is read back off the block entity's own saved tag rather
     * than trusted. {@code BrushableBlockEntity} has no getter for it, and the
     * existing {@code theDigCarriesLoot} says outright that it never manages to
     * check the loot half of its own name; this one does.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDigIsSeatedOnTheWorkingBenches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DerelictFeature.Working working = excavate(helper, RuinAge.RECENT, 3030L, 3);

        if (working.digs() == 0) {
            throw new AssertionError("a working with three benches took no digs at all");
        }

        BoundingBox pit = working.pit();
        String wanted = OctiaLoot.RUIN_DIG.location().toString();
        boolean found = false;

        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(pit.minX(), pit.minY(), pit.minZ()),
                new BlockPos(pit.maxX(), pit.maxY(), pit.maxZ()))) {
            if (!(level.getBlockEntity(p) instanceof BrushableBlockEntity brush)) {
                continue;
            }
            String table = brush.saveWithoutMetadata(level.registryAccess()).getString("LootTable");
            if (!table.equals(wanted)) {
                throw new AssertionError("a dig inside the working carries '" + table
                        + "' and should carry '" + wanted + "'");
            }
            found = true;
        }

        if (!found) {
            throw new AssertionError("no brushable block inside the working's own bounds " + pit
                    + " - the digs went back to the grass and the CALLED readout points at nothing");
        }
        helper.succeed();
    }

    /**
     * <b>The cut never takes the ground the hull is standing on.</b>
     *
     * <p>The pit is three below grade against the hull, which is the whole point
     * of it, and three below grade is deeper than the footing
     * {@code RuinGround.hasFooting} vouched for at {@code core.below(2)}. What
     * keeps the wreck up is horizontal clearance and nothing else: the nearest
     * column the working touches is two from the core, and the footing is the
     * nine columns within one of it. Move the near lip inward by one and the
     * wreck starts hanging over its own hole, with no error anywhere.
     *
     * <p>The ring is re-checked in the same breath. A working is a whole pass
     * writing blocks a few paces from a hull, and the ring is the slice that
     * decides whether the ruin is a ship at all.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWorkingNeverUndercutsTheHull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        layeredFloor(helper);
        BlockPos core = coreFor(helper, RuinAge.RECENT);

        DerelictFeature.stamp(level, RandomSource.create(2026L), core, false, RuinAge.RECENT);
        if (DerelictFeature.excavate(level, RandomSource.create(2026L), core,
                RuinAge.RECENT, 3) == null) {
            throw new AssertionError("the working declined flat, layered, dry ground at " + core);
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos footing = core.below(2).offset(dx, 0, dz);
                if (level.getBlockState(footing).isAir()) {
                    throw new AssertionError("the working dug out " + footing
                            + ", which is footing the hull is standing on");
                }
            }
        }

        if (!ShipCoreBlock.hullIntact(level, core, null)) {
            throw new AssertionError("the working broke the hull ring at " + core);
        }

        // And it still reads CALLED. Worth asking here and not only through
        // place(): a working takes the site's digs off the flat ring around the
        // wreck and puts them down inside a hole, and a hole is measured in the
        // one axis the call radius is easiest to fall out of. Asked with survey,
        // which is the question the core asks itself, rather than off the
        // blockstate - nothing has updated the core since it was stamped,
        // because RuinGround.put writes with flag 2 and sends no neighbour
        // update, which is the same reason the real build order puts the core
        // in last.
        if (ShipCoreBlock.survey(level, core, null) != ShipStatus.CALLED) {
            throw new AssertionError("a wreck whose digs all sit down in its working does not "
                    + "survey as CALLED - the cut carried them out of the call radius");
        }
        helper.succeed();
    }

    /**
     * <b>The radius written to is the radius that was checked.</b>
     *
     * <p>{@code raise} asks {@code clearOfStructures} over
     * {@link DerelictFeature#siteRadius()} before it knows whether this site
     * rolls a working at all, so the two can never be reconciled at run time -
     * the only thing that keeps them equal is this. A working that grew one
     * block past the radius would pass every other test here and then put a
     * spoil heap in somebody's wheat field, on some seeds and not others.
     *
     * <p>Checked by looking at the shell one block outside the radius and
     * insisting it is exactly the ground that was laid. The floor is uniform per
     * course, so "unchanged" is a thing this test can state rather than snapshot.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWorkingStaysInsideTheClearedRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        layeredFloor(helper);
        BlockPos core = coreFor(helper, RuinAge.RECENT);

        if (DerelictFeature.excavate(level, RandomSource.create(9119L), core,
                RuinAge.RECENT, 4) == null) {
            throw new AssertionError("the working declined flat, layered, dry ground at " + core);
        }

        int shell = DerelictFeature.siteRadius() + 1;
        int gradeY = gradeTop(helper).getY();

        for (int dx = -shell; dx <= shell; dx++) {
            for (int dz = -shell; dz <= shell; dz++) {
                if (Math.abs(dx) != shell && Math.abs(dz) != shell) {
                    continue;
                }
                for (int y = gradeY - 3; y <= gradeY + 3; y++) {
                    BlockPos p = new BlockPos(core.getX() + dx, y, core.getZ() + dz);
                    BlockState found = level.getBlockState(p);
                    if (!found.is(laidAt(y, gradeY))) {
                        throw new AssertionError("the working wrote " + found + " at " + p
                                + ", which is " + shell + " from the core and outside the "
                                + DerelictFeature.siteRadius() + " the site was cleared over");
                    }
                }
            }
        }
        helper.succeed();
    }

    /** What {@link #layeredFloor} put at this height, so the shell can be held to it. */
    private static Block laidAt(int y, int gradeY) {
        if (y > gradeY) {
            return Blocks.AIR;
        }
        return y >= gradeY - 1 ? Blocks.DIRT : Blocks.STONE;
    }
}
