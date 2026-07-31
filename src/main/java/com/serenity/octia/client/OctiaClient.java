package com.serenity.octia.client;

import com.serenity.octia.Octia;
import com.serenity.octia.world.OctiaWorldOption;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * The switch, on the screen where a world is decided.
 *
 * <p>A button on {@code CreateWorldScreen} reading <i>Octia: On</i> or
 * <i>Octia: Off</i>. It sets {@link OctiaWorldOption#setPending(boolean)}; the
 * world's own store picks that up the first time the save is opened and keeps
 * it forever after. Toggling it later has no effect on worlds already made,
 * which is the intent — the choice belongs to the world, not to the client.
 *
 * <p>Done with {@code ScreenEvents.AFTER_INIT} rather than a mixin. Fabric
 * offers this hook precisely so a mod can add a widget to a vanilla screen
 * without patching bytecode, and a mixin into a screen is a class of breakage
 * that arrives silently on the next Minecraft release.
 */
public final class OctiaClient implements ClientModInitializer {

    /**
     * Top-left, above the tab row. The create screen lays its own widgets out
     * from the centre, so the corner is the one place a foreign button does not
     * collide with vanilla at any window size.
     */
    private static final int X = 6;
    private static final int Y = 6;
    private static final int W = 110;
    private static final int H = 20;

    /** Must stay public and no-arg — Fabric instantiates entrypoints reflectively. */
    public OctiaClient() {
    }

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof CreateWorldScreen)) {
                return;
            }
            Button button = Button.builder(label(), b -> {
                OctiaWorldOption.setPending(!OctiaWorldOption.pending());
                b.setMessage(label());
                Octia.LOGGER.info("Octia: next world will be created {}.",
                        OctiaWorldOption.pending() ? "ENABLED" : "DISABLED");
            }).bounds(X, Y, W, H).build();

            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("octia.create_world.toggle.tooltip")));

            Screens.getButtons(screen).add(button);
        });
    }

    private static Component label() {
        return Component.translatable(OctiaWorldOption.pending()
                ? "octia.create_world.toggle.on"
                : "octia.create_world.toggle.off");
    }
}
