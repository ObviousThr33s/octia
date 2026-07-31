package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.ship.ShipStatus;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * MILESTONE 1 - the ship.
 *
 * <p>These run on a real dedicated server, which is the only place the claims
 * below can actually be tested. In particular, whether the moorings store is
 * dimension-agnostic is not answerable without two live dimensions.
 */
public class ShipGameTest implements FabricGameTest {

    private static final BlockPos CORE = new BlockPos(1, 1, 1);

    /** Rings the core with frame panels, completing the hull. */
    private static void buildHull(GameTestHelper helper, BlockPos core) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                helper.setBlock(core.offset(dx, 0, dz), OctiaBlocks.ANDESITE_FRAME_PANEL);
            }
        }
    }

    /** A bare core is adrift, and adrift means absent from the store. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bareCoreIsAdrift(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);

        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.ADRIFT);

        BlockPos absolute = helper.absolutePos(CORE);
        ShipMoorings moorings = ShipMoorings.get(helper.getLevel().getServer());
        if (moorings.isMoored(absolute)) {
            throw new AssertionError("an adrift core must not be moored at " + absolute);
        }
        helper.succeed();
    }

    /** Completing the hull moors the ship, and the store learns its position. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void completingTheHullMoorsTheShip(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);

        // The last panel placed fires neighborChanged, which reconciles.
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);

        BlockPos absolute = helper.absolutePos(CORE);
        if (!ShipMoorings.get(helper.getLevel().getServer()).isMoored(absolute)) {
            throw new AssertionError("hull is intact but " + absolute + " is not in the moorings");
        }
        helper.succeed();
    }

    /** Breaking the hull unmoors it again. Mooring is a state, not an event. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void breakingTheHullUnmoors(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);

        helper.setBlock(CORE.offset(1, 0, 0), Blocks.AIR);

        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.ADRIFT);
        BlockPos absolute = helper.absolutePos(CORE);
        if (ShipMoorings.get(helper.getLevel().getServer()).isMoored(absolute)) {
            throw new AssertionError("hull is broken but " + absolute + " is still moored");
        }
        helper.succeed();
    }

    /**
     * A dig site in range calls the ship. This is the archaeology hook.
     *
     * <p>The re-survey is explicit because the core does not tick: a block six
     * away cannot notify it, and hooking every placement in the game to catch
     * that would cost the "additive, not invasive" pillar. Right-clicking is
     * the documented way to ask, so the test asks.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDigSiteCallsTheShip(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);

        // Outside the hull ring, inside the call radius.
        helper.setBlock(CORE.offset(3, 0, 3), Blocks.SUSPICIOUS_GRAVEL);
        helper.useBlock(CORE, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.CALLED);
        helper.succeed();
    }

    /** A decorated pot calls it too - both are archaeology. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDecoratedPotAlsoCalls(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);
        helper.setBlock(CORE.offset(2, 0, 3), Blocks.DECORATED_POT);
        helper.useBlock(CORE, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.CALLED);
        helper.succeed();
    }

    /**
     * A dig placed inside the hull ring calls the ship with no prompting,
     * because that position IS a neighbour. Pins the boundary between what
     * notifies itself and what has to be asked.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDigOnTheRingCallsImmediately(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);
        helper.setBlock(CORE.offset(0, 1, 0), Blocks.SUSPICIOUS_SAND);

        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.CALLED);
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b>
     *
     * <p>The whole design rests on a ship existing at the same coordinates
     * across the era stack, which is only true because the dimension is not
     * part of the moorings key. Here a position is moored from the Overworld
     * and then read back through the <em>Nether's</em> server handle.
     *
     * <p>If anyone ever fetches the store from the current level instead of
     * {@code server.overworld()}, or folds a dimension id into the key, this
     * fails - and nothing else would notice.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mooringsAreDimensionAgnostic(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);

        BlockPos absolute = helper.absolutePos(CORE);
        MinecraftServer server = helper.getLevel().getServer();

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            throw new AssertionError("no Nether on this server; cannot prove the claim");
        }

        ShipMoorings fromNether = ShipMoorings.get(nether.getServer());
        if (!fromNether.isMoored(absolute)) {
            throw new AssertionError(
                    "moored in the Overworld at " + absolute + " but not visible from the Nether. "
                            + "The store has become per-dimension, which breaks the era stack.");
        }

        // Same object, not merely equal contents - one store for the save.
        if (fromNether != ShipMoorings.get(helper.getLevel().getServer())) {
            throw new AssertionError("the Overworld and the Nether resolved different store instances");
        }
        helper.succeed();
    }

    /**
     * The core answers its declared light for every status.
     *
     * <p>Read off the blockstate rather than by placing it: placing runs
     * {@code onPlace}, which reconciles against a hull that is not there and
     * reverts the status to ADRIFT before the light can be read. The wiring
     * under test is the {@code lightLevel} lambda at registration, and that is
     * a property of the state, not of the world.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void coreEmitsTheLightItDeclares(GameTestHelper helper) {
        for (ShipStatus expected : ShipStatus.values()) {
            int actual = OctiaBlocks.SHIP_CORE.defaultBlockState()
                    .setValue(ShipCoreBlock.STATUS, expected)
                    .getLightEmission();
            if (actual != expected.lightLevel()) {
                throw new AssertionError("status " + expected.getSerializedName() + " should emit "
                        + expected.lightLevel() + " but emitted " + actual);
            }
        }
        helper.succeed();
    }

    /**
     * Placing the final CORNER panel completes the hull.
     *
     * <p>This is the regression that motivated the panel telling the core:
     * vanilla neighbour updates reach only the six faces, so a hull finished at
     * a diagonal was silently never noticed.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aCornerPanelCompletesTheHull(GameTestHelper helper) {
        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        buildHull(helper, CORE);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);

        // Remove and replace a diagonal - the case no face update can see.
        BlockPos corner = CORE.offset(1, 0, 1);
        helper.setBlock(corner, Blocks.AIR);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.ADRIFT);

        helper.setBlock(corner, OctiaBlocks.ANDESITE_FRAME_PANEL);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);
        helper.succeed();
    }
}
