package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * A derelict: a Serenity-class hull that did not make it, half-buried at a dig.
 *
 * <p>This is the archaeology hook told from the other side. The beacon at spawn
 * is a ship that answered; a derelict is one that was called and never arrived,
 * and the dig that called it is still there in the ground beside it. Everything
 * the mod's mechanics say is true of it structurally: the core is ringed by
 * eight frame panels, so it is a real hull and not scenery, and there is
 * brushable ground inside {@link ShipCoreBlock#CALL_RADIUS}, so a survey of it
 * returns {@link ShipStatus#CALLED}. A player who finds one and right-clicks the
 * core gets the same readout they would get from a ship they built themselves.
 *
 * <p><b>This feature runs in two different worlds, and they are not the same.</b>
 * Natural generation hands it a {@code WorldGenRegion}, which writes blocks
 * straight into the chunk and never calls {@code onPlace}. {@code /place feature}
 * and the gametests hand it a live {@code ServerLevel}, where
 * {@code LevelChunk.setBlockState} calls {@code onPlace} on every write - so the
 * core surveys itself the instant it lands. Anything built here has to read the
 * same either way, which is why the core is placed last: see {@link #place}.
 *
 * <p><b>Nothing here ever touches the moorings store, and that is deliberate.</b>
 * {@link com.serenity.octia.ship.ShipMoorings} is {@code SavedData} owned by the
 * server thread, and chunks generate on workers, several at once; mooring from
 * inside a feature would be an unsynchronised write to a map the server is
 * reading. Under natural generation the store therefore stays untouched and the
 * derelict enters it the first time anything surveys the core - a right-click,
 * or any neighbour change. Discovery is registration. Under {@code /place} the
 * core's own {@code onPlace} does it immediately, on the server thread, which is
 * safe for the same reason a hand-placed core is.
 *
 * <p>The consequence to know: a naturally generated derelict nobody has touched
 * is a mark the debug map does not show, because the map plots the store. That
 * is the map being accurate about the store, not the store being wrong.
 */
public class DerelictFeature extends Feature<NoneFeatureConfiguration> {

    /** How far down the hull sits relative to the ground it was found on. */
    private static final int SINK = 2;

    /** Exposed so a caller that placed one can work out where the core went. */
    public static int sink() {
        return SINK;
    }

    /** Tallest the surviving mast stub can be. Short - it is a wreck. */
    private static final int MAST_MAX = 4;

    /** Digs are scattered in this ring, outside the hull and inside the call radius. */
    private static final int DIG_MIN = 2;
    private static final int DIG_MAX = 4;

    /** Debris hugs the hull rather than reaching out to the dig ring. */
    private static final int DEBRIS_MAX = 3;

    /** How far up and down {@link #scatter} looks for the local surface. */
    private static final int SURFACE_UP = 4;
    private static final int SURFACE_DOWN = 3;

    public DerelictFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // The world switch, honoured here rather than at the biome modification.
        // Fabric's biome modifications are global to the client, not per-save, so
        // the only place a per-world flag can be applied is at the moment of
        // placement. See OctiaWorldgen for why this reads a cached boolean and
        // not the SavedData it came from.
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();

        // The origin is already the surface: the placed feature runs a heightmap
        // modifier before this is ever called. Asking the heightmap again here
        // was not merely redundant, it was wrong - the _WG heightmaps live on
        // ProtoChunk and are empty on a chunk that has finished generating, so
        // the second question answers bottom-of-world anywhere the first is not
        // still in flight. The placement modifier owns the surface; the feature
        // builds relative to what it was handed.
        BlockPos core = context.origin().below(SINK);

        if (!canBuildAt(level, core)) {
            return false;
        }

        ring(level, core);
        mast(level, random, core);
        debris(level, random, core);
        int digs = dig(level, random, core);

        // The core goes in LAST, and the order is load-bearing.
        //
        // Natural generation hands this a WorldGenRegion, which writes blocks
        // straight into the chunk and never runs onPlace - so the status below
        // is simply what the core will read. But the same feature also runs
        // against a live ServerLevel, from `/place feature` and from the
        // gametests, and there LevelChunk.setBlockState DOES call onPlace, which
        // surveys. Placing the core before its digs existed made those two
        // contexts disagree: worldgen produced a CALLED wreck and /place
        // produced a MOORED one, because the survey ran while the ground was
        // still undisturbed. Building the evidence first makes the survey and
        // the literal agree, whichever path got here.
        setBlockAt(level, core, coreState(digs > 0 ? ShipStatus.CALLED : ShipStatus.MOORED));
        return true;
    }

    /**
     * Somewhere solid, unflooded, and low enough to bury.
     *
     * <p>Rejecting rather than terraforming is the point. A feature that flattens
     * ground to fit is a feature that leaves a scar visible from three hundred
     * blocks away, and the derelict is supposed to look found, not installed.
     */
    private static boolean canBuildAt(WorldGenLevel level, BlockPos core) {
        if (level.isOutsideBuildHeight(core.above(MAST_MAX)) || level.isOutsideBuildHeight(core.below(1))) {
            return false;
        }
        if (!level.getFluidState(core).is(Fluids.EMPTY)) {
            return false;
        }
        // The whole ring needs something underneath it, or the hull generates
        // hanging over a cave and reads as a bug rather than a ruin.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockState(core.offset(dx, -1, dz)).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The eight panels around where the core will go: exactly the shape
     * {@link ShipCoreBlock#hullIntact} looks for, so this is a ship by the same
     * rule a hand-built one is. Nothing here is decorative.
     */
    private static void ring(WorldGenLevel level, BlockPos core) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                setBlockAt(level, core.offset(dx, 0, dz),
                        OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                                .setValue(AndesiteFramePanelBlock.LIGHT, PanelLight.NONE));
            }
        }
    }

    /**
     * What is left standing of the mast: unbroken from the base to the snap.
     *
     * <p>This used to roll per course, skipping individual blocks anywhere up
     * the column. That is not what a broken mast looks like - it produced holes
     * mid-column with panels floating above them, which reads as a glitch rather
     * than as damage. A mast breaks in one place. The randomness belongs in
     * <em>where</em> it breaks, not in which blocks survive below the break.
     *
     * <p>One lit panel is allowed at the base. It is the only light a derelict
     * carries and the thing that makes one visible at night from a distance.
     */
    private static void mast(WorldGenLevel level, RandomSource random, BlockPos core) {
        int height = 2 + random.nextInt(MAST_MAX - 1);
        for (int y = 1; y <= height; y++) {
            PanelLight light = y == 1 && random.nextBoolean() ? PanelLight.GENERIC : PanelLight.NONE;
            setBlockAt(level, core.above(y),
                    OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                            .setValue(AndesiteFramePanelBlock.LIGHT, light));
        }
    }

    /**
     * Panels thrown clear of the wreck, resting on whatever they landed on.
     *
     * <p>Only ever written into air with something solid below, so debris never
     * floats and never replaces the terrain it fell onto.
     */
    private static void debris(WorldGenLevel level, RandomSource random, BlockPos core) {
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            // Tighter than the digs. Debris that lands as far out as the dig
            // ring stops reading as thrown clear of this wreck and starts
            // reading as unrelated blocks that happen to be nearby.
            BlockPos spot = scatter(level, random, core, DIG_MIN, DEBRIS_MAX);
            if (spot != null) {
                setBlockAt(level, spot, OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }

    /**
     * The dig itself: brushable ground, loaded with the loot a ruin carries.
     *
     * @return how many actually took, since a wreck on stone may get none
     */
    private static int dig(WorldGenLevel level, RandomSource random, BlockPos core) {
        int placed = 0;
        int attempts = 3 + random.nextInt(4);

        for (int i = 0; i < attempts; i++) {
            BlockPos spot = scatter(level, random, core, DIG_MIN, DIG_MAX);
            if (spot == null) {
                continue;
            }
            // Gravel or sand to match what it is sitting on, so the dig looks
            // like disturbed ground rather than an imported block.
            BlockState below = level.getBlockState(spot.below());
            BlockState brushable = below.is(Blocks.SAND) || below.is(Blocks.RED_SAND)
                    ? Blocks.SUSPICIOUS_SAND.defaultBlockState()
                    : Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();

            setBlockAt(level, spot, brushable);

            if (level.getBlockEntity(spot) instanceof BrushableBlockEntity brush) {
                brush.setLootTable(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON, random.nextLong());
            }
            placed++;
        }
        return placed;
    }

    /**
     * A free position in a ring around the core, or null if nothing was free.
     *
     * <p>Bounded by {@link ShipCoreBlock#CALL_RADIUS} at the outer edge on
     * purpose: a dig outside that radius is a dig the core cannot hear, and a
     * derelict whose own site does not call it would be the mod contradicting
     * itself in the terrain.
     */
    private static BlockPos scatter(WorldGenLevel level, RandomSource random, BlockPos core,
                                    int min, int max) {
        int span = Math.min(max, ShipCoreBlock.CALL_RADIUS);
        for (int tries = 0; tries < 8; tries++) {
            int dx = random.nextInt(span * 2 + 1) - span;
            int dz = random.nextInt(span * 2 + 1) - span;
            if (Math.abs(dx) < min && Math.abs(dz) < min) {
                continue;
            }
            BlockPos surface = surfaceNear(level, core.offset(dx, 0, dz));
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    /**
     * The first free space over solid ground in one column, near the wreck.
     *
     * <p>A short local scan rather than a heightmap read. The {@code _WG}
     * heightmaps are only maintained while a chunk is generating, so a feature
     * that consults them is quietly undefined anywhere else - including under a
     * gametest, which is how this was caught. Walking a handful of blocks is
     * cheap, exact, and answers the same in every context.
     */
    private static BlockPos surfaceNear(WorldGenLevel level, BlockPos column) {
        for (int y = SURFACE_UP; y >= -SURFACE_DOWN; y--) {
            BlockPos at = column.above(y);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            if (level.getBlockState(at.below()).isAir()) {
                continue;
            }
            if (!level.getFluidState(at).is(Fluids.EMPTY)) {
                continue;
            }
            return at;
        }
        return null;
    }

    private static BlockState coreState(ShipStatus status) {
        return OctiaBlocks.SHIP_CORE.defaultBlockState().setValue(ShipCoreBlock.STATUS, status);
    }

    /**
     * Writes one block without neighbour updates.
     *
     * <p>{@code Feature.setBlock} is the sanctioned worldgen write and does not
     * run {@code onPlace}, which is what keeps this off the moorings store. See
     * the class note - that is intended, not incidental.
     */
    private static void setBlockAt(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }
}
