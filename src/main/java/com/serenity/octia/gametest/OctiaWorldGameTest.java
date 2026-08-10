package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipStatus;
import com.serenity.octia.world.OctiaBeacon;
import com.serenity.octia.world.OctiaWorldOption;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The world-create switch, and the thing it switches on.
 *
 * <p>The button itself cannot be clicked here — {@code CreateWorldScreen} is
 * client-only and no server-side test can reach it. What these pin is the part
 * that actually carries the decision: the store the button writes through, and
 * the structure the store gates. If the flag round-trips and the beacon builds,
 * the only untested link left is the click, and a click that sets one boolean
 * is the cheapest link in the chain to inspect by eye.
 */
public class OctiaWorldGameTest implements FabricGameTest {

    /** Off-centre and above the floor, so the plinth has room inside the test box. */
    private static final BlockPos BASE = new BlockPos(3, 2, 3);

    /**
     * The store can be built for the running world at all.
     *
     * <p>The null below is unreachable: {@code computeIfAbsent}
     * constructs one when the file is absent and also when reading it
     * fails, which it swallows. What this pins is therefore narrow -
     * that {@code get} returns rather than throws, which is the
     * {@code SavedData.Factory} wiring and no more. A wrong file name
     * or a null {@code DataFixTypes} would both still pass here; see
     * the note in {@code ShipMoorings} for why the second loses data
     * quietly instead of crashing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theWorldHasAnOption(GameTestHelper helper) {
        OctiaWorldOption option = OctiaWorldOption.get(helper.getLevel().getServer());
        if (option == null) {
            throw new AssertionError("every world must carry an Octia option");
        }
        helper.succeed();
    }

    /**
     * The beacon is raised once and only once.
     *
     * <p>This is what stops a mast growing a second mast on every reload: the
     * claim is taken from the same saved store the flag lives in, so it survives
     * a restart rather than resetting with the JVM.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBeaconIsClaimedOnlyOnce(GameTestHelper helper) {
        OctiaWorldOption option = OctiaWorldOption.get(helper.getLevel().getServer());

        boolean first = option.claimBeacon();
        boolean second = option.claimBeacon();

        // The suite shares one world, so another test may already have claimed it.
        // The claim that matters is that it is never available twice in a row.
        if (first && second) {
            throw new AssertionError("the beacon was claimed twice - reloading would stack masts");
        }
        if (!option.beaconRaised()) {
            throw new AssertionError("after a claim the beacon must read as raised");
        }
        helper.succeed();
    }

    /**
     * The pending flag is what the create-screen button actually writes.
     *
     * <p><b>And that is all it proves.</b> Both assertions read back a
     * value this method has just written to a static field, so the only
     * regression it can catch is a setter that ignores its argument. The
     * link that carries the decision - pending, through the no-arg
     * constructor, into {@code enabled()} - is not exercised and cannot
     * be from here: {@code get} memoises one store per save, and this
     * suite shares a world that already has one.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSwitchRemembersBothPositions(GameTestHelper helper) {
        boolean original = OctiaWorldOption.pending();
        try {
            OctiaWorldOption.setPending(false);
            if (OctiaWorldOption.pending()) {
                throw new AssertionError("the switch would not go off");
            }
            OctiaWorldOption.setPending(true);
            if (!OctiaWorldOption.pending()) {
                throw new AssertionError("the switch would not go on");
            }
        } finally {
            OctiaWorldOption.setPending(original);
        }
        helper.succeed();
    }

    /**
     * The structure itself: a lit mast on a plinth, ringed by frame panels.
     *
     * <p>Asserted block by block rather than by eye, because "there is something
     * near spawn" is exactly the kind of claim that stays true in a screenshot
     * long after it has stopped being true in the code.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBeaconBuildsWhatItPromises(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BASE);
        OctiaBeacon.build(helper.getLevel(), base);

        // The ring is present, and it is the hull shape the core recognises.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                helper.assertBlockPresent(OctiaBlocks.ANDESITE_FRAME_PANEL,
                        BASE.offset(dx, 0, dz));
            }
        }

        // The mast is lit along its whole length - an unlit beacon is not a beacon.
        for (int dy = 0; dy < 12; dy++) {
            helper.assertBlockPresent(OctiaBlocks.ANDESITE_FRAME_PANEL, BASE.above(dy));
            helper.assertBlockProperty(BASE.above(dy), AndesiteFramePanelBlock.LIGHT, PanelLight.STYLED);
        }

        // The crown must not be ADRIFT. A core derives its own status from its
        // surroundings, so one placed bare at the mast top reads itself back as adrift -
        // and adrift is light level 0, which would leave the top of a beacon dark. This
        // assertion is the reason the crown is ringed.
        helper.assertBlockPresent(OctiaBlocks.SHIP_CORE, BASE.above(12));
        if (helper.getBlockState(BASE.above(12)).getValue(ShipCoreBlock.STATUS) == ShipStatus.ADRIFT) {
            throw new AssertionError("the crown core is adrift, so the top of the beacon is unlit");
        }

        helper.succeed();
    }
}
