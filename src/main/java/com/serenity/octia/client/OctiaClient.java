package com.serenity.octia.client;

import java.util.List;

import com.serenity.octia.Octia;
import com.serenity.octia.world.OctiaWorldOption;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
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
 *
 * <p><b>AFTER_INIT fires more than once.</b> Fabric's {@code ScreenMixin} raises
 * it from the tail of both {@code init} and {@code resize}, and every window
 * resize or GUI-scale change goes through {@code resize}. On an ordinary screen
 * that is harmless, because {@code Screen.resize} rebuilds its widget lists from
 * empty first — but {@code CreateWorldScreen} overrides
 * {@code repositionElements()} to only re-arrange its tab bar and layout, so a
 * half-typed world name survives a resize and so does every widget already in
 * the list. Adding a freshly built button each time therefore stacks duplicates
 * forever, and they desync: rendering walks the list forward so the newest one
 * is what you see, while click dispatch walks it forward and stops at the first
 * hit, so the oldest one is the only one that ever updates its label. Hence the
 * marker subclass and the sweep below — re-entry has to be idempotent.
 */
public final class OctiaClient implements ClientModInitializer {

    /** Height of the button, and the gap it keeps from anything vanilla. */
    private static final int H = 20;
    private static final int GAP = 4;

    /** Widest we ever draw. Narrower windows get less; see {@link #widthFor}. */
    private static final int MAX_W = 110;

    /**
     * Narrowest still worth drawing. Vanilla {@code Button} renders its label
     * with a scrolling, scissored string, so a short button truncates rather
     * than spilling over its neighbour.
     *
     * <p><b>A floor, so it can beat {@link #widthFor}'s own rule.</b> The GUI
     * scale is chosen to keep the scaled width at 320 or more, so 320 is the
     * case to check: the gutter is (320 - 210) / 2 = 55 and only 55 - 4 - 4 =
     * 47 is free, but this floor draws 54 from x = 4 anyway. The button then
     * ends at 58 and its last three pixels sit under the tab column, where -
     * exactly as {@link #widthFor} sets out - the vanilla widget beneath takes
     * the click. It overlaps at all for scaled widths 320 to 325.
     */
    private static final int MIN_W = 54;

    /** Left margin. Small, because the gutter it lives in can be small. */
    private static final int X = 4;

    /**
     * Width of the centred column {@code CreateWorldScreen} lays its own tabs
     * out in — {@code CreateWorldScreen.TAB_COLUMN_WIDTH}, which is not public.
     * The free gutter is what is left of it on the left-hand side.
     */
    private static final int TAB_COLUMN_WIDTH = 210;

    /** Fallback height of the tab strip if the bar cannot be found. */
    private static final int TAB_STRIP_HEIGHT = 24;

    /**
     * The last create-screen this class chose a world type for.
     *
     * <p>Identity only - it is never dereferenced, so what it points at does
     * not matter, only whether it is the same object. Weak because a strong
     * static would pin one dead screen for the rest of the launch, and a
     * create-world screen holds the entire world-creation context behind it:
     * every loaded datapack's registries.
     */
    private static java.lang.ref.Reference<CreateWorldScreen> defaulted =
            new java.lang.ref.WeakReference<>(null);

    /** Must stay public and no-arg — Fabric instantiates entrypoints reflectively. */
    public OctiaClient() {
    }

    @Override
    public void onInitializeClient() {
        OctiaDebugOverlay.bootstrap();

        // The suit. Registered here rather than lazily on first render because
        // both halves of it are one-shot registrations - a model layer that has
        // to exist before any model is baked, and a callback that fires while
        // the renderers are being built. A hook installed after that has missed
        // the only event it wanted.
        HevSuit.bootstrap();

        // The squid, for the same reason and in the same shape: a model layer
        // that has to exist before any model is baked, and a renderer that has
        // to exist before any entity is drawn. Both are one-shot registrations,
        // and both are too late if they wait for the first sighting.
        VoidSquidRenderer.bootstrap();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof CreateWorldScreen)) {
                return;
            }

            List<AbstractWidget> buttons = Screens.getButtons(screen);

            // Sweep first. Counting down because removal shifts the tail left,
            // and removeIf would rely on ButtonList's iterator rather than on
            // the indexed remove it definitely implements.
            for (int i = buttons.size() - 1; i >= 0; i--) {
                if (buttons.get(i) instanceof OctiaToggle) {
                    buttons.remove(i);
                }
            }

            // The world type follows the switch, but only on the way in.
            //
            // AFTER_INIT fires again on every resize (see the class note), and
            // re-applying there would undo a world type the player picked from
            // the button between two window drags. So the default is applied
            // once per screen, on the first pass, and after that only a click on
            // the toggle moves it.
            CreateWorldScreen create = (CreateWorldScreen) screen;
            if (defaulted.get() != create) {
                defaulted = new java.lang.ref.WeakReference<>(create);
                SkyChoice.follow(create, OctiaWorldOption.pending());
            }

            int y = tabStripBottom(screen) + GAP;
            OctiaToggle button = new OctiaToggle(X, y, widthFor(scaledWidth), H, label(), b -> {
                OctiaWorldOption.setPending(!OctiaWorldOption.pending());
                b.setMessage(label());
                SkyChoice.follow(create, OctiaWorldOption.pending());
                Octia.LOGGER.info("Octia: next world will be created {}.",
                        OctiaWorldOption.pending() ? "ENABLED" : "DISABLED");
            });

            button.setTooltip(Tooltip.create(
                    Component.translatable("octia.create_world.toggle.tooltip")));

            buttons.add(button);
        });
    }

    /**
     * How wide the button may be without reaching the centred tab column.
     *
     * <p>Overlap is not merely ugly here, it is silent: the tab bar and the tab
     * column are registered by {@code init()} before anything a mod appends, and
     * click dispatch stops at the first widget under the cursor — so an
     * overlapping foreign button still draws on top while the vanilla widget
     * underneath quietly eats every click in the shared region.
     */
    private static int widthFor(int scaledWidth) {
        int gutter = (scaledWidth - TAB_COLUMN_WIDTH) / 2;
        int available = gutter - X - GAP;
        if (available >= MAX_W) {
            return MAX_W;
        }
        return Math.max(MIN_W, available);
    }

    /**
     * Bottom edge of the tab strip, read off the bar itself rather than assumed.
     *
     * <p>The strip is a private field of {@code CreateWorldScreen}, so it is
     * found by type among the screen's children. If a future version reshapes or
     * removes it, {@link #TAB_STRIP_HEIGHT} keeps the button off the tabs
     * instead of dropping it on top of them.
     */
    private static int tabStripBottom(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof TabNavigationBar bar) {
                return bar.getRectangle().bottom();
            }
        }
        return TAB_STRIP_HEIGHT;
    }

    private static Component label() {
        return Component.translatable(OctiaWorldOption.pending()
                ? "octia.create_world.toggle.on"
                : "octia.create_world.toggle.off");
    }

    /**
     * A {@link Button} that can be recognised on the way back in.
     *
     * <p>{@code Button.builder(...).build()} hands back a plain {@code Button},
     * and Fabric's button list de-duplicates on reference identity only, so
     * there would be no way to tell one of ours from vanilla's on the next
     * AFTER_INIT. The subclass is the marker; it adds no behaviour.
     */
    private static final class OctiaToggle extends Button {
        private OctiaToggle(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }
    }
}
