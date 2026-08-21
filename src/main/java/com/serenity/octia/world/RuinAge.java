package com.serenity.octia.world;

import net.minecraft.util.RandomSource;

/**
 * How long ago the people left.
 *
 * <p>One ruin type dressed three ways is more variety than three ruin types
 * dressed one way, and it is the difference between a structure and a place. A
 * derelict is always a hexahedron; whether its hearth is still laid or has gone
 * to ash is what makes two of them feel like different finds.
 *
 * <p><b>Nobody is ever home.</b> There is no fourth value for inhabited and
 * there should not be one. The ruins are always empty - that is what makes
 * meeting someone on the road mean anything, and it is why every prop here is a
 * thing left behind rather than a thing in use.
 */
public enum RuinAge {

    /** Left in a hurry. Hearth laid, bed made, tools down mid-job. */
    RECENT,

    /** Some seasons on it. Moss in the joints, one torch still burning. */
    WEATHERED,

    /** Long dead. Ash, cobweb, and whatever the ground has taken back. */
    ANCIENT;

    /**
     * Rolls an age, weighted to the middle.
     *
     * <p>Two in four weathered, one each at the ends. The extremes are the ones
     * that read as deliberate when you find them; making them common would make
     * both of them ordinary, which is the opposite of what they are for.
     */
    public static RuinAge roll(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> RECENT;
            case 3 -> ANCIENT;
            default -> WEATHERED;
        };
    }

    /** Chance in eight that any one prop is present at all. */
    public int presenceInEight() {
        return switch (this) {
            case RECENT -> 6;
            case WEATHERED -> 4;
            case ANCIENT -> 2;
        };
    }
}
