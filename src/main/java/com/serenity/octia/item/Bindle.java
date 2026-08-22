package com.serenity.octia.item;

/**
 * The arithmetic of a bindle, kept away from anything that needs a world.
 *
 * <p>A bindle is a cloth tied to a stick: four slots, no screen, and the only
 * thing it can do is take what is offered and give the last of it back. That is
 * all sums, and sums are the half of this that a JUnit test can reach - see
 * {@code BindleTest}. Everything below is integers on purpose. The moment a
 * method here needs an {@code ItemStack} it belongs in {@link BindleItem}
 * instead, where a {@code @GameTest} can drive it against a real inventory.
 *
 * <p><b>Why four.</b> A bindle is not storage, it is what somebody could carry
 * without a pack. Nine would be a backpack and one would be a pocket; four is
 * enough for the road - bread, a light, a tool, and whatever you picked up - and
 * few enough that choosing what goes in is a decision rather than a formality.
 */
public final class Bindle {

    /** How many stacks a bindle holds. See the class note for why it is four. */
    public static final int SLOTS = 4;

    /** The width of a full bar, in pixels, as vanilla draws one. */
    static final int BAR_PIXELS = 13;

    private Bindle() {
    }

    /**
     * How many of an offered stack a bindle can take.
     *
     * <p>Room is looked for twice and in this order: first whatever fits into
     * stacks already inside that match, then whatever a free slot would hold.
     * Topping up before opening a slot is what keeps four slots useful — a
     * bindle that opened a new slot for every handful would be full of ones.
     *
     * @param offered      how many items are being offered
     * @param maxStack     the offered item's stack limit
     * @param matchingRoom room left in each stack inside that already matches
     * @param freeSlots    slots not yet holding anything
     * @return how many items fit, never more than {@code offered}
     */
    public static int intake(int offered, int maxStack, int[] matchingRoom, int freeSlots) {
        if (offered <= 0 || maxStack <= 0) {
            return 0;
        }
        int room = 0;
        for (int spare : matchingRoom) {
            room += Math.max(0, spare);
        }
        room += Math.max(0, freeSlots) * maxStack;
        return Math.min(offered, room);
    }

    /**
     * The fullness bar, measured in slots rather than in items.
     *
     * <p>Slots, because slots are what a bindle runs out of. Counting items
     * would show a bindle holding one stack of cobble as nearly full and the
     * same bindle holding four eggs as nearly empty, when it is the second one
     * that cannot take anything else.
     *
     * @return the bar width in pixels, 0 when the bindle is empty
     */
    public static int barWidth(int slotsUsed) {
        if (slotsUsed <= 0) {
            return 0;
        }
        return Math.min(BAR_PIXELS, Math.max(1, Math.round(BAR_PIXELS * (float) slotsUsed / SLOTS)));
    }

    /** Whether another stack could be opened. */
    public static boolean hasRoom(int slotsUsed) {
        return slotsUsed < SLOTS;
    }
}
