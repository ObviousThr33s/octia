package com.serenity.octia.gametest;

import com.serenity.octia.world.Isle;
import com.serenity.octia.world.Landfall;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The island a sky world is given when it has none to offer.
 *
 * <p><b>What cannot be tested here, and why this is still worth having.</b>
 * {@link Landfall#secure} is the interesting half - it reads the save's spawn,
 * rings outward, and only builds when nothing answered - and none of that can
 * run inside a gametest, because a gametest world has ground everywhere by
 * construction and its spawn is not the box these run in. What can be pinned is
 * the thing that gets built when the search fails, which is the half a player
 * actually stands on. Same split {@code OctiaBeacon.build} already has: the rule
 * that decides where something goes and the thing that goes there are separate
 * problems, and only one of them fits in a box.
 *
 * <p>Built at radius three rather than the seven a real spawn gets, so the
 * island stays inside the structure and out of whatever test is sitting next
 * door. {@link Isle} is the shape at every size, so the small one proves the
 * same arithmetic - and {@code IsleTest} proves the arithmetic itself without a
 * world at all.
 */
public class LandfallGameTest implements FabricGameTest {

    /** Centred in the box with room for a radius-three disc, and off the floor. */
    private static final BlockPos SURFACE = new BlockPos(4, 3, 4);

    private static final int RADIUS = 3;

    /**
     * What {@code raise} hands back is somewhere a player can be put.
     *
     * <p>This is the whole promise. A position that is solid would suffocate
     * whoever arrives in it, and one with air underneath is a fall - the exact
     * fall the class exists to prevent. Air over something is the only answer
     * that is not one of those two.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void landfallHandsBackSomewhereToStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stand = Landfall.raise(level, helper.absolutePos(SURFACE), RADIUS);

        if (!level.getBlockState(stand).isAir()) {
            throw new AssertionError("landfall answered " + stand + ", which is solid."
                    + " A player put there is inside a block.");
        }
        if (level.getBlockState(stand.below()).isAir()) {
            throw new AssertionError("landfall answered " + stand + " with air beneath it."
                    + " That is the fall this class exists to prevent, not a landing.");
        }
        helper.succeed();
    }

    /** Grass on top, soil under it, stone under that - an overworld surface. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theIslandIsLayeredLikeGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(SURFACE);
        Landfall.raise(level, centre, RADIUS);

        if (!level.getBlockState(centre).is(Blocks.GRASS_BLOCK)) {
            throw new AssertionError("the top of the island is "
                    + level.getBlockState(centre).getBlock()
                    + " and not grass. An island you are given should look like somewhere,"
                    + " not like a platform.");
        }
        for (int depth = 1; depth <= Isle.SOIL; depth++) {
            BlockPos at = centre.below(depth);
            if (!level.getBlockState(at).is(Blocks.DIRT)) {
                throw new AssertionError("depth " + depth + " is "
                        + level.getBlockState(at).getBlock() + " and should be soil -"
                        + " a shovel must not reach stone on the first block.");
            }
        }
        if (!level.getBlockState(centre.below(Isle.SOIL + 1)).is(Blocks.STONE)) {
            throw new AssertionError("nothing under the soil is stone; the island has no keel");
        }
        helper.succeed();
    }

    /**
     * The island in the world is the island {@link Isle} describes.
     *
     * <p>Every column inside the disc is solid and every column outside it is
     * air, checked layer by layer against the same function that placed them.
     * That sounds circular and is not: what it pins is that {@code raise} walks
     * the shape it is handed rather than a bounding square, so the thing in the
     * world is round and tapered and does not quietly become a cube the day
     * somebody simplifies the loop.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theIslandKeepsTheShapeItWasGiven(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(SURFACE);
        Landfall.raise(level, centre, RADIUS);

        int thickness = Isle.thickness(RADIUS);
        for (int depth = 0; depth < thickness; depth++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockPos at = centre.offset(dx, -depth, dz);
                    boolean wanted = Isle.holds(RADIUS, depth, dx, dz);
                    boolean got = !level.getBlockState(at).isAir();
                    if (wanted != got) {
                        throw new AssertionError("at depth " + depth + ", offset ("
                                + dx + ", " + dz + "): Isle says " + (wanted ? "island" : "air")
                                + " and the world has " + (got ? "island" : "air"));
                    }
                }
            }
        }
        helper.succeed();
    }
}
