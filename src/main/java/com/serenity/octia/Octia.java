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
 * below is the only place the name is written in Java, and {@link #id(String)},
 * {@link #tr(String)}, {@link #keyBind(String)}, {@link #keyCategory()} and
 * {@link #property(String)} are the only sanctioned ways to build a string that
 * carries it. Keep it that way — a hardcoded {@code "octia:something"} or
 * {@code "octia.crew.none"} is the one thing a rename cannot find.
 *
 * <p><b>Three exceptions, and they are frozen rather than forgotten.</b>
 * {@code ShipMoorings}, {@code OctiaWorldOption} and {@code CrewConfig} each
 * name a file on disk. Those names are a contract with every save already
 * written, so they must <em>not</em> follow a rename; each carries a comment
 * saying so at its declaration.
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

    /**
     * A translation key in this mod's namespace, e.g. {@code tr("crew.aboard")}
     * for {@code octia.crew.aboard}.
     *
     * <p><b>Why this exists, when {@link #id(String)} already did.</b> The rule
     * above was written about {@code ResourceLocation} and stopped there, which
     * left every {@code Component.translatable("octia.crew.none")} outside it -
     * twenty-one of them. The rename script rewrites the package, the class
     * name and the {@link #MOD_ID} literal, and nothing else in Java, so all
     * twenty-one would survive a rename while {@code en_us.json} moved on
     * without them. The mod builds, loads, and shows the player a raw key.
     *
     * <p>Block and item names do not need this - Minecraft derives those from
     * the {@link ResourceLocation}, which is why the rename has never broken
     * them and why the gap went unnoticed.
     */
    public static String tr(String path) {
        return MOD_ID + "." + path;
    }

    /** A keybind translation key: {@code keyBind("debug")} is {@code key.octia.debug}. */
    public static String keyBind(String path) {
        return "key." + MOD_ID + "." + path;
    }

    /** This mod's keybind category, {@code key.categories.octia}. */
    public static String keyCategory() {
        return "key.categories." + MOD_ID;
    }

    /**
     * A system property name in this mod's namespace.
     *
     * <p><b>Half of a coupling that nothing enforces.</b> The other half is in
     * {@code build.gradle.kts}, which passes {@code -Doctia.worldgen.exit} and
     * {@code -Doctia.worldgen.radius} to the headless worldgen run, and
     * {@code tools/new-world.ps1}, which passes the Gradle properties that turn
     * those on. Change the name on one side only and nothing fails: the
     * property is simply never set, {@code Boolean.getBoolean} answers false,
     * and the headless server runs forever instead of stopping. Move all three
     * together.
     */
    public static String property(String path) {
        return MOD_ID + "." + path;
    }

    @Override
    public void onInitialize() {
        // Registration happens on the way through OctiaBlocks' static
        // initialiser. Doing it here, rather than at class-load time from some
        // earlier hook, is what guarantees the registries are open.
        OctiaBlocks.bootstrap();

        // The world-create switch, read at the one moment it can be read: the
        // first time a save's Overworld loads. Asking earlier means asking
        // before the save directory exists; asking later means the player is
        // already standing in a world that should have looked different.
        ServerWorldEvents.LOAD.register((server, level) -> {
            if (level != server.overworld()) {
                return;
            }
            OctiaWorldOption option = OctiaWorldOption.get(server);

            // Published here, on the server thread, before a single chunk can
            // generate - this is the only read of the save's flag that the
            // worldgen workers are allowed to depend on. See OctiaWorldgen.
            OctiaWorldgen.setActive(option.enabled());

            if (!option.enabled()) {
                LOGGER.info("Octia: disabled for this world. Spawn left as vanilla found it.");
                return;
            }
            if (option.claimBeacon()) {
                OctiaBeacon.raise(level);

                // The same claim covers both: this is the one moment a save is
                // new, and the guaranteed derelict is as much a first-load fact
                // as the beacon is. A rarity filter cannot promise a player will
                // ever meet one, so the first is placed rather than rolled.
                OctiaWorldgen.placeNearSpawn(level);
            }
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
