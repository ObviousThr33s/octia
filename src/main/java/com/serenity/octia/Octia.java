package com.serenity.octia;

import com.serenity.octia.crew.Crew;
import com.serenity.octia.debug.OctiaDebug;
import com.serenity.octia.world.OctiaBeacon;
import com.serenity.octia.world.OctiaWorldOption;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Octia — a Serenity-class ship.
 *
 * <p>Nothing is registered yet. This is the bare Fabric entrypoint: it proves
 * the toolchain, the mappings, and the loader handshake all work before any
 * content leans on them.
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
        // before the save directory exists; asking later means the player is
        // already standing in a world that should have looked different.
        ServerWorldEvents.LOAD.register((server, level) -> {
            if (level != server.overworld()) {
                return;
            }
            OctiaWorldOption option = OctiaWorldOption.get(server);
            if (!option.enabled()) {
                LOGGER.info("Octia: disabled for this world. Spawn left as vanilla found it.");
                return;
            }
            if (option.claimBeacon()) {
                OctiaBeacon.raise(level);
            }
        });

        // The crew. Registered here rather than lazily on first command because
        // the muster hangs off server start and stop, and a hook installed after
        // the server has already started has missed the only event it wanted.
        Crew.bootstrap();

        // The debug view's payload types. Common, not client: a type known to
        // one side only is a disconnect on the first packet, and a dedicated
        // server has to know the C2S type to receive a request at all.
        OctiaDebug.bootstrap();

        LOGGER.info("Octia: hull cold, registry open. Andesite aboard.");
    }
}
