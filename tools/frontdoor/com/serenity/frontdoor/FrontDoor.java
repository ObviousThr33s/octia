package com.serenity.frontdoor;

import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * OCTIA front door - the desktop entry to the repo.
 *
 * <pre>
 * ACT TWO
 * MILESTONE 1
 * DESOP_[0.1.0.R.M.B.H]_door
 * SEEK KEG |ALL|
 * </pre>
 *
 * <p>A JFrame with nothing on it but paint. No look-and-feel, no decorations,
 * no components that draw themselves their own way - the whole surface goes
 * through {@link Nocturne}, so the window matches the design project rather
 * than matching Windows.
 *
 * <p>Two modes:
 * <ul>
 *   <li>no arguments - open the door;
 *   <li>{@code --write-icon <path>} - render the .ico and exit, which is what
 *       the installer calls so the desktop shortcut has a real icon.
 * </ul>
 *
 * <p>Deliberately not here yet: pointing the obelisk. That is the next
 * milestone and it lands in {@link DoorwayView} and {@link Threshold}.
 */
public final class FrontDoor {

    /** The artifact this window is, in the notation. */
    public static final String ARTIFACT = "DESOP_[0.1.0.R.M.B.H]_door";

    private static final String[] CODEX = {
            "ACT TWO",
            "MILESTONE 1",
            ARTIFACT,
            "SEEK KEG |ALL|",
    };

    /** How close to an edge counts as a grab, in px. */
    private static final int GRIP = 6;

    private FrontDoor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--write-icon".equals(args[0])) {
            Path out = Paths.get(args[1]).toAbsolutePath();
            IconFile.writeIco(out);
            System.out.println("wrote " + out);
            return;
        }
        if (args.length >= 2 && ("--shot".equals(args[0]) || "--shot-dev".equals(args[0]))) {
            Path out = Paths.get(args[1]).toAbsolutePath();
            Dimension size = args.length >= 4
                    ? new Dimension(Integer.parseInt(args[2]), Integer.parseInt(args[3]))
                    : new Dimension(1180, 760);
            boolean through = "--shot-dev".equals(args[0]);
            // On the event thread, not this one. TerminalView.append hops to
            // the EDT when it is called from anywhere else - which is right for
            // the reader threads it was written for, and would mean the sample
            // lines below arrived after the paint that was supposed to show
            // them. A shot that renders an empty terminal is not a check.
            SwingUtilities.invokeAndWait(() -> {
                try {
                    shoot(out, size, through);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            System.out.println("wrote " + out);
            return;
        }
        Path repo = args.length >= 1 && !args[0].startsWith("--")
                ? Paths.get(args[0]).toAbsolutePath().normalize()
                : discoverRepo();
        SwingUtilities.invokeLater(() -> open(repo));
    }

    /**
     * Renders the window to a PNG without opening it. The window is
     * undecorated, so the panel is the whole of it and this is exactly what
     * appears on screen - which makes it a real check rather than an
     * approximation, and one that can be run without a person watching.
     */
    private static void shoot(Path out, Dimension size, boolean through) throws IOException {
        FrontDoorPanel panel = compose(discoverRepo(), new AtomicReference<>(),
                new AtomicReference<>(), () -> { }, () -> { });
        if (through) {
            // Sample output rather than a live run, on purpose: this has to be
            // renderable on a machine with no game and no Gradle, and what is
            // being checked is the drawing - the gutter alignment, the four
            // tones, the wrap - not the plumbing. Every line below is a real
            // shape one of the two scripts prints.
            TerminalView t = panel.terminal();
            t.running("VERIFY");
            t.append(TerminalView.Lane.DOOR,
                    "SEEK PLAY |DEV| - verify first (it builds), then the game on -SkipBuild");
            t.append(TerminalView.Lane.VRFY, "  repo: D:\\Serenity\\octia");
            t.append(TerminalView.Lane.VRFY, "==> build  (compile, remap, package)");
            t.append(TerminalView.Lane.VRFY, "BUILD SUCCESSFUL in 4s");
            t.append(TerminalView.Lane.VRFY, "==> gametest  (headless dedicated server, real game)");
            t.append(TerminalView.Lane.VRFY, "  pass  shipgametest.acornerpanelcompletesthehull");
            t.append(TerminalView.Lane.VRFY, "  pass  derelictgametest.thehullisacubeandtheringsurvives");
            t.append(TerminalView.Lane.VRFY, "  FAIL  crewbenchgametest.thebenchisexactlyasreachableasdeclared");
            t.append(TerminalView.Lane.VRFY, "        the run declared octia.crew.network=required and no bench"
                    + " answered. Tried http://127.0.0.1:1234, http://127.0.0.1:11434,"
                    + " http://127.0.0.1:8080. Start an endpoint, or declare"
                    + " octia.crew.network=absent and assert the offline contract instead.");
            t.append(TerminalView.Lane.VRFY, "  60 test(s): 1 failed, 0 errored, 0 skipped");
            t.append(TerminalView.Lane.VRFY, "VERIFY FAILED");
            t.append(TerminalView.Lane.DOOR, "VERIFY FAILED (exit 1) - opening the game anyway");
            t.append(TerminalView.Lane.PLAY, "  mod     : octia 0.2.0-alpha.1  (Minecraft 1.21.1)");
            t.append(TerminalView.Lane.PLAY, "==> runClient");
            t.append(TerminalView.Lane.PLAY, "[14:29:16] [main/INFO] (octia) Octia: hull cold,"
                    + " registry open. Andesite aboard.");
            t.running("PLAY");
            panel.through();
        }
        panel.setSize(size);
        panel.doLayout();
        for (java.awt.Component c : panel.getComponents()) {
            c.doLayout();
        }
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size.width, size.height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        panel.paint(g);
        g.dispose();
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        javax.imageio.ImageIO.write(img, "png", out.toFile());
    }

    // ---- the window -------------------------------------------------------

    /** Builds the panel and everything on it. One definition, two callers. */
    private static FrontDoorPanel compose(Path repo, AtomicReference<FrontDoorPanel> ref,
                                          AtomicReference<DevRun> runRef,
                                          Runnable onMinimise, Runnable onClose) {
        TerminalView terminal = new TerminalView();
        DevRun dev = new DevRun(repo, terminal);
        runRef.set(dev);

        // Through the door, then start. In that order, so the first line of
        // output has somewhere to land before it is written.
        Runnable enter = () -> {
            FrontDoorPanel p = ref.get();
            if (p != null) {
                p.through();
            }
            dev.start();
        };

        DoorwayView door = new DoorwayView("ENTER   /   SEEK PLAY |DEV|", enter);

        List<PortalButton> calls = new ArrayList<>();
        // The one primary call. The two below it still exist because a gate you
        // can only run as a pair is a gate you stop running - sometimes you
        // want the game without the minute, or the minute without the game.
        calls.add(new PortalButton(PortalButton.Kind.PRIMARY,
                "SEEK PLAY |DEV|", "both gates", enter));
        calls.add(new PortalButton(PortalButton.Kind.SECONDARY,
                "SEEK PLAY |ALL|", "tools\\play.ps1",
                () -> script(repo, "tools\\play.ps1", ref)));
        calls.add(new PortalButton(PortalButton.Kind.SECONDARY,
                "SEEK VERIFY |CI|", "tools\\verify.ps1",
                () -> script(repo, "tools\\verify.ps1", ref)));
        calls.add(new PortalButton(PortalButton.Kind.SECONDARY,
                "SEEK CODEX |ALL|", "AGENTS.md",
                () -> reveal(repo, "AGENTS.md", ref)));
        calls.add(new PortalButton(PortalButton.Kind.SECONDARY,
                "SEEK REPO |DEV|", repo.getFileName().toString(),
                () -> reveal(repo, "", ref)));

        FrontDoorPanel panel = new FrontDoorPanel(CODEX, ARTIFACT, door, terminal, calls,
                onMinimise, onClose);
        ref.set(panel);
        return panel;
    }

    private static void open(Path repo) {
        JFrame frame = new JFrame("Octia - Front Door");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setBackground(Nocturne.BG);
        frame.setIconImages(IconFile.frameIcons());

        AtomicReference<FrontDoorPanel> ref = new AtomicReference<>();
        AtomicReference<DevRun> runRef = new AtomicReference<>();
        FrontDoorPanel panel = compose(repo, ref, runRef,
                () -> frame.setExtendedState(JFrame.ICONIFIED),
                frame::dispose);

        // Every way out lands here - the chrome button, Escape, and the window
        // manager all end in dispose - so this is the one place that has to
        // remember the door started a game and a build.
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                DevRun dev = runRef.get();
                if (dev != null) {
                    dev.stopAll();
                }
            }
        });

        frame.setContentPane(panel);
        frame.setFocusTraversalPolicy(new ForwardOnly());
        frame.setFocusTraversalPolicyProvider(true);
        frame.setMinimumSize(new Dimension(1020, 660));
        frame.pack();
        frame.setLocationRelativeTo(null);

        installWindowGestures(frame, panel);
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        panel.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });

        frame.setVisible(true);
        panel.focusFirstCall();
    }

    /**
     * Drag by the header, resize by any edge. An undecorated window gets none
     * of this for free, and a window that cannot be moved is not a window.
     */
    private static void installWindowGestures(JFrame frame, FrontDoorPanel panel) {
        MouseAdapter gestures = new MouseAdapter() {
            private Point grabScreen;
            private Rectangle grabBounds;
            private int edge;

            @Override
            public void mouseMoved(MouseEvent e) {
                panel.setCursor(cursorFor(edgeAt(e.getPoint(), panel)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                edge = edgeAt(e.getPoint(), panel);
                if (edge == 0 && !panel.inHeader(e.getPoint())) {
                    return;
                }
                grabScreen = e.getLocationOnScreen();
                grabBounds = frame.getBounds();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                grabScreen = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grabScreen == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int dx = now.x - grabScreen.x;
                int dy = now.y - grabScreen.y;
                if (edge == 0) {
                    frame.setLocation(grabBounds.x + dx, grabBounds.y + dy);
                    return;
                }
                Rectangle r = new Rectangle(grabBounds);
                Dimension min = frame.getMinimumSize();
                if ((edge & WEST) != 0) {
                    int w = Math.max(min.width, grabBounds.width - dx);
                    r.x = grabBounds.x + grabBounds.width - w;
                    r.width = w;
                }
                if ((edge & EAST) != 0) {
                    r.width = Math.max(min.width, grabBounds.width + dx);
                }
                if ((edge & NORTH) != 0) {
                    int h = Math.max(min.height, grabBounds.height - dy);
                    r.y = grabBounds.y + grabBounds.height - h;
                    r.height = h;
                }
                if ((edge & SOUTH) != 0) {
                    r.height = Math.max(min.height, grabBounds.height + dy);
                }
                frame.setBounds(r);
                frame.validate();
            }
        };
        panel.addMouseListener(gestures);
        panel.addMouseMotionListener(gestures);
    }

    /**
     * A door admits nothing backward. Shift-Tab resolves to the same component
     * Tab would, so the keyboard only ever moves forward through the calls and
     * wraps at the end - there is no way to walk back in through the door.
     */
    private static final class ForwardOnly extends java.awt.ContainerOrderFocusTraversalPolicy {
        @Override
        public java.awt.Component getComponentBefore(java.awt.Container root,
                                                     java.awt.Component current) {
            return getComponentAfter(root, current);
        }
    }

    private static final int NORTH = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 4;
    private static final int EAST = 8;

    private static int edgeAt(Point p, JComponent c) {
        int e = 0;
        if (p.y <= GRIP) {
            e |= NORTH;
        }
        if (p.y >= c.getHeight() - GRIP) {
            e |= SOUTH;
        }
        if (p.x <= GRIP) {
            e |= WEST;
        }
        if (p.x >= c.getWidth() - GRIP) {
            e |= EAST;
        }
        return e;
    }

    private static Cursor cursorFor(int edge) {
        return switch (edge) {
            case NORTH -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case SOUTH -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            case WEST -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            case EAST -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case NORTH | WEST -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case NORTH | EAST -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case SOUTH | WEST -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case SOUTH | EAST -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    // ---- the calls --------------------------------------------------------

    /**
     * Hands a tools script to its own PowerShell window and gets out of the
     * way. {@code start} takes the first quoted argument as the window title,
     * which is also what keeps a path with a space in it from being read as
     * one.
     */
    private static void script(Path repo, String relative,
                               AtomicReference<FrontDoorPanel> ui) {
        Path script = repo.resolve(relative);
        if (!Files.isRegularFile(script)) {
            say(ui, "NOT FOUND: " + script);
            return;
        }
        try {
            new ProcessBuilder("cmd.exe", "/c", "start", "OCTIA " + relative,
                    "powershell.exe", "-NoExit", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString())
                    .directory(repo.toFile())
                    .start();
            say(ui, "LAUNCHED " + relative);
        } catch (IOException e) {
            say(ui, "FAILED: " + e.getMessage());
        }
    }

    private static void reveal(Path repo, String relative,
                               AtomicReference<FrontDoorPanel> ui) {
        Path target = relative.isEmpty() ? repo : repo.resolve(relative);
        if (!Files.exists(target)) {
            say(ui, "NOT FOUND: " + target);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                say(ui, "NO SHELL AVAILABLE");
                return;
            }
            Desktop.getDesktop().open(target.toFile());
            say(ui, "OPENED " + target.getFileName());
        } catch (IOException e) {
            say(ui, "FAILED: " + e.getMessage());
        }
    }

    private static void say(AtomicReference<FrontDoorPanel> ui, String message) {
        FrontDoorPanel p = ui.get();
        if (p != null) {
            p.status(message);
        }
    }

    // ---- where the repo is ------------------------------------------------

    /**
     * Walks up from the working directory looking for the marker files. The
     * launcher passes the root explicitly; this is the fallback for running the
     * class straight out of an IDE.
     */
    private static Path discoverRepo() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isRegularFile(p.resolve("gradlew.bat"))
                    && Files.isRegularFile(p.resolve("settings.gradle.kts"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }
}
