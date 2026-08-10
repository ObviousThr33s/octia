package com.serenity.octia.crew;

import java.util.Locale;

/**
 * One order, as a cleric gives it.
 *
 * <p>This is the whole vocabulary a model is allowed to speak into the world.
 * It is deliberately tiny — six verbs — because the clerics on this bench are
 * 3B to 14B instruction models, and the reliability of a local model's output
 * falls off a cliff the moment the grammar is wide enough to be interesting.
 * A small closed vocabulary is the difference between a crew that walks and a
 * crew that emits JSON-shaped apologies.
 *
 * <p><b>Everything unrecognised is a hold.</b> Not an error, not a retry, not a
 * guess. A model that rambles gets a crew member who stands still, which is
 * legible from across the room; a model whose rambling is <i>interpreted</i>
 * gets a crew member who walks into a ravine and it is never clear why.
 *
 * <p>No Minecraft classes are imported here on purpose. Parsing is the part of
 * the crew that can be wrong in a way a unit test can catch, so it is kept
 * where a unit test can reach it — see {@code OrderTest} and the split
 * described in AGENTS.md III - pure logic gets a JUnit test, in-world behaviour
 * gets a {@code @GameTest}, and both run under verify.ps1. docs/DEVOPS.md
 * argues the GameTest half of that.
 */
public record Order(Verb verb, String arg) {

    /** The closed vocabulary. */
    public enum Verb {
        /** Speak to the server. {@code arg} is the line. */
        SAY,
        /** Walk toward a named player and stop short of them. {@code arg} is the name. */
        FOLLOW,
        /** Walk a compass direction. {@code arg} is a {@link Heading} name, lowercase. */
        GO,
        /** Turn to face a named player without moving. {@code arg} is the name. */
        LOOK,
        /** Jump once. {@code arg} is empty. */
        JUMP,
        /** Stand still. The default, and the answer to anything unparseable. */
        HOLD
    }

    /**
     * The four compass headings and the yaw each one means.
     *
     * <p>Minecraft's yaw is not the compass most people carry: zero faces
     * <em>south</em> (+Z) and the angle grows clockwise, so west is +90 and east
     * is -90. Writing that down once here is cheaper than getting it wrong in
     * three places, and it is the kind of constant that a unit test can pin.
     *
     * <p>The other load-bearing fact about this enum is its <em>spelling</em>.
     * {@code name().toLowerCase(Locale.ROOT)} is the canonical {@code arg} of a
     * GO order: {@code parse} writes it, {@code Tender.next} writes it, and
     * {@code CrewPlayer.steer} reads it back through {@link #of(String)}. The
     * round trip closes only because the case labels in {@code of} below are
     * those same four words, written out by hand. Rename a constant and the two
     * writers follow it silently while {@code of} does not - the arg stops
     * matching, every GO becomes a HOLD, and a crew member just stops walking.
     */
    public enum Heading {
        NORTH(180.0F),
        SOUTH(0.0F),
        EAST(-90.0F),
        WEST(90.0F);

        private final float yaw;

        Heading(float yaw) {
            this.yaw = yaw;
        }

        public float yaw() {
            return yaw;
        }

        /** @return the heading named, or null. Accepts {@code n}, {@code north}, any case. */
        public static Heading of(String name) {
            if (name == null || name.isEmpty()) {
                return null;
            }
            String s = name.trim().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "n", "north" -> NORTH;
                case "s", "south" -> SOUTH;
                case "e", "east" -> EAST;
                case "w", "west" -> WEST;
                default -> null;
            };
        }
    }

    /** The order given when nothing else was understood. */
    public static final Order HOLD = new Order(Verb.HOLD, "");

    /** A spoken line longer than this is clipped; models pad, and chat has limits. */
    private static final int MAX_SAY = 96;

    public Order {
        arg = arg == null ? "" : arg;
    }

    /**
     * Reads one order out of whatever a model actually said.
     *
     * <p>Three things get stripped before parsing, each because a cleric on this
     * bench does it:
     *
     * <ul>
     *   <li><b>Reasoning blocks.</b> DeepSeek-R1-Distill and Qwen3 open with a
     *       {@code <think>} monologue that can run hundreds of tokens. The order,
     *       if there is one, is after it. An unterminated block means the model
     *       spent its whole budget thinking, and there is no order at all.
     *   <li><b>Markdown.</b> Instruction-tuned models fence short answers in
     *       backticks and bold them without being asked.
     *   <li><b>Preamble.</b> "Order: ", "> ", "- ", quotes. Cheap to drop.
     * </ul>
     *
     * <p>Only the first non-empty line is considered. A model that offers three
     * candidate orders has not given one.
     */
    public static Order parse(String reply) {
        if (reply == null) {
            return HOLD;
        }

        String text = stripThinking(reply);
        String line = firstLine(text);
        if (line.isEmpty()) {
            return HOLD;
        }

        int cut = line.indexOf(' ');
        String head = (cut < 0 ? line : line.substring(0, cut)).toLowerCase(Locale.ROOT);
        String rest = cut < 0 ? "" : line.substring(cut + 1).trim();

        return switch (head) {
            case "say", "chat", "speak" -> rest.isEmpty() ? HOLD : new Order(Verb.SAY, clip(rest));
            case "follow", "come", "goto" -> new Order(Verb.FOLLOW, name(rest));
            case "go", "walk", "move", "head" -> {
                Heading heading = Heading.of(rest);
                yield heading == null ? HOLD : new Order(Verb.GO, heading.name().toLowerCase(Locale.ROOT));
            }
            case "look", "face", "watch" -> new Order(Verb.LOOK, name(rest));
            case "jump", "hop" -> new Order(Verb.JUMP, "");
            case "hold", "stop", "wait", "stay", "idle" -> HOLD;
            // A bare heading with no verb is common and unambiguous; take it.
            case "north", "south", "east", "west" -> new Order(Verb.GO, head);
            default -> HOLD;
        };
    }

    /**
     * Drops a reasoning model's {@code <think>} block.
     *
     * <p>An unterminated block returns empty rather than the raw monologue: a
     * model still mid-thought has not issued an order, and feeding the monologue
     * to the parser would let a stray "go north" inside the reasoning become a
     * command the model never actually gave.
     */
    private static String stripThinking(String text) {
        int open = text.indexOf("<think>");
        if (open < 0) {
            return text;
        }
        int close = text.indexOf("</think>", open);
        if (close < 0) {
            return "";
        }
        return text.substring(0, open) + text.substring(close + "</think>".length());
    }

    /** First line with anything on it, cleaned of markdown and preamble. */
    private static String firstLine(String text) {
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            // Fences and bullets, in any combination and any order.
            line = line.replaceAll("^[`*_>#\\-\\s\"']+", "");
            line = line.replaceAll("[`*_\\s\"'.!,;]+$", "");
            if (line.isEmpty()) {
                continue;
            }
            // Only the colon form is unwrapped, and only for four labels:
            // "Order: go north" becomes "go north". "action = jump" is NOT
            // rewritten - it reaches the switch as the head "action", matches
            // nothing, and holds. That is the intended answer rather than a
            // gap: a model that invented its own syntax did not give an order.
            int colon = line.indexOf(':');
            if (colon >= 0 && colon <= 12 && line.substring(0, colon).matches("(?i)\\s*(order|action|command|reply)")) {
                line = line.substring(colon + 1).trim();
            }
            if (!line.isEmpty()) {
                return line;
            }
        }
        return "";
    }

    /**
     * A player name out of the tail of an order.
     *
     * <p>Takes the first word only and keeps just the characters a Minecraft
     * name can hold, so {@code follow kfman, please} and {@code follow "kfman"}
     * both resolve. An empty result is legal and means "whoever is nearest" —
     * the crew member decides, which is better than refusing to move because a
     * model forgot to name anybody.
     */
    private static String name(String rest) {
        if (rest.isEmpty()) {
            return "";
        }
        String first = rest.split("\\s+")[0];
        String cleaned = first.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    private static String clip(String said) {
        String flat = said.replaceAll("\\s+", " ").trim();
        return flat.length() <= MAX_SAY ? flat : flat.substring(0, MAX_SAY);
    }

    @Override
    public String toString() {
        // The parser's own grammar, which is not cosmetic. Situation.of ends
        // every report with "your last order: " and this string, so a cleric is
        // shown its previous order in exactly the form it is allowed to answer
        // with, and /octia crew prints the same text in its status column. A
        // prettier toString would quietly teach models a vocabulary that parse
        // rejects.
        String spoken = verb.name().toLowerCase(Locale.ROOT);
        return arg.isEmpty() ? spoken : spoken + " " + arg;
    }
}
