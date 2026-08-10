package com.serenity.octia.codex;

import java.util.List;
import java.util.Optional;

/**
 * The four-line KEG block.
 *
 * <pre>
 * ACT ONE
 * MILESTONE 0
 * START HERE
 * SEEK KEG
 * </pre>
 *
 * <p>Four lines because a Minecraft sign has four. The notation was authored
 * on a sign at {@code (-112, 67, -149)} and the constraint is the point:
 * anything that will not fit in four lines is more than one milestone.
 *
 * <p>The third line is the <b>subject</b>, and it takes either form seen in
 * the wild - a bare directive ({@code START HERE}) as on the sign, or an
 * {@link ArtifactId} ({@code OCTIOID_[0.1.0.A.C.T.1]_build}) as in the
 * composite. {@link #artifact()} tells you which you got rather than making
 * callers re-parse it.
 *
 * <p><b>Nothing under {@code src/main} calls this package.</b> Its only caller
 * today is {@code NotationTest}, and that is on purpose rather than neglect:
 * AGENTS.md I asks for the notation as types instead of re-parsed strings, so
 * the codex exists ahead of its callers and must not be pruned as dead code.
 * <b>Seam:</b> {@link #parse(String)} is where a KEG sign read out of the world
 * enters the JVM - whatever reads sign text first should land here, not on a
 * second string parser written beside it.
 */
public record Notation(Act act, int milestone, String subject, Seek seek) {

    public Notation {
        if (milestone < 0) {
            // MILESTONE 0 is the entry, not the first achievement, so zero is
            // meaningful and negative is not.
            throw new IllegalArgumentException("milestone is zero-based: " + milestone);
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("the third line needs a subject");
        }
        if (seek == null) {
            throw new IllegalArgumentException("every block ends in a SEEK");
        }
        subject = subject.trim();
    }

    /** A block whose subject is a directive. */
    public static Notation of(Act act, int milestone, String directive, Seek seek) {
        return new Notation(act, milestone, directive, seek);
    }

    /** A block whose subject is an artifact. */
    public static Notation of(Act act, int milestone, ArtifactId artifact, Seek seek) {
        return new Notation(act, milestone, artifact.toNotation(), seek);
    }

    /** The subject as an artifact, or empty when it is a plain directive. */
    public Optional<ArtifactId> artifact() {
        try {
            return Optional.of(ArtifactId.parse(subject));
        } catch (IllegalArgumentException notAnArtifact) {
            return Optional.empty();
        }
    }

    /** The four lines, in order. */
    public List<String> lines() {
        return List.of(act.toNotation(), "MILESTONE " + milestone, subject, seek.toNotation());
    }

    /** The block as written, newline-separated, no trailing newline. */
    public String block() {
        return String.join("\n", lines());
    }

    /**
     * Parses a four-line block. Blank lines are dropped first, so a block
     * copied off a sign - where unused lines are empty strings - parses the
     * same as one typed by hand.
     */
    public static Notation parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("no block given");
        }
        List<String> lines = text.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        if (lines.size() != 4) {
            throw new IllegalArgumentException(
                    "a block is exactly four lines, got " + lines.size() + ": " + lines);
        }

        String milestoneLine = lines.get(1).toUpperCase();
        if (!milestoneLine.startsWith("MILESTONE")) {
            throw new IllegalArgumentException("second line must be MILESTONE <n>: " + lines.get(1));
        }
        int milestone;
        try {
            milestone = Integer.parseInt(milestoneLine.substring("MILESTONE".length()).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("MILESTONE takes a number: " + lines.get(1));
        }

        return new Notation(Act.parse(lines.get(0)), milestone, lines.get(2), Seek.parse(lines.get(3)));
    }

    @Override
    public String toString() {
        return block();
    }
}
