package com.serenity.octia.gametest;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.world.ArchFeature;
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
 * MILESTONE 3 - the arch on the node.
 *
 * <p><b>These drive {@code ArchFeature.raise} rather than {@code place}.</b>
 * The feature declines every chunk whose cell's node is somewhere else, which is
 * 1,023 chunks out of every 1,024, and the gametest world's seed is different
 * every run - so a test that went through {@code place} would be testing whether
 * it got lucky. The gate has its own test below; everything else builds the arch
 * directly with a heading the test chose.
 */
public class ArchGameTest implements FabricGameTest {

    private static final BlockPos GROUND = new BlockPos(4, 3, 4);

    /** Ground under the arch, wide enough for the span whichever way it faces. */
    private static void floor(GameTestHelper helper, BlockPos centre) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy >= -3; dy--) {
                    helper.setBlock(centre.offset(dx, dy, dz), Blocks.GRAVEL);
                }
            }
        }
    }

    /**
     * Raises one at a chosen heading and hands back the block its piers stand on.
     *
     * <p>The air above is wiped first, and that is not tidiness. Pier height
     * varies, so raising a second arch over the remains of a taller first one
     * leaves stale blocks standing above the new ring - and every assertion here
     * finds the top of the structure by walking up a column, so it would measure
     * the ghost of the previous one and report a shape nothing ever built.
     */
    private static BlockPos raise(GameTestHelper helper, Sightlines.Heading heading, long seed) {
        floor(helper, GROUND);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 1; dy <= 14; dy++) {
                    helper.setBlock(GROUND.offset(dx, dy, dz), Blocks.AIR);
                }
            }
        }
        OctiaWorldgen.setActive(true);

        BlockPos base = helper.absolutePos(GROUND);
        boolean built = ArchFeature.raise(helper.getLevel(), base, heading, RandomSource.create(seed));
        if (!built) {
            throw new AssertionError("the arch declined ground it should have accepted");
        }
        return base;
    }

    /** A position in the arch's frame: {@code c} across the thread, {@code a} along it. */
    private static BlockPos at(BlockPos base, boolean alongX, int a, int c, int y) {
        return base.offset(alongX ? a : c, y, alongX ? c : a);
    }

    private static boolean alongX(Sightlines.Heading heading) {
        return heading == Sightlines.Heading.EAST || heading == Sightlines.Heading.WEST;
    }

    /** Walks up the outermost column to find where the piers stop. */
    private static int springing(ServerLevel level, BlockPos base, boolean alongX) {
        int y = 0;
        while (level.getBlockState(at(base, alongX, 0, ArchFeature.halfSpan(), y + 1))
                .is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            y++;
        }
        return y;
    }

    /**
     * <b>Keystone plus four.</b> Five stones in the ring, each one course higher
     * than the one outside it, with the keystone alone on the centre line. An
     * even span could not carry a keystone at all - there would be a joint where
     * the middle stone belongs - so the shape and the odd span are one claim.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRingIsKeystonePlusFour(GameTestHelper helper) {
        Sightlines.Heading heading = Sightlines.Heading.EAST;
        BlockPos base = raise(helper, heading, 11L);
        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(heading);

        int half = ArchFeature.halfSpan();
        if (half != 2) {
            throw new AssertionError("the span is keystone plus " + (half * 2) + ", not plus four");
        }

        int top = springing(level, base, alongX);
        int stones = 0;
        for (int c = -half; c <= half; c++) {
            int y = top + (half - Math.abs(c));
            BlockPos pos = at(base, alongX, 0, c, y);
            if (!level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                throw new AssertionError("the ring has no stone at " + pos + " (c=" + c + ")");
            }
            stones++;

            // And nothing above it, or the ring is a wall with a hole in it.
            BlockPos over = at(base, alongX, 0, c, y + 1);
            if (level.getBlockState(over).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                throw new AssertionError("the ring is stacked rather than stepped, at " + over);
            }
        }
        if (stones != 5) {
            throw new AssertionError("the ring is " + stones + " stones; it should be five");
        }
        helper.succeed();
    }

    /**
     * The keystone is the brightest block and the only one of its kind. What
     * carries across a valley at night is a bright point with two dimmer ones
     * under it - a shape rather than a dot - and the dark springers are what
     * keep the step visible instead of smearing the ring into one glow.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theKeystoneCarriesTheLight(GameTestHelper helper) {
        Sightlines.Heading heading = Sightlines.Heading.SOUTH;
        BlockPos base = raise(helper, heading, 12L);
        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(heading);

        int half = ArchFeature.halfSpan();
        int top = springing(level, base, alongX);

        for (int c = -half; c <= half; c++) {
            BlockPos pos = at(base, alongX, 0, c, top + (half - Math.abs(c)));
            BlockState stone = level.getBlockState(pos);
            if (!stone.is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                throw new AssertionError("the ring has no stone at c=" + c + ", " + pos);
            }
            PanelLight light = stone.getValue(AndesiteFramePanelBlock.LIGHT);
            PanelLight want = switch (Math.abs(c)) {
                case 0 -> PanelLight.STYLED;
                case 1 -> PanelLight.GENERIC;
                default -> PanelLight.NONE;
            };
            if (light != want) {
                throw new AssertionError("the ring stone at c=" + c + " is " + light
                        + " and should be " + want);
            }
        }
        helper.succeed();
    }

    /**
     * <b>You walk through it, and the way you face is the way the thread runs.</b>
     * The opening is three wide and clear to the underside of the ring, and the
     * arch is one block thick along the thread - so there is nothing to step
     * over and nothing to duck under on the way through.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void youWalkThroughItDownTheLeg(GameTestHelper helper) {
        Sightlines.Heading heading = Sightlines.Heading.EAST;
        BlockPos base = raise(helper, heading, 13L);
        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(heading);

        int half = ArchFeature.halfSpan();
        int top = springing(level, base, alongX);

        // Three wide, clear from the ground up to whatever stone spans it.
        for (int c = -half + 1; c <= half - 1; c++) {
            int ceiling = top + (half - Math.abs(c));
            for (int y = 1; y < ceiling; y++) {
                BlockPos pos = at(base, alongX, 0, c, y);
                if (!level.getBlockState(pos).isAir()) {
                    throw new AssertionError("the opening is blocked at " + pos);
                }
            }
        }

        // One block thick. A player walking the leg passes one plane of stone.
        for (int a = -2; a <= 2; a++) {
            if (a == 0) {
                continue;
            }
            for (int c = -half; c <= half; c++) {
                for (int y = 1; y <= top + half; y++) {
                    BlockPos pos = at(base, alongX, a, c, y);
                    if (level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                        throw new AssertionError("the arch is more than one block thick, at " + pos);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * Squared across the leg, both ways round. Build one facing east and its
     * span runs on Z; build one facing south and it runs on X. Getting this
     * backwards would put the opening side-on to the thread, so you would walk
     * past the arch rather than through it - and it would still look correct in
     * a screenshot.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSpanIsSquaredAcrossTheLeg(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int half = ArchFeature.halfSpan();

        BlockPos base = raise(helper, Sightlines.Heading.EAST, 14L);
        int top = springing(level, base, true);
        if (!level.getBlockState(base.offset(0, top + half, 0)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            throw new AssertionError("no keystone over the centre of an east-west arch");
        }
        if (level.getBlockState(base.offset(half, 1, 0)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            throw new AssertionError("an east-west arch put a pier on X; it should span Z");
        }
        if (!level.getBlockState(base.offset(0, 1, half)).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
            throw new AssertionError("an east-west arch has no pier on Z");
        }
        helper.succeed();
    }

    /**
     * <b>An arch never breaks.</b> The obelisks between nodes erode and snap on
     * purpose, so that what is still upright carries information. A node is the
     * thing that is maintained. Twenty different randoms, twenty whole rings -
     * if erosion is ever added here, this is the test that should stop it.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anArchIsNeverBroken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean alongX = alongX(Sightlines.Heading.EAST);
        int half = ArchFeature.halfSpan();

        for (long seed = 0; seed < 20; seed++) {
            BlockPos base = raise(helper, Sightlines.Heading.EAST, seed);
            int top = springing(level, base, alongX);
            for (int c = -half; c <= half; c++) {
                BlockPos pos = at(base, alongX, 0, c, top + (half - Math.abs(c)));
                if (!level.getBlockState(pos).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    throw new AssertionError("an arch generated broken at seed " + seed + ", " + pos);
                }
            }
        }
        helper.succeed();
    }

    /** Masonry, like the obelisk. No core, ever. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anArchIsNotAShip(GameTestHelper helper) {
        BlockPos base = raise(helper, Sightlines.Heading.SOUTH, 15L);
        ServerLevel level = helper.getLevel();

        for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, -1, -4), base.offset(4, 12, 4))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ShipCoreBlock) {
                throw new AssertionError("an arch generated a ship core at " + p);
            }
        }
        helper.succeed();
    }

    /**
     * <b>The gate.</b> Every chunk of a cell is offered this feature and only
     * the one holding the node may take it. Here the origin is deliberately two
     * chunks off the node, inside the same cell, so the answer must be no.
     *
     * <p>It writes nothing when it declines, which is why driving {@code place}
     * at a position outside the test plot is safe: the gate returns before the
     * feature reads or touches the level at all.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aChunkWithoutTheNodeGetsNoArch(GameTestHelper helper) {
        OctiaWorldgen.setActive(true);
        ServerLevel level = helper.getLevel();

        BlockPos here = helper.absolutePos(GROUND);
        Sightlines.Node node = Sightlines.legAt(level.getSeed(), here.getX(), here.getZ()).from();

        // Thirty-two blocks along X is two chunks over, and the node's jitter is
        // bounded well inside the cell, so this is still the same cell.
        BlockPos elsewhere = new BlockPos(node.x() + 32, here.getY(), node.z());

        boolean built = OctiaWorldgen.arch().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(16L),
                elsewhere,
                NoneFeatureConfiguration.INSTANCE));

        if (built) {
            throw new AssertionError("an arch generated in a chunk that holds no node");
        }
        helper.succeed();
    }

    /** The switch reaches this one too. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldGeneratesNoArch(GameTestHelper helper) {
        floor(helper, GROUND);
        OctiaWorldgen.setActive(false);

        ServerLevel level = helper.getLevel();
        boolean built = OctiaWorldgen.arch().place(new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                RandomSource.create(17L),
                helper.absolutePos(GROUND),
                NoneFeatureConfiguration.INSTANCE));

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (built) {
            throw new AssertionError("an arch generated in a world with Octia switched off");
        }
        helper.succeed();
    }
}
