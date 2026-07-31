package com.serenity.frontdoor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/**
 * The .btn, transposed. Outlined, never filled - the primary is an accent
 * border on nothing, which is the system's most visible rule.
 *
 * <p>States are the system's own and are not restyled per use: a hover tint, a
 * pressed tint one step deeper, and a 2px accent focus ring at 2px offset for
 * the keyboard. Written on a bare JComponent rather than a JButton so the paint
 * is ours end to end and no look-and-feel can reach it.
 */
public final class PortalButton extends JComponent {

    public enum Kind {
        /** .btn-primary: accent text, accent border. */
        PRIMARY,
        /** .btn-secondary: divider border, body text. */
        SECONDARY,
        /** Window chrome: a bare glyph, no border, hover tint only. */
        CHROME
    }

    /** What the chrome kind draws. */
    public enum Mark { NONE, MINIMISE, CLOSE }

    private final Kind kind;
    private final String label;
    private final String hint;
    private final Mark mark;
    private final Runnable action;

    private boolean hover;
    private boolean pressed;
    private boolean focused;

    public PortalButton(Kind kind, String label, String hint, Runnable action) {
        this(kind, label, hint, Mark.NONE, action);
    }

    public PortalButton(Kind kind, String label, String hint, Mark mark, Runnable action) {
        this.kind = kind;
        this.label = label;
        this.hint = hint;
        this.mark = mark;
        this.action = action;
        setOpaque(false);
        setFocusable(kind != Kind.CHROME);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                if (isFocusable()) {
                    requestFocusInWindow();
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean fire = pressed && contains(e.getX(), e.getY());
                pressed = false;
                repaint();
                if (fire) {
                    fire();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    pressed = false;
                    repaint();
                    fire();
                }
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                focused = false;
                repaint();
            }
        });
    }

    private void fire() {
        if (action != null) {
            action.run();
        }
    }

    /**
     * A bare JComponent has no accessible context at all, so drawing our own
     * button would otherwise cost a screen reader the button. Supplying one
     * with the push-button role puts back what not using JButton took away.
     */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PUSH_BUTTON;
                }
            };
            accessibleContext.setAccessibleName(
                    label != null ? label : String.valueOf(mark));
        }
        return accessibleContext;
    }

    @Override
    public Dimension getPreferredSize() {
        return kind == Kind.CHROME ? new Dimension(34, 30) : new Dimension(300, 40);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Nocturne.quality(g0);
        double w = getWidth();
        double h = getHeight();

        if (kind == Kind.CHROME) {
            paintChrome(g, w, h);
            g.dispose();
            return;
        }

        boolean primary = kind == Kind.PRIMARY;
        Color tone = primary ? Nocturne.ACCENT : Nocturne.TEXT;
        Shape box = Nocturne.round(0.5, 0.5, w - 1, h - 1, Nocturne.RADIUS_MD);

        float fill = pressed ? (primary ? 0.22f : 0.14f) : hover ? (primary ? 0.12f : 0.07f) : 0f;
        if (fill > 0f) {
            g.setPaint(Nocturne.alpha(tone, fill));
            g.fill(box);
        }

        g.setStroke(new BasicStroke(1f));
        g.setPaint(primary ? Nocturne.ACCENT : Nocturne.DIVIDER);
        g.draw(box);

        double pad = Nocturne.SPACE_3 * 1.2;
        g.setFont(Nocturne.heading(14f));
        FontMetrics fm = g.getFontMetrics();
        double baseline = h / 2.0 + fm.getAscent() / 2.0 - fm.getDescent() / 4.0;
        g.setPaint(primary ? Nocturne.ACCENT : Nocturne.TEXT);
        g.drawString(label, (float) pad, (float) baseline);

        if (hint != null) {
            g.setFont(Nocturne.meta(11f));
            FontMetrics hm = g.getFontMetrics();
            int hw = hm.stringWidth(hint);
            double hx = w - pad - hw;
            if (hx > pad + fm.stringWidth(label) + Nocturne.SPACE_4) {
                g.setPaint(Nocturne.alpha(Nocturne.TEXT, hover || focused ? 0.62f : 0.42f));
                g.drawString(hint, (float) hx, (float) baseline);
            }
        }

        if (focused) {
            g.setStroke(new BasicStroke(2f));
            g.setPaint(Nocturne.ACCENT);
            g.draw(Nocturne.round(-2, -2, w + 4, h + 4, Nocturne.RADIUS_MD + 2));
        }

        g.dispose();
    }

    private void paintChrome(Graphics2D g, double w, double h) {
        if (hover || pressed) {
            g.setPaint(Nocturne.alpha(mark == Mark.CLOSE ? Nocturne.ACCENT : Nocturne.TEXT,
                    pressed ? 0.18f : 0.10f));
            g.fill(Nocturne.round(0, 0, w, h, Nocturne.RADIUS_SM));
        }
        double cx = w / 2.0;
        double cy = h / 2.0;
        double r = 4.5;
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(hover ? Nocturne.ACCENT_200 : Nocturne.alpha(Nocturne.TEXT, 0.60f));
        if (mark == Mark.MINIMISE) {
            g.draw(new Rectangle2D.Double(cx - r, cy + 0.5, r * 2, 0));
        } else if (mark == Mark.CLOSE) {
            Path2D.Double x = new Path2D.Double();
            x.moveTo(cx - r, cy - r);
            x.lineTo(cx + r, cy + r);
            x.moveTo(cx + r, cy - r);
            x.lineTo(cx - r, cy + r);
            g.draw(x);
        }
    }
}
