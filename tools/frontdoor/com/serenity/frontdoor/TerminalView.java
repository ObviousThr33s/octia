package com.serenity.frontdoor;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * What is on the other side of the door: the two gates, reporting.
 *
 * <p>This takes {@link DoorwayView}'s place in the right-hand region the first
 * time a run starts, and the swap is the whole idea rather than a layout
 * convenience. The door is the ENTER affordance; once it has been entered there
 * is nothing left for it to offer, and a window that keeps drawing an unopened
 * door beside a running build is describing a state the program is no longer
 * in. You went through, so this is what is through it.
 *
 * <p><b>Two lanes, one column.</b> The build runs once and then verify and play
 * report side by side, so every line carries which of them said it and the
 * gutter is fixed-width - {@code [VRFY] } is exactly as wide as {@code [PLAY] }
 * because both names are four characters, which is why they are four
 * characters. That is the only reason this class needs {@link Nocturne#mono};
 * a proportional font would put the two lanes' text at two different left
 * edges and the reader would have to find the start of each line rather than
 * running an eye down one.
 *
 * <p><b>It has to survive being painted with nothing attached.</b>
 * {@code FrontDoor --shot} composes the panel headlessly to render the window
 * to a PNG, and it does that without starting anything - so an empty terminal
 * is a legitimate state with a legitimate appearance, not a case to guard
 * against.
 */
public final class TerminalView extends JComponent {

    /** Which gate said it. Four characters each, so the gutter is one width. */
    public enum Lane {
        /** The dev client. */
        PLAY,
        /** The headless gametest gate. */
        VRFY,
        /** The door itself, narrating what it is about to do. */
        DOOR
    }

    /**
     * How a line reads at a glance.
     *
     * <p>Classified from the text rather than declared by the caller, because
     * the caller is a pipe: these lines are stdout from two PowerShell scripts
     * that already colour themselves for a console nobody is looking at. The
     * shapes below are those scripts' own conventions - {@code ==>} is
     * {@code Step()} in both {@code verify.ps1} and {@code play.ps1}, and the
     * two-space {@code pass}/{@code FAIL} prefixes are verify's result block.
     */
    private enum Tone { STEP, PASS, FAIL, PLAIN }

    private record Line(Lane lane, String text, Tone tone) { }

    /** One display row: a logical line, or a wrapped continuation of one. */
    private record Row(Line line, String text, boolean continuation) { }

    /**
     * How much scrollback is kept.
     *
     * <p>A {@code runClient} session prints for as long as you play, and
     * Minecraft is chatty - this is the difference between a terminal and a
     * memory leak with a font. Two thousand lines is more than anyone scrolls
     * and costs a few hundred kilobytes.
     */
    private static final int CAP = 2000;

    private static final float SIZE = 11.5f;
    private static final int HEAD = 30;
    private static final int PAD = 14;

    /** Rows moved per wheel notch. */
    private static final int WHEEL = 3;

    private final Deque<Line> lines = new ArrayDeque<>();

    /** Rows from the bottom. Zero means following the tail. */
    private int scrollBack;

    private String running;

    /** Wrapped rows for {@link #cachedWidth}, or null when it must be rebuilt. */
    private List<Row> cachedRows;
    private int cachedWidth = -1;
    private int cachedCount = -1;

    public TerminalView() {
        setOpaque(true);
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                scrollBack -= e.getWheelRotation() * WHEEL;
                repaint();
            }
        });
    }

    // ---- what the door tells it -------------------------------------------

    /**
     * Adds a line. Safe from any thread.
     *
     * <p>The readers that call this are one plain {@code Thread} per process,
     * blocked on {@code BufferedReader.readLine}. Making them hop to the event
     * thread here rather than at every call site is the difference between one
     * place that knows about Swing's threading rule and four.
     */
    public void append(Lane lane, String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> append(lane, text));
            return;
        }
        lines.addLast(new Line(lane, text == null ? "" : stripAnsi(text), tone(text)));
        while (lines.size() > CAP) {
            lines.removeFirst();
        }
        cachedRows = null;
        // Only the tail moves when following, so a reader that has scrolled up
        // to read a failure does not get yanked back down by the next line.
        repaint();
    }

    /** A one-word note in the header: what is happening, or null when idle. */
    public void running(String what) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> running(what));
            return;
        }
        running = what;
        repaint();
    }

    /** Whether anything has ever been said here. Drives the door/terminal swap. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    private static Tone tone(String raw) {
        if (raw == null) {
            return Tone.PLAIN;
        }
        String s = raw.trim();
        if (s.startsWith("==>")) {
            return Tone.STEP;
        }
        if (s.startsWith("pass ") || s.equals("VERIFY OK") || s.startsWith("BUILD SUCCESSFUL")) {
            return Tone.PASS;
        }
        if (s.startsWith("FAIL ") || s.startsWith("skip ")
                || s.contains("VERIFY FAILED") || s.contains("BUILD FAILED")
                || s.startsWith("FAILURE:") || s.startsWith("error:")
                || s.startsWith("Exception") || s.startsWith("Caused by:")) {
            return Tone.FAIL;
        }
        return Tone.PLAIN;
    }

    /**
     * Drops ANSI colour escapes.
     *
     * <p>Both scripts are invoked with {@code --console=plain} so Gradle does
     * not emit any, but {@code Write-Host -ForegroundColor} can still reach a
     * redirected pipe on some hosts, and one unhandled escape turns a line into
     * mojibake with a stray {@code [32m} in front of it.
     */
    private static String stripAnsi(String s) {
        int esc = s.indexOf(0x1B);
        if (esc < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0x1B) {
                while (i < s.length() && s.charAt(i) != 'm') {
                    i++;
                }
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    // ---- paint -------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Nocturne.quality(g0);
        int w = getWidth();
        int h = getHeight();

        g.setPaint(Nocturne.BG);
        g.fillRect(0, 0, w, h);

        head(g, w);

        g.setFont(Nocturne.mono(SIZE));
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();
        // Monospace, so one measurement of the gutter holds for every lane.
        int gutter = fm.stringWidth("[XXXX] ");
        int textLeft = PAD + gutter;
        int textWidth = Math.max(1, w - textLeft - PAD);

        if (lines.isEmpty()) {
            Nocturne.text(g, "nothing has been run yet", PAD, HEAD + PAD + fm.getAscent(),
                    Nocturne.mono(SIZE), Nocturne.alpha(Nocturne.TEXT, 0.30f));
            g.dispose();
            return;
        }

        List<Row> rows = rows(fm, textWidth);
        int bodyTop = HEAD + PAD / 2;
        int visible = Math.max(1, (h - bodyTop - PAD) / lineH);

        // Clamp here rather than in the wheel handler: the bounds depend on the
        // wrap, which depends on the width, which is only known while painting.
        int maxBack = Math.max(0, rows.size() - visible);
        if (scrollBack > maxBack) {
            scrollBack = maxBack;
        }
        if (scrollBack < 0) {
            scrollBack = 0;
        }

        int first = Math.max(0, rows.size() - visible - scrollBack);
        int last = Math.min(rows.size(), first + visible);

        Rectangle clip = new Rectangle(0, bodyTop, w, h - bodyTop);
        g.setClip(clip);

        int y = bodyTop + fm.getAscent();
        for (int i = first; i < last; i++) {
            Row row = rows.get(i);
            if (!row.continuation()) {
                g.setFont(Nocturne.mono(SIZE));
                g.setPaint(laneColour(row.line().lane()));
                g.drawString("[" + row.line().lane() + "] ", PAD, y);
            }
            g.setPaint(toneColour(row.line().tone()));
            g.drawString(row.text(), textLeft, y);
            y += lineH;
        }

        g.setClip(null);
        if (scrollBack > 0) {
            scrolled(g, w, h, fm, scrollBack);
        }
        g.dispose();
    }

    /** The strip along the top: what this is, and whether anything is running. */
    private void head(Graphics2D g, int w) {
        Nocturne.text(g, "TERMINAL", PAD, HEAD / 2.0 + 4, Nocturne.kicker(10f),
                Nocturne.alpha(Nocturne.TEXT, 0.38f));

        String right = running != null ? running : "IDLE";
        FontMetrics fm = g.getFontMetrics(Nocturne.kicker(10f));
        Nocturne.text(g, right, w - PAD - fm.stringWidth(right), HEAD / 2.0 + 4,
                Nocturne.kicker(10f),
                running != null ? Nocturne.ACCENT_300 : Nocturne.alpha(Nocturne.TEXT, 0.28f));

        Nocturne.rule(g, PAD, HEAD, w - PAD * 2);
    }

    /**
     * Says so when the tail is not on screen.
     *
     * <p>Without it, scrolling up during a run looks identical to a run that
     * has stopped producing output - and those want opposite reactions.
     */
    private void scrolled(Graphics2D g, int w, int h, FontMetrics fm, int back) {
        String note = back + " lines below";
        int tw = fm.stringWidth(note);
        double x = w - PAD - tw;
        double y = h - PAD / 2.0;
        g.setPaint(Nocturne.alpha(Nocturne.SURFACE, 0.92f));
        g.fill(Nocturne.round(x - 8, y - fm.getAscent() - 3,
                tw + 16, fm.getHeight() + 4, Nocturne.RADIUS_SM));
        Nocturne.text(g, note, x, y, Nocturne.mono(SIZE), Nocturne.ACCENT_300);
    }

    private static Color laneColour(Lane lane) {
        return switch (lane) {
            case PLAY -> Nocturne.alpha(Nocturne.ACCENT_400, 0.85f);
            case VRFY -> Nocturne.alpha(Nocturne.NEUTRAL_400, 0.75f);
            case DOOR -> Nocturne.alpha(Nocturne.TEXT, 0.30f);
        };
    }

    private static Color toneColour(Tone tone) {
        return switch (tone) {
            case STEP -> Nocturne.ACCENT_300;
            case PASS -> Nocturne.OK;
            case FAIL -> Nocturne.BAD;
            case PLAIN -> Nocturne.alpha(Nocturne.TEXT, 0.66f);
        };
    }

    // ---- wrapping ----------------------------------------------------------

    /**
     * Logical lines, wrapped to the current width.
     *
     * <p>Wrapped rather than clipped, and the exception proves the rule: the
     * one line in a whole run that has to be read in full is the assertion
     * message on a failed gametest, and it is by far the longest thing either
     * script prints. Clipping is the cheaper implementation and it throws away
     * precisely the output the window exists to show.
     *
     * <p>Cached on width and line count, because this runs on every repaint and
     * a repaint happens on every line of a build.
     */
    private List<Row> rows(FontMetrics fm, int width) {
        if (cachedRows != null && cachedWidth == width && cachedCount == lines.size()) {
            return cachedRows;
        }
        List<Row> out = new ArrayList<>(lines.size());
        for (Line line : lines) {
            String text = line.text();
            if (text.isEmpty()) {
                out.add(new Row(line, "", false));
                continue;
            }
            boolean first = true;
            int at = 0;
            while (at < text.length()) {
                int take = fits(fm, text, at, width);
                out.add(new Row(line, text.substring(at, at + take), !first));
                at += take;
                first = false;
            }
        }
        cachedRows = out;
        cachedWidth = width;
        cachedCount = lines.size();
        return out;
    }

    /**
     * How many characters from {@code at} fit in {@code width}.
     *
     * <p>Measured rather than divided by a character width. The font is
     * fixed-width for the Latin set this output is made of, but
     * {@code Nocturne.quality} turns fractional metrics on and a stack trace
     * can carry anything, so asking the {@link FontMetrics} is the only answer
     * that cannot drift. Always takes at least one character, or a width
     * narrower than one glyph would loop forever.
     */
    private static int fits(FontMetrics fm, String text, int at, int width) {
        int lo = 1;
        int hi = text.length() - at;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(text.substring(at, at + mid)) <= width) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return Math.max(1, lo);
    }
}
