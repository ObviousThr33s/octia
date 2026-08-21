package com.serenity.octia.world;

import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Generate a world, then stop. The other half of tools/new-world.ps1.
 *
 * <p><b>Why the mod stops its own server.</b> A Minecraft server has no
 * generate-and-exit mode, so a script has to end it somehow, and the two
 * obvious ways are both bad. Killing the process races the final chunk save,
 * which is the one thing a world generator must never do. Writing {@code stop}
 * to its console means pushing a line through PowerShell, through the Gradle
 * launcher, and into a forked JVM - and that path put a byte-order mark in
 * front of the word twice, so the server answered "Unknown or incomplete
 * command: &lt;BOM&gt;stop" and kept running with the finished world locked
 * behind it. Connecting stdin at all also made every other Gradle task hang
 * waiting for an EOF no script sends.
 *
 * <p>None of that is worth debugging when the server is already running our
 * code. It stops itself, on the server thread, after the work is done.
 *
 * <p><b>Inert unless asked.</b> Both switches are system properties, read once
 * at server start. Nothing sets them but the generator script, so a normal game
 * pays one {@code getProperty} call and nothing else. This does mean a shipped
 * jar contains a hook that will shut a server down - it takes a deliberate
 * {@code -Doctia.worldgen.exit=true} on the command line to reach it, which is
 * not something anyone does by accident.
 */
public final class HeadlessRun {

    /** Set to run headlessly: generate, report, halt. */
    private static final String EXIT = "octia.worldgen.exit";

    /** Optional radius in chunks to generate past spawn, for density sampling. */
    private static final String RADIUS = "octia.worldgen.radius";

    /** How often to say something while grinding through a large radius. */
    private static final int PROGRESS_EVERY = 256;

    /**
     * A phase ordered after everything else on SERVER_STARTED.
     *
     * <p>The beacon and the spawn derelict are placed on that same event, and
     * this one halts the server. Getting the order wrong means halting before
     * the world has anything in it, and the run would look like a success -
     * a generated world, a clean exit, and no ruins. Registration order alone
     * would give the right answer today and silently stop doing so the moment
     * somebody reorders two lines in the entrypoint, so it is stated instead.
     */
    private static final ResourceLocation LAST = Octia.id("headless_last");

    private HeadlessRun() {
    }

    public static void bootstrap() {
        if (!Boolean.getBoolean(EXIT)) {
            return;
        }

        ServerLifecycleEvents.SERVER_STARTED.addPhaseOrdering(Event.DEFAULT_PHASE, LAST);
        ServerLifecycleEvents.SERVER_STARTED.register(LAST, server -> {
            int radius = Integer.getInteger(RADIUS, 0);
            Octia.LOGGER.info("Octia worldgen: headless run, radius {} chunk(s).", radius);

            if (radius > 0) {
                generate(server.overworld(), radius);
            }
            report(server);

            // halt(false): do not block waiting for the server thread, because
            // this IS the server thread. It sets the loop to stop and the normal
            // shutdown path runs from there, saving every chunk on the way out -
            // which is the entire reason this exists rather than a kill.
            Octia.LOGGER.info("Octia worldgen: done, stopping.");
            server.halt(false);
        });
    }

    /**
     * Forces a square of chunks around spawn to full generation.
     *
     * <p>Blocking, on the server thread, deliberately. Nobody is connected and
     * nothing is ticking that matters; the alternative is a tick-spread job that
     * has to decide when it is finished, which is more machinery for a tool that
     * exits the moment it is done.
     */
    private static void generate(ServerLevel level, int radius) {
        BlockPos spawn = level.getSharedSpawnPos();
        int cx0 = spawn.getX() >> 4;
        int cz0 = spawn.getZ() >> 4;

        int total = (radius * 2 + 1) * (radius * 2 + 1);
        int done = 0;

        for (int cx = cx0 - radius; cx <= cx0 + radius; cx++) {
            for (int cz = cz0 - radius; cz <= cz0 + radius; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
                if (++done % PROGRESS_EVERY == 0) {
                    Octia.LOGGER.info("Octia worldgen: {} / {} chunks", done, total);
                }
            }
        }
        Octia.LOGGER.info("Octia worldgen: {} chunks generated", done);
    }

    /** What the save ended up with, on one line, for the script to read back. */
    private static void report(MinecraftServer server) {
        OctiaWorldOption option = OctiaWorldOption.get(server);
        BlockPos beacon = option.beaconAt();
        Octia.LOGGER.info("Octia worldgen: enabled={} beacon={} moorings={}",
                option.enabled(),
                beacon == null ? "not recorded" : beacon.toShortString(),
                com.serenity.octia.ship.ShipMoorings.get(server).count());
    }
}
