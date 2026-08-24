package com.serenity.octia.gametest;

import java.util.EnumMap;
import java.util.Map;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipStatus;
import com.serenity.octia.world.Habitation;
import com.serenity.octia.world.RuinAge;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
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

    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                helper.setBlock(centre.offset(dx, -1, dz), Blocks.GRAVEL);
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
}
