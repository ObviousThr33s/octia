package com.serenity.octia.codex;

import java.util.ArrayList;
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
        List<String> flags = new ArrayList<>();
        String tail = m.group(5);
        if (tail != null && !tail.isEmpty()) {
            // Leading separator always present when the group is non-empty.
            for (String f : tail.substring(1).split("[._]")) {
                if (!f.isEmpty()) {
                    flags.add(f);
                }
            }
        }
        return new ArtifactId(m.group(1), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                flags, m.group(6));
    }

    /** The flags read as a word, e.g. {@code S.A.F.E} becomes {@code SAFE}. */
    public String flagWord() {
        return String.join("", flags);
    }

    /** Canonical, dotted: {@code OCTIOID_[0.1.0.A.C.T.1]_build}. */
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
     */
    public String toFilesystemName() {
        return toNotation().replace('.', '_');
    }

    @Override
    public String toString() {
        return toNotation();
    }
}
