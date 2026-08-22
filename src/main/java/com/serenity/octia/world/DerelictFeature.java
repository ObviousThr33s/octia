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
     * would look at twice - which is exactly what an ancient one should be, so
     * {@link #sink(RuinAge)} adds a course at that end.
     */
    private static final int SINK = 1;

    /** How far down to look for real ground, dropping through air and water. */
    private static final int DESCEND_MAX = 24;

    /** How deep the wreck sits, by how long it has been there. */
    private static int sink(RuinAge age) {
        return age == RuinAge.ANCIENT ? SINK + 1 : SINK;
    }

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

    /**
     * How far below a surface a core could possibly have ended up.
     *
     * <p>For callers that need to find the core they just placed. It cannot be
     * calculated any more - the wreck walks down through air and water to real
     * ground and then sinks by its age - so the honest answer is a search
     * bound rather than an offset.
     */
    public static int searchDepth() {
        return DESCEND_MAX + SINK + 2;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // The world switch, honoured here rather than at the biome modification.
        // Fabric's biome modifications are per-launch, not per-save, so the only
        // place a per-world flag can be applied is the moment of placement.
        if (!OctiaWorldgen.active()) {
            return false;
        }
        return raise(context.level(), context.random(), context.origin(), true);
    }

    /**
     * A wreck on a column, with or without yielding to whatever else is there.
     *
     * <p>The one definition of what placing a derelict means, so that the three
     * callers - the rarity roll above, the guaranteed spawn wreck, and the
     * beamline station in {@link BeamlineDerelictFeature} - cannot drift apart
     * on the descent, the sink, or the order of the draws. The only thing they
     * ever disagreed about is the structure check, so that is the parameter.
     *
     * @param yieldToStructures whether a village or a mineshaft on the site is
     *                          allowed to refuse it. False only for the wreck
     *                          that must exist - see {@link #seat}.
     */
    public static boolean raise(WorldGenLevel level, RandomSource random, BlockPos column,
            boolean yieldToStructures) {
        Seat seat = survey(level, random, column);
        if (seat == null) {
            return false;
        }

        // A wreck in a village square reads as somebody's yard ornament rather
        // than as a ship that came and failed.
        //
        // DIG_MAX and not the cube's own radius, and that is the whole point of
        // the number. The cube is one block either side of the core, but this
        // feature goes on to scatter digs out to four and debris out to three -
        // so a site cleared on the hull's footprint alone would pass, build
        // clear of the village, and then put suspicious gravel in somebody's
        // wheat. The radius checked has to be the radius written to.
        if (yieldToStructures
                && !RuinGround.clearOfStructures(level, seat.core().below(1), DIG_MAX, DIG_MAX, 3)) {
            return false;
        }

        return build(level, random, seat);
    }

    /**
     * Where a wreck's core lands for a given column: walk down to real ground,
     * then sink by however old this one is. No question asked about who else
     * claimed the site - that is {@link #clearOfStructures}' job in {@link #place},
     * and deliberately not this method's.
     *
     * <p><b>Extracted in the lives-and-islands merge, and the extraction is the
     * resolution.</b> The two branches disagreed about how the guaranteed spawn
     * wreck should be placed, and each was protecting something real. This side
     * went straight to {@link #seat} so the guarantee could not be refused by a
     * village near spawn. The islands side went through {@link #place} to gain
     * the walk-down and the age-based sink, which "surface minus a constant"
     * stopped being able to express the moment either landed - and then had to
     * hunt down the column for a {@code ShipCoreBlock} afterwards, because
     * routing through {@code place} hides where the core ended up.
     *
     * <p>Both wanted things that fit together. This method is {@code place}
     * without the structure check: the caller that must not be refused calls
     * this and then {@link #seat}, and gets the descent and the sink without the
     * veto. It also gives that caller the core position outright, so the
     * downward scan for a placed core is not needed - the islands side's
     * insistence that the position be found rather than assumed is kept, by
     * returning the found position instead of searching for it.
     *
     * <p>{@code place} calls this too, so the walk-down and the sink have one
     * definition rather than two. The age is rolled before the descent, matching
     * the order {@code place} consumed the {@link RandomSource} in before this
     * was pulled out - reordering those two draws would hand every existing seed
     * a different world.
     *
     * @param column where the placement modifier or the ring search wants a wreck
     * @return the core's position, or null if nothing down there holds a world up
     */
    public static Seat survey(WorldGenLevel level, RandomSource random, BlockPos column) {
        RuinAge age = RuinAge.roll(random);

        // The column is the surface something else chose, which over water is
        // the top of the water. Walk down from it, through air and through
        // fluid, to whatever is actually holding the world up - the same idea
        // that puts the beacon on a seabed instead of afloat.
        BlockPos floor = RuinGround.descend(level, column, DESCEND_MAX);
        if (floor == null) {
            return null;
        }

        return new Seat(floor.below(sink(age)), age, RuinGround.submerged(level, floor));
    }

    /**
     * What a column turns into: where the core lands, how old the wreck is, and
     * whether it is under water.
     *
     * <p>The three travel together because they are decided together and every
     * one of them is needed at the far end - the age dresses the hull and picks
     * the erosion, the wetness decides between kelp and a made bed, and the core
     * is where it all goes. Passing only the core, which is what the shape of
     * this file was before the merge, is what forced the wetness and the age to
     * be recomputed downstream from a position that had already moved.
     */
    public record Seat(BlockPos core, RuinAge age, boolean wet) {
    }

    /**
     * Builds the wreck on a known column, with no question asked about who else
     * might have claimed it.
     *
     * <p>Split out of {@link #place} for one caller: {@link OctiaWorldgen#placeNearSpawn},
     * which is the promise that a player meets a derelict at all. A rarity roll
     * cannot make that promise, so the first one is placed rather than rolled -
     * and a structure check in front of it can refuse all forty candidate
     * columns and quietly hand back a world with no starter wreck. Between "the
     * first wreck is guaranteed" and "no wreck ever shares ground with a
     * village", the guarantee wins, because a player who never meets one has no
     * way to discover the rest of the mod.
     *
     * <p>Keeping the check out of here also keeps it off the server thread at
     * world load. The structure query's second read is a
     * {@code getChunk(..., STRUCTURE_STARTS, true)} - {@code require} is true,
     * so on a live level it <em>generates</em> what is missing. Forty candidate
     * columns of that during {@code SERVER_STARTED} is a stall nobody asked for.
     */
    public static boolean seat(WorldGenLevel level, RandomSource random, BlockPos column) {
        return raise(level, random, column, false);
    }

    /**
     * Builds the wreck on a site that has already been surveyed.
     *
     * <p><b>This split is the merge's doing, and the alternative was a silent
     * bug.</b> Both callers need the same construction and they disagree about
     * exactly one thing: {@link #place} asks {@link RuinGround#clearOfStructures}
     * and {@link #seat} deliberately does not. That single difference is the only
     * reason two entry points exist.
     *
     * <p>What made it dangerous is that a wreck now walks down to real ground and
     * then sinks by its age, and both happen in {@link #survey}. Wire either
     * caller so that the descent runs twice - by handing an already-surveyed core
     * back into something that surveys again - and every wreck in the world sits
     * about two courses too deep. It compiles, it generates, and nothing says a
     * word. So the descent happens in exactly one place, its result travels as a
     * {@link Seat}, and no path takes a core back to the top of the pipeline.
     */
    private static boolean build(WorldGenLevel level, RandomSource random, Seat seat) {
        BlockPos core = seat.core();
        RuinAge age = seat.age();
        boolean wet = seat.wet();

        if (!RuinGround.hasFooting(level, core.below(2), 1)) {
            return false;
        }

        if (wet) {
            // Under the sea is allowed; the surface of it is not. A wreck that
            // straddles the waterline is neither a shipwreck nor a ruin, it is
            // a bug with its roof in the air - so require the water to still be
            // water two courses above the cube.
            if (!RuinGround.submerged(level, core.above(3))) {
                return false;
            }
        } else if (!RuinGround.isDry(level, core, 1, 1, 1)) {
            // The cube's own volume, not just the ground under it. A footing
            // check passes on a lake shore that rises over the top course.
            return false;
        }

        cube(level, random, core, age);
        debris(level, random, core);
        int digs = RuinGround.dig(level, random, core, DIG_MIN, DIG_MAX, 3 + random.nextInt(4), wet);

        // The core goes in LAST, and the order is load-bearing.
        //
        // Against a live ServerLevel every write runs onPlace, so the core
        // surveys itself as it lands. Placing it before its digs existed made
        // the two contexts disagree: worldgen produced a CALLED wreck and
        // /place produced a MOORED one, because the survey ran over undisturbed
        // ground. Building the evidence first makes the survey and the literal
        // agree, whichever path got here.
        RuinGround.put(level, core, coreState(digs > 0 ? ShipStatus.CALLED : ShipStatus.MOORED));

        // Dressed last, and it has to be. Habitation refuses any position in a
        // hull ring, and it can only see the ring once the core is standing.
        //
        // Not underwater, though. Every prop in that pass is a thing somebody
        // left in a room - a lit campfire, a made bed, a path worn into dirt -
        // and none of them mean anything on a seabed. A submerged wreck is
        // dressed by the ocean, which puts kelp and sea pickles on it for free.
        // A proper submerged palette from world 0's marine blocks would be
        // better than nothing here; nothing is better than a bed.
        if (!wet) {
            Habitation.dress(level, random, core, age);
        }

        // Say where this went. A feature leaves no record of itself anywhere, so
        // without this the mod cannot answer "where are the wrecks" about its
        // own world. Queued rather than written - see RuinRegistry.
        RuinRegistry.report(level.getLevel(), OctiaWorldgen.DERELICT, core);
        return true;
    }

    /**
     * The hexahedron and its core, written at a position someone else chose.
     *
     * <p>Exists for {@link TemplateRuinFeature}. A template cannot contain a
     * core, because erosion would eventually take a panel out of the ring and
     * the ruin would stop being a ship without anybody noticing; it carries a
     * marker instead and the hull is stamped here, afterwards, out of reach of
     * the rot processor.
     *
     * @param called whether a dig is already in the ground nearby, which is the
     *               difference between a wreck that was summoned and one that
     *               merely floated
     */
    public static void stamp(WorldGenLevel level, RandomSource random, BlockPos core, boolean called) {
        // Never ANCIENT here. A template ruin decides its own age for dressing,
        // but the hull it was told to carry should be a whole one - the marker
        // said a ship was here, not that its lid was gone.
        cube(level, random, core, RuinAge.WEATHERED);
        RuinGround.put(level, core, coreState(called ? ShipStatus.CALLED : ShipStatus.MOORED));
    }

    /**
     * Twenty-six panels around where the core will go, top course weathered.
     *
     * <p>The exemption is the whole design: {@code dy == 0} is the slice
     * {@link ShipCoreBlock#hullIntact} reads, so erosion is not allowed near it.
     * Everything above may go.
     */
    private static void cube(WorldGenLevel level, RandomSource random, BlockPos core, RuinAge age) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    // An ancient wreck has lost its lid entirely rather than
                    // lost some of it. Combined with sinking a course deeper,
                    // what is left is the ring and the floor - a hull worn down
                    // to precisely the part that still makes it a ship.
                    if (dy > 0 && age == RuinAge.ANCIENT) {
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
