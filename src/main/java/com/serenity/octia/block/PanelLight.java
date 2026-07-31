package com.serenity.octia.block;

import net.minecraft.util.StringRepresentable;

/**
 * How a frame panel is lit: not at all, generically, or with styling.
 *
 * <p>Each constant answers its own light level rather than being looked up in a
 * switch somewhere else â€” the panel asks the value what it is worth and takes
 * the answer. Adding a fourth light means adding a line here and a blockstate
 * variant, and nothing else.
 */
public enum PanelLight implements StringRepresentable {

    /** Dark. Andesite frame, no lamp behind it. */
    NONE("none", 0),

    /** A plain lamp. Bright enough to suppress mob spawns, dim enough to read as utility. */
    GENERIC("generic", 7),

    /** A dressed lamp. Full daylight-equivalent. */
    STYLED("styled", 15);

    private final String serializedName;
    private final int lightLevel;

    PanelLight(String serializedName, int lightLevel) {
        this.serializedName = serializedName;
        this.lightLevel = lightLevel;
    }

    /** Block light emitted, 0-15. */
    public int lightLevel() {
        return lightLevel;
    }

    /** The next light in the cycle, wrapping. A panel advances by asking for this. */
    public PanelLight next() {
        PanelLight[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
