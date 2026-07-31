package com.serenity.frontdoor;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * The doorway's geometry. One number decides all of it.
 *
 * <p><b>37 percent to median.</b> Each side of the opening recedes toward the
 * median - the centre of the opening - by 37% of its own distance to that
 * median. The left edge is half a width away from the median, so it moves in by
 * 0.37 of a half width; the right edge mirrors it; top and bottom do the same
 * against the horizontal median. The far opening is therefore the near opening
 * scaled by {@code 1 - 0.37 = 0.63} about its own centre, and the vanishing
 * point is the median itself: dead ahead.
 *
 * <p>Why a fixed fraction rather than a fixed depth: at 0.63 both jambs are
 * visible at once and neither is foreshortened to a line, so which side is
 * which never has to be inferred. That is the whole job of the number - you do
 * not get lost in the passage. Shallower and the door flattens into a painted
 * rectangle; deeper and one jamb swallows the view.
 *
 * <p>All four faces read off the same pair of rectangles, so nothing here can
 * drift out of agreement with anything else here.
 */
public final class Threshold {

    /** The fraction of the distance to the median that each side gives up. */
    public static final double DEPTH_TO_MEDIAN = 0.37;

    private final Rectangle2D.Double near;
    private final Rectangle2D.Double far;

    public Threshold(Rectangle2D near) {
        this.near = new Rectangle2D.Double(near.getX(), near.getY(),
                near.getWidth(), near.getHeight());
        this.far = atDepth(1.0);
    }

    /** The opening in the near face of the wall. */
    public Rectangle2D.Double near() {
        return copy(near);
    }

    /** The far opening, {@code 0.63} of the near one about the same centre. */
    public Rectangle2D.Double far() {
        return copy(far);
    }

    /**
     * The cross-section of the passage at depth {@code t}: 0 is the near
     * opening, 1 the far one. Values outside that run the same line - {@code t}
     * below zero grows the section toward the viewer, which is how the light
     * that spills out of the door onto the floor is shaped.
     */
    public Rectangle2D.Double atDepth(double t) {
        double k = 1.0 - DEPTH_TO_MEDIAN * t;
        double w = near.width * k;
        double h = near.height * k;
        return new Rectangle2D.Double(
                near.getCenterX() - w / 2.0,
                near.getCenterY() - h / 2.0,
                w, h);
    }

    // ---- the four faces ---------------------------------------------------
    // Each is the quadrilateral between one near edge and its far counterpart.

    public Path2D.Double leftJamb() {
        return quad(near.getMinX(), near.getMinY(), far.getMinX(), far.getMinY(),
                far.getMinX(), far.getMaxY(), near.getMinX(), near.getMaxY());
    }

    public Path2D.Double rightJamb() {
        return quad(near.getMaxX(), near.getMinY(), far.getMaxX(), far.getMinY(),
                far.getMaxX(), far.getMaxY(), near.getMaxX(), near.getMaxY());
    }

    /** The head of the passage, seen from below - the darkest face. */
    public Path2D.Double lintel() {
        return quad(near.getMinX(), near.getMinY(), near.getMaxX(), near.getMinY(),
                far.getMaxX(), far.getMinY(), far.getMinX(), far.getMinY());
    }

    /** The floor of the passage, which catches what comes through - the lightest. */
    public Path2D.Double sill() {
        return quad(near.getMinX(), near.getMaxY(), near.getMaxX(), near.getMaxY(),
                far.getMaxX(), far.getMaxY(), far.getMinX(), far.getMaxY());
    }

    private static Path2D.Double quad(double x1, double y1, double x2, double y2,
                                      double x3, double y3, double x4, double y4) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.lineTo(x4, y4);
        p.closePath();
        return p;
    }

    private static Rectangle2D.Double copy(Rectangle2D r) {
        return new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
