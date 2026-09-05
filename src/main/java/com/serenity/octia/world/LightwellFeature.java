package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.lightwell.LightwellPlan;
import com.serenity.octia.tower.TowerPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The lightwell: a pyramid dug downward, put into the ground.
 *
 * <p>This is the half of the feature that cannot be proven headless.
 * {@link LightwellPlan} decides <em>what a well is</em> - the geometry, the
 * block count, whether the light reaches - and is a pure function with no
 * Minecraft on its imports, tested exhaustively in milliseconds. This decides
 * <em>where one goes and how the earth is moved</em>, which needs a world, and
 * so is defended by a gametest instead. The line between them is the same one
 * {@link ArchFeature} draws between {@code place} and {@code raise}.
 *
 * <p><b>This is the one feature in the mod that carves.</b> Every ruin here
 * places blocks and removes none - that is what lets a derelict be dropped on
 * uneven ground without terraforming it, and it is why {@code LightwellPlan}
 * emits only what the well <em>adds</em>. A well is different in kind: it is a
 * hole, and a hole that does not remove the earth is a solid block of stone
 * with frame panels buried in it. So the excavation lives here, deliberately,
 * on the side of the line where a world exists to dig.
 *
 * <p><b>Carve first, then build.</b> The two passes are not interchangeable.
 * The retaining wall and the kerb both stand at coordinates inside the volume
 * being hollowed, so building first and carving second would quarry away the
 * structure it had just laid.
 *
 * <p><b>Why these measurements.</b> The plan's own daylight check chose them.
 * A well is lit by one shaft, so its top ring is always the widest and always
 * the darkest, and the top ring is exactly the whole span from shaft to mouth.
 * That means a wide mouth cannot be lit by a narrow shaft at any depth - mouth
 * 43 on a five-block step leaves its top two terraces past the reach of the sky
 * entirely. The way to a deep well that is bright at every level is therefore
 * NOT a bigger mouth but a smaller step: mouth 15 on a step of one gives seven
 * terraces, twenty-eight blocks deep, with every ring inside
 * {@link LightwellPlan#brightestRing()}. That is a measured result rather than
 * a taste, and it is why the numbers below look modest.
 *
 * <p><b>What is not claimed.</b> The well is lit by daylight, so it is dark at
 * night, like anything else with windows. The plan emits no lamps; adding them
 * is a change to the plan with its own tests, not something smuggled in on this
 * side where nothing can check it.
 */
public class LightwellFeature extends Feature<NoneFeatureConfiguration> {

    /** Width at grade. Odd, so the shaft has a centre column to stand on. */
    private static final int MOUTH = 15;

    /** The shaft, constant top to bottom. Widening it is how a wider well stays lit. */
    private static final int SHAFT = 3;

    /** One block in per level: seven terraces rather than two, all of them lit. */
    private static final int INSET = 1;

    /** Three blocks of headroom and the floor slab. */
    private static final int STOREY = 4;

    /**
     * The well this feature builds, resolved once.
     *
     * <p>Static and final because the plan is a pure function of four constants
     * and cannot fail at runtime: if these measurements did not resolve, this
     * class would refuse to load rather than generate a broken well in
     * somebody's save, which is the failure that is cheap now and expensive
     * later.
     */
    private static final LightwellPlan PLAN = LightwellPlan.of(MOUTH, SHAFT, INSET, STOREY);

    /**
     * Room demanded below the apex before a well is cut.
     *
     * <p>Not decoration. {@code setBlock} outside build height is a silent
     * no-op, so a well seated too low would carve its upper storeys, fail to lay
     * its lower floors, and leave a shaft opening onto nothing - which looks
     * exactly like a feature rather than a truncation.
     */
    private static final int FLOOR_CLEARANCE = 8;

    public LightwellFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** The well's geometry, for the gametests that walk the shape. */
    public static LightwellPlan plan() {
        return PLAN;
    }

    /** Half the mouth: how far the footing has to reach on either side. */
    public static int reach() {
        return (MOUTH - 1) / 2;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos surface = RuinGround.surfaceNear(level, context.origin());
        if (surface == null) {
            return false;
        }

        return sink(level, surface);
    }

    /**
     * Whether this is ground a well may be cut into.
     *
     * <p>Named and public for the reason {@link ArchFeature#siteIsClear} is: it
     * is the half of the siting a gametest can actually reach, and a well is the
     * most destructive thing in the mod, so what it refuses matters more than
     * what it accepts.
     *
     * <p>Three questions, and each rejects rather than terraforms. Is the whole
     * mouth standing on something dry - a well cut half into a lake fills, and a
     * well cut half over a cliff opens out of its own side. Is there room below
     * the apex. Has somebody already built here - a village over a twenty-eight
     * block hole is not a landmark, it is a disaster.
     */
    public static boolean siteWillTake(WorldGenLevel level, BlockPos grade) {
        int reach = reach();

        // The floor the mouth stands on, and the headroom a well wants is
        // downward rather than up - so this is asked about the rim only, and the
        // depth is checked separately below.
        if (!RuinGround.hasFooting(level, grade.below(1), reach, reach, 1)) {
            return false;
        }
        if (level.isOutsideBuildHeight(grade.below(PLAN.depth() + FLOOR_CLEARANCE))) {
            return false;
        }
        return RuinGround.clearOfStructures(level, grade.below(1), reach, reach, 1);
    }

    /**
     * Cuts a well into known ground.
     *
     * <p>Public and separate from {@link #place} for the reason
     * {@link ArchFeature#raise} is: <em>where a well goes</em> and <em>what a
     * well is</em> are different jobs, and folding them together would make the
     * shape untestable except by accident of what generates near a test plot.
     *
     * @param grade the first free block above the ground; the mouth opens here
     *              and every {@code y} in the plan is measured from it
     * @return whether the ground took it
     */
    public static boolean sink(WorldGenLevel level, BlockPos grade) {
        if (!siteWillTake(level, grade)) {
            return false;
        }
        excavate(level, grade);
        build(level, grade);
        return true;
    }

    /**
     * Hollows the envelope out: the galleries, and then the bore between them.
     *
     * <p><b>Two passes, and the second one is not redundant.</b> The first
     * clears each storey across the full width of its own level - the space
     * people stand in. That leaves the <em>floor planes themselves</em> solid,
     * which is right everywhere the plan lays a slab and wrong in exactly one
     * place: the shaft. {@code LightwellPlan} deliberately puts no block inside
     * the shaft, so at every floor level the shaft footprint was cleared by
     * nobody and filled by nobody, and each storey ended in a stone plug across
     * the light.
     *
     * <p>The unit tests could not have caught it. They assert the plan places
     * nothing in the shaft, and the plan does not - the plan was correct and the
     * digging was wrong. It took a gametest, cutting into real stone, to say
     * "the shaft is blocked at y=-24 by minecraft:stone". That is the whole
     * argument for the line this feature is drawn on.
     *
     * <p>So the second pass bores the shaft from the mouth to the apex floor,
     * straight through the floor planes, which is what a well is.
     */
    private static void excavate(WorldGenLevel level, BlockPos grade) {
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1. the galleries: each storey, out to its own level's wall
        for (int i = 0; i < PLAN.levels(); i++) {
            int outer = PLAN.outerHalf(i);
            int floor = PLAN.floorY(i);
            for (int y = floor + 1; y < floor + PLAN.storey(); y++) {
                for (int x = -outer; x <= outer; x++) {
                    for (int z = -outer; z <= outer; z++) {
                        RuinGround.put(level, grade.offset(x, y, z), air);
                    }
                }
            }
        }

        // 2. the bore: the shaft, unbroken from grade to the apex floor. It
        //    stops ON that floor rather than through it - the apex is where the
        //    well bottoms out, and the one slab the light is meant to land on.
        int shaftHalf = (PLAN.shaft() - 1) / 2;
        int apexFloor = PLAN.floorY(PLAN.levels() - 1);
        for (int y = -1; y > apexFloor; y--) {
            for (int x = -shaftHalf; x <= shaftHalf; x++) {
                for (int z = -shaftHalf; z <= shaftHalf; z++) {
                    RuinGround.put(level, grade.offset(x, y, z), air);
                }
            }
        }
    }

    /** Lays every block the plan calls for, in the order the plan gives them. */
    private static void build(WorldGenLevel level, BlockPos grade) {
        for (LightwellPlan.Block b : PLAN.blocks()) {
            RuinGround.put(level, grade.offset(b.x(), b.y(), b.z()), stateFor(b.cell()));
        }
    }

    /**
     * The one place the shared vocabulary is turned into blocks.
     *
     * <p>{@link TowerPlan.Cell} describes a lamp as "frame panel at the middling
     * tier" and a beacon as "the brightest thing the mod owns", and
     * {@link PanelLight} answers with 7 and 15. Those are the same two
     * descriptions, so the mapping is read off rather than invented. A cell this
     * class does not build refuses loudly instead of quietly placing stone: a
     * new cell in the vocabulary should break here and be dealt with, not appear
     * in the world as an unmarked block.
     */
    private static BlockState stateFor(TowerPlan.Cell cell) {
        return switch (cell) {
            case FRAME -> OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState();
            case LAMP -> OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                    .setValue(AndesiteFramePanelBlock.LIGHT, PanelLight.GENERIC);
            case BEACON -> OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                    .setValue(AndesiteFramePanelBlock.LIGHT, PanelLight.STYLED);
            case CORE -> OctiaBlocks.SHIP_CORE.defaultBlockState();
            default -> throw new IllegalStateException(
                    "the lightwell has no block for the cell " + cell);
        };
    }
}
