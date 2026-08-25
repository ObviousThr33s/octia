package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

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
 *
 * <p><b>[2026-08-24] Two passes were added here and every existing seed's world
 * moved.</b> The light budget in {@link #cube} and the open working in
 * {@link #excavate} both draw from the same {@link RandomSource} the rest of the
 * build already draws from, and inserting a draw anywhere in that stream shifts
 * every draw after it. So a save made before this date and loaded after it grows
 * different wrecks in unloaded chunks, at different ages, with different erosion,
 * beside the ones it already has. That is accepted rather than overlooked: this
 * mod is at alpha, nobody is holding a seed, and the alternative - appending
 * every new draw to the end of the stream forever - is a rule that makes the
 * build order unreadable within about three features. It is written down here so
 * the day somebody does hold a seed, they find out from a comment instead of
 * from the terrain.
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

    /**
     * How deep the wreck sits, by how long it has been there.
     *
     * <p>Public because two other things have to agree with it rather than copy
     * it. {@link #excavate} works out where the surrounding ground surface is by
     * adding this back on to the core it was given, and a gametest that wants a
     * core seated the way a wreck of a stated age would be seats it from here.
     * A hard-coded one or two in either place is a constant that has been copied
     * rather than the constant the feature used, and the copy goes stale in
     * silence.
     */
    public static int sink(RuinAge age) {
        return age == RuinAge.ANCIENT ? SINK + 1 : SINK;
    }

    /** Digs ring the wreck outside the cube and inside the call radius. */
    private static final int DIG_MIN = 2;
    private static final int DIG_MAX = 4;

    /** Debris hugs the hull rather than reaching out to the dig ring. */
    private static final int DEBRIS_MAX = 3;

    /** Chance in eight that any given top-course panel has weathered away. */
    private static final int EROSION_IN_EIGHT = 3;

    /**
     * The eight positions around the middle of a course, in a fixed order.
     *
     * <p>Only {@link #cube}'s light budget reads this, and it reads it from a
     * rolled start so two wrecks of the same age are not lit at the same
     * corners. The order itself carries no meaning - unlike {@code Mystery.RING},
     * which this deliberately does not borrow, because that one is indexed by a
     * bearing and reordering it would silently rotate every mark in every world.
     * Nothing here is a bearing.
     */
    private static final int[][] RING = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1},
    };

    /**
     * How many of a wreck's panels are still burning, by age.
     *
     * <p><b>Age is read by light before it is read by moss.</b> The owner's note
     * on the walked world was "cool but too dark!", and the honest fix is not a
     * brighter block or a torch dropped on top - it is that a ship abandoned last
     * season still has power in it and one abandoned an age ago does not. So the
     * count is the age, and a player learns to read a wreck's age from across a
     * field by how much of it is lit, before they are close enough to see a
     * single strand of moss.
     *
     * <p>A budget rather than a chance per panel, and that is the difference
     * between a rule and a lottery. One-in-eight per panel gives a wreck that
     * happens to come up dark and a wreck that happens to come up blazing at the
     * same age, which teaches nothing. A count teaches immediately.
     *
     * <p>provisional - owner tunes by walking the world
     */
    private static int litPanels(RuinAge age) {
        return switch (age) {
            case RECENT -> 5;
            case WEATHERED -> 2;
            case ANCIENT -> 1;
        };
    }

    /**
     * How many dry sites in this many carry an open working.
     *
     * <p>Three, so a plain wreck stays the common thing. A working is a whole
     * second object on the site - a cut, a heap and a seam - and if every wreck
     * had one then finding one would mean nothing, which is the same argument
     * {@link RuinAge#roll} makes about the ages at its two ends.
     *
     * <p>provisional - owner tunes by walking the world
     */
    private static final int WORKING_IN = 3;

    /**
     * How far below grade a working's floor sits, by age.
     *
     * <p>An old cut is a shallower cut, because the years have been filling it
     * in since the day it was left. Two is still unmistakably a dug hole and
     * three is a working somebody meant to come back to.
     *
     * <p>provisional - owner tunes by walking the world
     */
    private static int workingDepth(RuinAge age) {
        return age == RuinAge.ANCIENT ? 2 : 3;
    }

    /**
     * The deepest any working ever cuts.
     *
     * <p>Not a taste decision - it is the number {@link #raise} has to know
     * before the age is even rolled, because the site check happens first. See
     * the note there.
     */
    private static final int WORKING_DEPTH_MAX = 3;

    /**
     * Half the working's width across the hull face. Three wide, so the cut is
     * exactly as wide as the face it was cut against and reads as having been
     * taken off the ship rather than dug near it.
     */
    private static final int WORKING_HALF = 1;

    /** How far the spoil heap sits off the pit's own edge. Beside it, clear of it. */
    private static final int HEAP_OFF = WORKING_HALF + 1;

    /**
     * The most ore a seam ever shows.
     *
     * <p><b>Capped below one vanilla vein on purpose, and the number is the
     * cap.</b> Vanilla's smallest iron placement, {@code ore_iron_small}, is
     * four blocks; three is strictly under it. So a player who finds a working
     * has found a thing to look at and never a thing to stand at - the seam is
     * scenery, and the moment it out-yields walking twenty blocks and digging
     * down it has stopped being scenery and become a farm. That is also the
     * whole answer to the owner's "lots of ore... little to explore": the fix
     * for too much ore is not more ore, it is ore that means something.
     */
    private static final int SEAM_MAX = 3;

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

    /**
     * The radius the site is cleared over, and therefore the radius everything
     * this feature is allowed to write to.
     *
     * <p>Public so a gametest can hold the working's real footprint to the same
     * number {@link #raise} checks, rather than to a copy of it. The clearance
     * is asked for before the working is rolled, so the two cannot be reconciled
     * at run time - the only way they stay equal is for something to measure the
     * built thing against this and fail loudly when it grows.
     */
    public static int siteRadius() {
        return DIG_MAX;
    }

    /** How far below the core the site check reaches, and so how deep anything here may cut. */
    public static int siteDepth() {
        return 1 + WORKING_DEPTH_MAX;
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
        //
        // [2026-08-24] The open working joined that list and the radius did NOT
        // have to move, which is a fact worth stating rather than leaving to be
        // rediscovered. A working cuts out to WORKING_DEPTH_MAX + 1 along the
        // face and lays its spoil heap HEAP_OFF across it - four and two - so it
        // fits inside DIG_MAX by construction, and DerelictGameTest holds it
        // there. Grow the pit and this line has to grow with it.
        //
        // The BOX did have to move, downward. A working cuts below the ground
        // rather than standing on it, and the old span started at the core's own
        // floor, so a pit three deep was being dug through terrain nothing had
        // asked a question about. Starting the span at the deepest a working can
        // reach is over-covering for the two wrecks in three that never cut one,
        // and over-covering is the safe direction: it declines a site above a
        // mineshaft, which is exactly the site where a pit would open into
        // somebody else's structure. The cost, named: derelicts are now refused
        // slightly more often near deep structures than they were, and nobody
        // has measured how much more.
        if (yieldToStructures
                && !RuinGround.clearOfStructures(level, seat.core().below(siteDepth()),
                        DIG_MAX, DIG_MAX, 3 + WORKING_DEPTH_MAX)) {
            return false;
        }

        return build(level, random, seat);
    }

    /**
     * Where a wreck's core lands for a given column: walk down to real ground,
     * then sink by however old this one is. No question asked about who else
     * claimed the site - that is {@link RuinGround#clearOfStructures}' job in
     * {@link #place}, and deliberately not this method's.
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

        int attempts = 3 + random.nextInt(4);

        // The open working, on one dry site in three, and rolled here rather
        // than after the digs because the digs want its benches to stand on.
        //
        // Never under water. Everything a working says is said by disturbed
        // ground - a step cut into a bank, a heap with its layers upside down -
        // and on a seabed the sea has been rearranging that ground for as long
        // as the wreck has been there. A crisp cut on the sea floor would read
        // as a bug rather than as a dig, which is the same reason the dressing
        // below stays out of the water.
        Working working = !wet && random.nextInt(WORKING_IN) == 0
                ? excavate(level, random, core, age, attempts)
                : null;

        // A working has already put the site's digs in the ground, seated on its
        // own benches. Without one they ring the wreck as they always have.
        int digs = working != null
                ? working.digs()
                : RuinGround.dig(level, random, core, DIG_MIN, DIG_MAX, attempts, wet);

        // Plating thrown clear, and thrown clear AFTER the ground was cut. The
        // order is not cosmetic. A working refuses any column whose surface is
        // not open ground, and a frame panel is not open ground - so debris
        // scattered first lands in the pit's footprint often enough to cancel a
        // large share of workings outright, silently, for no reason anybody
        // would ever guess from the terrain. Thrown afterwards it lands in the
        // cut instead, which is exactly where plating pulled off a hull by
        // somebody standing in a hole would end up.
        debris(level, random, core);

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
            if (working != null) {
                // The path is given somewhere to go. A worn line that wanders
                // off in a rolled direction says somebody walked; a worn line
                // that runs from the hearth to the lip of the cut says what
                // they walked for, and it is the same few blocks of coarse dirt
                // either way. Two call sites rather than a null, because the
                // overload's contract is a target and not the absence of one.
                Habitation.dress(level, random, core, age, working.lip());
            } else {
                Habitation.dress(level, random, core, age);
            }
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
        stamp(level, random, core, called, RuinAge.WEATHERED);
    }

    /**
     * The same hull, at an age the caller names.
     *
     * <p>The age is what decides whether the lid survives and how much of the
     * hull is still lit, and neither of those can be reached through
     * {@link #place}, which rolls its own. So anything that needs to hold two
     * ages of the same wreck side by side - which so far is one gametest, and
     * it is the test that says the light means anything - asks for the age
     * outright. The overload above keeps its own promise about ANCIENT by
     * naming WEATHERED rather than by hiding this one.
     */
    public static void stamp(WorldGenLevel level, RandomSource random, BlockPos core,
                             boolean called, RuinAge age) {
        cube(level, random, core, age);
        RuinGround.put(level, core, coreState(called ? ShipStatus.CALLED : ShipStatus.MOORED));
    }

    /**
     * Twenty-six panels around where the core will go, top course weathered,
     * and however much of it is still lit.
     *
     * <p>The exemption is the whole design: {@code dy == 0} is the slice
     * {@link ShipCoreBlock#hullIntact} reads, so erosion is not allowed near it.
     * Everything above may go.
     *
     * <p><b>[2026-08-24] The lighting rule here is a reversal, and the decision
     * it reverses is kept below rather than deleted.</b> What stood here was:
     *
     * <pre>
     *     PanelLight light = dy &lt; 0 &amp;&amp; random.nextInt(8) == 0
     *             ? PanelLight.GENERIC : PanelLight.NONE;
     * </pre>
     *
     * with the reasoning that about one lit panel in the buried course is the
     * only light a derelict carries besides its core; that it cannot shine up
     * through the gaps, because erosion only opens the {@code dy > 0} course and
     * the {@code dy == 0} ring is exempt, so a solid slice always sits over it;
     * and that what it shows instead is where the ground has fallen away from
     * the hull - a slope, a cave roof, or a player digging. All of that is still
     * true and it is still a good idea. It was answering a different question
     * from the one the owner asked after walking the world, which was "cool but
     * too dark!" - a light that only appears where the ground has already been
     * cut away is invisible to somebody looking for the wreck in the first
     * place. The owner has ruled; the buried lamp is folded into the budget
     * below, where the deepest course is simply last in line for it.
     *
     * <p><b>Lighting a panel cannot unmoor a ship, and that was checked rather
     * than assumed.</b> {@code hullIntact} tests each ring neighbour with
     * {@code state.is(OctiaBlocks.ANDESITE_FRAME_PANEL)} - a block identity
     * test, at {@code ShipCoreBlock:141} - and {@code LIGHT} is a blockstate
     * property, so a lit ring panel and a dark one are the same block to it.
     * The dressing has a hard rule against writing into the ring for exactly
     * this reason; this pass never writes a different block there, only a
     * different state of the same one.
     *
     * <p><b>The survivors are decided before anything is written.</b> The budget
     * has to be spent on panels that exist, and the cheap way to know which
     * those are is to read the world back after building - which works on a live
     * level and is a question this file has already been burned by asking twice.
     * A mask costs twenty-seven booleans and asks nothing.
     */
    private static void cube(WorldGenLevel level, RandomSource random, BlockPos core, RuinAge age) {
        boolean[][][] standing = new boolean[3][3][3];

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
                    standing[dx + 1][dy + 1][dz + 1] = true;
                }
            }
        }

        // The lid first, and the order is the answer to "too dark". The lid is
        // the only course a dry wreck shows above ground, so light spent there
        // is light a player can see from a distance and walk toward; light spent
        // in the buried course is light nobody meets until they are already
        // standing on the thing. An ancient wreck has no lid and sits a course
        // deeper, so its one lamp falls through to the ring and stays hidden,
        // which is the age saying itself without a second rule.
        boolean[][][] lit = new boolean[3][3][3];
        int budget = litPanels(age);
        int start = random.nextInt(RING.length);
        int spent = 0;

        for (int dy = 1; dy >= -1 && spent < budget; dy--) {
            for (int i = 0; i <= RING.length && spent < budget; i++) {
                // i == 0 is the middle of the course, which on the lid is the
                // one face you look down on. The ring follows, from a rolled
                // start, so two wrecks of the same age are not lit at the same
                // corners.
                int dx = i == 0 ? 0 : RING[(start + i - 1) % RING.length][0];
                int dz = i == 0 ? 0 : RING[(start + i - 1) % RING.length][1];
                if (!standing[dx + 1][dy + 1][dz + 1]) {
                    continue;
                }
                lit[dx + 1][dy + 1][dz + 1] = true;
                spent++;
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!standing[dx + 1][dy + 1][dz + 1]) {
                        continue;
                    }
                    RuinGround.put(level, core.offset(dx, dy, dz),
                            OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                                    .setValue(AndesiteFramePanelBlock.LIGHT,
                                            lit[dx + 1][dy + 1][dz + 1]
                                                    ? PanelLight.GENERIC : PanelLight.NONE));
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

    /**
     * What an open working left in the ground.
     *
     * <p>Everything downstream of the cut needs a different part of it and none
     * of them can recompute it - the digs want the benches, the dressing wants
     * somewhere for its path to end, and a test wants to walk the shape rather
     * than assume it. Handing back the pieces is what stops each of those
     * re-deriving the geometry from the constants and drifting when a constant
     * moves.
     *
     * @param face     which hull face the cut was taken against, rolled. Nothing
     *                 may hard-code it; a test that names north passes on a
     *                 Tuesday.
     * @param lip      a standing position on undisturbed ground at the pit's
     *                 outer edge - where a path arrives, and where somebody
     *                 stepping down into the cut steps from
     * @param pit      the volume that was dug out, floor to grade
     * @param benches  the standing position on each step, ordered from the lip
     *                 down to the floor, which is the order you walk in
     * @param heapCap  the top block of the spoil heap, or null if the ground
     *                 beside the cut would not take a heap at all
     * @param digs     how many brushable blocks the site ended up with, which is
     *                 what decides whether the core reads CALLED
     */
    public record Working(Direction face, BlockPos lip, BoundingBox pit,
                          List<BlockPos> benches, BlockPos heapCap, int digs) {
    }

    /**
     * Cuts an open working against one face of a wreck, and seats the site's
     * digs in it.
     *
     * <p><b>Three verbatim complaints, one object.</b> "it would be cool if
     * there were already some dig sites dug out", "lots of ore... little to
     * explore", and "maybe a mining operation was here". A stepped pit answers
     * the first literally - the site has already been dug, and you arrive after
     * the fact. The seam in its far face answers the second by making ore a
     * thing to read rather than a thing to mine. And the three together answer
     * the third, because a hole, a heap and a face somebody was following is
     * what a mining operation leaves behind when it goes.
     *
     * <p><b>The pit is deepest against the hull and climbs out away from it.</b>
     * So the ramp is walkable from the outside in, and what you walk down toward
     * is the ship - the working faces the thing it was working on. It also puts
     * the only rock wall in the cut opposite the hull, which is where the seam
     * goes: from the floor you are looking at the face they were following.
     *
     * <p><b>It never reaches a column within one of the core.</b> That is the
     * clearance that keeps it off the hull's footing.
     * {@link RuinGround#hasFooting} vouched for {@code core.below(2)} over a
     * radius of one before any of this ran, and those nine columns are the cube
     * itself and the ground directly under it. The nearest column this touches
     * is at two. So the pit can be three below grade against the hull - which is
     * the whole point of it, a cut that exposes the ship's flank - without ever
     * taking a block the footing check vouched for. If the near lip is ever
     * moved inward, that guarantee is gone and the wreck starts hanging over its
     * own hole.
     *
     * <p><b>Nothing is invented and nothing collapses.</b> The spoil heap is
     * made of the blocks the pit removed, re-laid; the seam only appears where
     * there is rock to put it in. And a gravel-walled variant that falls in
     * behind you was ruled out before it was written: {@code RuinGround.put}'s
     * own note says flag 2 does not suppress {@code onPlace} on a live
     * {@code ServerLevel}, so gravel hanging over a fresh cut stands during
     * natural generation and falls within ticks under {@code /place} and in
     * every gametest. The two contexts would disagree and no test could hold
     * either of them.
     *
     * <p>Public for the reason {@link #stamp} is: a one-in-three roll and an age
     * are both decided inside {@link #place}, so a test that can only go through
     * the feature cannot reach this at all.
     *
     * @param digAttempts how many digs the site was going to get anyway. They
     *                    are spent on the benches first and scatter as usual
     *                    once the benches are full or refuse them.
     * @return what was cut, or null if the ground would not take a working
     */
    public static Working excavate(WorldGenLevel level, RandomSource random, BlockPos core,
                                   RuinAge age, int digAttempts) {
        Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Direction across = face.getClockWise();
        int depth = workingDepth(age);

        // Where the ground surface is, derived from the core rather than looked
        // up. The wreck sank by its age to get here, so adding that back is the
        // one arithmetic that cannot disagree with where the hull actually sits.
        int gradeY = core.getY() + sink(age) - 1;

        if (!diggable(level, core, face, across, gradeY, depth)) {
            return null;
        }

        BlockState[] strata = cut(level, core, face, across, gradeY, depth);
        if (age != RuinAge.RECENT) {
            slump(level, random, core, face, across, gradeY, depth, strata, age);
        }
        seam(level, random, core, face, across, gradeY, depth);
        BlockPos heapCap = heap(level, random, core, face, across, gradeY, depth, strata, age);

        List<BlockPos> benches = new ArrayList<>();
        for (int along = depth + 1; along >= 2; along--) {
            benches.add(core.relative(face, along)
                    .atY(gradeY - removedAt(depth, along) + 1).immutable());
        }
        List<BlockPos> steps = List.copyOf(benches);

        BoundingBox pit = BoundingBox.fromCorners(
                core.relative(face, 2).relative(across, -WORKING_HALF).atY(gradeY - depth + 1),
                core.relative(face, depth + 1).relative(across, WORKING_HALF).atY(gradeY));

        int digs = RuinGround.dig(level, random, core, DIG_MIN, DIG_MAX, digAttempts, false, steps);

        return new Working(face,
                core.relative(face, depth + 2).atY(gradeY + 1).immutable(),
                pit, steps, heapCap, digs);
    }

    /**
     * How many blocks come out of the column this far from the hull.
     *
     * <p>One arithmetic in one place, because the refusal, the cut, the benches
     * and the spoil all have to agree about it exactly. Two of them disagreeing
     * by one is a pit with a step you cannot climb, and it would only ever show
     * up as a screenshot.
     */
    private static int removedAt(int depth, int along) {
        return depth - (along - 2);
    }

    /**
     * Whether this site will take a stepped cut, asked before a single block
     * moves.
     *
     * <p><b>Refusing rather than terraforming, the same as everything else that
     * touches ground here.</b> A working cut into a slope is a staircase, and a
     * working cut into a cave roof is a hole in a ceiling. Both are worse objects
     * than the plain wreck the site gets instead, and two dry sites in three are
     * getting the plain wreck anyway.
     *
     * <p>Every column has to be exactly at the wreck's own grade - not within a
     * tolerance. A tolerance is what turns a bench into a step that is sometimes
     * two high, and the one thing a dig site has to be is walkable. The block
     * above may be anything replaceable, so a tuft or a layer of snow does not
     * cost the site its working; the cut takes it with the ground under it.
     *
     * <p>The tread each column will end on is asked the vanilla support question
     * directly. That is the same question {@code Habitation.settled} asks and for
     * the same reason: a bench whose floor is a slab or a fence is a bench a dig
     * cannot be seated on, and the dig would silently go somewhere else.
     */
    private static boolean diggable(WorldGenLevel level, BlockPos core, Direction face,
                                    Direction across, int gradeY, int depth) {
        for (int along = 2; along <= depth + 1; along++) {
            int removed = removedAt(depth, along);
            for (int c = -WORKING_HALF; c <= WORKING_HALF; c++) {
                BlockPos column = core.relative(face, along).relative(across, c);
                if (!atGrade(level, column, gradeY)) {
                    return false;
                }
                for (int d = 0; d < removed; d++) {
                    BlockPos p = column.atY(gradeY - d);
                    if (level.getBlockState(p).isAir() || RuinGround.submerged(level, p)) {
                        return false;
                    }
                }
                BlockPos tread = column.atY(gradeY - removed);
                if (!level.getBlockState(tread).isFaceSturdy(level, tread, Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether this column's ground is exactly at the wreck's own grade.
     *
     * <p>Block reads, never a heightmap, for the reason {@link RuinGround}'s
     * header gives at length. It lives here rather than there because only the
     * working asks it today - the same rule {@code Habitation.settled} is kept
     * under. The day a second cut wants it, it moves.
     */
    private static boolean atGrade(WorldGenLevel level, BlockPos column, int gradeY) {
        BlockPos top = column.atY(gradeY);
        if (level.getBlockState(top).isAir() || RuinGround.submerged(level, top)) {
            return false;
        }
        BlockPos over = top.above();
        return level.getBlockState(over).canBeReplaced() && !RuinGround.submerged(level, over);
    }

    /**
     * Takes the material out, and remembers it in the order it came.
     *
     * <p>The strata are read off the column against the hull, which is the only
     * one that goes through every level, so the record is complete rather than
     * assembled from several holes. Shallowest first, because that is the order
     * a person digging meets them and the order the heap has to invert.
     */
    private static BlockState[] cut(WorldGenLevel level, BlockPos core, Direction face,
                                    Direction across, int gradeY, int depth) {
        BlockState[] strata = new BlockState[depth];
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int along = 2; along <= depth + 1; along++) {
            int removed = removedAt(depth, along);
            for (int c = -WORKING_HALF; c <= WORKING_HALF; c++) {
                BlockPos column = core.relative(face, along).relative(across, c);

                // Whatever was standing on the ground goes with the ground. A
                // tuft left hanging over a dug column is a plant growing on
                // nothing, which is the exact defect the playtest found under a
                // decorated pot.
                BlockPos over = column.atY(gradeY + 1);
                if (!level.getBlockState(over).isAir()) {
                    RuinGround.put(level, over, air);
                }

                for (int d = 0; d < removed; d++) {
                    BlockPos p = column.atY(gradeY - d);
                    if (along == 2 && c == 0) {
                        strata[d] = level.getBlockState(p);
                    }
                    RuinGround.put(level, p, air);
                }
            }
        }
        return strata;
    }

    /**
     * What the years do to an open cut.
     *
     * <p>A recent working is crisp: the edges are where the tool left them. Every
     * older one has had its walls coming in since the day it was abandoned, so a
     * block off the face lies on the floor - and it is a block off the face,
     * taken from the deepest stratum, not gravel invented for the look of it.
     *
     * <p>An ancient one has gone further than that: the top step has grassed
     * over, which is what turns a dig into a hummock you would walk past. That
     * is also why an ancient working almost never shows a seam - see
     * {@link #seam}, which needs rock and finds grass.
     */
    private static void slump(WorldGenLevel level, RandomSource random, BlockPos core,
                              Direction face, Direction across, int gradeY, int depth,
                              BlockState[] strata, RuinAge age) {
        BlockPos fallen = core.relative(face, 2)
                .relative(across, random.nextInt(WORKING_HALF * 2 + 1) - WORKING_HALF)
                .atY(gradeY - depth + 1);
        RuinGround.put(level, fallen, strata[strata.length - 1]);

        if (age != RuinAge.ANCIENT) {
            return;
        }
        for (int c = -WORKING_HALF; c <= WORKING_HALF; c++) {
            RuinGround.put(level,
                    core.relative(face, depth + 1).relative(across, c).atY(gradeY - 1),
                    Blocks.GRASS_BLOCK.defaultBlockState());
        }
    }

    /**
     * The face they were following, if there is rock to follow it in.
     *
     * <p>The far face is the riser between the pit floor and the first step back
     * up - the one wall in the cut that is neither the hull nor the sky, and the
     * one you are looking straight at from the bottom. It is three blocks wide
     * and one high, which is why {@link #SEAM_MAX} did not have to be argued
     * about: the geometry caps it at three before the constant does.
     *
     * <p>No seam in a dirt wall. A seam needs rock to be a seam, and a shallow
     * cut through a meadow simply has none - which is honest, and is the same
     * whitelist discipline {@code Habitation.isGround} is written under. It also
     * means an ancient working, whose top step {@link #slump} has already put
     * back to grass, usually shows nothing at all: the right answer for a hole
     * nobody has worked in a lifetime.
     */
    private static void seam(WorldGenLevel level, RandomSource random, BlockPos core,
                             Direction face, Direction across, int gradeY, int depth) {
        int y = gradeY - depth + 1;
        int left = 1 + random.nextInt(SEAM_MAX);

        for (int c = -WORKING_HALF; c <= WORKING_HALF && left > 0; c++) {
            BlockPos p = core.relative(face, 3).relative(across, c).atY(y);
            BlockState wall = level.getBlockState(p);
            if (!wall.is(BlockTags.BASE_STONE_OVERWORLD)) {
                continue;
            }
            RuinGround.put(level, p, ore(wall, random));
            left--;
        }
    }

    /**
     * Which ore, matched to the rock it is sitting in.
     *
     * <p>Iron or coal and nothing rarer, because the seam is meant to say what
     * they were here for, and what people are here for is iron and coal. A
     * diamond in the wall would say something else entirely and would turn the
     * scenery into a destination, which is the thing {@link #SEAM_MAX} exists to
     * prevent.
     */
    private static BlockState ore(BlockState wall, RandomSource random) {
        boolean deep = wall.is(Blocks.DEEPSLATE);
        boolean iron = random.nextBoolean();
        if (deep) {
            return (iron ? Blocks.DEEPSLATE_IRON_ORE : Blocks.DEEPSLATE_COAL_ORE)
                    .defaultBlockState();
        }
        return (iron ? Blocks.IRON_ORE : Blocks.COAL_ORE).defaultBlockState();
    }

    /**
     * The spoil, re-laid beside the cut with its strata upside down.
     *
     * <p><b>This is the one tell every real dig leaves, and it is why the heap
     * is worth building at all.</b> Material comes out of a hole top-first and
     * goes on the pile in that order, so the pile ends up with the deepest thing
     * on top: stone lying over dirt, next to a hole with dirt lying over stone.
     * Nobody has to be told this. A player who has ever dug anything reads it in
     * one glance, and a player who has not still sees that the ground beside the
     * pit is the wrong way round.
     *
     * <p>Nothing is invented. Both courses are blocks {@link #cut} took out of
     * the ground a few paces away, which is also what keeps the heap matching
     * whatever biome it is in for free.
     *
     * <p>The heap follows the ground rather than being cut into it - a column
     * that has no surface beside the pit simply gets no heap, and the rest of
     * the mound still lands. It is deliberately laid on top of grade rather than
     * seated in it, because a heap is the one thing on this site that is not a
     * hole.
     *
     * @return the top block of the mound nearest the hull, or null if none of it
     *         found ground
     */
    private static BlockPos heap(WorldGenLevel level, RandomSource random, BlockPos core,
                                 Direction face, Direction across, int gradeY, int depth,
                                 BlockState[] strata, RuinAge age) {
        int side = random.nextBoolean() ? 1 : -1;
        BlockState shallow = strata[0];
        BlockState cap = heapCap(age, strata[strata.length - 1]);
        BlockPos capAt = null;

        for (int along = 2; along <= depth + 1; along++) {
            BlockPos ground = RuinGround.surfaceNear(level,
                    core.relative(face, along).relative(across, HEAP_OFF * side).atY(gradeY + 1),
                    1, 1);
            if (ground == null) {
                continue;
            }
            RuinGround.put(level, ground, shallow);

            BlockPos over = ground.above();
            if (!level.getBlockState(over).canBeReplaced() || RuinGround.submerged(level, over)) {
                continue;
            }
            RuinGround.put(level, over, cap);
            if (capAt == null) {
                capAt = over.immutable();
            }
        }
        return capAt;
    }

    /**
     * What is lying on top of the heap, by how long ago it was made.
     *
     * <p>The inversion underneath is untouched at every age - that is the tell,
     * and burying it would be throwing away the reason for the heap. This is the
     * skin the years laid over it, and it is the second place age is read on
     * this site: raw spoil, then weather, then the ground taking it back. A
     * grassed mound beside a grass-lipped pit is why an ancient working reads as
     * a lump in a field rather than as a dig.
     */
    private static BlockState heapCap(RuinAge age, BlockState deepest) {
        return switch (age) {
            case RECENT -> deepest;
            case WEATHERED -> Blocks.COARSE_DIRT.defaultBlockState();
            case ANCIENT -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private static BlockState coreState(ShipStatus status) {
        return OctiaBlocks.SHIP_CORE.defaultBlockState().setValue(ShipCoreBlock.STATUS, status);
    }
}
