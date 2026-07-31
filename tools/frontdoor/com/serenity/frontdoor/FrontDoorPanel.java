package com.serenity.frontdoor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The window's one panel: the codex block and the calls on the left, the door
 * on the right.
 *
 * <p>Left-aligned and asymmetric, per the system's direction - content hugs the
 * left edge and the whitespace is on the right, except here the whitespace is
 * the door.
 *
 * <p>Laid out by hand rather than by a LayoutManager. The column is a single
 * downward flow and {@link #flow} is the only place its geometry is decided, so
 * the painter and the layout cannot disagree about where anything is.
 */
public final class FrontDoorPanel extends JPanel {

    private static final int HEADER = 46;
    private static final int FOOTER = 38;
    private static final int GUTTER = 34;
    private static final int COLUMN = 384;
    private static final int BUTTON_H = 40;

    private static final String DECK =
            "The way in. Every side of the passage gives up 37 percent of its "
                    + "distance to the median, so both jambs stay in view and out is "
                    + "never a guess.";

    private final String[] codex;
    private final String artifact;
    private final List<PortalButton> portals = new ArrayList<>();
    private final DoorwayView door;
    private final PortalButton minimise;
    private final PortalButton close;

    private String status;
    private Timer statusTimer;

    /** Filled by {@link #flow}; read by the painter on the same pass. */
    private double buttonsTop;
    private double plaqueTop;
    private double plaqueHeight;
    private double ruleOne;
    private double ruleTwo;
    private List<String> deckLines = List.of();

    public FrontDoorPanel(String[] codex, String artifact, DoorwayView door,
                          List<PortalButton> calls, Runnable onMinimise, Runnable onClose) {
        this.codex = codex.clone();
        this.artifact = artifact;
        this.door = door;
        setOpaque(true);
        setBackground(Nocturne.BG);
        setLayout(null);

        add(door);
        for (PortalButton b : calls) {
            portals.add(b);
            add(b);
        }
        minimise = new PortalButton(PortalButton.Kind.CHROME, null, null,
                PortalButton.Mark.MINIMISE, onMinimise);
        close = new PortalButton(PortalButton.Kind.CHROME, null, null,
                PortalButton.Mark.CLOSE, onClose);
        add(minimise);
        add(close);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1180, 760);
    }

    /** Puts the keyboard on the first call, so Tab starts where the eye does. */
    public void focusFirstCall() {
        if (!portals.isEmpty()) {
            portals.get(0).requestFocusInWindow();
        }
    }

    /** The strip that drags the window. */
    public boolean inHeader(Point p) {
        if (p.y > HEADER) {
            return false;
        }
        return !minimise.getBounds().contains(p) && !close.getBounds().contains(p);
    }

    /** A transient line in the footer, in place of a modal nobody wants. */
    public void status(String message) {
        status = message;
        repaint();
        if (statusTimer != null) {
            statusTimer.stop();
        }
        statusTimer = new Timer(7000, e -> {
            status = null;
            repaint();
        });
        statusTimer.setRepeats(false);
        statusTimer.start();
    }

    // ---- layout -----------------------------------------------------------

    /**
     * Walks the left column top to bottom once, recording where each band
     * landed. Called from both {@link #doLayout} and the painter; it writes
     * fields and moves nothing, so calling it twice is free.
     */
    private void flow() {
        FontMetrics deckFm = getFontMetrics(Nocturne.body(13f));
        deckLines = Nocturne.wrap(deckFm, DECK, COLUMN);

        double y = HEADER + 34;          // kicker baseline
        y += 30;                         // to the h1 baseline
        y += 20;                         // past the h1
        y += Nocturne.SPACE_6;
        y += deckLines.size() * 19;
        y += Nocturne.SPACE_6;

        ruleOne = Math.round(y);
        y += Nocturne.SPACE_8;

        plaqueTop = Math.round(y);
        plaqueHeight = Math.round(Nocturne.SPACE_4 * 2 + codex.length * 21);
        y = plaqueTop + plaqueHeight + Nocturne.SPACE_8;

        ruleTwo = Math.round(y);
        y += Nocturne.SPACE_8;

        buttonsTop = Math.round(y);
    }

    @Override
    public void doLayout() {
        flow();
        int w = getWidth();
        int h = getHeight();

        close.setBounds(w - GUTTER / 2 - 34, HEADER / 2 - 15, 34, 30);
        minimise.setBounds(close.getX() - 36, close.getY(), 34, 30);

        double y = buttonsTop;
        for (PortalButton b : portals) {
            b.setBounds(GUTTER, (int) Math.round(y), COLUMN, BUTTON_H);
            y += BUTTON_H + Nocturne.SPACE_3;
        }

        int doorX = GUTTER + COLUMN + GUTTER;
        int doorW = Math.max(0, w - doorX - GUTTER);
        int doorH = Math.max(0, h - HEADER - FOOTER);
        door.setBounds(doorX, HEADER, doorW, doorH);
    }

    // ---- paint ------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        flow();
        Graphics2D g = Nocturne.quality(g0);
        int w = getWidth();
        int h = getHeight();

        g.setPaint(Nocturne.BG);
        g.fillRect(0, 0, w, h);

        header(g, w);
        column(g);
        footer(g, w, h);

        g.dispose();
    }

    private void header(Graphics2D g, int w) {
        Obelisk.star(g, GUTTER, HEADER / 2.0 - 9, 18, Nocturne.ACCENT);
        Nocturne.text(g, "OCTIOID", GUTTER + 26, HEADER / 2.0 + 5,
                Nocturne.kicker(12f), Nocturne.TEXT);
        Nocturne.text(g, "FRONT DOOR", GUTTER + 26 + 74, HEADER / 2.0 + 5,
                Nocturne.kicker(12f), Nocturne.alpha(Nocturne.TEXT, 0.38f));
    }

    private void column(Graphics2D g) {
        double y = HEADER + 34;
        Nocturne.text(g, "ACT TWO / MILESTONE 1", GUTTER, y,
                Nocturne.kicker(10f), Nocturne.ACCENT);

        y += 30;
        Nocturne.text(g, "Front Door", GUTTER, y, Nocturne.heading(38f), Nocturne.TEXT);

        y += 20 + Nocturne.SPACE_6;
        g.setFont(Nocturne.body(13f));
        g.setPaint(Nocturne.alpha(Nocturne.TEXT, 0.62f));
        for (String line : deckLines) {
            g.drawString(line, GUTTER, (float) y);
            y += 19;
        }

        Nocturne.rule(g, GUTTER, ruleOne, COLUMN);
        plaque(g);
        Nocturne.rule(g, GUTTER, ruleTwo, COLUMN);
    }

    /**
     * The codex block. Four lines because a sign has four - the notation was
     * designed on one, and the constraint is the point. Rendered rather than
     * summarised: this is the part of the project that is meant to outlast the
     * code around it, so it is on the door itself.
     */
    private void plaque(Graphics2D g) {
        java.awt.geom.RoundRectangle2D.Double box =
                Nocturne.round(GUTTER, plaqueTop, COLUMN, plaqueHeight, Nocturne.RADIUS_MD);
        g.setPaint(Nocturne.SURFACE);
        g.fill(box);
        Nocturne.hairline(g, box);

        // The accent mark down the left edge - a short mark stays solid.
        g.setPaint(Nocturne.ACCENT);
        g.fill(new java.awt.geom.Rectangle2D.Double(
                GUTTER, plaqueTop + Nocturne.SPACE_3, 2,
                plaqueHeight - Nocturne.SPACE_3 * 2));

        double x = GUTTER + Nocturne.SPACE_4 + 4;
        double y = plaqueTop + Nocturne.SPACE_4 + 13;
        for (int i = 0; i < codex.length; i++) {
            boolean call = i == codex.length - 1;
            Nocturne.text(g, codex[i], x, y, Nocturne.meta(12f),
                    call ? Nocturne.ACCENT_300 : Nocturne.alpha(Nocturne.TEXT, 0.86f));
            y += 21;
        }
    }

    private void footer(Graphics2D g, int w, int h) {
        double y = h - FOOTER / 2.0 + 4;
        Obelisk.dots(g, GUTTER, y - 15, 14, Nocturne.alpha(Nocturne.NEUTRAL_400, 0.5f));

        String left = status != null ? status : "IF THE LANG HOLDS";
        Color leftColour = status != null
                ? Nocturne.ACCENT_300
                : Nocturne.alpha(Nocturne.TEXT, 0.34f);
        Nocturne.text(g, left, GUTTER + 22, y, Nocturne.meta(10f), leftColour);

        String right = "DEPTH " + Math.round(Threshold.DEPTH_TO_MEDIAN * 100)
                + "% TO MEDIAN   /   " + artifact;
        FontMetrics fm = g.getFontMetrics(Nocturne.meta(10f));
        Nocturne.text(g, right, w - GUTTER - fm.stringWidth(right), y,
                Nocturne.meta(10f), Nocturne.alpha(Nocturne.TEXT, 0.34f));
    }

    /** The window's own edge; an undecorated frame has to draw its own. */
    @Override
    protected void paintBorder(Graphics g0) {
        Graphics2D g = Nocturne.quality(g0);
        Nocturne.hairline(g, new Rectangle(0, 0, getWidth() - 1, getHeight() - 1),
                Nocturne.NEUTRAL_800);
        g.dispose();
    }
}
