package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.world.EraEcho;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * MILESTONE 2 - the echo across eras.
 *
 * <p>The motes cannot be asserted and are not the point. What is testable is the
 * decision underneath: whether a set of coordinates should feel occupied from
 * where you are standing. That decision is the mod's central claim - a ship
 * exists at the same coordinates across every layer of the era stack - and this
 * is the first thing in the game that acts on it.
 */
public class EraEchoGameTest implements FabricGameTest {

    private static final BlockPos CORE = new BlockPos(2, 2, 2);

    /** Rings a core so it moors itself, and returns its absolute position. */
    private static BlockPos moorAShip(GameTestHelper helper, BlockPos relative) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                helper.setBlock(relative.offset(dx, 0, dz), OctiaBlocks.ANDESITE_FRAME_PANEL);
            }
        }
        helper.setBlock(relative, OctiaBlocks.SHIP_CORE);
        return helper.absolutePos(relative);
    }

    /**
     * <b>The load-bearing test.</b> A ship moored in one world is felt in another.
     *
     * <p>Moored through the Overworld, then asked about from the Nether at the
     * same coordinates. If {@code ShipMoorings} ever grows a dimension in its
     * key - the one change that would quietly dismantle the era stack - this
     * stops finding anything, and so does the feature.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aShipIsFeltFromAnotherWorld(GameTestHelper helper) {
        BlockPos core = moorAShip(helper, CORE);

        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new AssertionError("no Nether on this server; cannot prove the claim");
        }
        if (!ShipMoorings.get(nether.getServer()).isMoored(core)) {
            throw new AssertionError("the ship did not moor, so there is nothing to echo");
        }

        // The Nether has no core at these coordinates, and almost certainly no
        // loaded chunk either - which is itself the honest answer, so load it
        // the way a player standing there would have.
        nether.getChunk(core);

        BlockPos felt = EraEcho.find(nether, core);
        if (felt == null) {
            throw new AssertionError("a ship moored at " + core
                    + " is not felt from the Nether at the same coordinates - "
                    + "the moorings store has stopped being dimension-agnostic");
        }
        helper.succeed();
    }

    /** Standing beside your own ship is a ship, not an echo of one. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aShipYouAreStandingOnDoesNotEcho(GameTestHelper helper) {
        BlockPos core = moorAShip(helper, CORE);

        BlockPos felt = EraEcho.find(helper.getLevel(), core);
        if (felt != null) {
            throw new AssertionError("the core at " + core
                    + " echoed in the world it is actually standing in");
        }
        helper.succeed();
    }

    /** Far coordinates are somebody else's problem. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void distantMooringsAreNotFelt(GameTestHelper helper) {
        BlockPos core = moorAShip(helper, CORE);

        BlockPos felt = EraEcho.find(helper.getLevel(), core.offset(400, 0, 400));
        if (felt != null) {
            throw new AssertionError("a mooring 400 blocks away was felt at " + felt);
        }
        helper.succeed();
    }

    /**
     * An unmoored ship stops being felt anywhere.
     *
     * <p>The echo reads the store rather than the world, so the thing that ends
     * it is the hull breaking - not the blocks vanishing from the era you happen
     * to be in.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void breakingTheHullSilencesTheEcho(GameTestHelper helper) {
        BlockPos core = moorAShip(helper, CORE);

        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new AssertionError("no Nether on this server; cannot prove the claim");
        }
        nether.getChunk(core);

        if (EraEcho.find(nether, core) == null) {
            throw new AssertionError("nothing was felt before the hull was broken");
        }

        helper.setBlock(CORE.offset(1, 0, 0), Blocks.AIR);

        if (EraEcho.find(nether, core) != null) {
            throw new AssertionError("the ship unmoored but is still felt from the Nether");
        }
        helper.succeed();
    }
}
