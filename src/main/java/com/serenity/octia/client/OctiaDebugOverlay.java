package com.serenity.octia.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.serenity.octia.debug.OctiaDebug;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import org.lwjgl.glfw.GLFW;

/**
 * The debug map: where Octia's generations are, drawn on the HUD.
 *
 * <p>North is up and the map does not rotate with the player. That is on
 * purpose. A rotating minimap is nicer to navigate by and worse to debug with,
 * because two people comparing screenshots of the same save see different
 * pictures. Fixed north means a mark at the top of the box is at lower Z, every
 * time, for everyone.
 *
 * <p><b>What it can and cannot show.</b> The moorings store is keyed by position
 * with no dimension, deliberately — see {@code ShipMoorings}. So this cannot
 * know whether a given mark belongs to the dimension you are standing in, and it
 * does not guess: it plots every mooring in the save and says as much in the
 * readout. A mark on the map is a claim about coordinates, not about here.
 *
 * <p>Off-map moorings are not dropped. They are clamped to the edge of the box
 * and drawn dimmer, so the direction to the nearest thing is still readable when
 * everything is out of range - an empty box and a box whose contents are all
 * far away are very different states, and a debug tool that renders them
 * identically is lying by omission.
 */
public final class OctiaDebugOverlay {

    /** Box size in pixels. Square; big enough to read, small enough to ignore. */
    private static final int SIZE = 112;

    /** Distance from the top-right corner of the screen. */
    private static final int MARGIN = 6;

    /** Ranges the map cycles through, in blocks from the centre to an edge. */
    private static final int[] RANGES = {64, 128, 256, 512, 1024};

    /** Ticks between refreshes while the overlay is open. Twenty is one second. */
    private static final int REFRESH_TICKS = 40;

    private static final int COLOUR_PANEL = 0xB0000000;
    private static final int COLOUR_EDGE = 0xFF4A4A4A;
    private static final int COLOUR_GRID = 0x33FFFFFF;
    private static final int COLOUR_TEXT = 0xFFD8D3C8;
    private static final int COLOUR_DIM = 0xFF8A8A8A;
    private static final int COLOUR_BEACON = 0xFFFFD24A;
    private static final int COLOUR_MOORING = 0xFF7FDBCA;
    private static final int COLOUR_FAR = 0xFF3E6E66;
    private static final int COLOUR_PLAYER = 0xFFFFFFFF;
    private static final int COLOUR_OFF = 0xFFE06C4E;

    private static KeyMapping toggleKey;
    private static KeyMapping rangeKey;

    private static boolean open;
    private static int rangeIndex = 1;
    private static int sinceRefresh;

    private static OctiaDebug.Snapshot snapshot;

    private OctiaDebugOverlay() {
    }

    public static void bootstrap() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.octia.debug", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.octia"));
        rangeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.octia.debug_range", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.octia"));

        ClientPlayNetworking.registerGlobalReceiver(OctiaDebug.Snapshot.TYPE, (payload, context) ->
                context.client().execute(() -> snapshot = payload));

        ClientTickEvents.END_CLIENT_TICK.register(OctiaDebugOverlay::tick);
        HudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
    }

    private static void tick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            open = !open;
            // Ask immediately on open rather than waiting out the interval, so
            // the box is never blank for two seconds after being summoned.
            if (open) {
                sinceRefresh = REFRESH_TICKS;
            }
        }
        while (rangeKey.consumeClick()) {
            rangeIndex = (rangeIndex + 1) % RANGES.length;
        }

        if (!open || client.player == null) {
            return;
        }
        if (++sinceRefresh >= REFRESH_TICKS) {
            sinceRefresh = 0;
            if (ClientPlayNetworking.canSend(OctiaDebug.Request.TYPE)) {
                ClientPlayNetworking.send(new OctiaDebug.Request());
            }
        }
    }

    private static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!open || client.player == null || client.options.hideGui) {
            return;
        }

        int left = graphics.guiWidth() - SIZE - MARGIN;
        int top = MARGIN;
        panel(graphics, left, top);

        if (snapshot == null) {
            graphics.drawString(client.font, "octia: waiting for server", left + 6, top + 6, COLOUR_DIM, false);
            return;
        }

        marks(graphics, client.player, left, top);
        readout(graphics, client, left, top);
    }

    /** The box, its edge, and the crosshair through the middle. */
    private static void panel(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + SIZE, top + SIZE, COLOUR_PANEL);
        graphics.fill(left, top, left + SIZE, top + 1, COLOUR_EDGE);
        graphics.fill(left, top + SIZE - 1, left + SIZE, top + SIZE, COLOUR_EDGE);
        graphics.fill(left, top, left + 1, top + SIZE, COLOUR_EDGE);
        graphics.fill(left + SIZE - 1, top, left + SIZE, top + SIZE, COLOUR_EDGE);

        int mid = SIZE / 2;
        graphics.fill(left + mid, top + 1, left + mid + 1, top + SIZE - 1, COLOUR_GRID);
        graphics.fill(left + 1, top + mid, left + SIZE - 1, top + mid + 1, COLOUR_GRID);
    }

    /** Every mooring, the beacon, and the player, plotted north-up. */
    private static void marks(GuiGraphics graphics, Player player, int left, int top) {
        int range = RANGES[rangeIndex];
        double centreX = player.getX();
        double centreZ = player.getZ();
        int mid = SIZE / 2;
        double scale = (double) (mid - 3) / range;

        BlockPos beacon = snapshot.beacon();
        for (long packed : snapshot.moorings()) {
            BlockPos pos = BlockPos.of(packed);
            boolean isBeacon = beacon != null
                    && pos.getX() == beacon.getX() && pos.getZ() == beacon.getZ();
            plot(graphics, left, top, mid, scale, pos, centreX, centreZ,
                    isBeacon ? COLOUR_BEACON : COLOUR_MOORING, isBeacon ? 3 : 2);
        }

        // The beacon is drawn even when it is not among the moorings - an
        // unmoored beacon is exactly the broken state worth seeing.
        if (beacon != null) {
            plot(graphics, left, top, mid, scale, beacon, centreX, centreZ, COLOUR_BEACON, 3);
        }

        graphics.fill(left + mid - 1, top + mid - 1, left + mid + 2, top + mid + 2, COLOUR_PLAYER);
    }

    /** One mark, clamped to the edge and dimmed if it falls outside the box. */
    private static void plot(GuiGraphics graphics, int left, int top, int mid, double scale,
                             BlockPos pos, double centreX, double centreZ, int colour, int size) {
        double dx = (pos.getX() + 0.5 - centreX) * scale;
        double dz = (pos.getZ() + 0.5 - centreZ) * scale;

        int limit = mid - 3;
        boolean outside = Math.abs(dx) > limit || Math.abs(dz) > limit;
        if (outside) {
            double worst = Math.max(Math.abs(dx), Math.abs(dz));
            dx = dx / worst * limit;
            dz = dz / worst * limit;
            colour = colour == COLOUR_BEACON ? COLOUR_OFF : COLOUR_FAR;
        }

        int x = left + mid + (int) Math.round(dx);
        int y = top + mid + (int) Math.round(dz);
        int half = Math.max(1, size / 2);
        graphics.fill(x - half, y - half, x + half + 1, y + half + 1, colour);
    }

    /** The indicators, under the box. Facts, not reassurance. */
    private static void readout(GuiGraphics graphics, Minecraft client, int left, int top) {
        List<String> lines = new ArrayList<>();
        lines.add("OCTIA DEBUG  -  north up  -  " + RANGES[rangeIndex] + "b");
        lines.add("world: " + (snapshot.enabled() ? "ON" : "OFF"));

        if (!snapshot.beaconRaised()) {
            lines.add("beacon: not raised");
        } else if (snapshot.beacon() == null) {
            // Distinct from "no beacon" on purpose: worlds made before the
            // position was recorded have one standing and cannot say where.
            lines.add("beacon: raised, position not recorded");
        } else {
            BlockPos b = snapshot.beacon();
            lines.add("beacon: " + b.getX() + " " + b.getY() + " " + b.getZ()
                    + "  (" + flat(client.player, b) + "b " + bearing(client.player, b) + ")");
        }

        lines.add("moorings: " + snapshot.moorings().size() + "  (whole save, all dimensions)");

        BlockPos nearest = nearest(client.player);
        if (nearest != null) {
            lines.add("nearest: " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
                    + "  (" + flat(client.player, nearest) + "b " + bearing(client.player, nearest) + ")");
        }

        lines.add("here: " + client.player.blockPosition().getX()
                + " " + client.player.blockPosition().getY()
                + " " + client.player.blockPosition().getZ()
                + "  facing " + facing(client.player));
        lines.add("F6 close  -  F7 range");

        int y = top + SIZE + 3;
        for (String line : lines) {
            int width = client.font.width(line);
            graphics.drawString(client.font, line,
                    left + SIZE - width, y, line.startsWith("OCTIA") ? COLOUR_TEXT : COLOUR_DIM, false);
            y += 10;
        }
    }

    private static BlockPos nearest(Player player) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (long packed : snapshot.moorings()) {
            BlockPos pos = BlockPos.of(packed);
            double d = flatSquared(player, pos);
            if (d < bestDistance) {
                bestDistance = d;
                best = pos;
            }
        }
        return best;
    }

    /** The eight points, in the order {@link #point} indexes them. */
    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /**
     * Compass bearing from the player to a position, eight points.
     *
     * <p><b>Absolute, not relative to where the player is looking.</b> The box
     * above does not rotate, and neither does this. A mark that reads NW reads
     * NW on everyone's screenshot and stays NW while you spin on the spot,
     * whereas a facing-relative "ahead" or "to your left" would contradict the
     * picture it is printed under every time you turned - the one thing a debug
     * readout must never do. {@link #facing} is what closes the gap: bearing
     * says where the thing is, facing says which way you are pointed, and the
     * difference between them is the turn.
     *
     * <p>Eight points rather than sixteen because the box is the precise
     * instrument and the text is the glance. NNE would buy eleven degrees of
     * resolution that the pixel already shows better.
     */
    private static String bearing(Player player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();

        // Standing on it. atan2(0, 0) answers zero, which would print a
        // confident "N" for a direction that does not exist.
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) {
            return "here";
        }

        // atan2(dx, -dz), not the textbook atan2(dz, dx): Minecraft's north is
        // negative Z, so this puts zero at north and grows clockwise through
        // east - which is the order POINTS is written in.
        return point(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    /**
     * Which way the player is pointed, same eight points.
     *
     * <p>Read off the yaw rather than {@code getDirection()}, which snaps to
     * four. Yaw zero is south in Minecraft, so the half-turn puts it on the same
     * north-zero scale everything else here uses.
     */
    private static String facing(Player player) {
        return point(player.getYRot() + 180.0);
    }

    /** One of {@link #POINTS} for a bearing in degrees, north zero, clockwise. */
    private static String point(double degrees) {
        int index = (int) Math.round(degrees / 45.0);
        return POINTS[((index % 8) + 8) % 8];
    }

    /**
     * Horizontal distance only. Y is left out because the moorings store spans
     * dimensions, so a vertical component would be arithmetic on two numbers
     * that may not share a space.
     */
    private static long flat(Player player, BlockPos pos) {
        return Math.round(Math.sqrt(flatSquared(player, pos)));
    }

    private static double flatSquared(Player player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return dx * dx + dz * dz;
    }
}
