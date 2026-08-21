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

    /**
     * How far in from the box edge a mark at maximum range lands.
     *
     * <p>The widest mark's reach plus the border: BEACON draws a 7x7 ring, so
     * three pixels either side of centre, over a one-pixel edge. A mark exactly
     * {@code range} blocks out therefore rests its outer pixels on the border
     * rather than half outside it.
     *
     * <p>One constant, used by both {@code marks} and {@code plot}. It was
     * written as a bare 3 in each of them, with a comment warning that changing
     * one and not the other would clamp marks at a radius the scale never sends
     * them to - and then the beacon grew from five wide to seven and both were
     * left at 3, which is exactly the drift the comment predicted.
     */
    private static final int INSET = 4;

    /** Ticks between refreshes while the overlay is open. Twenty is one second. */
    private static final int REFRESH_TICKS = 40;

    private static final int COLOUR_PANEL = 0xB0000000;
    private static final int COLOUR_EDGE = 0xFF4A4A4A;
    private static final int COLOUR_GRID = 0x33FFFFFF;
    private static final int COLOUR_TEXT = 0xFFD8D3C8;
    private static final int COLOUR_DIM = 0xFF8A8A8A;
    private static final int COLOUR_BEACON = 0xFFFFD24A;
    private static final int COLOUR_MOORING = 0xFF7FDBCA;

    /**
     * Obelisks. Violet, because the other three marks already hold the warm end
     * and the teal, and a landmark should not be mistaken for a hull at a
     * glance - which is the whole reason the marks are shapes rather than dots.
     */
    private static final int COLOUR_OBELISK = 0xFFA98CD9;

    private static final int COLOUR_FAR = 0xFF3E6E66;
    private static final int COLOUR_PLAYER = 0xFFFFFFFF;
    private static final int COLOUR_OFF = 0xFFE06C4E;

    private static KeyMapping toggleKey;
    private static KeyMapping rangeKey;

    private static boolean open;
    private static int rangeIndex = 1;
    private static int sinceRefresh;

    /**
     * The last snapshot the server sent, or null before the first one arrives.
     *
     * <p><b>Never cleared.</b> Nothing resets this on disconnect, so it outlives
     * the world it describes: quit to the title, join a server that does not
     * have Octia installed, and {@code canSend} correctly refuses to ask for a
     * fresh one while the box goes on drawing the previous save's moorings as
     * though they were here. Every other cross-save leak in this mod is closed
     * at the boundary - see the SERVER_STOPPED reset in {@code Octia.onInitialize}
     * - and this is the one that is not.
     */
    private static OctiaDebug.Snapshot snapshot;

    private OctiaDebugOverlay() {
    }

    public static void bootstrap() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.octia.debug", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.octia"));
        rangeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.octia.debug_range", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.octia"));

        // The types themselves are not registered here. OctiaDebug.bootstrap()
        // does that from Octia.onInitialize, and Fabric runs every main
        // entrypoint before any client one, so both are already known by the
        // time this receiver is installed. Registering them on this side alone
        // is the mistake OctiaDebug's own note is about.
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

    /** Every obelisk and mooring, the beacon, and the player, plotted north-up. */
    private static void marks(GuiGraphics graphics, Player player, int left, int top) {
        int range = RANGES[rangeIndex];
        double centreX = player.getX();
        double centreZ = player.getZ();
        int mid = SIZE / 2;
        double scale = (double) (mid - INSET) / range;

        // Obelisks first, so a ship drawn at the same coordinates sits on top of
        // one. A landmark is context; a hull is the thing being debugged.
        for (long packed : snapshot.obelisks()) {
            plot(graphics, left, top, mid, scale, BlockPos.of(packed), centreX, centreZ,
                    COLOUR_OBELISK, Mark.OBELISK);
        }

        BlockPos beacon = snapshot.beacon();
        for (long packed : snapshot.moorings()) {
            BlockPos pos = BlockPos.of(packed);
            boolean isBeacon = beacon != null
                    && pos.getX() == beacon.getX() && pos.getZ() == beacon.getZ();
            plot(graphics, left, top, mid, scale, pos, centreX, centreZ,
                    isBeacon ? COLOUR_BEACON : COLOUR_MOORING, isBeacon ? Mark.BEACON : Mark.MOORING);
        }

        // The beacon is drawn even when it is not among the moorings - an
        // unmoored beacon is exactly the broken state worth seeing.
        if (beacon != null) {
            plot(graphics, left, top, mid, scale, beacon, centreX, centreZ, COLOUR_BEACON, Mark.BEACON);
        }

        Mark.PLAYER.draw(graphics, left + mid, top + mid, COLOUR_PLAYER);
    }

    /**
     * How a mark is drawn. Shape, not size.
     *
     * <p>Scale alone does not separate marks at these dimensions - the previous
     * attempt asked for 2px and 3px squares and got two identical 3px squares,
     * because {@code 3 / 2} is 1 and nobody looked. Colour does not save it
     * either: three saturated dots on a dark panel at three pixels across are
     * three dots. Silhouette is the only channel with room left, so each mark is
     * a different shape and the player is deliberately the only open one - the
     * reticle reads straight through it, which is what makes "you" legible while
     * sitting on the crosshair intersection.
     */
    private enum Mark {

        /** A filled 3x3 block. The common case, and the quietest. */
        MOORING {
            @Override
            void draw(GuiGraphics graphics, int x, int y, int colour) {
                graphics.fill(x - 1, y - 1, x + 2, y + 2, colour);
            }
        },

        /**
         * A standing stone: one pixel wide, five tall.
         *
         * <p>The only mark that is not symmetric about both axes, and that is
         * what makes it findable. A hull is a block, a beacon is a ring, you are
         * a cross - none of them are a line, so an obelisk reads as an obelisk
         * from the corner of the eye without anybody having to check a colour.
         */
        OBELISK {
            @Override
            void draw(GuiGraphics graphics, int x, int y, int colour) {
                graphics.fill(x, y - 2, x + 1, y + 3, colour);
            }
        },

        /**
         * A hollow 7x7 ring. Bigger and outlined, so it wins at a glance.
         *
         * <p>Seven rather than five because the beacon now stands at spawn -
         * it used to be placed at the world origin, which on most seeds was
         * somewhere else entirely. A five-wide ring put its edge on exactly the
         * pixels the player's cross reaches, so standing at your own beacon
         * drew the two marks through each other and the overlap changed as the
         * rounding shifted by a block. At seven the cross sits cleanly inside
         * the ring and the pair reads as one thing: you, at the beacon.
         */
        BEACON {
            @Override
            void draw(GuiGraphics graphics, int x, int y, int colour) {
                graphics.fill(x - 3, y - 3, x + 4, y - 2, colour);
                graphics.fill(x - 3, y + 3, x + 4, y + 4, colour);
                graphics.fill(x - 3, y - 2, x - 2, y + 3, colour);
                graphics.fill(x + 3, y - 2, x + 4, y + 3, colour);
            }
        },

        /** An open cross, 5px across, with nothing in the middle. */
        PLAYER {
            @Override
            void draw(GuiGraphics graphics, int x, int y, int colour) {
                graphics.fill(x - 2, y, x - 1, y + 1, colour);
                graphics.fill(x + 2, y, x + 3, y + 1, colour);
                graphics.fill(x, y - 2, x + 1, y - 1, colour);
                graphics.fill(x, y + 2, x + 1, y + 3, colour);
            }
        };

        abstract void draw(GuiGraphics graphics, int x, int y, int colour);
    }

    /** One mark, clamped to the edge and dimmed if it falls outside the box. */
    private static void plot(GuiGraphics graphics, int left, int top, int mid, double scale,
                             BlockPos pos, double centreX, double centreZ, int colour, Mark mark) {
        double dx = (pos.getX() + 0.5 - centreX) * scale;
        double dz = (pos.getZ() + 0.5 - centreZ) * scale;

        int limit = mid - INSET;
        boolean outside = Math.abs(dx) > limit || Math.abs(dz) > limit;
        if (outside) {
            double worst = Math.max(Math.abs(dx), Math.abs(dz));
            dx = dx / worst * limit;
            dz = dz / worst * limit;
            colour = colour == COLOUR_BEACON ? COLOUR_OFF : COLOUR_FAR;
        }

        mark.draw(graphics, left + mid + (int) Math.round(dx), top + mid + (int) Math.round(dz), colour);
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

        // Said differently from the moorings line, because it IS different. The
        // moorings are every one in the save; the obelisks are the ones near
        // enough to be worth sending, capped - see OctiaDebug.obelisksNear. A
        // readout that presented both as totals would be lying about one.
        int obelisks = snapshot.obelisks().size();
        lines.add("obelisks: " + obelisks + (obelisks >= OctiaDebug.OBELISK_LIMIT
                ? "+  (capped, nearest first)"
                : "  (within " + OctiaDebug.OBELISK_REACH + "b of you)"));

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
