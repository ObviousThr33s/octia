package com.serenity.frontdoor;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The design doc's symbols, kept as the path data they were authored as.
 *
 * <p>The strings below are copied verbatim out of the {@code <symbol>} defs in
 * {@code Obelisk Icon.dc.html}. They are not re-drawn in Java geometry calls on
 * purpose: a change upstream is then a copy-paste, and nobody has to prove by
 * eye that a hand transcription still matches the icon. {@link #path} is the
 * small parser that pays for that.
 *
 * <p>Everything is authored on a 24x24 square at stroke-width 1.5 - the doc's
 * one prop, at its default.
 */
public final class Glyphs {

    private Glyphs() {
    }

    /** Every symbol in the doc shares this viewBox. */
    public static final double VIEWBOX = 24.0;
    /** The doc's {@code strokeWidth} prop, default value. */
    public static final double STROKE = 1.5;

    // ---- the obelisk (symbol #ob-mono, shared by every variant) ------------

    public static final String OBELISK_BODY =
            "M12 2.4 L15.1 6.8 L15.8 17.6 L8.2 17.6 L8.9 6.8 Z";
    public static final String OBELISK_SHOULDER = "M8.9 6.8 H15.1";
    public static final String OBELISK_BASE =
            "M7.4 17.6 H16.6 L17.3 21.2 H6.7 Z";

    /** #ob-sentinel: the four side marks, stroked in the accent. */
    public static final String[] ANTENNAE = {
            "M7.3 10.2 H5.0",
            "M7.0 14.2 H4.7",
            "M16.7 10.2 H19.0",
            "M17.0 14.2 H19.3",
    };

    /** #ob-star: the brand mark. */
    public static final String STAR =
            "M12 2.6 C12.7 8.3 15.7 11.3 21.4 12 C15.7 12.7 12.7 15.7 12 21.4 "
                    + "C11.3 15.7 8.3 12.7 2.6 12 C8.3 11.3 11.3 8.3 12 2.6 Z";

    /** #ob-dots: the three-dot mark, as filled circles rather than a path. */
    public static final double[][] DOTS = {
            {5.2, 12.0, 1.7}, {12.0, 12.0, 1.7}, {18.8, 12.0, 1.7},
    };

    // ---- use --------------------------------------------------------------

    /** The stroke width to use when a 24-unit glyph is drawn at {@code size}. */
    public static double stroke(double size) {
        return STROKE * size / VIEWBOX;
    }

    /** A glyph path scaled to {@code size} and placed with its box at x,y. */
    public static Shape at(String d, double x, double y, double size) {
        AffineTransform t = AffineTransform.getTranslateInstance(x, y);
        t.scale(size / VIEWBOX, size / VIEWBOX);
        return t.createTransformedShape(path(d));
    }

    // ---- the parser -------------------------------------------------------

    private static final Pattern TOKEN = Pattern.compile(
            "([MmLlHhVvCcQqZz])|(-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)");

    /**
     * Parses the subset of SVG path syntax the design doc actually uses:
     * moveto, lineto, horizontal, vertical, cubic, quadratic and close, in both
     * absolute and relative form. Anything else is a deliberate error rather
     * than a silent misdraw - if the doc grows an arc, this should stop, not
     * guess.
     */
    public static Path2D.Double path(String d) {
        List<String> tk = new ArrayList<>();
        Matcher m = TOKEN.matcher(d);
        while (m.find()) {
            tk.add(m.group());
        }

        Path2D.Double p = new Path2D.Double();
        double cx = 0, cy = 0, startX = 0, startY = 0;
        char cmd = 0;
        int i = 0;

        while (i < tk.size()) {
            char head = tk.get(i).charAt(0);
            if (isCommand(head)) {
                cmd = head;
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    cx = startX;
                    cy = startY;
                    continue;
                }
            }
            if (i >= tk.size()) {
                break;
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    double x = num(tk, i++), y = num(tk, i++);
                    if (rel) {
                        x += cx;
                        y += cy;
                    }
                    p.moveTo(x, y);
                    cx = startX = x;
                    cy = startY = y;
                    // A second coordinate pair after M is an implicit lineto.
                    cmd = rel ? 'l' : 'L';
                    break;
                }
                case 'L': {
                    double x = num(tk, i++), y = num(tk, i++);
                    if (rel) {
                        x += cx;
                        y += cy;
                    }
                    p.lineTo(x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                case 'H': {
                    double x = num(tk, i++);
                    if (rel) {
                        x += cx;
                    }
                    p.lineTo(x, cy);
                    cx = x;
                    break;
                }
                case 'V': {
                    double y = num(tk, i++);
                    if (rel) {
                        y += cy;
                    }
                    p.lineTo(cx, y);
                    cy = y;
                    break;
                }
                case 'C': {
                    double x1 = num(tk, i++), y1 = num(tk, i++);
                    double x2 = num(tk, i++), y2 = num(tk, i++);
                    double x = num(tk, i++), y = num(tk, i++);
                    if (rel) {
                        x1 += cx; y1 += cy;
                        x2 += cx; y2 += cy;
                        x += cx;  y += cy;
                    }
                    p.curveTo(x1, y1, x2, y2, x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                case 'Q': {
                    double x1 = num(tk, i++), y1 = num(tk, i++);
                    double x = num(tk, i++), y = num(tk, i++);
                    if (rel) {
                        x1 += cx; y1 += cy;
                        x += cx;  y += cy;
                    }
                    p.quadTo(x1, y1, x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                default:
                    throw new IllegalArgumentException(
                            "unsupported path command '" + cmd + "' in: " + d);
            }
        }
        return p;
    }

    private static boolean isCommand(char c) {
        return "MmLlHhVvCcQqZz".indexOf(c) >= 0;
    }

    private static double num(List<String> tk, int i) {
        if (i >= tk.size()) {
            throw new IllegalArgumentException("path ended mid-command");
        }
        return Double.parseDouble(tk.get(i));
    }
}
