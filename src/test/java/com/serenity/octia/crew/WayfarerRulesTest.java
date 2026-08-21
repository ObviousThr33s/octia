package com.serenity.octia.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two rules that keep a wayfarer's chaos harmless.
 *
 * <p>A stranger is driven by a local instruction model, which will sometimes
 * answer with something strange. The design's response is not to constrain the
 * model harder - it is to make strange answers unable to matter. Two of the
 * three mechanisms are pure logic and are pinned here; the third is that a
 * {@code CrewPlayer} has no client and therefore no hands, which is a fact about
 * the class rather than a rule that can regress.
 *
 * <p>No Minecraft classes are touched, for the same reason {@code OrderTest}
 * does not touch them: this is the part that can be wrong in a way a unit test
 * catches, so it is kept where a unit test can reach it.
 */
class WayfarerRulesTest {

    @Test
    @DisplayName("a stranger may speak, walk, look and do nothing")
    void permitKeepsTheReservedVocabulary() {
        assertEquals(Order.Verb.SAY, Wayfarer.permit(new Order(Order.Verb.SAY, "cold night")).verb());
        assertEquals(Order.Verb.GO, Wayfarer.permit(new Order(Order.Verb.GO, "north")).verb());
        assertEquals(Order.Verb.LOOK, Wayfarer.permit(new Order(Order.Verb.LOOK, "kfman")).verb());
        assertEquals(Order.Verb.HOLD, Wayfarer.permit(Order.HOLD).verb());
    }

    @Test
    @DisplayName("a stranger never follows, whatever the model says")
    void permitRefusesFollow() {
        // The verb that would turn a traveller into a companion. A model will
        // produce it - "follow" is in every crew example it may have seen - and
        // the character survives only because this drops it.
        assertEquals(Order.Verb.HOLD, Wayfarer.permit(new Order(Order.Verb.FOLLOW, "kfman")).verb());
    }

    @Test
    @DisplayName("a stranger never hops")
    void permitRefusesJump() {
        assertEquals(Order.Verb.HOLD, Wayfarer.permit(new Order(Order.Verb.JUMP, "")).verb());
    }

    @Test
    @DisplayName("the arg survives a permitted order untouched")
    void permitDoesNotRewriteWhatItAllows() {
        Order said = new Order(Order.Verb.SAY, "long way to the coast");
        assertEquals("long way to the coast", Wayfarer.permit(said).arg());
    }
}
