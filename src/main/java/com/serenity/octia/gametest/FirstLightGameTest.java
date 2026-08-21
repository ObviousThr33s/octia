package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.ship.ShipStatus;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * First light: a hull that completes says so, once.
 *
 * <p><b>What is asserted, and what is deliberately not.</b> The sound and the
 * motes are not tested and should not be - {@code EraEcho} settled that argument
 * already: "whether an echo should happen is a decision about the store and the
 * world and can be asserted; particles cannot." The decision here is
 * {@link ShipMoorings#moor}'s return value, because that boolean <i>is</i> the
 * event. If it is right, {@code FirstLight} fires exactly when it should; if it
 * is wrong, no amount of looking at particles would tell you which way.
 *
 * <p><b>One method, not three.</b> The suite runs every {@code @GameTest}
 * against a single server and the moorings store is save-wide, so assertions
 * about it cannot be ordered across methods. That is not hypothetical - splitting
 * a latch across two tests is exactly what failed while {@code KeepInventory} was
 * being written. The arc below runs forward and leaves its own position unmoored.
 */
public class FirstLightGameTest implements FabricGameTest {

    /** Off-centre and above the floor, clear of other tests' boxes. */
    private static final BlockPos CORE = new BlockPos(3, 2, 3);

    /**
     * It fires on the transition, not on the survey, and again after a genuine
     * unmoor.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void firstLightFiresOnceThenAgainAfterUnmooring(GameTestHelper helper) {
        ShipMoorings moorings = ShipMoorings.get(helper.getLevel().getServer());
        BlockPos absolute = helper.absolutePos(CORE);

        // The suite shares a world; another run may have left this position in
        // the store. Start from a known state rather than assuming one.
        moorings.unmoor(absolute);

        helper.setBlock(CORE, OctiaBlocks.SHIP_CORE);
        ring(helper, CORE);

        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);
        if (!moorings.isMoored(absolute)) {
            throw new AssertionError("the hull is intact but " + absolute + " never reached the store");
        }

        // The claim. reconcile runs again on every neighbour change and every
        // right-click, so if moor() answered true a second time First Light
        // would fire for a ship that has been sitting there all along.
        if (moorings.moor(absolute)) {
            throw new AssertionError("moor() answered 'new' for a position already moored - "
                    + "first light would fire on every survey");
        }

        // Mooring is a state, not an event: break the ring and the position
        // leaves the store, so completing it again is genuinely a new arrival.
        helper.setBlock(CORE.offset(1, 0, 0), Blocks.AIR);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.ADRIFT);
        if (moorings.isMoored(absolute)) {
            throw new AssertionError("a broken hull left " + absolute + " in the moorings");
        }

        helper.setBlock(CORE.offset(1, 0, 0), OctiaBlocks.ANDESITE_FRAME_PANEL);
        helper.assertBlockProperty(CORE, ShipCoreBlock.STATUS, ShipStatus.MOORED);
        if (!moorings.isMoored(absolute)) {
            throw new AssertionError("re-completing the hull did not moor " + absolute + " again");
        }

        // Left as found, so the next test to use this position starts clean.
        moorings.unmoor(absolute);
        helper.succeed();
    }

    /** The eight horizontal neighbours, which is what {@code hullIntact} counts. */
    private static void ring(GameTestHelper helper, BlockPos core) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                helper.setBlock(core.offset(dx, 0, dz), OctiaBlocks.ANDESITE_FRAME_PANEL);
            }
        }
    }
}
