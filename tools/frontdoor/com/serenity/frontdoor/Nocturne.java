package com.serenity.frontdoor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nocturne, transposed to AWT.
 *
 * <p>The design system is a stylesheet; Swing cannot read one. This class is
 * the one place the tokens cross over, so a retune upstream is a retune of
 * this file and nothing else. Every value below is copied from
 * {@code _ds/nocturne-.../styles.css} - the comment beside it names the CSS
 * variable it came from, so the two can be diffed by eye.
 *
 * <p>Nothing else in this program may name a colour, a font or a radius.
 * That is the same rule the system's own readme states ("never hard-code a
 * hex, a font name or a px value the tokens already carry"), and it is the
 * rule that makes a restyle a one-file change.
 */
public final class Nocturne {

    private Nocturne() {
    }

    // ---- roles ------------------------------------------------------------

    /** --color-bg */
    public static final Color BG = hex("#161826");
    /** --color-surface */
    public static final Color SURFACE = hex("#232532");
    /** --color-text */
    public static final Color TEXT = hex("#e9e9ed");
    /** --color-accent: the product's blurple, used as a line and a glow. */
    public static final Color ACCENT = hex("#9184d9");
    /** --color-divider: color-mix(in srgb, #e9e9ed 16%, transparent) */
    public static final Color DIVIDER = alpha(TEXT, 0.16f);

    /**
     * A gate that passed, and a gate that did not. The only two hues in this
     * class outside the violet and the neutrals.
     *
     * <p><b>They have to be outside it, and that is the argument for adding
     * them rather than reaching for {@code ACCENT_300} and {@code ACCENT_700}.</b>
     * Everything else here is brand: it says "this is Octia" and nothing more,
     * so any of it can be swapped for any other of it and only the mood moves.
     * A test result is not mood. If pass and fail are two steps of one ramp,
     * they are two brightnesses of the same colour, and the one reading they
     * must never need is a side-by-side comparison to tell apart - which is
     * exactly the reading a scrolling log denies you.
     *
     * <p>Lightness is matched to the 300 step of the ramps so they sit level
     * with {@link #ACCENT_300} on {@link #BG} rather than jumping out of the
     * surface, and both are desaturated well below a terminal's stock green and
     * red for the same reason the rest of this palette is: nothing here shouts.
     */
    public static final Color OK = hex("#7fd6a8");
    /** @see #OK */
    public static final Color BAD = hex("#e2796a");

    // ---- tonal ramps ------------------------------------------------------
    // Generated upstream in OKLCH on one shared lightness scale, so the same
    // step of any role carries the same visual weight.

    public static final Color NEUTRAL_100 = hex("#f3f5fe");
    public static final Color NEUTRAL_200 = hex("#e4e7f5");
    public static final Color NEUTRAL_300 = hex("#cfd3e5");
    public static final Color NEUTRAL_400 = hex("#b2b6ca");
    public static final Color NEUTRAL_500 = hex("#9397ab");
    public static final Color NEUTRAL_600 = hex("#75798c");
    public static final Color NEUTRAL_700 = hex("#595d6c");
    public static final Color NEUTRAL_800 = hex("#3f424d");
    public static final Color NEUTRAL_900 = hex("#292b31");

    public static final Color ACCENT_100 = hex("#f5f4ff");
    public static final Color ACCENT_200 = hex("#e7e5fe");
    public static final Color ACCENT_300 = hex("#d2cefd");
    public static final Color ACCENT_400 = hex("#b5abfc");
    public static final Color ACCENT_500 = hex("#968ae0");
    public static final Color ACCENT_600 = hex("#796cbf");
    public static final Color ACCENT_700 = hex("#5d5294");
    public static final Color ACCENT_800 = hex("#423a6a");
    public static final Color ACCENT_900 = hex("#2b2741");

    // ---- spacing (density 0.70x) ------------------------------------------

    public static final float SPACE_1 = 2.8f;
    public static final float SPACE_2 = 5.6f;
    public static final float SPACE_3 = 8.4f;
    public static final float SPACE_4 = 11.2f;
    public static final float SPACE_6 = 16.8f;
    public static final float SPACE_8 = 22.4f;

    // ---- radii ------------------------------------------------------------

    public static final float RADIUS_SM = 4f;
    public static final float RADIUS_MD = 8f;
    public static final float RADIUS_LG = 14f;

    /**
     * The end-fade of a rule, in px. A Nocturne signature: freestanding rules
     * fade to transparent over one deck baseline unit at each end rather than
     * stopping cleanly. Box outlines and short accent marks stay solid.
     */
    public static final double RULE_FADE = 48.0;

    // ---- type -------------------------------------------------------------

    private static final String BODY_FAMILY =
            firstInstalled("Inter", "Segoe UI Variable Text", "Segoe UI", Font.SANS_SERIF);
    private static final String HEADING_FAMILY =
            firstInstalled("Inter Medium", "Inter", "Segoe UI Variable Display", "Segoe UI",
                    Font.SANS_SERIF);

    /**
     * The one fixed-width family, for output that has to line up in columns.
     *
     * <p>The only family here whose fallback is {@link Font#MONOSPACED} rather
     * than {@code SANS_SERIF}, and that is the whole reason it is a separate
     * constant instead of another size of the body face. A proportional font
     * that falls back to another proportional font still reads; a column
     * gutter that falls back to a proportional font stops being a column.
     */
    private static final String MONO_FAMILY =
            firstInstalled("Cascadia Mono", "Consolas", "JetBrains Mono", Font.MONOSPACED);

    /**
     * Headings. Weight stays at the system's 500 where the family offers it and
     * falls back to regular where it does not - deliberately, because in this
     * system "hierarchy is size and space", never a bolder heading.
     */
    public static Font heading(float size) {
        return font(HEADING_FAMILY, size, -0.015f);
    }

    /** Body copy. */
    public static Font body(float size) {
        return font(BODY_FAMILY, size, 0f);
    }

    /** The h6 / card-kicker treatment: small, uppercase, widely tracked. */
    public static Font kicker(float size) {
        return font(HEADING_FAMILY, size, 0.10f);
    }

    /** card-meta: small, tracked a little less than a kicker. */
    public static Font meta(float size) {
        return font(BODY_FAMILY, size, 0.08f);
    }

    /**
     * Program output: logs, columns, anything that has to line up.
     *
     * <p><b>Tracking is zero and must stay zero.</b> Every other face here is
     * partly defined by its tracking - that is what separates a kicker from
     * meta on the same family. This one is defined by the absence of it.
     * {@link java.awt.font.TextAttribute#TRACKING} adds a fraction of the point
     * size to every advance, which is exactly the property a fixed-width font
     * is chosen for destroying. A tracked monospace font is a proportional font
     * with extra steps, and the gutter it draws drifts one pixel per character.
     */
    public static Font mono(float size) {
        return font(MONO_FAMILY, size, 0f);
    }

    // ---- painting helpers -------------------------------------------------

    /**
     * A scratch Graphics2D set up for quality output. Always returns a copy -
     * dispose it, never the original.
     */
    public static Graphics2D quality(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g2;
    }

    /** The .hr: a 1px rule that fades to nothing at both ends. */
    public static void rule(Graphics2D g, double x, double y, double w) {
        if (w < 4) {
            return;
        }
        double fade = Math.min(RULE_FADE, w / 3.0);
        float f = (float) (fade / w);
        Color clear = alpha(DIVIDER, 0f);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y), new Point2D.Double(x + w, y),
                new float[] {0f, f, 1f - f, 1f},
                new Color[] {clear, DIVIDER, DIVIDER, clear},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, 1));
    }

    /** --shadow-sm: on a dark ground, elevation is a hairline edge. */
    public static void hairline(Graphics2D g, Shape s) {
        hairline(g, s, NEUTRAL_800);
    }

    public static void hairline(Graphics2D g, Shape s, Color edge) {
        Graphics2D h = (Graphics2D) g.create();
        h.setStroke(new BasicStroke(1f));
        h.setPaint(edge);
        h.draw(s);
        h.dispose();
    }

    /**
     * The ambient half of --shadow-md/-lg: darkness pooled around a shape.
     * Paint before filling the shape; the inner half is covered by the fill.
     * Shade mixed from black is a shadow, not a colour - the one place the
     * system permits it.
     */
    public static void ambient(Graphics2D g, Shape s, int spread, float strength) {
        Graphics2D a = (Graphics2D) g.create();
        for (int i = spread; i >= 1; i--) {
            float t = 1f - (float) i / spread;
            a.setPaint(new Color(0f, 0f, 0f, strength * t * t));
            a.setStroke(new BasicStroke(i * 2f));
            a.draw(s);
        }
        a.dispose();
    }

    public static RoundRectangle2D.Double round(double x, double y, double w, double h,
                                                double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    // ---- text -------------------------------------------------------------

    /** Draws a single line, returning the advance so callers can chain. */
    public static double text(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setPaint(c);
        g.drawString(s, (float) x, (float) y);
        return g.getFontMetrics(f).stringWidth(s);
    }

    /**
     * Greedy word wrap. Split out from the drawing so layout can ask how tall a
     * paragraph will be before there is a Graphics to draw it with.
     */
    public static List<String> wrap(java.awt.FontMetrics fm, String s, double width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(probe) > width && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(probe);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Word-wraps into {@code width} and returns the baseline just past the last
     * line drawn.
     */
    public static double wrapped(Graphics2D g, String s, double x, double y, double width,
                                 double leading, Font f, Color c) {
        g.setFont(f);
        g.setPaint(c);
        double baseline = y;
        for (String line : wrap(g.getFontMetrics(f), s, width)) {
            g.drawString(line, (float) x, (float) baseline);
            baseline += leading;
        }
        return baseline;
    }

    // ---- colour arithmetic ------------------------------------------------

    /** color-mix(in srgb, c {@code a}%, transparent). */
    public static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.round(Math.max(0f, Math.min(1f, a)) * 255f));
    }

    /** Linear sRGB-space mix; {@code t}=0 is a, {@code t}=1 is b. */
    public static Color blend(Color a, Color b, double t) {
        double k = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k));
    }

    /** Scales toward black. Shade, not colour. */
    public static Color shade(Color c, double k) {
        return new Color(
                (int) Math.round(c.getRed() * k),
                (int) Math.round(c.getGreen() * k),
                (int) Math.round(c.getBlue() * k),
                c.getAlpha());
    }

    private static Color hex(String s) {
        return new Color(Integer.parseInt(s.substring(1), 16));
    }

    // ---- font plumbing ----------------------------------------------------

    private static final Map<String, Font> FONTS = new HashMap<>();

    private static Font font(String family, float size, float tracking) {
        String key = family + '/' + size + '/' + tracking;
        Font cached = FONTS.get(key);
        if (cached != null) {
            return cached;
        }
        Font f = new Font(family, Font.PLAIN, Math.round(size)).deriveFont(size);
        if (tracking != 0f) {
            Map<TextAttribute, Object> attrs = new HashMap<>();
            attrs.put(TextAttribute.TRACKING, tracking);
            f = f.deriveFont(attrs);
        }
        FONTS.put(key, f);
        return f;
    }

    private static String firstInstalled(String... candidates) {
        Set<String> installed = new HashSet<>();
        for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            installed.add(name);
        }
        for (String c : candidates) {
            if (installed.contains(c)) {
                return c;
            }
        }
        return Font.SANS_SERIF;
    }

    /** For the about line: which families actually resolved on this machine. */
    public static List<String> resolvedFamilies() {
        List<String> out = new ArrayList<>();
        out.add(HEADING_FAMILY);
        out.add(BODY_FAMILY);
        out.add(MONO_FAMILY);
        return out;
    }
}
