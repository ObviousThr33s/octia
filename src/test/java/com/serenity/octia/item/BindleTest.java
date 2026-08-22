package com.serenity.octia.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sums a bindle does, checked without a world.
 *
 * <p>These are the half of the bindle that does not need Minecraft running - see
 * {@code docs/DEVOPS.md} for why that split exists at all. The other half, where
 * real stacks are pushed through a real inventory, is {@code BindleGameTest}.
 */
class BindleTest {

    @Test
    @DisplayName("nothing offered is nothing taken")
    void nothingOffered() {
        assertEquals(0, Bindle.intake(0, 64, new int[0], Bindle.SLOTS));
        assertEquals(0, Bindle.intake(-3, 64, new int[0], Bindle.SLOTS));
    }

    @Test
    @DisplayName("a full bindle with no matching stack takes nothing")
    void fullAndNothingMatches() {
        assertEquals(0, Bindle.intake(12, 64, new int[0], 0));
        assertFalse(Bindle.hasRoom(Bindle.SLOTS));
    }

    /**
     * The rule that makes four slots enough: a bindle already holding fifty
     * cobble tops that stack up before it spends a slot on the rest.
     */
    @Test
    @DisplayName("room in a matching stack counts before a free slot is spent")
    void topsUpFirst() {
        assertEquals(14, Bindle.intake(14, 64, new int[] {14}, 0));
        assertEquals(14, Bindle.intake(64, 64, new int[] {14}, 0));
    }

    @Test
    @DisplayName("free slots each hold a full stack of whatever is offered")
    void freeSlots() {
        assertEquals(128, Bindle.intake(200, 64, new int[0], 2));
        // An offered stack limited to sixteen fills a slot with sixteen, not
        // sixty-four. Ender pearls in a bindle behave like ender pearls.
        assertEquals(32, Bindle.intake(200, 16, new int[0], 2));
        // And one that stacks to one is one per slot, which is what stops a
        // bindle of four buckets pretending to hold more.
        assertEquals(4, Bindle.intake(9, 1, new int[0], 4));
    }

    @Test
    @DisplayName("an offer smaller than the room is taken whole")
    void neverMoreThanOffered() {
        assertEquals(3, Bindle.intake(3, 64, new int[] {60, 60}, 2));
    }

    /**
     * The bar counts slots, not items - see the note on {@code Bindle.barWidth}.
     * Empty draws nothing at all; anything at all draws something visible.
     */
    @Test
    @DisplayName("the bar is empty at nothing, full at four, and never invisibly thin")
    void theBar() {
        assertEquals(0, Bindle.barWidth(0));
        assertEquals(Bindle.BAR_PIXELS, Bindle.barWidth(Bindle.SLOTS));
        assertEquals(Bindle.BAR_PIXELS, Bindle.barWidth(Bindle.SLOTS + 1));
        assertTrue(Bindle.barWidth(1) >= 1);
        assertTrue(Bindle.barWidth(1) < Bindle.barWidth(2));
        assertTrue(Bindle.barWidth(2) < Bindle.barWidth(3));
    }

    @Test
    @DisplayName("room is room until the last slot is taken")
    void room() {
        assertTrue(Bindle.hasRoom(0));
        assertTrue(Bindle.hasRoom(Bindle.SLOTS - 1));
        assertFalse(Bindle.hasRoom(Bindle.SLOTS));
    }
}
