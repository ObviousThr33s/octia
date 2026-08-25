package com.serenity.octia.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipStatus;
import com.serenity.octia.world.Habitation;
import com.serenity.octia.world.Mystery;
import com.serenity.octia.world.RuinAge;
import com.serenity.octia.world.Sightlines;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * MILESTONE 2 - the lived-in pass.
 *
 * <p>What is worth pinning here is not which props appear. That is a judgement,
 * it is meant to vary, and a test that demanded a campfire would only ever be an
 * obstacle to changing the palette. What is worth pinning is the rule that
 * cannot be allowed to break quietly: dressing must never unmoor a ship.
 */
public class HabitationGameTest implements FabricGameTest {

    private static final BlockPos ANCHOR = new BlockPos(5, 3, 5);

    /** How far the floor reaches, and therefore how far a path can be seen to go. */
    private static final int REACH = 7;

    /**
     * How many dressings the path tests measure one at a time.
     *
     * <p>Small on purpose. Each one wipes the plot back to bare gravel first, so
     * the cost is a floor rebuild per seed rather than per test, and the tests
     * below assert per dressing rather than over a pile of them - which is the
     * only form of the arm count that can tell three arms from one arm rolled
     * three times.
     */
    private static final int SEEDS = 8;

    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                helper.setBlock(centre.offset(dx, -1, dz), Blocks.GRAVEL);
            }
        }
    }

    /**
     * The plot wiped back to bare gravel, props and all.
     *
     * <p>A path is written into the floor, so a second dressing over the first
     * one's dirt finds ground that is no longer ground and lays nothing - and a
     * prop standing where a path wants to go blocks it the same way. Measuring
     * one dressing at a time is the only way to ask what one dressing did.
     */
    private static void clear(GameTestHelper helper, BlockPos centre) {
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.AIR);
                }
                helper.setBlock(centre.offset(dx, -1, dz), Blocks.GRAVEL);
            }
        }
    }

    /** What a worn path is made of, at either age that lays one. */
    private static boolean isPath(BlockState state) {
        return state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    /**
     * Every path block on the site's floor, as offsets from the anchor.
     *
     * <p>The floor plane alone, because that is where a path lives and nothing
     * else does: props stand on the surface and a path is written one below it,
     * so scanning that one layer separates the two without having to name a
     * single prop.
     */
    private static List<BlockPos> pathsAround(ServerLevel level, BlockPos anchor) {
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if (isPath(level.getBlockState(anchor.offset(dx, -1, dz)))) {
                    found.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        return found;
    }

    /** How many of the four cardinals have at least one path block on them. */
    private static int cardinalArms(List<BlockPos> paths) {
        boolean[] arm = new boolean[4];
        for (BlockPos p : paths) {
            if (p.getX() == 0 && p.getZ() < 0) {
                arm[0] = true;
            }
            if (p.getZ() == 0 && p.getX() > 0) {
                arm[1] = true;
            }
            if (p.getX() == 0 && p.getZ() > 0) {
                arm[2] = true;
            }
            if (p.getZ() == 0 && p.getX() < 0) {
                arm[3] = true;
            }
        }
        int count = 0;
        for (boolean present : arm) {
            if (present) {
                count++;
            }
        }
        return count;
    }

    /**
     * Every path block sits in the octant the aim named.
     *
     * <p>The claim is exact rather than approximate, and it can be: a walk offsets
     * both axes by the same stride, so a diagonal path has {@code |dx| == |dz|}
     * and a cardinal one has a zero, and in both cases the sign of each axis is
     * the mark's own offset. A path block anywhere else was not aimed.
     */
    private static void assertInOctant(List<BlockPos> paths, Mystery.Mark want, int seed) {
        for (BlockPos p : paths) {
            if (Integer.signum(p.getX()) != want.dx() || Integer.signum(p.getZ()) != want.dz()) {
                throw new AssertionError("seed " + seed + " laid a path block at offset "
                        + p.getX() + "," + p.getZ() + " when the seed says the node is at "
                        + want.dx() + "," + want.dz()
                        + " - the path points somewhere nothing is");
            }
        }
    }

    /**
     * A core with its eight panels, exactly the shape hullIntact reads.
     *
     * <p>Takes a RELATIVE position. {@code helper.setBlock} offsets whatever it
     * is given by the test's own origin, so handing it an absolute position
     * builds the hull thousands of blocks away and the assertion then reports
     * "property status is missing" - which reads like the core lost its
     * blockstate rather than like it was never there.
     */
    private static void hull(GameTestHelper helper, BlockPos relativeCore) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                helper.setBlock(relativeCore.offset(dx, 0, dz), OctiaBlocks.ANDESITE_FRAME_PANEL);
            }
        }
        helper.setBlock(relativeCore, OctiaBlocks.SHIP_CORE);
    }

    /**
     * <b>The load-bearing test.</b> Dressing never breaks a hull.
     *
     * <p>A prop dropped into one of the core's eight ring slots replaces a frame
     * panel, {@code hullIntact} fails, and the ship unmoors - a mooring lost to
     * decoration, which would surface months later as a mark missing from a map
     * with nothing to connect it to. Run at the age that places the most, many
     * times over, because a guard that holds on one roll proves nothing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dressingNeverBreaksAHull(GameTestHelper helper) {
        floor(helper, ANCHOR);
        ServerLevel level = helper.getLevel();
        hull(helper, ANCHOR);

        helper.assertBlockProperty(ANCHOR, ShipCoreBlock.STATUS, ShipStatus.MOORED);

        BlockPos core = helper.absolutePos(ANCHOR);
        for (int seed = 0; seed < 40; seed++) {
            Habitation.dress(level, RandomSource.create(seed), core, RuinAge.RECENT);
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos ring = core.offset(dx, 0, dz);
                if (!level.getBlockState(ring).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("dressing put " + level.getBlockState(ring).getBlock()
                            + " into the hull ring at " + ring + " - the ship is unmoored");
                }
            }
        }
        if (!ShipCoreBlock.hullIntact(level, core, null)) {
            throw new AssertionError("the hull is broken after dressing");
        }
        helper.succeed();
    }

    /** Dressing only ever writes into air. It never overwrites the ruin. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dressingNeverOverwritesTheRuin(GameTestHelper helper) {
        floor(helper, ANCHOR);
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);

        // A wall of panels the dressing has every opportunity to trample.
        for (int dx = -3; dx <= 3; dx++) {
            helper.setBlock(ANCHOR.offset(dx, 0, 3), OctiaBlocks.ANDESITE_FRAME_PANEL);
        }

        for (int seed = 0; seed < 40; seed++) {
            Habitation.dress(level, RandomSource.create(seed), anchor, RuinAge.RECENT);
        }

        for (int dx = -3; dx <= 3; dx++) {
            helper.assertBlockPresent(OctiaBlocks.ANDESITE_FRAME_PANEL, ANCHOR.offset(dx, 0, 3));
        }
        helper.succeed();
    }

    /**
     * The three ages produce visibly different places.
     *
     * <p>Counted rather than named: what matters is that age changes the result
     * at all, not which blocks it chooses. A recent ruin should carry more than
     * an ancient one, because that is what the presence weighting is for, and a
     * change that flattened the ages into one would otherwise pass unnoticed.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ageChangesWhatIsLeftBehind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<RuinAge, Integer> placed = new EnumMap<>(RuinAge.class);

        int lane = 0;
        for (RuinAge age : RuinAge.values()) {
            BlockPos anchor = helper.absolutePos(ANCHOR.offset(0, lane * 6, 0));
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    level.setBlockAndUpdate(anchor.offset(dx, -1, dz), Blocks.GRAVEL.defaultBlockState());
                }
            }

            for (int seed = 0; seed < 12; seed++) {
                Habitation.dress(level, RandomSource.create(seed), anchor, age);
            }

            int count = 0;
            for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-7, 0, -7), anchor.offset(7, 2, 7))) {
                BlockState state = level.getBlockState(p);
                if (!state.isAir()) {
                    count++;
                }
            }
            placed.put(age, count);
            lane++;
        }

        if (placed.get(RuinAge.RECENT) <= placed.get(RuinAge.ANCIENT)) {
            throw new AssertionError("a recent ruin left " + placed.get(RuinAge.RECENT)
                    + " things and an ancient one " + placed.get(RuinAge.ANCIENT)
                    + " - age no longer changes what is left behind");
        }
        helper.succeed();
    }

    /**
     * F4 - a pot never floats.
     *
     * <p>The 23.43.26 playtest found a decorated pot standing on a grass tuft
     * at 264 193 22: surfaceNear answers "the block below is not air", and a
     * tuft is not air. Half of this floor is that trap and half is honest
     * gravel, so the dressing has both something to refuse and somewhere to
     * go. The assertion is the decision - every pot stands on a face that
     * holds it up - never which props appeared. ANCIENT, because it is the
     * only age that places a pot.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPotNeverFloats(GameTestHelper helper) {
        floor(helper, ANCHOR);
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (((dx + dz) & 1) == 0) {
                    helper.setBlock(ANCHOR.offset(dx, 0, dz), Blocks.SHORT_GRASS);
                }
            }
        }

        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);
        for (int seed = 0; seed < 40; seed++) {
            Habitation.dress(level, RandomSource.create(seed), anchor, RuinAge.ANCIENT);
        }

        // The law: support, checked the same way the fix asks it. A pot over a
        // tuft fails here, and so does a pot atop an earlier seed's pot.
        int pots = 0;
        for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-7, -1, -7), anchor.offset(7, 3, 7))) {
            if (!level.getBlockState(p).is(Blocks.DECORATED_POT)) {
                continue;
            }
            pots++;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                throw new AssertionError("a pot floats at " + p.immutable() + " over "
                        + level.getBlockState(p.below()).getBlock()
                        + " - ground that holds nothing up");
            }
        }
        // Vacuity guard: deterministic per the fixed seed list, and about ten
        // store rolls over forty seeds against a half-sturdy board makes zero
        // effectively impossible. If the balance ever shifts, this says so
        // instead of passing empty.
        if (pots == 0) {
            throw new AssertionError("the dressing never placed a pot - the assertion never ran");
        }
        helper.succeed();
    }

    /**
     * No entity is ever spawned. The ruins are empty, always.
     *
     * <p>Measured as a difference rather than a total. The suite shares one
     * world and the crew tests seat fake players in it, so counting entities
     * near this box asks "is anything nearby" - which was answered with twelve
     * and had nothing to do with dressing. Before and after is the only form of
     * this question that is about the code under test.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void nobodyIsEverHome(GameTestHelper helper) {
        floor(helper, ANCHOR);
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);
        AABB around = new AABB(anchor).inflate(8.0);

        int before = level.getEntities(null, around).size();
        for (int seed = 0; seed < 20; seed++) {
            Habitation.dress(level, RandomSource.create(seed), anchor, RuinAge.RECENT);
        }
        int after = level.getEntities(null, around).size();

        if (after > before) {
            throw new AssertionError("dressing spawned " + (after - before)
                    + " entity/entities - a ruin is a place somebody left, not one they are in");
        }
        helper.succeed();
    }

    /**
     * C4a - a path runs at the node, and not just somewhere.
     *
     * <p><b>The bearing is read out of the world, never typed in here.</b> That
     * is the whole discipline of this test: a compass point written into the
     * assertion would agree with whatever heading the code picked, which is
     * exactly the bug the aiming replaced - a path that looked like evidence and
     * was a coin toss. So the expected answer comes from
     * {@link Habitation#aim} against this level's own seed and this plot's own
     * position, and if the aiming ever stops consulting the lattice the two
     * disagree.
     *
     * <p>The plot cannot be moved, so it lands wherever the suite's world puts
     * it - and about seven times in a thousand that is inside a node's
     * {@link Mystery#ARRIVED} disc, where the correct answer is the crossroads
     * rather than a path. The test asserts whichever of the two the seed says,
     * so it is never vacuous and never wrong about which rule applies.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPathRunsTowardTheLattice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);
        Mystery.Mark want = Habitation.aim(level.getSeed(), anchor, null);

        int laid = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            clear(helper, ANCHOR);
            Habitation.dress(level, RandomSource.create(seed), anchor, RuinAge.RECENT);

            List<BlockPos> paths = pathsAround(level, anchor);
            laid += paths.size();
            if (want == null) {
                if (!paths.isEmpty() && cardinalArms(paths) < 3) {
                    throw new AssertionError("this plot stands on its cell's node, so seed "
                            + seed + " should have put out a crossroads - it put out "
                            + cardinalArms(paths) + " arms");
                }
                continue;
            }
            assertInOctant(paths, want, seed);
        }

        // Vacuity guard. A path is rolled at RuinAge.RECENT six times in eight
        // and its first stride is always one block, which is inside the floor,
        // so a run of eight fixed seeds laying nothing at all would mean the
        // path stopped happening rather than that this test got unlucky.
        if (laid == 0) {
            throw new AssertionError("no seed laid a single path block - the assertion never ran");
        }
        helper.succeed();
    }

    /**
     * C4a - a told target is obeyed, which is the interface another feature
     * calls.
     *
     * <p>{@code dress(level, random, anchor, age, pathTarget)} exists for a
     * caller that already knows where its path should run. Pinning it here means
     * the day somebody changes what the fifth argument means, this fails rather
     * than that caller quietly laying paths at the lattice instead of at the
     * thing it was built beside.
     *
     * <p>The target is the next node along this cell's leg, read from the seed,
     * so the heading is still the world's and not the test's - and it is
     * hundreds of blocks away, so it can never collapse to the anchor's own
     * column.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPathObeysAToldTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);
        long seed = level.getSeed();

        Sightlines.Leg leg = Sightlines.legAt(seed, anchor.getX(), anchor.getZ());
        BlockPos target = new BlockPos(leg.to().x(), anchor.getY(), leg.to().z());
        Mystery.Mark want = Habitation.aim(seed, anchor, target);
        if (want == null) {
            throw new AssertionError("the next node landed on this plot's own column at "
                    + target + " - a leg is a whole cell long and cannot");
        }

        int laid = 0;
        for (int roll = 0; roll < SEEDS; roll++) {
            clear(helper, ANCHOR);
            Habitation.dress(level, RandomSource.create(roll), anchor, RuinAge.RECENT, target);

            List<BlockPos> paths = pathsAround(level, anchor);
            laid += paths.size();
            assertInOctant(paths, want, roll);
        }
        if (laid == 0) {
            throw new AssertionError("no seed laid a single path block - the assertion never ran");
        }
        helper.succeed();
    }

    /**
     * C4a - a site with nowhere to point puts out a crossroads.
     *
     * <p><b>Two halves, because the rule has two halves and only one of them
     * needs a world.</b> The decision is a pure function of the seed and a
     * position, and a gametest plot cannot be picked up and set down on a lattice
     * node - so the decision is asked of {@link Habitation#aim} at the node this
     * plot's own cell actually has, read from the seed, and again at a position
     * two {@link Mystery#ARRIVED} radii off it. On the node there must be no
     * bearing; off it there must be one, or every ruin in the cell would read as
     * a crossroads.
     *
     * <p>The placement then goes through the public contract: a target in the
     * anchor's own column is a site with nowhere to point, which is documented to
     * mean the same thing as standing on the node. What is asserted is the shape
     * a junction has to have - three or four arms, every one of them square to
     * the world - and never which three.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSiteAtANodePutsOutStubs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);
        long seed = level.getSeed();

        Sightlines.Node node = Sightlines.node(seed,
                Sightlines.cell(anchor.getX()), Sightlines.cell(anchor.getZ()));
        BlockPos onNode = new BlockPos(node.x(), anchor.getY(), node.z());
        if (Habitation.aim(seed, onNode, null) != null) {
            throw new AssertionError("a site standing on the node at " + onNode
                    + " still has a bearing - the crossroads can never happen");
        }
        BlockPos offNode = onNode.offset(2 * Mystery.ARRIVED, 0, 0);
        if (Habitation.aim(seed, offNode, null) == null) {
            throw new AssertionError("a site " + (2 * Mystery.ARRIVED)
                    + " blocks off the node at " + offNode
                    + " reads as a crossroads - so would every ruin in the cell");
        }

        int junctions = 0;
        for (int roll = 0; roll < SEEDS; roll++) {
            clear(helper, ANCHOR);
            Habitation.dress(level, RandomSource.create(roll), anchor, RuinAge.RECENT, anchor);

            List<BlockPos> paths = pathsAround(level, anchor);
            if (paths.isEmpty()) {
                continue;
            }
            junctions++;
            for (BlockPos p : paths) {
                if (p.getX() != 0 && p.getZ() != 0) {
                    throw new AssertionError("a crossroads laid a diagonal block at offset "
                            + p.getX() + "," + p.getZ()
                            + " - four arms square to the world read as a junction, eight read"
                            + " as a sunburst");
                }
            }
            int arms = cardinalArms(paths);
            if (arms < 3) {
                throw new AssertionError("seed " + roll + " put out " + arms
                        + " arms - fewer than three is a path with a kink in it, not a junction");
            }
        }
        if (junctions == 0) {
            throw new AssertionError("no seed laid a crossroads - the assertion never ran");
        }
        helper.succeed();
    }

    /**
     * C4a - an ancient pot carries what the ruin's digs carry.
     *
     * <p>A bare decorated pot breaks into four bricks, which is what a player
     * could have made it out of, so the one container an ANCIENT ruin has told
     * them nothing on being found. This asserts the decision - the pot carries at
     * least one face - and never which sherd, because which sherd is a coin toss
     * on purpose: {@code docs/MYSTERIES.md} ruled that the mod may not assign
     * vanilla's marks private meanings, and a test that named one would be the
     * first step toward a cipher.
     *
     * <p>It also re-pins the thing the sherds must not have broken.
     * {@code ShipCoreBlock.findDig} matches on {@code Blocks.DECORATED_POT} and
     * nothing else, so a core beside a ruin still reads CALLED - block entity
     * data is invisible to it, and this says so out loud rather than leaving it
     * to be rediscovered.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anAncientPotCarriesASherd(GameTestHelper helper) {
        floor(helper, ANCHOR);
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(ANCHOR);

        for (int seed = 0; seed < 40; seed++) {
            Habitation.dress(level, RandomSource.create(seed), anchor, RuinAge.ANCIENT);
        }

        int pots = 0;
        for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-REACH, -1, -REACH),
                anchor.offset(REACH, 3, REACH))) {
            if (!level.getBlockState(p).is(Blocks.DECORATED_POT)) {
                continue;
            }
            pots++;
            if (!(level.getBlockEntity(p) instanceof DecoratedPotBlockEntity jar)) {
                throw new AssertionError("the pot at " + p.immutable()
                        + " has no block entity - nothing can carry a sherd");
            }
            PotDecorations faces = jar.getDecorations();
            int sherds = 0;
            if (faces.back().isPresent()) {
                sherds++;
            }
            if (faces.left().isPresent()) {
                sherds++;
            }
            if (faces.right().isPresent()) {
                sherds++;
            }
            if (faces.front().isPresent()) {
                sherds++;
            }
            if (sherds == 0) {
                throw new AssertionError("the pot at " + p.immutable()
                        + " carries no sherd - it breaks into four bricks and says nothing");
            }
        }
        // The same vacuity guard aPotNeverFloats carries, and for the same
        // reason: about ten store rolls over forty fixed seeds makes zero pots
        // impossible unless the pot stopped being placed.
        if (pots == 0) {
            throw new AssertionError("the dressing never placed a pot - the assertion never ran");
        }
        if (ShipCoreBlock.findDig(level, anchor) == null) {
            throw new AssertionError("a pot full of sherds no longer reads as a dig"
                    + " - a core beside this ruin would sit MOORED instead of CALLED");
        }
        helper.succeed();
    }
}
