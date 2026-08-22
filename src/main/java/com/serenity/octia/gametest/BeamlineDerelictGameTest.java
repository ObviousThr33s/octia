package com.serenity.octia.gametest;

import com.serenity.octia.world.Beamline;
import com.serenity.octia.world.OctiaWorldgen;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * The station wreck builds only where the lattice put a station.
 *
 * <p><b>What can and cannot be tested in a plot.</b> Where the stations are is a
 * function of the world seed, and a gametest cannot choose one - so no test can
 * arrange for a station to land on the laid floor thirteen blocks from its
 * neighbour. What a test can do is the negative, and the negative is the whole
 * mechanism: on ground the wild derelict accepts, the station wreck must decline,
 * because there is no station here. If it ever accepts that ground, the gate is
 * not gating and every chunk in the world grows a hull.
 *
 * <p>The arithmetic half - that a chunk answers with a station only when one is
 * really in it, and that every station is claimed by exactly the chunk that
 * holds it - is {@code BeamlineTest}, where it costs milliseconds and sweeps
 * thousands of chunks.
 */
public class BeamlineDerelictGameTest implements FabricGameTest {

    /**
     * Where the feature is handed its surface, and why it is not the box floor.
     *
     * <p>Same spot and same depth as {@code DerelictGameTest}, and that is the
     * point rather than a coincidence: this test's whole claim is that two
     * features answer differently about <em>identical</em> ground, so the ground
     * has to be ground the other one demonstrably accepts.
     */
    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /**
     * Solid ground around the drop point, four deep.
     *
     * <p>Four, because the wreck sinks two courses and then refuses ground that
     * is air one further down. A single course - which is what this test laid on
     * its first run - is refused by every derelict feature there is, and the
     * refusal looks exactly like the station gate working. Five blocks of reach
     * because gametest plots are thirteen apart and {@code setBlock} does not
     * bounds-check.
     */
    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    private static boolean place(GameTestHelper helper, BlockPos where, boolean station) {
        ServerLevel level = helper.getLevel();

        // Explicitly, and not because it is tidy: the per-save switch is a
        // static that other tests in this suite set and the server clears, and
        // a feature that declines because Octia is switched off looks precisely
        // like a feature that declined because there is no station here.
        OctiaWorldgen.setActive(true);

        return (station ? OctiaWorldgen.derelictStation() : OctiaWorldgen.derelict())
                .place(new FeaturePlaceContext<>(
                        Optional.empty(),
                        level,
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(1234L),
                        helper.absolutePos(where),
                        NoneFeatureConfiguration.INSTANCE));
    }

    /**
     * Same ground, two answers. The wild wreck takes it; the station wreck does
     * not, because the lattice threw no tangent that lands here.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStationWreckDeclinesGroundWithNoStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(GROUND);

        floor(helper, GROUND);

        // The one case that would make this test a lie rather than a failure:
        // if a station really is in this chunk, declining is the wrong answer
        // and there is no way to move the plot. Roughly one chunk in 1,575, and
        // said out loud rather than silently passed over.
        if (Beamline.inChunk(level.getSeed(), absolute.getX() >> 4, absolute.getZ() >> 4) != null) {
            helper.succeed();
            return;
        }

        if (place(helper, GROUND, true)) {
            throw new AssertionError("the station wreck built on a chunk with no station in it - "
                    + "the gate is not gating, and every chunk in a real world would grow a hull");
        }
        if (!place(helper, GROUND, false)) {
            throw new AssertionError("the wild derelict refused this ground too, so the test above "
                    + "proved nothing about the station gate");
        }
        helper.succeed();
    }
}
