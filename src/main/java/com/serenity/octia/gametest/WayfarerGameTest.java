package com.serenity.octia.gametest;

import com.serenity.octia.crew.WayfarerLedger;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * MILESTONE 2 - strangers on the road.
 *
 * <p>What a wayfarer <em>says</em> is a language model's business and is not
 * testable, nor should it be. What is testable is the part built to survive a
 * model having a bad day: the ledger that lets somebody be remembered by place,
 * and the two rules that keep chaos harmless.
 *
 * <p>The band and the permitted vocabulary live in JUnit rather than here - they
 * are arithmetic and a switch, and {@code WayfarerRulesTest} reaches them without
 * needing a world. That split is AGENTS.md rule III: pure logic gets a unit
 * test, in-world behaviour gets a GameTest.
 */
public class WayfarerGameTest implements FabricGameTest {

    /**
     * The ledger remembers a person and where they were met.
     *
     * <p>This is the feature, not a detail of it. Anyone can spawn a wanderer;
     * one who turns up a second time and opens with the <em>place</em> is a
     * different thing, because it means the first meeting outlived itself.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theLedgerRemembersWhereSomebodyWasMet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WayfarerLedger ledger = WayfarerLedger.get(server);

        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        ledger.remember("Kestrel", where);

        if (!ledger.hasMet("Kestrel")) {
            throw new AssertionError("the ledger did not record the meeting");
        }
        if (!where.equals(ledger.where("Kestrel"))) {
            throw new AssertionError("the ledger recorded " + ledger.where("Kestrel")
                    + " but the meeting was at " + where);
        }
        helper.succeed();
    }

    /** Somebody never met is not remembered, and says so rather than guessing. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anUnmetStrangerIsNotRemembered(GameTestHelper helper) {
        WayfarerLedger ledger = WayfarerLedger.get(helper.getLevel().getServer());

        if (ledger.hasMet("NobodyAtAll")) {
            throw new AssertionError("the ledger claims to have met somebody it has not");
        }
        if (ledger.where("NobodyAtAll") != null) {
            throw new AssertionError("the ledger invented a place for an unmet stranger");
        }
        helper.succeed();
    }

    /**
     * The latest meeting is the one that is kept.
     *
     * <p>A traveller remembers roughly where somebody was last standing, not a
     * history of every road they have shared. Anything richer would be a promise
     * the rest of the design cannot keep.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void meetingAgainMovesThePlace(GameTestHelper helper) {
        WayfarerLedger ledger = WayfarerLedger.get(helper.getLevel().getServer());

        BlockPos first = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(3, 1, 3));

        ledger.remember("Vesper", first);
        ledger.remember("Vesper", second);

        if (!second.equals(ledger.where("Vesper"))) {
            throw new AssertionError("the ledger kept the older meeting at " + ledger.where("Vesper"));
        }
        helper.succeed();
    }

    /** The store is one per save, like the moorings, not one per dimension. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theLedgerIsOnePerSave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        if (WayfarerLedger.get(server) != WayfarerLedger.get(server)) {
            throw new AssertionError("two calls resolved different ledgers");
        }
        helper.succeed();
    }
}
