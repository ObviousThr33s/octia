package com.serenity.frontdoor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SEEK PLAY |DEV|: both gates, one press, reporting into {@link TerminalView}.
 *
 * <p><b>Why the two are sequenced and not simultaneous.</b> The obvious reading
 * of "run verify alongside play" is two processes at once, and the Minecraft
 * side of that is genuinely fine - {@code runClient} keeps its world in
 * {@code run/saves} and {@code runGametest} keeps its in
 * {@code build/gametest/world}, so the two never contend for a
 * {@code session.lock}, and verify's stale-server sweep matches on
 * {@code runGametest} in a command line, which a client never carries.
 *
 * <p>Gradle is what makes it impossible. Both invocations lock the same
 * {@code .gradle/8.14.2/*.lock} files and write the same {@code build/} tree -
 * and {@code runClient} is a {@code JavaExec} <em>inside</em> the build, so the
 * build does not end when the game window appears. It ends when you quit. Start
 * verify after play and it blocks on the project lock for the length of the
 * session; start play after verify and you wait a minute, once.
 *
 * <p>So: verify first, which builds. Then play with {@code -SkipBuild}, which
 * is a switch {@code play.ps1} already has. One build, the gate answered before
 * you are in the world, and the answer still on screen behind the game.
 *
 * <p><b>Play runs whatever verify said.</b> The call is named PLAY; a red gate
 * is a thing to be told, not a veto on the button you pressed. The terminal
 * says so in its own lane and the game still opens.
 */
final class DevRun {

    private final Path repo;
    private final TerminalView terminal;

    /** Children, so a closed door does not leave a game and a build behind. */
    private final List<Process> alive = Collections.synchronizedList(new ArrayList<>());

    private final AtomicBoolean running = new AtomicBoolean();

    DevRun(Path repo, TerminalView terminal) {
        this.repo = repo;
        this.terminal = terminal;
    }

    /** Starts the pair, unless they are already going. Returns immediately. */
    void start() {
        if (!running.compareAndSet(false, true)) {
            terminal.append(TerminalView.Lane.DOOR, "already running - this call is not re-entrant");
            return;
        }
        Thread t = new Thread(this::sequence, "octia-dev-run");
        t.setDaemon(true);
        t.start();
    }

    private void sequence() {
        try {
            terminal.append(TerminalView.Lane.DOOR,
                    "SEEK PLAY |DEV| - verify first (it builds), then the game on -SkipBuild");

            terminal.running("VERIFY");
            int verify = run(TerminalView.Lane.VRFY, "tools\\verify.ps1");

            terminal.append(TerminalView.Lane.DOOR, verify == 0
                    ? "gate green - opening the game"
                    : "VERIFY FAILED (exit " + verify + ") - opening the game anyway");

            terminal.running("PLAY");
            int play = run(TerminalView.Lane.PLAY, "tools\\play.ps1", "-SkipBuild");
            terminal.append(TerminalView.Lane.DOOR, "the game closed (exit " + play + ")");
        } finally {
            terminal.running(null);
            running.set(false);
        }
    }

    /**
     * Runs one script to completion, streaming its output into the terminal.
     *
     * <p>Blocking, and called only from {@link #sequence} on its own thread.
     *
     * @return the exit code, or -1 if it could not be started at all
     */
    private int run(TerminalView.Lane lane, String relative, String... args) {
        Path script = repo.resolve(relative);
        if (!Files.isRegularFile(script)) {
            terminal.append(lane, "NOT FOUND: " + script);
            return -1;
        }

        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-Command", command(script, args))
                    .directory(repo.toFile())
                    // One stream, so a failure lands in sequence with the output
                    // that led to it rather than in a second pane nobody reads.
                    .redirectErrorStream(true)
                    .start();
            alive.add(process);

            // UTF-8 both ends. Windows PowerShell 5.1 writes a redirected pipe
            // in the console codepage, which is not what a modern JVM decodes
            // with by default - the two disagree and every em-dash in these
            // scripts' output arrives as mojibake. The preamble in command()
            // is the other half of this.
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    terminal.append(lane, line);
                }
            }
            return process.waitFor();
        } catch (IOException e) {
            terminal.append(lane, "FAILED to start " + relative + ": " + e.getMessage());
            return -1;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            terminal.append(lane, "interrupted");
            return -1;
        } finally {
            if (process != null) {
                alive.remove(process);
            }
        }
    }

    /**
     * The PowerShell command line: force UTF-8 out, then call the script.
     *
     * <p>{@code -Command} rather than {@code -File} because the encoding has to
     * be set inside the same session, before the script prints anything.
     * Single-quoted with doubled quotes, which is PowerShell's own escape, so a
     * repo path containing a space or an apostrophe survives.
     */
    private static String command(Path script, String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; & '")
                .append(script.toString().replace("'", "''"))
                .append('\'');
        for (String arg : args) {
            sb.append(' ').append(arg);
        }
        return sb.toString();
    }

    /**
     * Ends everything this started, for the window's close button.
     *
     * <p>Descendants and not just the child. The child is a PowerShell that
     * launched {@code gradlew.bat}, which launched a Gradle launcher, which
     * forked the Minecraft client - killing only the top of that leaves the
     * game running with no window that admits to owning it. This repo has met
     * the shape before: {@code tools/new-world.ps1} carries the same note about
     * killing gradle and leaving the server it forked behind.
     */
    void stopAll() {
        List<Process> snapshot;
        synchronized (alive) {
            snapshot = new ArrayList<>(alive);
        }
        for (Process p : snapshot) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }
    }
}
