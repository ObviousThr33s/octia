package com.serenity.frontdoor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * The sentinel obelisk - antennae and dishes.
 *
 * <p>Variant 1c of the design doc is the sentinel: the shared obelisk body with
 * four accent side marks. The dishes are the one addition made here, and they
 * are made in the doc's own language rather than beside it - same 1.5 stroke,
 * same accent, apex landing exactly on the end of the mark so the pair reads as
 * one part. {@link #DISHES} turns them off and leaves the doc's icon untouched.
 *
 * <p>Everything is drawn in the glyph's own 24-unit space and scaled by the
 * transform, so the stroke scales with it and the icon holds at 16px and at
 * 400px alike.
 */
public final class Obelisk {

    private Obelisk() {
    }

    /** The addition. False renders exactly what {@code #ob-sentinel} draws. */
    public static final boolean DISHES = true;

    /**
     * tip x, tip y, outward direction, dish half-height, dish depth.
     *
     * <p>The dishes are filled crescents rather than stroked curves. A stroked
     * curve this small cannot survive the icon's own line weight - at 128px the
     * stroke is thicker than the cup is deep, and the two rim tips and the apex
     * close up into a fork on the end of a stick. Filled, the dish is a
     * silhouette, and a silhouette holds at any weight because it has none.
     * The lamp variant in the design doc fills its accent marks for the same
     * reason, so this stays inside the system rather than beside it.
     */
    private static final double[][] DISH_AT = {
            {5.0, 10.2, -1, 1.60, 2.10},
            {4.7, 14.2, -1, 1.30, 1.70},
            {19.0, 10.2, +1, 1.60, 2.10},
            {19.3, 14.2, +1, 1.30, 1.70},
    };

    /**
     * How far the outward face dips back toward the mast, as a share of the
     * depth. Low, so the crescent stays thick through the middle: a dish that
     * is deep but thin reads as a bar, which is the mark it is supposed to be
     * sitting on the end of.
     */
    private static final double CONCAVITY = 0.12;

    /**
     * Paints the sentinel with its 24-unit box at {@code (x, y)}, sized to
     * {@code size}.
     *
     * @param fill   the body, filled so the obelisk occludes whatever is behind
     *               it - this is what makes it a silhouette in the doorway
     * @param rim    the body outline
     * @param accent the side marks and their dishes
     */
    public static void paint(Graphics2D g0, double x, double y, double size,
                             Color fill, Color rim, Color accent) {
        paint(g0, x, y, size, Glyphs.STROKE, fill, rim, accent);
    }

    /**
     * As above, with the stroke given in glyph units.
     *
     * <p>The doc's 1.5 is a ratio, and a ratio held at every size makes an icon
     * that is correct at 24px into a club at 300. An icon keeps the ratio; a
     * drawing at scene size does not, and passes a finer stroke here. This is
     * the only knob that decides which of the two you get.
     */
    public static void paint(Graphics2D g0, double x, double y, double size,
                             double strokeUnits, Color fill, Color rim, Color accent) {
        Graphics2D g = (Graphics2D) g0.create();
        AffineTransform t = AffineTransform.getTranslateInstance(x, y);
        t.scale(size / Glyphs.VIEWBOX, size / Glyphs.VIEWBOX);
        g.transform(t);
        // Stroke width is now expressed in glyph units, exactly as the SVG has it.
        g.setStroke(new BasicStroke((float) strokeUnits, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

        Path2D body = Glyphs.path(Glyphs.OBELISK_BODY);
        Path2D base = Glyphs.path(Glyphs.OBELISK_BASE);

        if (fill != null) {
            g.setPaint(fill);
            g.fill(body);
            g.fill(base);
        }

        if (accent != null) {
            g.setPaint(accent);
            for (String d : Glyphs.ANTENNAE) {
                g.draw(Glyphs.path(d));
            }
            if (DISHES) {
                for (double[] a : DISH_AT) {
                    g.fill(dish(a[0], a[1], a[2], a[3], a[4]));
                }
            }
        }

        if (rim != null) {
            g.setPaint(rim);
            g.draw(body);
            g.draw(Glyphs.path(Glyphs.OBELISK_SHOULDER));
            g.draw(base);
        }

        g.dispose();
    }

    /**
     * One dish, as a closed crescent.
     *
     * <p>The inward face is a quadratic solved so its midpoint lands exactly on
     * the tip of the mark - the mast ends where the dish is deepest, with no
     * gap and no overlap at any size. The outward face runs between the same
     * two rim tips but dips back toward the mast, which is what makes the
     * silhouette a bowl facing out rather than a leaf.
     */
    private static Path2D.Double dish(double tx, double ty, double dir, double r,
                                      double depth) {
        double rimX = tx + dir * depth;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(rimX, ty - r);
        // inward face, through the tip
        p.quadTo(tx - dir * depth, ty, rimX, ty + r);
        // outward face, back to the first rim tip
        p.quadTo(tx + dir * depth * CONCAVITY, ty, rimX, ty - r);
        p.closePath();
        return p;
    }

    /**
     * What the sentinel actually occupies inside its 24-unit box, in glyph
     * units - measured from the paths rather than written down beside them.
     * The obelisk does not fill its box (the body starts at y 2.4) and the
     * dishes push past where the marks end, so anything sizing or placing the
     * glyph has to ask rather than assume. Stroke width is not included: this
     * is the geometry, not the ink.
     */
    public static Rectangle2D bounds() {
        if (BOUNDS == null) {
            Rectangle2D r = Glyphs.path(Glyphs.OBELISK_BODY).getBounds2D();
            Rectangle2D.union(r, Glyphs.path(Glyphs.OBELISK_BASE).getBounds2D(), r);
            for (String d : Glyphs.ANTENNAE) {
                Rectangle2D.union(r, Glyphs.path(d).getBounds2D(), r);
            }
            if (DISHES) {
                for (double[] a : DISH_AT) {
                    Rectangle2D.union(r, dish(a[0], a[1], a[2], a[3], a[4]).getBounds2D(), r);
                }
            }
            BOUNDS = r;
        }
        return (Rectangle2D) BOUNDS.clone();
    }

    private static Rectangle2D BOUNDS;

    /** The brand mark from {@code #ob-star}. */
    public static void star(Graphics2D g0, double x, double y, double size, Color c) {
        Graphics2D g = Nocturne.quality(g0);
        g.setPaint(c);
        g.setStroke(new BasicStroke((float) Glyphs.stroke(size), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(Glyphs.at(Glyphs.STAR, x, y, size));
        g.dispose();
    }

    /** The three-dot mark from {@code #ob-dots}. */
    public static void dots(Graphics2D g0, double x, double y, double size, Color c) {
        Graphics2D g = Nocturne.quality(g0);
        g.setPaint(c);
        double k = size / Glyphs.VIEWBOX;
        for (double[] d : Glyphs.DOTS) {
            double r = d[2] * k;
            g.fill(new Ellipse2D.Double(x + d[0] * k - r, y + d[1] * k - r, r * 2, r * 2));
        }
        g.dispose();
    }
}
