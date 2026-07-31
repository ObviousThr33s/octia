package com.serenity.octioid.codex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A pipe-delimited audience: {@code |SEEK|SAGE|HELIOS|ALL|USERS|}.
 *
 * <p>Leading and trailing pipes are part of the form, not decoration.
 *
 * <p><b>An open question is preserved here rather than hidden.</b> Written as
 * an alternation, {@code |A|B|C|} reads as a <em>selector</em> - pick one. But
 * the Mobius note divides by it:
 *
 * <pre>aberration = d(theta)/d(arc) / |SCOPE|</pre>
 *
 * which implies a <em>cardinality</em> - how many. Those are different
 * functions and produce different aberration curves. This class therefore
 * exposes both {@link #size()} and {@link #members()} and commits to neither.
 * Picking one silently would bake a guess into the physics.
 */
public final class Scope {

    /** Everyone. The widest scope, under which aberration averages toward zero. */
    public static final Scope ALL = new Scope(List.of("ALL"));

    private final List<String> members;

    private Scope(List<String> members) {
        this.members = List.copyOf(members);
    }

    /** Builds a scope from member names, in order. */
    public static Scope of(String... names) {
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("a scope needs at least one member");
        }
        List<String> out = new ArrayList<>(names.length);
        for (String n : names) {
            String trimmed = n == null ? "" : n.trim().toUpperCase();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("empty scope member");
            }
            out.add(trimmed);
        }
        return new Scope(out);
    }

    /**
     * Parses {@code |A|B|C|}. Tolerant of missing outer pipes on input;
     * {@link #toNotation()} always emits them.
     */
    public static Scope parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("no scope given");
        }
        String body = text.trim();
        if (body.startsWith("|")) {
            body = body.substring(1);
        }
        if (body.endsWith("|")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            throw new IllegalArgumentException("empty scope: '" + text + "'");
        }
        return of(body.split("\\|", -1));
    }

    /** The members, in written order. */
    public List<String> members() {
        return Collections.unmodifiableList(members);
    }

    /**
     * How many members. This is the cardinality reading of {@code |SCOPE|} -
     * see the class note before using it as a divisor.
     */
    public int size() {
        return members.size();
    }

    public boolean contains(String name) {
        return name != null && members.contains(name.trim().toUpperCase());
    }

    /** Canonical form, outer pipes included. */
    public String toNotation() {
        return "|" + String.join("|", members) + "|";
    }

    @Override
    public String toString() {
        return toNotation();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Scope other && members.equals(other.members);
    }

    @Override
    public int hashCode() {
        return members.hashCode();
    }
}
