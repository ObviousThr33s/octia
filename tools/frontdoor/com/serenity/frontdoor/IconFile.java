package com.serenity.frontdoor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The desktop icon: the sentinel on a Nocturne tile, and the .ico container
 * Windows needs in order to show it.
 *
 * <p>Two renderings, not one scaled. Above 48px the icon is the sentinel with
 * its antennae and dishes; below it the marks are finer than a pixel and turn
 * to mush, so the small sizes fall back to the plain obelisk with a stroke held
 * at a whole pixel. An icon that is unreadable at 16px is not a smaller icon,
 * it is a smudge.
 *
 * <p>The .ico is assembled by hand because Java has no writer for it. Every
 * entry is a 32-bit DIB, including 256. PNG-compressed entries are legal and
 * smaller, and Explorer reads them - but GDI+ does not, so a PNG entry cannot
 * be checked without shipping it and looking. DIB entries round-trip through
 * {@code System.Drawing.Icon} at every size, which makes the icon verifiable
 * before it reaches a desktop. A third of a megabyte is a fair price for that.
 */
public final class IconFile {

    private IconFile() {
    }

    /** The sizes Explorer, the taskbar, Alt-Tab and the shortcut all draw from. */
    public static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    /** Below this the side marks cannot survive; draw the plain obelisk. */
    private static final int DETAIL_FLOOR = 48;

    // ---- the artwork ------------------------------------------------------

    public static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Nocturne.quality(img.getGraphics());

        double r = size * 0.22;
        Shape tile = Nocturne.round(0, 0, size, size, r);
        g.setPaint(new java.awt.LinearGradientPaint(
                new Point2D.Double(0, 0), new Point2D.Double(0, size),
                new float[] {0f, 1f},
                new Color[] {Nocturne.blend(Nocturne.BG, Nocturne.SURFACE, 0.45), Nocturne.BG},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(tile);

        // The glow the obelisk stands in, same as the one beyond the door.
        Graphics2D glow = (Graphics2D) g.create();
        glow.clip(tile);
        glow.setPaint(new RadialGradientPaint(
                new Point2D.Double(size / 2.0, size * 0.66), (float) (size * 0.52),
                new float[] {0f, 1f},
                new Color[] {Nocturne.alpha(Nocturne.ACCENT, 0.30f),
                        Nocturne.alpha(Nocturne.ACCENT, 0f)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        glow.fill(tile);
        glow.dispose();

        if (size >= 24) {
            Nocturne.hairline(g, Nocturne.round(0.5, 0.5, size - 1, size - 1, r),
                    Nocturne.alpha(Nocturne.ACCENT_700, 0.85f));
        }

        double box = size * (size >= DETAIL_FLOOR ? 0.80 : 0.86);
        double bx = (size - box) / 2.0;
        double by = (size - box) / 2.0;

        if (size >= DETAIL_FLOOR) {
            Obelisk.paint(g, bx, by, box,
                    Nocturne.blend(Nocturne.BG, Nocturne.ACCENT_900, 0.55),
                    Nocturne.ACCENT_200,
                    Nocturne.ACCENT);
        } else {
            compact(g, bx, by, box, size);
        }

        g.dispose();
        return img;
    }

    /**
     * The small sizes: body and base only, at a stroke rounded to a whole pixel
     * so it lands on the grid instead of smearing across two.
     */
    private static void compact(Graphics2D g0, double x, double y, double box, int size) {
        Graphics2D g = (Graphics2D) g0.create();
        AffineTransform t = AffineTransform.getTranslateInstance(x, y);
        double k = box / Glyphs.VIEWBOX;
        t.scale(k, k);
        g.transform(t);
        float sw = (float) (Math.max(1.0, Math.round(Glyphs.STROKE * k)) / k);
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D body = Glyphs.path(Glyphs.OBELISK_BODY);
        Path2D base = Glyphs.path(Glyphs.OBELISK_BASE);
        g.setPaint(Nocturne.blend(Nocturne.BG, Nocturne.ACCENT_900, 0.55));
        g.fill(body);
        g.fill(base);
        g.setPaint(Nocturne.ACCENT_200);
        g.draw(body);
        g.draw(base);
        if (size >= 24) {
            g.setPaint(Nocturne.ACCENT);
            g.draw(Glyphs.path(Glyphs.OBELISK_SHOULDER));
        }
        g.dispose();
    }

    /** What the JFrame hands the window manager. */
    public static List<Image> frameIcons() {
        List<Image> out = new ArrayList<>();
        for (int s : SIZES) {
            out.add(render(s));
        }
        return out;
    }

    // ---- the container ----------------------------------------------------

    public static void writeIco(Path out) throws IOException {
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<byte[]> payloads = new ArrayList<>();
        for (int s : SIZES) {
            payloads.add(dib(render(s)));
        }

        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        // ICONDIR
        le16(ico, 0);                 // reserved
        le16(ico, 1);                 // type: icon
        le16(ico, SIZES.length);

        int offset = 6 + 16 * SIZES.length;
        for (int i = 0; i < SIZES.length; i++) {
            int s = SIZES[i];
            byte[] data = payloads.get(i);
            ico.write(s >= 256 ? 0 : s);   // 0 means 256
            ico.write(s >= 256 ? 0 : s);
            ico.write(0);                  // palette size: none
            ico.write(0);                  // reserved
            le16(ico, 1);                  // colour planes
            le16(ico, 32);                 // bits per pixel
            le32(ico, data.length);
            le32(ico, offset);
            offset += data.length;
        }
        for (byte[] data : payloads) {
            ico.write(data);
        }
        Files.write(out, ico.toByteArray());
    }

    /**
     * A 32-bit bottom-up DIB with the AND mask zeroed - the alpha channel does
     * the masking, and a stale AND mask is the classic way an icon ends up with
     * a black box behind it.
     */
    private static byte[] dib(BufferedImage img) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteArrayOutputStream b = new ByteArrayOutputStream();

        // BITMAPINFOHEADER
        le32(b, 40);
        le32(b, w);
        le32(b, h * 2);       // XOR bitmap plus AND mask
        le16(b, 1);
        le16(b, 32);
        le32(b, 0);           // BI_RGB
        le32(b, w * h * 4);
        le32(b, 0);
        le32(b, 0);
        le32(b, 0);
        le32(b, 0);

        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                b.write(argb & 0xFF);            // B
                b.write((argb >>> 8) & 0xFF);    // G
                b.write((argb >>> 16) & 0xFF);   // R
                b.write((argb >>> 24) & 0xFF);   // A
            }
        }
        int maskRow = ((w + 31) / 32) * 4;
        b.write(new byte[maskRow * h]);
        return b.toByteArray();
    }

    private static void le16(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
    }

    private static void le32(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }
}
