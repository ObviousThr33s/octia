package com.serenity.octia.codex;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipe-delimited audience: {@code |SEEK|SAGE|DEVOPS|ALL|USERS|}.
 *
 * <p>Leading and trailing pipes are part of the form, not decoration.
 *
 * <p><b>SETTLED 2026-08-28 by KEG. The question was never which reading was
 * right - it was who was reading.</b> Written as an alternation,
 * {@code |A|B|C|} reads as a <em>selector</em>: pick one. Divided by, as the
 * Mobius note does:
 *
 * <pre>aberration = d(theta)/d(arc) / |SCOPE|</pre>
 *
 * it reads as a <em>cardinality</em>: how many. Both are correct, and they
 * serve different consumers:
 *
 * <ul>
 *   <li><b>{@link Reading#SIMPLEX}</b> - one channel at a time. The pipes are a
 *       selector, {@link #selected(Endian)} is the answer, and the divisor is 1
 *       because a simplex reader is only ever on one channel.</li>
 *   <li><b>{@link Reading#MULTIPLEX}</b> - every channel at once. The pipes are
 *       a count, {@link #size()} is the answer, and that is what divides in the
 *       aberration.</li>
 * </ul>
 *
 * <p>Use {@link #divisor(Reading)} rather than reaching for {@link #size()}
 * when the number is going into the physics, because it is the reading and not
 * the member list that decides what the denominator is.
 *
 * <p><b>Endian.</b> A scope is also read from an end. {@link Endian#LEFT} reads
 * it as written and {@link Endian#RIGHT} reads it from the far end, which is
 * what selects a different member under the simplex reading without changing a
 * character of the notation. {@link #cycle()} rotates the members one step, so
 * a scope can be walked through its own channels. The dev saves named
 * {@code ENDIAN_RIGHT} and {@code MODE_WORLD_ENDIAN_KEG_} are the in-world
 * record of this, and AGENTS.md is clear that the world outranks this file if
 * they ever disagree.
 *
 * <p>This class is deliberately for <b>both machine and user</b>: the same
 * pipes that a person reads off a sign are the ones a server divides by.
 * Nothing here is fast, and nothing here needs to be - a scope is a handful of
 * short strings read once, and legibility is worth more than cycles at this
 * size.
 */
public final class Scope {

    /**
     * Who is reading the pipes. See the class note - this is the axis that
     * settled the old open question, and it is why {@code |A|B|C|} can be both
     * a selector and a count without contradicting itself.
     */
    public enum Reading {

        /** One channel at a time. The pipes select; the divisor is 1. */
        SIMPLEX,

        /** Every channel at once. The pipes count; the divisor is the count. */
        MULTIPLEX;

        /** As it would be written in a scope line. */
        public String toNotation() {
            return name();
        }
    }

    /**
     * Which end a scope is read from.
     *
     * <p>Only the simplex reading can tell the difference: a multiplex reader is
     * on every channel already, so turning the list around changes nothing it
     * can observe. That asymmetry is the point rather than an oversight.
     */
    public enum Endian {

        /** As written, left to right. {@code |A|B|C|} selects A. */
        LEFT,

        /** From the far end. {@code |A|B|C|} selects C. */
        RIGHT
    }

    /**
     * Everyone. Under the <em>selector</em> reading, one name among many.
     *
     * <p><b>It still settles nothing about the divisor, and now it does not
     * have to.</b> {@link #size()} is 1 here - the constant holds a single
     * member spelled ALL, not a population - so {@code / |SCOPE|} divides by one
     * under either reading. Whether ALL should instead stand for an unbounded
     * cardinality is a separate question about this constant, not about the
     * notation, and it is still open.
     */
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

    /**
     * The members, in written order. The field is already immutable - the
     * constructor stores {@code List.copyOf(members)} - so this hands it back
     * directly rather than wrapping an unmodifiable view around an
     * unmodifiable list.
     */
    public List<String> members() {
        return members;
    }

    /**
     * How many members. This is the raw count, and it is the cardinality
     * reading of {@code |SCOPE|} - but prefer {@link #divisor(Reading)} when the
     * number is going into the aberration, because a simplex reader divides by
     * one no matter how many channels are written down.
     */
    public int size() {
        return members.size();
    }

    /**
     * The members in the order this end reads them. {@link Endian#LEFT} is the
     * written order; {@link Endian#RIGHT} is the reverse.
     */
    public List<String> read(Endian endian) {
        if (endian == null) {
            throw new IllegalArgumentException("a scope is read from an end");
        }
        if (endian == Endian.LEFT) {
            return members;
        }
        List<String> out = new ArrayList<>(members);
        java.util.Collections.reverse(out);
        return List.copyOf(out);
    }

    /**
     * The one member a simplex reader is on, entering from this end.
     *
     * <p>Meaningless under {@link Reading#MULTIPLEX}, which is why it takes an
     * {@link Endian} and not a {@link Reading}: asking for the selection is
     * already the simplex question.
     */
    public String selected(Endian endian) {
        return read(endian).get(0);
    }

    /**
     * What {@code / |SCOPE|} divides by, for this reader.
     *
     * <p><b>Simplex divides by one.</b> Not by the member count - a simplex
     * reader occupies a single channel, so the written width of the scope
     * attenuates nothing for them. Multiplex divides by {@link #size()}. This
     * method exists so that the difference is stated at the call site instead of
     * being remembered.
     */
    public int divisor(Reading reading) {
        if (reading == null) {
            throw new IllegalArgumentException("a divisor needs a reading");
        }
        return reading == Reading.SIMPLEX ? 1 : size();
    }

    /**
     * The same scope rotated one step, so the next member is the one this end
     * selects: {@code |A|B|C|} cycles to {@code |B|C|A|}.
     *
     * <p>Cycling a one-member scope returns an equal scope rather than throwing.
     * A single channel that keeps selecting itself is the correct answer, not an
     * error, and {@link #ALL} is exactly that case.
     */
    public Scope cycle() {
        if (members.size() == 1) {
            return this;
        }
        List<String> out = new ArrayList<>(members.subList(1, members.size()));
        out.add(members.get(0));
        return new Scope(out);
    }

    /** The scope written from the other end. {@code |A|B|C|} to {@code |C|B|A|}. */
    public Scope reversed() {
        return new Scope(read(Endian.RIGHT));
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
