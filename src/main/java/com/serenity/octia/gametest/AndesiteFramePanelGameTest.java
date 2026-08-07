package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;

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
 * <p>These live in {@code src/main} rather than a test-only source set so the
 * {@code gametest} run config sees them without a second source set to wire up.
 * They do <b>not</b> reach end users, and it is worth knowing why before anyone
 * writes that they do: {@code fabric-gametest-api-v1} is one of Fabric API's
 * {@code devOnlyModules}, so it is absent from the published fat jar, and
 * {@code FabricGameTestModInitializer} — the only thing that ever reads the
 * {@code fabric-gametest} entrypoint — therefore never loads. Nothing crashes;
 * the classes simply sit inert in a release jar, about two kilobytes of it.
 * Shipping them live would mean {@code include()}-ing that module at the
 * version fabric-api's own POM pins, and a dedicated server would additionally
 * need {@code -Dfabric-api.gametest.command=true} before {@code /test runall}
 * existed at all.
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
        helper.setBlock(PANEL, OctiaBlocks.ANDESITE_FRAME_PANEL);
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
        helper.setBlock(PANEL, OctiaBlocks.ANDESITE_FRAME_PANEL);

        for (PanelLight expected : PanelLight.values()) {
            helper.setBlock(PANEL, OctiaBlocks.ANDESITE_FRAME_PANEL
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
