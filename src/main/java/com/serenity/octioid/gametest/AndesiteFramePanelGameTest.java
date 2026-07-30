package com.serenity.octioid.gametest;

import com.serenity.octioid.OctioidBlocks;
import com.serenity.octioid.block.AndesiteFramePanelBlock;
import com.serenity.octioid.block.PanelLight;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * In-world tests for the frame panel, run by Minecraft's own GameTest
 * framework on a real headless server.
 *
 * <p>This is deliberately not a unit test. A unit test can prove
 * {@link PanelLight#next()} cycles; only a running server can prove that
 * right-clicking the block actually changes its blockstate, that the change
 * survives the client/server split, and that the game recomputes the light.
 * Those are the things that break.
 *
 * <p>These ship in the release jar rather than a test-only source set. The cost
 * is about two kilobytes; the benefit is that anyone with the mod installed can
 * run {@code /test runall} and verify their own copy, which matters more for a
 * mod meant to stay open and inspectable.
 */
public class AndesiteFramePanelGameTest implements FabricGameTest {

    /** Sits one block above the structure floor. */
    private static final BlockPos PANEL = new BlockPos(0, 1, 0);

    /**
     * Right-clicking walks none to generic to styled and wraps back.
     *
     * <p>The wrap is the half that regresses: an off-by-one in {@code next()}
     * would still advance correctly and only fail on the third click.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cyclesThroughEveryLightAndWraps(GameTestHelper helper) {
        helper.setBlock(PANEL, OctioidBlocks.ANDESITE_FRAME_PANEL);
        helper.assertBlockProperty(PANEL, AndesiteFramePanelBlock.LIGHT, PanelLight.NONE);

        helper.useBlock(PANEL);
        helper.assertBlockProperty(PANEL, AndesiteFramePanelBlock.LIGHT, PanelLight.GENERIC);

        helper.useBlock(PANEL);
        helper.assertBlockProperty(PANEL, AndesiteFramePanelBlock.LIGHT, PanelLight.STYLED);

        helper.useBlock(PANEL);
        helper.assertBlockProperty(PANEL, AndesiteFramePanelBlock.LIGHT, PanelLight.NONE);

        helper.succeed();
    }

    /**
     * The light level the game reads back matches what the enum declares.
     *
     * <p>The level is wired by a lambda at registration, not by the block class,
     * so this asserts that the wiring exists at all — a missing
     * {@code lightLevel} in the Properties builder is invisible until something
     * looks for the light.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void emitsTheLightItDeclares(GameTestHelper helper) {
        helper.setBlock(PANEL, OctioidBlocks.ANDESITE_FRAME_PANEL);

        for (PanelLight expected : PanelLight.values()) {
            helper.setBlock(PANEL, OctioidBlocks.ANDESITE_FRAME_PANEL
                    .defaultBlockState()
                    .setValue(AndesiteFramePanelBlock.LIGHT, expected));

            int actual = helper.getBlockState(PANEL).getLightEmission();
            if (actual != expected.lightLevel()) {
                throw new AssertionError(
                        "light " + expected.getSerializedName() + " should emit "
                                + expected.lightLevel() + " but emitted " + actual);
            }
        }

        helper.succeed();
    }
}
