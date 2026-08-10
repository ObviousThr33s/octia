package com.serenity.octia.codex;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An artifact name in the bracket-flag form:
 *
 * <pre>
 * SERENITY_[0.0.0.S.A.F.E].project
 * OCTIOID_[0.1.0.A.C.T.1]_build
 * </pre>
 *
 * <p>Name, then a bracket holding a version triple followed by dot-separated
 * status flags, then the artifact type.
 *
 * <p><b>On the two separators.</b> The dev world's level name is
 * {@code SERENITY_[0.0.0.S.A.F.E].project} while its folder on disk is
 * {@code SERENITY_[0_0_0_S_A_F_E]_project}. That is not a second notation:
 * Minecraft sanitises dots out of save-directory names. The dotted form is
 * canonical and authored, the underscore form is derived.
 * {@link #toFilesystemName()} performs that derivation so nobody has to
 * rediscover it by comparing a save folder against the name they typed.
 */
public record ArtifactId(String name, int major, int minor, int patch,
                         List<String> flags, String type) {

    // Name, [triple + flags], then either . or _ before the type - accepting
    // both on input is what lets a folder name round-trip back to canonical.
    //
    // Seam: a flag is matched as [A-Za-z0-9]+, not a single character, though
    // docs/NOTATION.md says flags are single characters. Nothing emits a longer
    // one today; the + is the hook if a flag vocabulary ever grows words. Note
    // flagWord() only reads as a word while they stay one character each.
    private static final Pattern FORM = Pattern.compile(
            "^([A-Za-z0-9]+)_\\[(\\d+)[._](\\d+)[._](\\d+)((?:[._][A-Za-z0-9]+)*)\\][._]([A-Za-z0-9]+)$");

    public ArtifactId {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("artifact needs a name");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("artifact needs a type");
        }
        flags = List.copyOf(flags == null ? List.of() : flags);
    }

    /**
     * Builds an id directly. Note this upper-cases the name and
     * {@link #parse(String)} does not, so {@code of("serenity", ...)} and
     * {@code parse("serenity_[0.0.0]_project")} are <b>not equal</b> as records
     * even though NOTATION.md says NAME is caps. Nothing calls this yet, so the
     * asymmetry has never bitten; it is written down rather than fixed silently,
     * because fixing it means deciding whether the parser may normalise a name
     * it was handed - which changes what {@link #toNotation()} echoes back.
     */
    public static ArtifactId of(String name, int major, int minor, int patch,
                                String type, String... flags) {
        return new ArtifactId(name.toUpperCase(), major, minor, patch,
                List.of(flags), type);
    }

    /** Parses either the canonical dotted form or a filesystem-sanitised one. */
    public static ArtifactId parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("no artifact id given");
        }
        Matcher m = FORM.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "not an artifact id: '" + text + "'. Expected NAME_[major.minor.patch.FLAGS]_type");
        }
        // Group 5 is (?:[._]FLAG)* inside a match that already succeeded, so it
        // always participates: never null, at worst empty. Its leading
        // separator is present whenever it is non-empty, and every segment it
        // yields is non-empty by construction - so neither a null check nor an
        // empty-segment filter is reachable here.
        String tail = m.group(5);
        List<String> flags = tail.isEmpty()
                ? List.of()
                : List.of(tail.substring(1).split("[._]"));
        return new ArtifactId(m.group(1), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                flags, m.group(6));
    }

    /** The flags read as a word, e.g. {@code S.A.F.E} becomes {@code SAFE}. */
    public String flagWord() {
        return String.join("", flags);
    }

    /**
     * Canonical, dotted: {@code OCTIOID_[0.1.0.A.C.T.1]_build}.
     *
     * <p><b>The separator before the type is always {@code _}</b>, even when the
     * name was authored with a dot. All three dev worlds and
     * {@code tools/new-world.ps1} spell a level name as
     * {@code SERENITY_[a.b.c.S.A.F.E].project}, so this is deliberately not a
     * round trip for a world name: it comes back {@code ..._project}. That
     * follows the form line in docs/NOTATION.md and is pinned by
     * {@code NotationTest}. Look a save up by {@link #toFilesystemName()}.
     */
    public String toNotation() {
        StringBuilder b = new StringBuilder(name).append("_[")
                .append(major).append('.').append(minor).append('.').append(patch);
        for (String f : flags) {
            b.append('.').append(f);
        }
        return b.append("]_").append(type).toString();
    }

    /**
     * The form a filesystem will actually show, with dots inside the bracket
     * folded to underscores - which is what Minecraft does to a save folder.
     *
     * <p>The replace is unconditional, which is safe for anything that came
     * through {@link #parse(String)}: the pattern admits only
     * {@code [A-Za-z0-9]} in the name and the type, and {@link #toNotation()}
     * already writes an underscore before the type, so the only dots left to
     * fold are the ones inside the bracket. A name built by hand - or handed to
     * {@code of}, which validates nothing - can carry a dot, and that dot is
     * folded too.
     */
    public String toFilesystemName() {
        return toNotation().replace('.', '_');
    }

    @Override
    public String toString() {
        return toNotation();
    }
}
