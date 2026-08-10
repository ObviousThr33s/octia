package com.serenity.octia;

import com.serenity.octia.crew.Crew;
import com.serenity.octia.debug.OctiaDebug;
import com.serenity.octia.world.HeadlessRun;
import com.serenity.octia.world.OctiaBeacon;
import com.serenity.octia.world.OctiaWorldOption;
import com.serenity.octia.world.OctiaWorldgen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Octia — a Serenity-class ship.
 *
 * <p>This is the {@code main} entrypoint, the one that runs on both sides.
 * It is not the only one - {@code fabric.mod.json} also names
 * {@code client.OctiaClient} and the {@code gametest} classes - but it is the
 * one that owns everything below, and everything the mod registers is reached
 * from {@link #onInitialize()} in this order:
 *
 * <ol>
 *   <li>{@link OctiaBlocks#bootstrap()} - the blocks and their block items are
 *       filed into the registries on the way through that class's static
 *       initialiser; the call itself adds them to creative tabs.</li>
 *   <li>{@code ServerWorldEvents.LOAD} - reads the per-save switch the moment a
 *       save's Overworld loads, publishes it where the generation workers can
 *       see it, and on a save's first load raises the beacon and seats the
 *       spawn derelict.</li>
 *   <li>{@code ServerLifecycleEvents.SERVER_STOPPED} - clears that switch, so a
 *       second world opened in the same launch cannot inherit the first one's
 *       answer.</li>
 *   <li>{@link Crew#bootstrap()} - the muster's lifecycle hooks, its tick, what
 *       it can hear in chat, and the {@code /octia crew} commands.</li>
 *   <li>{@link OctiaDebug#bootstrap()} - the debug view's two payload types and
 *       the two hooks that serve them, registered common-side because both ends
 *       must know the types.</li>
 *   <li>{@link OctiaWorldgen#bootstrap()} - the two features, and the biome
 *       modifications that schedule them into the Overworld.</li>
 * </ol>
 *
 * <p>Nothing here starts a server, opens a save, or touches a world. Apart from
 * the closing log line it is all registry writes and event subscriptions, which
 * is what makes doing it eagerly safe, and why every per-save decision hangs off
 * an event instead of happening here.
 *
 * <p><b>On the name.</b> The mod's name is expected to change as goals,
 * milestones, and metrics change. Everywhere else it appears it is generated:
 * {@code fabric.mod.json} is templated from {@code gradle.properties}, and the
 * resource trees are moved by {@code tools/rename-mod.ps1}. {@link #MOD_ID}
 * below is the only place the name is written in Java, and {@link #id(String)}
 * is the only sanctioned way to build a {@link ResourceLocation} for this mod.
 * Keep it that way — a hardcoded {@code "octia:something"} string literal is
 * the one thing a rename cannot find.
 */
public final class Octia implements ModInitializer {

    /** The ResourceLocation namespace. Must equal {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "octia";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Must stay public and no-arg. Fabric Loader instantiates the entrypoint
     * reflectively through {@code DefaultLanguageAdapter}, so a private
     * constructor compiles clean and then dies at load with
     * {@code IllegalAccessException} - a failure no amount of building catches.
     */
    public Octia() {
    }

    /**
     * Builds a {@link ResourceLocation} in this mod's namespace.
     *
     * @param path a snake_case registry path, e.g. {@code "andesite_frame_panel"}
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // Registration happens on the way through OctiaBlocks' static
        // initialiser. Doing it here, rather than at class-load time from some
        // earlier hook, is what guarantees the registries are open.
        OctiaBlocks.bootstrap();

        // The world-create switch, read at the one moment it can be read: the
        // first time a save's Overworld loads. Asking earlier means asking
        // before the save directory exists.
        //
        // Only the FLAG is read here, and it has to be. What it publishes is
        // what the chunk-generation workers consult, and chunks begin
        // generating inside prepareLevels - before the server has started.
        ServerWorldEvents.LOAD.register((server, level) -> {
            if (level != server.overworld()) {
                return;
            }
            OctiaWorldOption option = OctiaWorldOption.get(server);
            OctiaWorldgen.setActive(option.enabled());

            if (!option.enabled()) {
                LOGGER.info("Octia: disabled for this world. Spawn left as vanilla found it.");
            }
        });

        // Placement waits for SERVER_STARTED, and the wait is the whole point.
        //
        // Both of these are placed relative to the world spawn, and at LOAD the
        // world spawn does not exist yet: Minecraft chooses it in prepareLevels,
        // which runs after every level is created. getSharedSpawnPos() answers
        // (0, y, 0) until then. On the seeds this was first built against that
        // was invisible, because their spawn genuinely is near the origin - but
        // on seed 1, spawn is (112, 67, 176), and the beacon went up 209 blocks
        // away from the player while the log cheerfully reported a derelict "48
        // blocks from spawn" that was 170 blocks from where anyone arrives.
        //
        // SERVER_STARTED is the first moment the answer is real.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            OctiaWorldOption option = OctiaWorldOption.get(server);
            if (!option.enabled() || !option.claimBeacon()) {
                return;
            }
            ServerLevel overworld = server.overworld();
            OctiaBeacon.raise(overworld);

            // The same claim covers both: this is the one moment a save is new,
            // and the guaranteed derelict is as much a first-start fact as the
            // beacon. A rarity filter cannot promise a player will ever meet
            // one, so the first is placed rather than rolled.
            OctiaWorldgen.placeNearSpawn(overworld);
        });

        // Cleared on the way out so a second world opened in the same launch
        // cannot inherit the first one's answer. Without this the switch leaks
        // between saves, which is the exact failure the switch exists to prevent.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OctiaWorldgen.setActive(false));

        // The crew. Registered here rather than lazily on first command because
        // the muster hangs off server start and stop, and a hook installed after
        // the server has already started has missed the only event it wanted.
        // "Lazily on first command" is not even on the table: Crew.bootstrap is
        // also what registers /octia, through CommandRegistrationCallback, which
        // fires while the server builds its command tree - before the
        // SERVER_STARTED that creates the muster. Five hooks in all: start,
        // stopping, end-of-tick, chat, and the command tree.
        Crew.bootstrap();

        // The debug view's payload types. Common, not client: a type known to
        // one side only is a disconnect on the first packet, and a dedicated
        // server has to know the C2S type to receive a request at all.
        OctiaDebug.bootstrap();

        // The terrain. Registered unconditionally because a biome modification
        // has to be; whether it actually places anything is decided per save,
        // inside the feature. See OctiaWorldgen for why it has to be that way.
        OctiaWorldgen.bootstrap();

        // Dev tooling, inert unless -Doctia.worldgen.exit=true is on the command
        // line. tools/new-world.ps1 is the only thing that sets it.
        HeadlessRun.bootstrap();

        LOGGER.info("Octia: hull cold, registry open. Andesite aboard.");
    }
}
