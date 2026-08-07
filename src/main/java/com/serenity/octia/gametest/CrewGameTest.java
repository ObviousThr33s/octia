package com.serenity.octia.gametest;

import com.serenity.octia.crew.Crew;
import com.serenity.octia.crew.CrewPlayer;
import com.serenity.octia.crew.Order;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * MILESTONE 2 - the crew.
 *
 * <p>Everything here is about the seam, not about the models. Whether a cleric
 * gives good orders is a question about the cleric; whether a body with no
 * client can be put in the player list, and whether it ticks once it is there,
 * is a question about this mod, and it is not answerable anywhere but in a
 * running server.
 *
 * <p>The second test is the load-bearing one. A crew member is a
 * {@code ServerPlayer} whose movement tick vanilla calls from the network
 * listener — a listener that never runs here, because there is no socket. If
 * {@code CrewPlayer.tick} ever stops calling {@code doTick} by hand, everything
 * still compiles, everything still logs on, the tab list still fills up, and
 * every crew member stands frozen forever. Nothing else in the suite would
 * notice.
 */
public class CrewGameTest implements FabricGameTest {

    /** Legal, unmistakable, and short enough to leave room for a suffix. */
    private static final String SEAT = "Testcleric";

    /** A floor to stand on. The empty structure does not come with one. */
    private static final int FLOOR = 6;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aClericTakesASeatInThePlayerList(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Crew crew = crew(server);

        CrewPlayer body = crew.summon(SEAT, "", helper.getLevel(), stand(helper, 2, 2), 0.0F);
        try {
            if (body == null) {
                throw new AssertionError("summon refused a legal name that nobody was using");
            }
            if (server.getPlayerList().getPlayerByName(SEAT) != body) {
                throw new AssertionError("aboard, but the player list cannot find " + SEAT
                        + " - a crew member the list does not know is not logged on");
            }
            if (!server.getPlayerList().getPlayers().contains(body)) {
                throw new AssertionError(SEAT + " is not among the server's players");
            }
            if (body.connection == null) {
                throw new AssertionError("no packet listener was attached; placeNewPlayer did not complete");
            }
        } finally {
            if (body != null) {
                crew.logOff(body, "the test is over");
            }
        }

        if (server.getPlayerList().getPlayerByName(SEAT) != null) {
            throw new AssertionError(SEAT + " is still in the player list after being dismissed");
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> Ordered north, a crew member has to actually
     * travel north, which can only happen if its body is being ticked.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aCrewMemberTicksItselfAndWalks(GameTestHelper helper) {
        floor(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Crew crew = crew(server);

        CrewPlayer body = crew.summon(SEAT + "2", "", helper.getLevel(), stand(helper, 2, 4), 0.0F);
        if (body == null) {
            throw new AssertionError("summon refused a legal name that nobody was using");
        }

        double startZ = body.getZ();
        body.setOrder(new Order(Order.Verb.GO, "north"));

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    double travelled = startZ - body.getZ();
                    crew.logOff(body, "the test is over");
                    if (travelled < 0.3) {
                        throw new AssertionError("ordered north and moved " + travelled
                                + " blocks in ten ticks. A crew member that does not move is a crew member"
                                + " whose body is not being ticked - see CrewPlayer.tick.");
                    }
                })
                .thenSucceed();
    }

    /**
     * Speaking must not need anyone to speak to.
     *
     * <p>A crew member alone on a server is the ordinary case while testing, and
     * broadcasting to an empty room is exactly where a chat path that assumes a
     * recipient falls over. The order is also asserted to clear itself: a
     * one-shot order that stays set says the same line every tick forever.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void speakingToAnEmptyRoomIsHarmless(GameTestHelper helper) {
        floor(helper);
        Crew crew = crew(helper.getLevel().getServer());

        CrewPlayer body = crew.summon(SEAT + "3", "", helper.getLevel(), stand(helper, 2, 2), 0.0F);
        if (body == null) {
            throw new AssertionError("summon refused a legal name that nobody was using");
        }
        body.setOrder(new Order(Order.Verb.SAY, "hello, glad to be aboard"));

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    Order after = body.order();
                    crew.logOff(body, "the test is over");
                    if (after.verb() != Order.Verb.HOLD) {
                        throw new AssertionError("a spoken line left the order set to " + after
                                + "; it would be repeated every tick");
                    }
                })
                .thenSucceed();
    }

    // ---- shared --------------------------------------------------------------

    private static Crew crew(MinecraftServer server) {
        Crew crew = Crew.of(server);
        if (crew == null) {
            throw new AssertionError("no muster on a started server - Crew.bootstrap missed SERVER_STARTED");
        }
        return crew;
    }

    /** Feet on top of the floor block at this spot in the test structure. */
    private static Vec3 stand(GameTestHelper helper, int x, int z) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z)));
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < FLOOR; x++) {
            for (int z = 0; z < FLOOR; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}
