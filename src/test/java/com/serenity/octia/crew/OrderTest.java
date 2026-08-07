package com.serenity.octia.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the clerics actually say, and what it is allowed to mean.
 *
 * <p>Every string below is the shape of a real answer from a local instruction
 * model: the bare order, the order wrapped in markdown, the order behind a
 * label, the order behind a reasoning block, and the answer that is not an
 * order at all. The parser is the only place a model's output touches the
 * world, so it is the only place where being strict is free.
 */
class OrderTest {

    @Test
    @DisplayName("a bare order parses")
    void bareOrder() {
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("go north"));
        assertEquals(new Order(Order.Verb.JUMP, ""), Order.parse("jump"));
        assertEquals(new Order(Order.Verb.FOLLOW, "kfman"), Order.parse("follow kfman"));
        assertEquals(new Order(Order.Verb.SAY, "hello, glad to be aboard"),
                Order.parse("say hello, glad to be aboard"));
    }

    @Test
    @DisplayName("markdown, labels, and stray punctuation are stripped")
    void dressedUpOrder() {
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("**go north**"));
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("`go north`"));
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("Order: GO NORTH"));
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("- go north."));
        assertEquals(new Order(Order.Verb.JUMP, ""), Order.parse("jump!"));
        assertEquals(new Order(Order.Verb.FOLLOW, "kfman"), Order.parse("follow \"kfman\","));
    }

    /**
     * DeepSeek-R1-Distill and Qwen3 are both on this bench and both open with a
     * monologue. The order is what comes after it.
     */
    @Test
    @DisplayName("a reasoning block is dropped, and the order after it survives")
    void reasoningBlock() {
        assertEquals(new Order(Order.Verb.GO, "south"),
                Order.parse("<think>The player is south of me, so I should walk south.</think>\ngo south"));
    }

    /**
     * The interesting half of the same case: a model that spent its whole token
     * budget thinking never issued an order, and the "go north" inside its
     * reasoning is not one either.
     */
    @Test
    @DisplayName("an unfinished thought is not an order")
    void unfinishedReasoning() {
        assertSame(Order.HOLD, Order.parse("<think>Maybe I should go north, or perhaps"));
    }

    @Test
    @DisplayName("anything unrecognised holds")
    void unrecognisedHolds() {
        assertSame(Order.HOLD, Order.parse("I think I will head north for a while."));
        assertSame(Order.HOLD, Order.parse("Sure! Here are three things I could do:"));
        assertSame(Order.HOLD, Order.parse("{\"action\": \"move\", \"direction\": \"north\"}"));
        assertSame(Order.HOLD, Order.parse(""));
        assertSame(Order.HOLD, Order.parse("   "));
        assertSame(Order.HOLD, Order.parse(null));
    }

    @Test
    @DisplayName("only the first line counts")
    void firstLineOnly() {
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("go north\ngo south\njump"));
    }

    @Test
    @DisplayName("a heading on its own is a walk order")
    void bareHeading() {
        assertEquals(new Order(Order.Verb.GO, "north"), Order.parse("north"));
    }

    @Test
    @DisplayName("a walk order with no legible direction holds rather than guessing")
    void nonsenseHeading() {
        assertSame(Order.HOLD, Order.parse("go somewhere nice"));
        assertSame(Order.HOLD, Order.parse("go"));
    }

    @Test
    @DisplayName("follow with nobody named is legal and means whoever is nearest")
    void followNobody() {
        assertEquals(new Order(Order.Verb.FOLLOW, ""), Order.parse("follow"));
        assertEquals(new Order(Order.Verb.FOLLOW, ""), Order.parse("come"));
    }

    @Test
    @DisplayName("say with nothing to say holds")
    void emptySpeech() {
        assertSame(Order.HOLD, Order.parse("say"));
    }

    @Test
    @DisplayName("speech is flattened and clipped, because chat has limits")
    void longSpeech() {
        Order said = Order.parse("say " + "a".repeat(400));
        assertEquals(Order.Verb.SAY, said.verb());
        assertEquals(96, said.arg().length());
    }

    /**
     * Minecraft's yaw is not a compass. Zero faces south and the angle grows
     * clockwise, so these four numbers are the ones a crew member turns to and
     * are worth pinning where a mistake would otherwise show up as a bot that
     * walks the wrong way and nobody can say why.
     */
    @Test
    @DisplayName("the headings mean the yaws Minecraft means")
    void headingYaw() {
        assertEquals(0.0F, Order.Heading.SOUTH.yaw());
        assertEquals(180.0F, Order.Heading.NORTH.yaw());
        assertEquals(90.0F, Order.Heading.WEST.yaw());
        assertEquals(-90.0F, Order.Heading.EAST.yaw());
    }

    @Test
    @DisplayName("headings are named the short way and the long way")
    void headingNames() {
        assertEquals(Order.Heading.NORTH, Order.Heading.of("n"));
        assertEquals(Order.Heading.NORTH, Order.Heading.of("NORTH"));
        assertEquals(Order.Heading.EAST, Order.Heading.of(" east "));
        assertNull(Order.Heading.of("up"));
        assertNull(Order.Heading.of(""));
        assertNull(Order.Heading.of(null));
    }

    @Test
    @DisplayName("one-shot orders are the ones that finish when carried out")
    void oneShots() {
        assertEquals(true, Order.parse("jump").isOneShot());
        assertEquals(true, Order.parse("say hi").isOneShot());
        assertEquals(false, Order.parse("go north").isOneShot());
        assertEquals(false, Order.parse("follow kfman").isOneShot());
    }
}
