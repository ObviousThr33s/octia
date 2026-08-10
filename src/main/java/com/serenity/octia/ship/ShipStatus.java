package com.serenity.octia.ship;

import net.minecraft.util.StringRepresentable;

/**
 * What the ship is doing. One property covers the whole of MILESTONE 1.
 *
 * <p>The three states are ordered by how much is true, and each answers its own
 * light level - the same visual grammar the frame panel uses, so a lit ship and
 * a lit panel mean the same thing to the eye.
 */
public enum ShipStatus implements StringRepresentable {

    /** No hull. A core sitting in the open is just a block. */
    ADRIFT("adrift", 0),

    /** Hull intact. The position is registered in the moorings. */
    MOORED("moored", 7),

    /** Hull intact and a dig site is within range. The ship has been called. */
    CALLED("called", 15);

    private final String serializedName;
    private final int lightLevel;

    ShipStatus(String serializedName, int lightLevel) {
        this.serializedName = serializedName;
        this.lightLevel = lightLevel;
    }

    public int lightLevel() {
        return lightLevel;
    }

    /**
     * True once the hull validates - both MOORED and CALLED are moored.
     *
     * <p>Defined by exclusion deliberately: the store cares whether there is a
     * hull, not which lit state it is in. It is also the line a fourth status
     * inherits silently - any status other than ADRIFT reads as moored unless
     * this says otherwise, and it is the moorings file, the spine of the whole
     * design, that pays for a wrong answer.
     */
    public boolean isMoored() {
        return this != ADRIFT;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
