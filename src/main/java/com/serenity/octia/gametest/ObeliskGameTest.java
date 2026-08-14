package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.world.ObeliskFeature;
import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.Sightlines;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * MILESTONE 2 - the obelisk.
 *
 * <p>An obelisk is masonry, not machinery, and the tests that matter are about
 * what it must <em>not</em> do. It carries no core, so it must never enter the
 * moorings store; a landmark that moored itself would put positions in the
 * spine that no player built and no player can take apart.
 *
 * <p><b>Nothing here assumes which way it is facing or whether it stands.</b>
 * The prism lies along the {@link Sightlines} leg under it, and that leg is a
 * function of the gametest world's seed, which is different every run. Distance
 * to that leg then decides how likely the obelisk is to be broken. So each test
 * reads the leg the same way the feature did and walks the shape it actually
 * built - a test that hard-coded north, or a height, would pass on a Tuesday.
 */
public class ObeliskGameTest implements FabricGameTest {

    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /** Ground under the drop point, deep enough for the plinth to sit on. */
    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    /** Runs the feature and hands back the plinth centre, or null if declined. */
    private static BlockPos place(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(true);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(GROUND);

        boolean built = OctiaWorldgen.obelisk().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                absolute,
                NoneFeatureConfiguration.INSTANCE));

        return built ? absolute.below(1) : null;
    }

    /** Whether the prism at this base runs along X, read the way the feature reads it. */
    private static boolean alongX(GameTestHelper helper, BlockPos base) {
        Sightlines.Heading heading =
                Sightlines.legAt(helper.getLevel().getSeed(), base.getX(), base.getZ()).heading();
        return heading == Sightlines.Heading.EAST || heading == Sightlines.Heading.WEST;
    }

    /** A position in the prism's frame: {@code a} along the thread, {@code c} across it. */
    private static BlockPos at(BlockPos base, boolean alongX, int a, int c, int y) {
        return base.offset(alongX ? a : c, y, alongX ? c : a);
    }

    /** It stands on a plinth one block proud of the prism, all andesite. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theObeliskStandsOnAPlinth(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(helper, base);
        int half = ObeliskFeature.along() / 2 + 1;
        int side = ObeliskFeature.across() / 2 + 1;

        for (int a = -half; a <= half; a++) {
            for (int c = -side; c <= side; c++) {
                BlockPos pos = at(base, alongX, a, c, 0);
                if (!level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("the plinth has a gap at " + pos);
                }
            }
        }

        // And it is a rectangle rather than a square: one step further across
        // the thread, at the same distance along it, is untouched ground. The
        // probe is deliberately inside the ring that fallen() scatters
        // into - both of its coordinates are under the scatter's minimum - so a
        // toppled panel landing nearby cannot fail this.
        BlockPos beyond = at(base, alongX, half, side + 1, 0);
        if (level.getBlockState(beyond).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            throw new AssertionError("the plinth is square; it should be longer along the thread, at "
                    + beyond);
        }
        helper.succeed();
    }

    /**
     * <b>The shape this landmark exists for.</b> A prism, solid, wider than one
     * block on both horizontal axes - it was a 1x1 column once, which is one
     * pixel across at the distance an obelisk is supposed to be seen from.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theObeliskIsAPrismAndIsSolid(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(helper, base);
        int half = ObeliskFeature.along() / 2;
        int side = ObeliskFeature.across() / 2;

        if (half < 1 || side < 1 || half == side) {
            throw new AssertionError("the footprint is not a rectangle: "
                    + ObeliskFeature.across() + "x" + ObeliskFeature.along());
        }

        // How tall it got: the tallest of every column, not one of them. The
        // slot ends its own columns at y=1, and erosion eats the top course of a
        // broken one at random, so no single column can be trusted to reach the
        // height and only the maximum over all of them is it.
        int height = 0;
        for (int a = -half; a <= half; a++) {
            for (int c = -side; c <= side; c++) {
                int y = 0;
                while (level.getBlockState(at(base, alongX, a, c, y + 1))
                        .is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    y++;
                }
                height = Math.max(height, y);
            }
        }
        if (height < ObeliskFeature.slotHigh() + 2) {
            throw new AssertionError("the obelisk is " + height + " blocks tall; that is a kerbstone");
        }

        // Solid below the slot, on every column. Only below: above it the top
        // course of a broken one is eroded on purpose, so asserting solidity all
        // the way up would be asserting that the weather never touched it.
        for (int y = 1; y < ObeliskFeature.slotLow(); y++) {
            for (int a = -half; a <= half; a++) {
                for (int c = -side; c <= side; c++) {
                    BlockPos pos = at(base, alongX, a, c, y);
                    if (!level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                        throw new AssertionError("a hole in the prism at " + pos);
                    }
                }
            }
        }

        // Nothing floating above the top. A broken obelisk is broken once.
        for (int y = height + 1; y <= height + 4; y++) {
            for (int a = -half; a <= half; a++) {
                for (int c = -side; c <= side; c++) {
                    BlockPos pos = at(base, alongX, a, c, y);
                    if (level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                        throw new AssertionError("a panel is floating above the top at " + pos);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * <b>The sightline itself.</b> The slot is bored the whole length of the
     * prism along the thread, and it is clear end to end - that is what makes it
     * a line of sight rather than a decorative notch. It runs along the leg's
     * axis and not across it, which is the only thing in the world that records
     * which way the thread went once the light is gone.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSlotLooksDownTheThread(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(helper, base);
        int half = ObeliskFeature.along() / 2;
        int side = ObeliskFeature.across() / 2;

        for (int y = ObeliskFeature.slotLow(); y <= ObeliskFeature.slotHigh(); y++) {
            for (int a = -half; a <= half; a++) {
                BlockPos pos = at(base, alongX, a, 0, y);
                if (!level.getBlockState(pos).isAir()) {
                    throw new AssertionError("the sighting slot is blocked at " + pos);
                }
            }

            // Across the thread at the same height it is solid, or the "slot" is
            // just a missing course and points at nothing.
            for (int c = -side; c <= side; c++) {
                if (c == 0) {
                    continue;
                }
                BlockPos pos = at(base, alongX, 0, c, y);
                if (!level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("the slot is open across the thread at " + pos);
                }
            }
        }
        helper.succeed();
    }

    /**
     * <b>The load-bearing test.</b> An obelisk is not a ship.
     *
     * <p>It has no core, so nothing about it should ever reach
     * {@code ShipMoorings}. Give it one - because a lit spire that also moored
     * would look like a feature rather than a mistake - and this fails.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anObeliskIsNotAShip(GameTestHelper helper) {
        BlockPos base = place(helper);
        if (base == null) {
            throw new AssertionError("the feature declined ground it should have accepted");
        }

        ServerLevel level = helper.getLevel();
        ShipMoorings moorings = ShipMoorings.get(level.getServer());

        for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -2, -8), base.offset(8, 16, 8))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ShipCoreBlock) {
                throw new AssertionError("an obelisk generated a ship core at " + p);
            }
            if (moorings.isMoored(p)) {
                throw new AssertionError("an obelisk put " + p + " into the moorings store");
            }
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoObelisk(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(false);

        ServerLevel level = helper.getLevel();
        boolean built = OctiaWorldgen.obelisk().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(77L),
                helper.absolutePos(GROUND),
                NoneFeatureConfiguration.INSTANCE));

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("an obelisk generated in a world with Octia switched off");
        }
        helper.succeed();
    }
}
