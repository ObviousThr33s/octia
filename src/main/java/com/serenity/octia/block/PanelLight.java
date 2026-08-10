package com.serenity.octia.block;

import net.minecraft.util.StringRepresentable;

/**
 * How a frame panel is lit: not at all, generically, or with styling.
 *
 * <p>Each constant answers its own light level rather than being looked up in a
 * switch somewhere else — the panel asks the value what it is worth and takes
 * the answer. Adding a fourth light costs one constant here, one variant in
 * {@code blockstates/andesite_frame_panel.json}, the block model that variant
 * names, that model's texture, one edit to
 * {@code AndesiteFramePanelGameTest.cyclesThroughEveryLightAndWraps}, which
 * clicks exactly three times and asserts the wrap, and the sentence in
 * {@code AndesiteFramePanelBlock}'s class javadoc that spells the cycle out.
 * The {@code lightLevel} lambda in {@code OctiaBlocks} and the gametest
 * {@code emitsTheLightItDeclares} need no edit: both read the enum rather than
 * naming its members.
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
