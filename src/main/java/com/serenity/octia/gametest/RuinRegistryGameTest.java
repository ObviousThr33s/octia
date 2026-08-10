package com.serenity.octia.gametest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.serenity.octia.world.RuinRegistry;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * MILESTONE 2 - the landmark registry.
 *
 * <p>A feature leaves no record of itself anywhere, so this is the only thing in
 * the mod that can answer "where are the ruins" from inside the game. What makes
 * it worth testing is not the map - it is the thread boundary underneath.
 * Features run on chunk-generation workers and {@code SavedData} belongs to the
 * server thread, so recording is a queue and a drain rather than a write, and a
 * regression there would look like landmarks that mostly appear.
 */
public class RuinRegistryGameTest implements FabricGameTest {

    private static final String KIND = "gametest_marker";

    /** A reported landmark reaches the store once the tick has drained it. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aReportedRuinIsRemembered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));

        RuinRegistry.report(helper.getLevel(), KIND, where);

        // The drain runs on END_SERVER_TICK, which has not happened yet inside
        // this test method. Waiting for it is the point rather than an
        // inconvenience: it is what proves the queue is actually being emptied.
        //
        // assertTrue rather than a thrown AssertionError. succeedWhen retries
        // its runnable every tick and catches GameTestAssertException only, so
        // a plain AssertionError escapes into the server tick loop and takes the
        // whole game test server down with a crash report - which is how this
        // was found.
        helper.succeedWhen(() -> helper.assertTrue(
                RuinRegistry.get(server).of(KIND).contains(where),
                "reported " + where + " under " + KIND + " but it never reached the store"));
    }

    /**
     * <b>The load-bearing test.</b> Reporting from another thread is safe.
     *
     * <p>This is how it actually happens - a feature on a chunk worker, while
     * the server thread is reading the same store. If {@code report} ever starts
     * touching {@code SavedData} directly, this is the shape of code that finds
     * it, and finding it here is enormously better than finding it as an
     * intermittent crash on a fresh world generating at speed.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void reportingFromAnotherThreadIsSafe(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));

        CountDownLatch done = new CountDownLatch(4);
        for (int worker = 0; worker < 4; worker++) {
            int offset = worker;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 32; i++) {
                        RuinRegistry.report(helper.getLevel(), KIND, base.offset(offset, 0, i));
                    }
                } finally {
                    done.countDown();
                }
            }, "octia-gametest-reporter-" + worker);
            thread.setDaemon(true);
            thread.start();
        }

        try {
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("the reporting threads did not finish");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while reporting", interrupted);
        }

        helper.succeedWhen(() -> helper.assertTrue(
                RuinRegistry.get(server).of(KIND).size() >= 128,
                "128 landmarks were reported from four threads but only "
                        + RuinRegistry.get(server).of(KIND).size()
                        + " arrived - the queue is losing entries"));
    }

    /** The nearest of a kind is found, and a far one is not. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theNearestLandmarkIsFound(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos here = helper.absolutePos(new BlockPos(1, 1, 1));

        RuinRegistry.report(helper.getLevel(), KIND, here.offset(8, 0, 0));
        RuinRegistry.report(helper.getLevel(), KIND, here.offset(64, 0, 0));

        helper.succeedWhen(() -> {
            RuinRegistry registry = RuinRegistry.get(server);
            BlockPos near = registry.nearest(here, KIND, 32.0);

            helper.assertTrue(near != null && near.getX() == here.getX() + 8,
                    "the nearest landmark within 32 blocks was " + near);
            helper.assertTrue(registry.nearest(here, KIND, 4.0) == null,
                    "a landmark 8 blocks away was found within a 4 block range");
            helper.assertTrue(registry.nearest(here, "no_such_kind", 999.0) == null,
                    "a kind that was never recorded returned a position");
        });
    }
}
