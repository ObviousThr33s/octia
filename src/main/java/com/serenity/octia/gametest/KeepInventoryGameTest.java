package com.serenity.octia.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Keep-inventory is held on, not merely switched on once.
 *
 * <p>The distinction is the whole mechanic. A one-shot {@code set} at world load
 * passes a naive test and then loses to a single {@code /gamerule} - and it would
 * lose silently, because nothing about a wrong game rule looks wrong until
 * somebody dies.
 *
 * <p><b>One test, not two, and the reason is the shared world.</b> This was
 * first written as a pair: one asserting the rule is on, one turning it off and
 * requiring it back. They raced. The suite runs every {@code @GameTest} against
 * a single server, so the second test's deliberate {@code set(false)} is visible
 * to the first, and "the rule is on" failed against a world some other test had
 * just switched off on purpose - see the same warning on
 * {@code OctiaWorldGameTest.theBeaconIsClaimedOnlyOnce}. Split assertions about
 * one global cannot be ordered, so they are not split. The arc below leaves the
 * rule the way it found it, which is also what makes it safe for its neighbours.
 *
 * <p><b>What the opening assertion is worth.</b> Less than it looks. By the time
 * any gametest runs, ticks have gone by, so an on-rule here could have come from
 * the load hook or from the tick hold and this cannot tell which. It is a
 * precondition, not proof of the load path. The claim that carries weight is the
 * one after the delay.
 *
 * <p>No player is killed here. Whether vanilla honours the rule is Mojang's test;
 * what is ours is the value of the rule.
 */
public class KeepInventoryGameTest implements FabricGameTest {

    /** Long enough for END_SERVER_TICK to have run, short enough to stay cheap. */
    private static final int SETTLE_TICKS = 2;

    /**
     * On to begin with, and it will not stay off.
     *
     * <p>Delayed rather than immediate on purpose: asserting in the same tick
     * would pass against a version of this mod that never installed the tick hook
     * at all, since the value would simply not have been read yet.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void keepInventoryIsHeldOn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameRules.BooleanValue rule = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);

        if (!rule.get()) {
            throw new AssertionError("an Octia world was running with keepInventory off");
        }

        rule.set(false, server);
        if (rule.get()) {
            throw new AssertionError("the test could not turn the rule off, so it proves nothing");
        }

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            if (!rule.get()) {
                throw new AssertionError(
                        "keepInventory stayed off after " + SETTLE_TICKS + " ticks - the hold is not running");
            }
            helper.succeed();
        });
    }
}
