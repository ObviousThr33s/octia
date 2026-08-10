package com.serenity.octia.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The eleven clerics on this bench, and the names they take aboard.
 *
 * <p>These are the real file names from {@code D:\Models}, not invented
 * examples. A coining rule tested on tidy input is a coining rule tested on
 * input that does not exist: the awkward cases here — a version number with a
 * dot in it, a quantisation tag with three underscores, a suffix that is two
 * letters long and means "instruction tuned" — are the whole difficulty.
 *
 * <p>Every name must be something Minecraft would accept for a profile, because
 * a name it would not accept is not a bug that shows up here. It shows up as a
 * crew member who never appears.
 */
class SeatNameTest {

    /** The roster, exactly as summon-clerics.ps1 brings it home. */
    private static final List<String> BENCH = List.of(
            "nomic-embed-text-v1.5.Q8_0.gguf",
            "gemma-3-12b-mmproj-f16.gguf",
            "whisper-ggml-large-v3-turbo.bin",
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            "gemma-3-12b-it-Q4_K_M.gguf",
            "Mistral-Nemo-Instruct-2407-Q4_K_M.gguf",
            "Qwen2.5-Coder-14B-Instruct-Q4_K_M.gguf",
            "DeepSeek-R1-Distill-Qwen-14B-Q4_K_M.gguf",
            "Qwen3-14B-Q4_K_M.gguf",
            "phi-4-Q4_K_M.gguf");

    @Test
    @DisplayName("every cleric on the bench gets a name a player could have")
    void wholeBenchIsLegal() {
        for (String cleric : BENCH) {
            String seat = SeatName.coin(cleric);
            assertTrue(SeatName.isLegal(seat), cleric + " coined the illegal name " + seat);
        }
    }

    @Test
    @DisplayName("the build details go, the model survives")
    void quantAndTuningStripped() {
        assertEquals("Llama_3_2_3B", SeatName.coin("Llama-3.2-3B-Instruct-Q4_K_M.gguf"));
        assertEquals("gemma_3_12b", SeatName.coin("gemma-3-12b-it-Q4_K_M.gguf"));
        assertEquals("Qwen3_14B", SeatName.coin("Qwen3-14B-Q4_K_M.gguf"));
        assertEquals("phi_4", SeatName.coin("phi-4-Q4_K_M.gguf"));
        // The only one here that is also truncated, which is easy to misread as
        // a rule: "nomic_embed_text_v1_5" is twenty-one characters, and what the
        // sixteen happen to keep is the whole readable name. No rule strips v1.5.
        assertEquals("nomic_embed_text", SeatName.coin("nomic-embed-text-v1.5.Q8_0.gguf"));
    }

    @Test
    @DisplayName("names too long for a profile are cut to sixteen and still read")
    void longNamesTruncate() {
        assertEquals("Meta_Llama_3_1_8", SeatName.coin("Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"));
        assertEquals("DeepSeek_R1_Dist", SeatName.coin("DeepSeek-R1-Distill-Qwen-14B-Q4_K_M.gguf"));
        assertEquals("Qwen2_5_Coder_14", SeatName.coin("Qwen2.5-Coder-14B-Instruct-Q4_K_M.gguf"));
        assertEquals("Mistral_Nemo_Ins", SeatName.coin("Mistral-Nemo-Instruct-2407-Q4_K_M.gguf"));
    }

    @Test
    @DisplayName("an endpoint's own ids work as well as file names")
    void servedIds() {
        assertEquals("llama3_2_3b", SeatName.coin("llama3.2:3b"));
        assertEquals("qwen2_5_coder_14", SeatName.coin("qwen2.5-coder-14b-instruct"));
        assertEquals("Llama_3_2_3B", SeatName.coin("bartowski/Llama-3.2-3B-Instruct-GGUF/Llama-3.2-3B-Instruct-Q4_K_M.gguf"));
    }

    @Test
    @DisplayName("nothing sensible left means a cleric is still called something")
    void degenerateInput() {
        assertEquals("cleric", SeatName.coin(""));
        assertEquals("cleric", SeatName.coin(null));
        assertEquals("cleric", SeatName.coin("---.gguf"));
    }

    @Test
    @DisplayName("two clerics never take the same seat name")
    void collisionsAreBrokenInsideSixteen() {
        Set<String> taken = new LinkedHashSet<>();
        String first = SeatName.coin("llama3.2:3b", taken);
        taken.add(first);
        String second = SeatName.coin("llama3.2:3b", taken);

        assertNotEquals(first, second);
        assertTrue(SeatName.isLegal(second), second + " is not a legal name");
        assertEquals("llama3_2_3b_2", second);
    }

    /** The disambiguator has to fit inside the sixteen, not hang off the end. */
    @Test
    @DisplayName("a collision on an already-full name still fits")
    void collisionOnAFullName() {
        Set<String> taken = new LinkedHashSet<>(List.of("DeepSeek_R1_Dist"));
        String second = SeatName.coin("DeepSeek-R1-Distill-Qwen-14B-Q4_K_M.gguf", taken);

        assertEquals(SeatName.MAX, second.length());
        assertTrue(SeatName.isLegal(second));
        assertEquals("DeepSeek_R1_Di_2", second);
    }

    @Test
    @DisplayName("the whole bench mustered at once gives eleven distinct names")
    void wholeBenchIsDistinct() {
        Set<String> taken = new LinkedHashSet<>();
        for (String cleric : BENCH) {
            String seat = SeatName.coin(cleric, taken);
            assertTrue(taken.add(seat.toLowerCase(java.util.Locale.ROOT)), "duplicate seat " + seat);
        }
        assertEquals(BENCH.size(), taken.size());
    }

    /**
     * Three of the eleven cannot hold a conversation. Mustering them and
     * watching every request fail is a worse first run than seating the eight
     * that can answer.
     */
    @Test
    @DisplayName("the embedder, the projector, and the ear are not chat clerics")
    void notEveryClericChats() {
        assertFalse(SeatName.isChatCleric("nomic-embed-text-v1.5.Q8_0.gguf"));
        assertFalse(SeatName.isChatCleric("gemma-3-12b-mmproj-f16.gguf"));
        assertFalse(SeatName.isChatCleric("whisper-ggml-large-v3-turbo.bin"));

        assertTrue(SeatName.isChatCleric("phi-4-Q4_K_M.gguf"));
        assertTrue(SeatName.isChatCleric("Qwen3-14B-Q4_K_M.gguf"));
    }

    @Test
    @DisplayName("what a legal name is")
    void legality() {
        assertTrue(SeatName.isLegal("phi_4"));
        assertTrue(SeatName.isLegal("A1234567890_2345"));
        assertFalse(SeatName.isLegal("A12345678901234567"));
        assertFalse(SeatName.isLegal("has a space"));
        assertFalse(SeatName.isLegal("dots.are.out"));
        assertFalse(SeatName.isLegal(""));
        assertFalse(SeatName.isLegal(null));
    }
}
