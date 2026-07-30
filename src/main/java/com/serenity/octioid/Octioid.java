package com.serenity.octioid;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Octioid — a Serenity-class ship.
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
 * Keep it that way — a hardcoded {@code "octioid:something"} string literal is
 * the one thing a rename cannot find.
 */
public final class Octioid implements ModInitializer {

    /** The ResourceLocation namespace. Must equal {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "octioid";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Octioid() {
        // Fabric constructs this reflectively; the private ctor is for the
        // static helpers below, which are the class's real purpose.
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
        // Registration happens on the way through OctioidBlocks' static
        // initialiser. Doing it here, rather than at class-load time from some
        // earlier hook, is what guarantees the registries are open.
        OctioidBlocks.bootstrap();
        LOGGER.info("Octioid: hull cold, registry open. Andesite aboard.");
    }
}
