package com.serenity.octia.crew;

import java.util.Locale;
import java.util.Set;

/**
 * Coining a player name for a cleric.
 *
 * <p>A Minecraft account name is at most <b>16 characters</b> of
 * {@code [A-Za-z0-9_]}. A cleric on the bench is called
 * {@code Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf} — thirty-eight characters,
 * three of them illegal. Something has to give, and the thing that gives is the
 * part of the filename that identifies the <em>build</em> rather than the
 * <em>model</em>: the container extension, the quantisation, and the
 * instruction-tuning suffix that every chat model carries and none is
 * distinguished by.
 *
 * <p>What survives is the vendor and the family, which is what a player reads
 * off the tab list and recognises: {@code Meta_Llama_3_1_8}, {@code phi_4},
 * {@code Qwen3_14B}, {@code gemma_3_12b}.
 *
 * <p>Truncation is from the right and nothing clever is attempted. Dropping
 * leading tokens to make room reads better on some names and turns
 * {@code Qwen2.5-Coder} into {@code 5_Coder} on others, and a naming rule that
 * is wrong occasionally is worse than one that is blunt always.
 *
 * <p>No Minecraft classes here either — same reason as {@link Order}.
 */
public final class SeatName {

    /** Vanilla's limit on a profile name. */
    public static final int MAX = 16;

    /** What a nameless cleric is called. */
    private static final String FALLBACK = "cleric";

    /**
     * Quantisation and precision tags, at the tail only.
     * Matches {@code -Q4_K_M}, {@code .Q8_0}, {@code -f16}, {@code -bf16}.
     */
    private static final String QUANT_TAIL =
            "(?i)[-_.](q\\d+([-_.][a-z0-9]+)*|f16|fp16|bf16|int8|int4|gguf)$";

    /** Tuning suffixes every chat cleric carries, so none is told apart by them. */
    private static final String TUNE_TAIL = "(?i)[-_.](instruct|instruction|chat|it)$";

    private SeatName() {
    }

    /**
     * Coins a name from a model id, with no regard for what is already aboard.
     *
     * @param modelId a file name ({@code phi-4-Q4_K_M.gguf}), an OpenAI-style id
     *                ({@code qwen3-14b}), or an Ollama tag ({@code llama3.2:3b})
     */
    public static String coin(String modelId) {
        if (modelId == null) {
            return FALLBACK;
        }

        // An org prefix or a directory is never the identifying part.
        String s = modelId;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }

        // Ollama writes llama3.2:3b; the tag after the colon is a size, keep it.
        s = s.replace(':', '-');

        s = s.replaceAll("(?i)\\.(gguf|bin|safetensors)$", "");

        // Repeatedly, because -Instruct-Q4_K_M is two tails and .Q8_0 hides one more.
        String previous;
        do {
            previous = s;
            s = s.replaceAll(QUANT_TAIL, "");
            s = s.replaceAll(TUNE_TAIL, "");
        } while (!s.equals(previous));

        s = s.replaceAll("[^A-Za-z0-9]+", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");

        if (s.isEmpty()) {
            return FALLBACK;
        }
        if (s.length() > MAX) {
            s = s.substring(0, MAX);
        }
        // Truncation can leave a trailing separator, which reads as a typo.
        s = s.replaceAll("_+$", "");
        return s.isEmpty() ? FALLBACK : s;
    }

    /**
     * Coins a name that nobody aboard is already using.
     *
     * <p>Two clerics can genuinely collide — {@code gemma-3-12b-it} and
     * {@code gemma-3-12b-mmproj-f16} both start {@code gemma_3_12b} — and two
     * players with one name is not a state the player list survives. The
     * disambiguator is appended inside the sixteen, never past it.
     *
     * @param taken names already seated, compared case-insensitively because the
     *              player list is not case-sensitive in practice
     */
    public static String coin(String modelId, Set<String> taken) {
        String base = coin(modelId);
        if (!isTaken(base, taken)) {
            return base;
        }
        for (int n = 2; n <= 99; n++) {
            String suffix = "_" + n;
            String head = base.length() + suffix.length() > MAX
                    ? base.substring(0, MAX - suffix.length())
                    : base;
            head = head.replaceAll("_+$", "");
            String candidate = head + suffix;
            if (!isTaken(candidate, taken)) {
                return candidate;
            }
        }
        // Ninety-eight disambiguators exhausted. This hands back a name that IS
        // taken, which is the one outcome the javadoc above says must not
        // happen - so catching it is the caller's job, and Crew.summon does:
        // it refuses any seat name already aboard and returns null. Reachable
        // only with ninety-nine clerics of one family, on a bench that
        // max_crew caps at eight by default.
        return base;
    }

    private static boolean isTaken(String candidate, Set<String> taken) {
        if (taken == null) {
            return false;
        }
        for (String t : taken) {
            if (t.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** True if this is a name vanilla would accept for a profile. */
    public static boolean isLegal(String name) {
        return name != null
                && !name.isEmpty()
                && name.length() <= MAX
                && name.matches("[A-Za-z0-9_]+");
    }

    /**
     * True if a model id names something that cannot hold a conversation.
     *
     * <p>The bench carries an embedding model, a vision projector, and a speech
     * model alongside the chat clerics. Mustering the whole roster and watching
     * three of them fail every request is a worse first run than quietly seating
     * the eight that can answer.
     */
    public static boolean isChatCleric(String modelId) {
        if (modelId == null) {
            return false;
        }
        String s = modelId.toLowerCase(Locale.ROOT);
        return !(s.contains("embed")
                || s.contains("mmproj")
                || s.contains("whisper")
                || s.contains("clip")
                || s.contains("rerank"));
    }
}
