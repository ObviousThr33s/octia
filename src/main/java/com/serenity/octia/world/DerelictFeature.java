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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A derelict: a Serenity-class hull that did not make it, half-sunk at a dig.
 *
 * <p><b>The ship is a cube.</b> That is the Hexahedron brief this mod descends
 * from, and it is also the shape the mechanics were always describing:
 * {@link ShipCoreBlock#hullIntact} wants the core's eight horizontal neighbours
 * to be frame panels, which is the middle slice of a 3x3x3. So a derelict is
 * twenty-six panels around one core - a solid hexahedron, sunk to two thirds,
 * with its top face breaking the ground.
 *
 * <p><b>Erosion may take anything except the ring.</b> The top course weathers
 * away at random, which is what stops every wreck looking stamped from the same
 * die. The core's own slice is exempt, and not for looks: remove one panel from
 * it and {@code hullIntact} fails, the core reads ADRIFT, and the ruin quietly
 * stops being a ship. A wreck eroded down to exactly the ring that still makes
 * it a hull is a better object than either a pristine cube or a rubble pile.
 *
 * <p><b>This feature runs in two different worlds.</b> Natural generation hands
 * it a {@code WorldGenRegion}, which writes blocks straight into the chunk and
 * never calls {@code onPlace}. {@code /place feature} and the gametests hand it
 * a live {@code ServerLevel}, where the core surveys itself the instant it
 * lands. It has to read the same either way, which is why the core is placed
 * last - see {@link #place}.
 *
 * <p><b>Nothing here touches the moorings store.</b>
 * {@link com.serenity.octia.ship.ShipMoorings} is {@code SavedData} owned by the
 * server thread and chunks generate on workers; mooring from inside a feature
 * would be an unsynchronised write to a map the server is reading. Under natural
 * generation a derelict therefore stays out of the store until something surveys
 * it - a right-click, or any neighbour change. Discovery is registration. The
 * consequence to know: a wild derelict nobody has touched is a mark the debug
 * map does not show, which is the map being accurate rather than wrong.
 */
public class DerelictFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * How far the core sits below the surface it was found on.
     *
     * <p>One, so the cube spans surface-2 to surface and its top course stands
     * clear. Two buries the whole thing and leaves a flush andesite tile nobody
     * would look at twice.
     */
    private static final int SINK = 1;

    /** Digs ring the wreck outside the cube and inside the call radius. */
    private static final int DIG_MIN = 2;
    private static final int DIG_MAX = 4;

    /** Debris hugs the hull rather than reaching out to the dig ring. */
    private static final int DEBRIS_MAX = 3;

    /** Chance in eight that any given top-course panel has weathered away. */
    private static final int EROSION_IN_EIGHT = 3;

    public DerelictFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Exposed so a caller that placed one can work out where the core went. */
    public static int sink() {
        return SINK;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // The world switch, honoured here rather than at the biome modification.
        // Fabric's biome modifications are per-launch, not per-save, so the only
        // place a per-world flag can be applied is the moment of placement.
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();

        // The origin is already the surface: the placed feature runs a heightmap
        // modifier before this is called. Asking a heightmap again here is not
        // merely redundant, it is wrong outside natural generation - see
        // RuinGround.
        BlockPos core = context.origin().below(SINK);

        if (!RuinGround.hasFooting(level, core.below(2), 1)) {
            return false;
        }

        // A wreck in a village square reads as somebody's yard ornament rather
        // than as a ship that came and failed. The cube is 3x3x3 sunk to two
        // thirds, so one block either side of the core and three courses up
        // covers everything this places except the scattered debris.
        if (!RuinGround.clearOfStructures(level, core.below(1), 1, 1, 3)) {
            return false;
        }

        cube(level, random, core);
        debris(level, random, core);
        int digs = RuinGround.dig(level, random, core, DIG_MIN, DIG_MAX, 3 + random.nextInt(4));

        // The core goes in LAST, and the order is load-bearing.
        //
        // Against a live ServerLevel every write runs onPlace, so the core
        // surveys itself as it lands. Placing it before its digs existed made
        // the two contexts disagree: worldgen produced a CALLED wreck and
        // /place produced a MOORED one, because the survey ran over undisturbed
        // ground. Building the evidence first makes the survey and the literal
        // agree, whichever path got here.
        RuinGround.put(level, core, coreState(digs > 0 ? ShipStatus.CALLED : ShipStatus.MOORED));
        return true;
    }

    /**
     * Twenty-six panels around where the core will go, top course weathered.
     *
     * <p>The exemption is the whole design: {@code dy == 0} is the slice
     * {@link ShipCoreBlock#hullIntact} reads, so erosion is not allowed near it.
     * Everything above may go.
     */
    private static void cube(WorldGenLevel level, RandomSource random, BlockPos core) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (dy > 0 && random.nextInt(8) < EROSION_IN_EIGHT) {
                        continue;
                    }
                    // About one lit panel in the buried course - the only light
                    // a derelict carries besides its core. It cannot shine up
                    // through the gaps: erosion only opens the dy > 0 course and
                    // the dy == 0 ring is exempt, so a solid slice always sits
                    // over it. It shows where the ground has fallen away from
                    // the hull instead - a slope, a cave roof, or a player digging.
                    PanelLight light = dy < 0 && random.nextInt(8) == 0
                            ? PanelLight.GENERIC : PanelLight.NONE;

                    RuinGround.put(level, core.offset(dx, dy, dz),
                            OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                                    .setValue(AndesiteFramePanelBlock.LIGHT, light));
                }
            }
        }
    }

    /** Plating thrown clear, resting on whatever it landed on. */
    private static void debris(WorldGenLevel level, RandomSource random, BlockPos core) {
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos spot = RuinGround.scatter(level, random, core, DIG_MIN, DEBRIS_MAX);
            if (spot != null) {
                RuinGround.put(level, spot, OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }

    private static BlockState coreState(ShipStatus status) {
        return OctiaBlocks.SHIP_CORE.defaultBlockState().setValue(ShipCoreBlock.STATUS, status);
    }
}
