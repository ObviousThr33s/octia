package com.serenity.frontdoor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;

/**
 * The door itself: the one thing on this window that is drawn rather than laid
 * out.
 *
 * <p>The composition is a wall with an opening cut in it, four faces receding
 * to a smaller opening ({@link Threshold}), and the sentinel standing beyond
 * it, lit from behind. The light that reaches the near face spills out onto the
 * floor in front, which is the second orientation cue after the jambs: out is
 * where the light widens.
 *
 * <p>Deliberately still. Pointing the obelisk is the next milestone, and it
 * lands here and in {@link Threshold} and nowhere else.
 */
public final class DoorwayView extends JComponent {

    /** Height of the opening as a share of the wall's; a door is a tall hole. */
    private static final double OPENING_ASPECT = 0.62;
    /** The wall left standing around the opening, as a share of its width. */
    private static final double FRAME = 0.115;
    /** Cross-sections drawn on the faces so the depth is countable, not felt. */
    private static final double[] RIBS = {1.0 / 3.0, 2.0 / 3.0};

    private final String caption;
    private final Runnable onEnter;
    private boolean hover;
    private boolean armed;

    public DoorwayView(String caption, Runnable onEnter) {
        this.caption = caption;
        this.onEnter = onEnter;
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                armed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                armed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean fire = armed && contains(e.getX(), e.getY());
                armed = false;
                repaint();
                if (fire && onEnter != null) {
                    onEnter.run();
                }
            }
        };
        addMouseListener(m);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Nocturne.quality(g0);
        int w = getWidth();
        int h = getHeight();

        g.setPaint(Nocturne.BG);
        g.fillRect(0, 0, w, h);

        // ---- where the door stands ---------------------------------------
        double top = h * 0.055;
        double floorY = h * 0.795;
        double slabH = floorY - top;
        double slabW = slabH * OPENING_ASPECT;
        double maxW = w - 2 * Nocturne.SPACE_8;
        if (slabW > maxW) {
            slabW = maxW;
            slabH = slabW / OPENING_ASPECT;
            top = floorY - slabH;
        }
        if (slabW < 60 || slabH < 90) {
            g.dispose();
            return;
        }
        double slabX = (w - slabW) / 2.0;
        double slabY = top;

        double frame = slabW * FRAME;
        Rectangle2D.Double opening = new Rectangle2D.Double(
                slabX + frame, slabY + frame, slabW - 2 * frame, slabH - frame);
        Threshold th = new Threshold(opening);
        Rectangle2D.Double far = th.far();

        double lift = hover ? (armed ? 1.35 : 1.18) : 1.0;

        floor(g, w, h, floorY);
        spill(g, th, opening, floorY, h, lift);
        wall(g, slabX, slabY, slabW, slabH);
        faces(g, th, opening, far);
        ribs(g, th, opening, far);
        beyond(g, far, lift);
        sentinel(g, far, lift);
        nearEdge(g, opening, lift);
        caption(g, w, floorY, h);

        g.dispose();
    }

    // ---- ground -----------------------------------------------------------

    private void floor(Graphics2D g, int w, int h, double floorY) {
        if (h - floorY < 2) {
            return;
        }
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, floorY), new Point2D.Double(0, h),
                new float[] {0f, 1f},
                new Color[] {Nocturne.blend(Nocturne.BG, Nocturne.NEUTRAL_900, 0.55),
                        Nocturne.BG},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Rectangle2D.Double(0, floorY, w, h - floorY));

        // The floor plane is only as wide as this component, and a plane that
        // stops dead at the component edge reads as a rectangle sitting on the
        // page rather than as ground. Fade both ends into the ground colour.
        double fade = Math.min(w * 0.30, 240);
        Color clear = Nocturne.alpha(Nocturne.BG, 0f);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, floorY), new Point2D.Double(fade, floorY),
                new float[] {0f, 1f}, new Color[] {Nocturne.BG, clear},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Rectangle2D.Double(0, floorY, fade, h - floorY));
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(w - fade, floorY), new Point2D.Double(w, floorY),
                new float[] {0f, 1f}, new Color[] {clear, Nocturne.BG},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Rectangle2D.Double(w - fade, floorY, fade, h - floorY));

        Nocturne.rule(g, w * 0.14, floorY, w * 0.72);
    }

    /**
     * What comes through the door, landing on the floor. The near opening is
     * the near edge of this shape and {@code atDepth(-1.6)} sets how far it has
     * spread by the bottom of the view - the same line the passage runs on,
     * continued toward the viewer.
     */
    private void spill(Graphics2D g, Threshold th, Rectangle2D.Double opening,
                       double floorY, int h, double lift) {
        double reach = (h - floorY) * 1.05;
        if (reach < 4) {
            return;
        }
        Rectangle2D.Double front = th.atDepth(-1.6);
        Path2D.Double light = new Path2D.Double();
        light.moveTo(opening.getMinX(), floorY);
        light.lineTo(opening.getMaxX(), floorY);
        light.lineTo(front.getMaxX(), floorY + reach);
        light.lineTo(front.getMinX(), floorY + reach);
        light.closePath();

        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, floorY), new Point2D.Double(0, floorY + reach),
                new float[] {0f, 0.45f, 1f},
                new Color[] {Nocturne.alpha(Nocturne.ACCENT, (float) (0.22 * lift)),
                        Nocturne.alpha(Nocturne.ACCENT, (float) (0.07 * lift)),
                        Nocturne.alpha(Nocturne.ACCENT, 0f)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(light);
    }

    // ---- the wall ---------------------------------------------------------

    private void wall(Graphics2D g, double x, double y, double w, double h) {
        Shape slab = Nocturne.round(x, y, w, h, Nocturne.RADIUS_SM);
        Nocturne.ambient(g, slab, 18, 0.40f);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, y), new Point2D.Double(0, y + h),
                new float[] {0f, 1f},
                new Color[] {Nocturne.SURFACE,
                        Nocturne.blend(Nocturne.SURFACE, Nocturne.NEUTRAL_900, 0.75)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(slab);
        Nocturne.hairline(g, slab);
    }

    // ---- the passage ------------------------------------------------------

    /**
     * The four receding faces. Each is a gradient along its own direction of
     * recession, dark at the near end and accent-tinted at the far one, because
     * the only light in the scene is on the far side. The lintel is darkest,
     * the sill lightest, and the two jambs differ by a few percent so the pair
     * is never a mirror - that difference is what tells you which way you are
     * facing at a glance.
     */
    private void faces(Graphics2D g, Threshold th, Rectangle2D.Double near,
                       Rectangle2D.Double far) {
        Color dark = Nocturne.shade(Nocturne.NEUTRAL_900, 0.72);

        gradientFace(g, th.lintel(),
                near.getCenterX(), near.getMinY(), far.getCenterX(), far.getMinY(),
                Nocturne.shade(dark, 0.80),
                Nocturne.blend(Nocturne.ACCENT_900, Nocturne.ACCENT_800, 0.30));

        gradientFace(g, th.leftJamb(),
                near.getMinX(), near.getCenterY(), far.getMinX(), far.getCenterY(),
                Nocturne.blend(dark, Nocturne.NEUTRAL_800, 0.22),
                Nocturne.blend(Nocturne.ACCENT_900, Nocturne.ACCENT_700, 0.62));

        gradientFace(g, th.rightJamb(),
                near.getMaxX(), near.getCenterY(), far.getMaxX(), far.getCenterY(),
                dark,
                Nocturne.blend(Nocturne.ACCENT_900, Nocturne.ACCENT_700, 0.44));

        gradientFace(g, th.sill(),
                near.getCenterX(), near.getMaxY(), far.getCenterX(), far.getMaxY(),
                Nocturne.blend(dark, Nocturne.NEUTRAL_800, 0.35),
                Nocturne.blend(Nocturne.ACCENT_800, Nocturne.ACCENT_600, 0.50));
    }

    private void gradientFace(Graphics2D g, Shape face, double nx, double ny,
                              double fx, double fy, Color nearC, Color farC) {
        if (Math.abs(nx - fx) < 0.01 && Math.abs(ny - fy) < 0.01) {
            g.setPaint(farC);
            g.fill(face);
            return;
        }
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(nx, ny), new Point2D.Double(fx, fy),
                new float[] {0f, 1f}, new Color[] {nearC, farC},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(face);
    }

    /**
     * Two cross-sections drawn across all four faces at even depths. They are
     * the reason the passage can be read rather than guessed at: with them the
     * depth is countable.
     */
    private void ribs(Graphics2D g, Threshold th, Rectangle2D.Double near,
                      Rectangle2D.Double far) {
        Area passage = new Area(near);
        passage.subtract(new Area(far));
        Graphics2D r = (Graphics2D) g.create();
        r.clip(passage);
        r.setStroke(new BasicStroke(1f));
        r.setPaint(Nocturne.alpha(Nocturne.TEXT, 0.07f));
        for (double t : RIBS) {
            r.draw(th.atDepth(t));
        }
        r.dispose();
    }

    // ---- what is on the other side ----------------------------------------

    private void beyond(Graphics2D g, Rectangle2D.Double far, double lift) {
        if (far.width < 2 || far.height < 2) {
            return;
        }
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, far.getMinY()), new Point2D.Double(0, far.getMaxY()),
                new float[] {0f, 1f},
                new Color[] {Nocturne.shade(Nocturne.ACCENT_900, 0.65), Nocturne.ACCENT_900},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(far);

        double gx = far.getCenterX();
        double gy = far.getMinY() + far.height * 0.66;
        float radius = (float) Math.max(far.width, far.height) * 0.72f;
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(gx, gy), radius,
                new float[] {0f, 0.42f, 1f},
                new Color[] {Nocturne.alpha(Nocturne.ACCENT_300, (float) (0.55 * lift)),
                        Nocturne.alpha(Nocturne.ACCENT_500, (float) (0.30 * lift)),
                        Nocturne.alpha(Nocturne.ACCENT_700, 0f)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        Graphics2D b = (Graphics2D) g.create();
        b.clip(far);
        b.fill(far);
        b.dispose();
    }

    /** The heaviest the sentinel's line is allowed to get on screen, in px. */
    private static final double MAX_STROKE_PX = 4.2;

    private void sentinel(Graphics2D g, Rectangle2D.Double far, double lift) {
        // Size and place from what the glyph actually occupies, not from its
        // box: the obelisk sits low in the box and the dishes stick out past
        // the marks, so box-centring would float it and box-fitting would
        // leave it small.
        Rectangle2D ink = Obelisk.bounds();
        double byHeight = far.height * 0.72 / (ink.getHeight() / Glyphs.VIEWBOX);
        double byWidth = far.width * 0.84 / (ink.getWidth() / Glyphs.VIEWBOX);
        double size = Math.min(byHeight, byWidth);
        if (size < 8) {
            return;
        }
        double unit = size / Glyphs.VIEWBOX;
        double bx = far.getCenterX() - ink.getCenterX() * unit;
        double by = far.getMaxY() - far.height * 0.05 - ink.getMaxY() * unit;

        // Held to a fixed weight on screen rather than the glyph's fixed ratio:
        // this is a thing standing at the end of a passage, not a 24px icon
        // blown up, and the marks have to stay marks.
        double units = Math.min(Glyphs.STROKE, MAX_STROKE_PX * Glyphs.VIEWBOX / size);

        Graphics2D s = (Graphics2D) g.create();
        s.clip(far);
        Obelisk.paint(s, bx, by, size, units,
                Nocturne.blend(Nocturne.BG, Nocturne.ACCENT_900, 0.45),
                Nocturne.alpha(Nocturne.ACCENT_200, (float) Math.min(1.0, 0.78 * lift)),
                Nocturne.alpha(Nocturne.ACCENT, (float) Math.min(1.0, 0.95 * lift)));
        s.dispose();
    }

    // ---- the lip of the opening -------------------------------------------

    private void nearEdge(Graphics2D g, Rectangle2D.Double opening, double lift) {
        Nocturne.hairline(g, opening, Nocturne.NEUTRAL_800);
        if (lift > 1.0) {
            Graphics2D e = (Graphics2D) g.create();
            e.setStroke(new BasicStroke(1.5f));
            e.setPaint(Nocturne.alpha(Nocturne.ACCENT, (float) ((lift - 1.0) * 1.6)));
            e.draw(opening);
            e.dispose();
        }
    }

    private void caption(Graphics2D g, int w, double floorY, int h) {
        if (caption == null || h - floorY < 26) {
            return;
        }
        g.setFont(Nocturne.kicker(11f));
        int tw = g.getFontMetrics().stringWidth(caption);
        Nocturne.text(g, caption, (w - tw) / 2.0, floorY + (h - floorY) * 0.52,
                Nocturne.kicker(11f),
                hover ? Nocturne.ACCENT_300 : Nocturne.alpha(Nocturne.TEXT, 0.55f));
    }
}
