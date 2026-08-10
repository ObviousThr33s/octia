package com.serenity.octia.codex;

/**
 * Which act. Spelled, never numeric, because the notation was authored on a
 * Minecraft sign and reads {@code ACT ONE}.
 *
 * <p>The five acts are the five era layers of the design brief in the same
 * order, earliest to present. That is why the enum stops at five: it is not an
 * arbitrary cap, it is the length of the era stack.
 */
public enum Act {

    /** The Infinite City. Peak forerunner civilisation; structures near-complete. */
    ONE(1, "Infinite City"),

    /** Ground-Down City and Wasteland. The city eroding. */
    TWO(2, "Ground-Down City + Wasteland"),

    /** Wasteland, almost no city. Sparse ruins. */
    THREE(3, "Wasteland"),

    /** The Lost Generation. City gone; almost-working ships linger. */
    FOUR(4, "The Lost Generation"),

    /** The Modern World. Present-day generation, rare ruins. */
    FIVE(5, "Modern World");

    private final int index;
    private final String era;

    Act(int index, String era) {
        this.index = index;
        this.era = era;
    }

    /** One-based position in the act order, matching the spelled name. */
    public int index() {
        return index;
    }

    /** The era layer this act names. */
    public String era() {
        return era;
    }

    /** As it appears on a sign: {@code ACT ONE}. */
    public String toNotation() {
        return "ACT " + name();
    }

    /**
     * Parses {@code ACT ONE} or bare {@code ONE}, case-insensitively.
     *
     * @throws IllegalArgumentException on anything else, deliberately. A
     *         silently-defaulted act would put a walkthrough in the wrong era.
     */
    public static Act parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("no act given");
        }
        // Case folding is done per-character, not with toUpperCase(): that uses
        // the default locale, and under tr/az it maps 'i' to a dotted capital,
        // so "five" would stop matching FIVE and a lower-case act would throw on
        // a machine whose locale nobody chose deliberately.
        String word = text.trim();
        if (word.regionMatches(true, 0, "ACT", 0, 3)) {
            word = word.substring(3).trim();
        }
        for (Act act : values()) {
            if (act.name().equalsIgnoreCase(word)) {
                return act;
            }
        }
        throw new IllegalArgumentException(
                "not an act: '" + text + "'. Acts are spelled ONE..FIVE, never numeric.");
    }
}
