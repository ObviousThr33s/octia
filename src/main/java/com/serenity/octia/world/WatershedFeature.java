package com.serenity.octia.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The watershed's body: a spring seat, a stepped runnel, and still bowls.
 *
 * <p>{@link Watershed} is the soul - it decides where a spring rises and how
 * many legs its fall may matter for, and it decides from the seed alone. This
 * class is the body: painted statics, built entirely from the single owning
 * chunk, in which nothing flows, nothing falls, and nothing ticks to any
 * effect. Every water cell is a still source at flag 2, exactly one block
 * deep, sealed on all four sides and below, with air above - a mirror.
 *
 * <p><b>The density arithmetic, written down so it is not re-derived wrong.</b>
 * Placed rarity 1/800 times the spring gate's ~1/2 is one watershed per
 * ~1,600 chunks - between the obelisk's 520 and the station's measured 1,575,
 * and rarer than the obelisk as the charter orders. A 512-block cell is 1,024
 * chunks, so a watered cell hosts about one watershed: one spring is a whole
 * watershed. The spring is born OF the node - same cell, same heading, same
 * downhill trace - while its body is painted where the ground offered it,
 * exactly as the obelisk reads the leg under its feet without standing on the
 * node.
 *
 * <p><b>The seal invariant, which is the law of every write.</b> For every
 * planned water cell: below it is planned andesite; each of its four
 * horizontal neighbours is planned andesite, surveyed-solid terrain, or
 * another planned water cell at the same Y; above it is air, cleared to Y+2.
 * No water cell is ever planned adjacent to a column whose floor is
 * unfindable - at least {@link #EDGE_GAP} of solid stands between any source
 * and open air-over-air, and at the geometry below the bowl's own rim ring is
 * that gap. All water is exactly one block deep, everywhere; that single
 * decision is what makes the seal checkable cell by cell, and
 * {@code WatershedGameTest.everyPoolIsSealed} checks it exactly that way.
 *
 * <p><b>Survey first, then carve, and the order is the contract.</b> The
 * survey is read-only and either returns a complete pre-validated plan or
 * nothing; if it returns nothing, zero blocks were written. Once writing
 * begins nothing can fail, because every write was validated before the
 * first one landed. The write order inside the carve is a law of its own -
 * see {@link Plan#write}.
 *
 * <p><b>No RandomSource, anywhere.</b> The soul is a pure function and the
 * body is a deterministic reading of the ground, so this feature draws no
 * randomness at all - determinism of the body is enforced by signature, not
 * asserted. A shape that wanted variety would want the ground to provide it,
 * and the ground does.
 *
 * <p><b>Deliberate absences.</b> No {@code RuinGround.dig} - a spring is not
 * a ruin and carries no loot. No {@code Habitation.dress} - the water is the
 * dressing. No {@code RandomSource}, as above.
 *
 * <p><b>And one absence that ended.</b> This class carried "no
 * {@code RuinRegistry.report} - the ARCH asymmetry" until 2026-08-28, on the
 * stated trigger "the day the map wants water". The map was not what asked
 * first; the owner's density dial did. {@code GREENFIELD.md} III.5 leaves
 * watershed density and the uphill-gate thresholds to be walked and ruled on,
 * and that ruling needs a count. Springs were the only Octia landmark a
 * finished save could not be asked about - the 8/24 playtest world's
 * {@code octia_ruins.dat} listed beacon 1, derelict 11, obelisk 11 and
 * waystation 9, and had no row for water at all. {@link #place} now reports,
 * on success only.
 *
 * <p><b>Recorded, not fixed</b> - corrections are new entries, the estate's
 * law. An obelisk plinth and a watershed can meet in one chunk at roughly
 * 1/416,000 chunks, and the survey does not know a panel from a hillside: it
 * could seat a bowl against a plinth. Rare enough to accept at alpha;
 * recorded here rather than silently gated. Also accepted and recorded: in a
 * frozen biome the mirror may freeze, and ice forming and melting in a sealed
 * one-deep bowl stays sealed - no code answers this because none is needed.
 */
public class WatershedFeature extends Feature<NoneFeatureConfiguration> {

    /** The spring eye: a disc of 5 water cells. */
    private static final int SEAT_RADIUS = 1;

    /** Intermediate bowls, 7 across. */
    private static final int POOL_RADIUS = 3;  // provisional - owner tunes by walking the world

    /**
     * The terminal basin is {@link Mystery#ARRIVED} made of water, and ARRIVED
     * itself (24) spans four chunks - illegal under the write window - so the
     * basin is the arrival disc at quarter scale, the largest bowl that always
     * fits the window with rim and gap. 13 across.
     */
    private static final int TERMINAL_RADIUS = Mystery.ARRIVED / 4;  // provisional - owner tunes by walking the world

    /** The smallest bowl that still reads as a basin; the shrink-to-fit floor. */
    private static final int MIN_TERMINAL_RADIUS = 2;

    /** The tallest terrace riser the runnel will step down; deeper is a cliff. */
    private static final int STEP_MAX_DROP = 3;  // provisional - owner tunes by walking the world

    /** Total course length in blocks, seat rim to terminal rim; keeps reach inside 1-2 chunks. */
    private static final int MAX_RUN = 40;  // provisional - owner tunes by walking the world

    /** Solid blocks required between any source and any open-edge column. */
    private static final int EDGE_GAP = 1;

    /** How far a rim column may reach down for solid before the site refuses. */
    private static final int RIM_DEPTH = 4;

    public WatershedFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /**
     * The edge gap, for the gametest that walks the rule rather than assuming
     * it. The {@code ObeliskFeature.across()} reasoning: a test that hard-codes
     * 1 passes against the wrong law the day the constant moves.
     */
    public static int edgeGap() {
        return EDGE_GAP;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        long seed = level.getSeed();

        // The soul's gate, before any block is read. A cell that is not the
        // uphill end of its own leg opens no spring, and the walk's length is
        // the pool budget the body may render.
        int legs = Watershed.fallLegs(seed,
                Sightlines.cell(origin.getX()), Sightlines.cell(origin.getZ()));
        if (legs == 0) {
            return false;
        }

        // The water runs the way the thread runs.
        Sightlines.Heading flow = Sightlines.legAt(seed, origin.getX(), origin.getZ()).heading();

        boolean carved = carve(level, origin, flow, legs);

        // Reported here and not inside carve, deliberately. carve is the
        // gametest seam - it exists so a test can hand the body a course
        // without owning the world seed - and a test that carves a spring on
        // purpose has not found a landmark. Only a spring the world actually
        // grew is worth recording, which is exactly the ones that come through
        // this method. DerelictFeature draws the same line in the same place.
        //
        // Only on success: carve returns false with zero writes when the
        // survey refuses, and a registry that lists springs which were never
        // built would answer the density question with the wrong number - the
        // one question this record was opened to answer.
        if (carved) {
            RuinRegistry.report(level.getLevel(), OctiaWorldgen.WATERSHED, origin);
        }
        return carved;
    }

    /**
     * The seam between the soul and the body, and it is public for the same
     * reason {@code DerelictFeature.raise} is callable from outside its own
     * placement path: a gametest cannot choose the world seed, so the body
     * must be offerable a course directly - the
     * {@code BeamlineDerelictGameTest} javadoc argument, in the other
     * direction.
     *
     * <p>Contract: survey first, read-only; returns false with zero writes if
     * the survey fails; once writing begins nothing can fail, because every
     * write was pre-validated. Draws no randomness. {@code legs} must be at
     * least 1 - callers guarantee it, since a spring's fall is 1 to
     * {@link Watershed#MAX_FALL_LEGS} by construction, and nothing here
     * asserts it.
     */
    public static boolean carve(WorldGenLevel level, BlockPos origin,
                                Sightlines.Heading flow, int legs) {
        Plan plan = survey(level, origin, flow, legs);
        if (plan == null) {
            return false;
        }
        plan.write(level);
        return true;
    }

    /**
     * The read-only half. Walks the ground the course would take, fits the
     * terminal basin, and only then materialises a plan in which every cell
     * has already been checked - against the window, against fluid, against
     * the ground, and against the plan's own earlier entries.
     *
     * <p>The stops, each with its rule: a drop deeper than
     * {@link #STEP_MAX_DROP} is EDGE - a cliff, a cave lip, the void; ground
     * rising ahead is RISE - water pools against the hill; the next write
     * leaving the window is WINDOW; {@link #MAX_RUN} steps is RUN. Every stop
     * is answered by the terminal basin, pulled back and shrunk to fit, and a
     * course that pulls back to its own seat collapses to a single basin
     * where the spring rose - which is also what makes this buildable on a
     * small flat gametest floor. Fluid met anywhere the plan would write
     * declines the site rather than unsealing anything.
     */
    private static Plan survey(WorldGenLevel level, BlockPos origin,
                               Sightlines.Heading flow, int legs) {
        // The placed JSON drops the origin at WORLD_SURFACE_WG; surfaceNear
        // owns the metre around it - the arch precedent.
        BlockPos seatAir = RuinGround.surfaceNear(level, origin);
        if (seatAir == null) {
            return null;
        }
        // Water replaces the ground's own top solid block, so the mirror sits
        // flush in the ground - no raised lip, austere.
        BlockPos seat = seatAir.below();

        // A spring born underwater is no spring.
        if (!RuinGround.isDry(level, seat, SEAT_RADIUS + 1, 0, 2)) {
            return null;
        }
        if (!RuinGround.hasFooting(level, seat.below(1),
                SEAT_RADIUS + 1, SEAT_RADIUS + 1, 3)) {
            return null;
        }
        // The seat obeys the same law as every bowl: top solid at water Y,
        // ground under that, the mirror open above.
        if (!discIsLevel(level, seat, SEAT_RADIUS)) {
            return null;
        }
        if (!rimRests(level, seat, SEAT_RADIUS)) {
            return null;
        }

        // The 3x3 region a feature may write, and the proof is positional,
        // not hopeful: every planned cell is tested for containment, and
        // nothing is ever clamped silently.
        Window window = windowOf(origin);
        if (!window.containsDisc(seat.getX(), seat.getZ(), SEAT_RADIUS + 1)) {
            return null;
        }

        int fx = flow.dx();
        int fz = flow.dz();
        // The two sides of the runnel, perpendicular to the flow.
        int ax = fz;
        int az = -fx;

        // The walk. Trough cells in course order; the tail is the suffix laid
        // since the last change of level, which is the only stretch the
        // terminal basin may be fitted against.
        List<BlockPos> trough = new ArrayList<>();
        List<Riser> risers = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();
        int tailStart = 0;

        // The seat's downhill rim cell becomes the junction mouth.
        BlockPos cursor = seat.offset(2 * fx, 0, 2 * fz);
        trough.add(cursor);

        int run = 0;
        while (run < MAX_RUN) {
            BlockPos next = cursor.offset(fx, 0, fz);
            // The write here would be the cell and its edging either side.
            if (!window.contains(next.getX() - 1, next.getZ() - 1)
                    || !window.contains(next.getX() + 1, next.getZ() + 1)) {
                break;  // WINDOW
            }
            BlockPos nextAir = next.above();
            if (!level.getFluidState(nextAir).is(Fluids.EMPTY)) {
                break;  // a shore ahead is an edge for this purpose
            }
            if (!level.getBlockState(nextAir).isAir()) {
                break;  // RISE - water pools against the hill
            }
            // Zero up on purpose: water does not step uphill.
            BlockPos probe = RuinGround.surfaceNear(level, nextAir, 0, STEP_MAX_DROP);
            if (probe == null) {
                break;  // EDGE - a cliff, a cave lip, the void
            }
            if (!level.getFluidState(probe.below()).is(Fluids.EMPTY)) {
                break;  // EDGE - the "ground" below is a lake's surface
            }

            int drop = nextAir.getY() - probe.getY();
            if (drop == 0) {
                trough.add(next);
                cursor = next;
                run++;
                continue;
            }

            // A terrace riser: cap the mouth of the closing segment at water
            // Y, andesite down the face, and resume on the lower floor.
            int lowerWaterY = probe.getY() - 1;
            risers.add(new Riser(new BlockPos(next.getX(), cursor.getY(), next.getZ()),
                    lowerWaterY));
            run++;

            // The pool budget is a cap, not a quota - flat ground never
            // spends it.
            if (pools.size() < legs - 1) {
                BlockPos poolCentre = new BlockPos(
                        next.getX() + (POOL_RADIUS + 1) * fx, lowerWaterY,
                        next.getZ() + (POOL_RADIUS + 1) * fz);
                if (bowlFits(level, window, poolCentre, POOL_RADIUS)) {
                    BlockPos poolMouth = new BlockPos(
                            poolCentre.getX() + (POOL_RADIUS + 1) * fx, lowerWaterY,
                            poolCentre.getZ() + (POOL_RADIUS + 1) * fz);
                    pools.add(new Pool(poolCentre, poolMouth));
                    trough.add(poolMouth);
                    tailStart = trough.size() - 1;
                    cursor = poolMouth;
                    run += 2 * (POOL_RADIUS + 1);
                    continue;
                }
            }

            // Validation failure degrades to a plain step - no bowl, no
            // guessing. The cursor stands on the riser column, which stays
            // solid; the tail restarts with the next wet cell.
            tailStart = trough.size();
            cursor = new BlockPos(next.getX(), lowerWaterY, next.getZ());
        }

        // The terminal basin: pulled back along the tail and shrunk toward
        // MIN_TERMINAL_RADIUS until everything holds. At an EDGE stop this is
        // the charter's image verbatim - water held at the edge of nothing,
        // its polished rim on the very lip, a still mirror.
        BlockPos basin = null;
        int basinR = 0;
        int basinMouth = -1;
        for (int m = trough.size() - 1; m >= tailStart && basin == null; m--) {
            BlockPos mouthCell = trough.get(m);
            if (tailStart == 0
                    && along(seat, mouthCell, fx, fz) <= SEAT_RADIUS + 2) {
                // The degenerate-course rule: the pull-back has reached the
                // seat's own rim, so the whole course collapses below.
                break;
            }
            for (int r = TERMINAL_RADIUS; r >= MIN_TERMINAL_RADIUS; r--) {
                BlockPos centre = mouthCell.offset((r + 1) * fx, 0, (r + 1) * fz);
                if (bowlFits(level, window, centre, r)) {
                    basin = centre;
                    basinR = r;
                    basinMouth = m;
                    break;
                }
            }
        }

        Plan plan = new Plan();

        if (basin == null) {
            // A spring that goes nowhere is a pool where it rose. Largest
            // radius that passes; if none passes, the site is refused with
            // zero writes.
            for (int r = TERMINAL_RADIUS; r >= MIN_TERMINAL_RADIUS; r--) {
                if (bowlFits(level, window, seat, r)) {
                    if (!planBowl(level, plan, seat, r)
                            || !plan.sealAir()
                            || !plan.clearOfStructures(level)) {
                        return null;
                    }
                    return plan;
                }
            }
            return null;
        }

        // Cells past the basin's mouth were surveyed and are not built - the
        // basin is the end of the course.
        trough.subList(basinMouth + 1, trough.size()).clear();

        // The junction mouths: exactly one rim cell per junction is
        // substituted by water, and they are the only cells allowed to
        // overwrite planned solid.
        Set<BlockPos> mouths = new HashSet<>();
        mouths.add(trough.get(0));
        for (Pool pool : pools) {
            mouths.add(pool.mouth());
        }
        mouths.add(trough.get(trough.size() - 1));

        // Materialise: bowls first so the mouths have a rim to substitute,
        // then the trough in course order, then the risers. Any disagreement
        // between elements - water where solid is planned, water at a
        // different Y, anything where the level holds fluid - fails the whole
        // survey here, with zero writes.
        if (!planBowl(level, plan, seat, SEAT_RADIUS)) {
            return null;
        }
        for (Pool pool : pools) {
            if (!planBowl(level, plan, pool.centre(), POOL_RADIUS)) {
                return null;
            }
        }
        if (!planBowl(level, plan, basin, basinR)) {
            return null;
        }
        for (BlockPos cell : trough) {
            if (!planTroughCell(level, plan, cell, ax, az, mouths.contains(cell))) {
                return null;
            }
        }
        for (Riser riser : risers) {
            if (!planRiser(level, plan, riser)) {
                return null;
            }
        }
        if (!plan.sealAir()) {
            return null;
        }
        // The obelisk's lesson: check the footprint you will actually litter,
        // and over-cover vertically, which is the safe direction to be wrong
        // in.
        if (!plan.clearOfStructures(level)) {
            return null;
        }
        return plan;
    }

    /** Distance along the flow axis from the seat to a position. */
    private static int along(BlockPos seat, BlockPos pos, int fx, int fz) {
        return (pos.getX() - seat.getX()) * fx + (pos.getZ() - seat.getZ()) * fz;
    }

    /** The owning chunk plus 16 blocks on every side - the safe write window. */
    private static Window windowOf(BlockPos origin) {
        int chunkX = SectionPos.blockToSectionCoord(origin.getX());
        int chunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        return new Window((chunkX << 4) - 16, (chunkZ << 4) - 16,
                (chunkX << 4) + 31, (chunkZ << 4) + 31);
    }

    /**
     * Whether a bowl of this radius, water at the centre's Y, fits here.
     * Read-only. The footing here is disc-shaped where the seat's is square,
     * because a lip basin's corners hang over the void by design - the rim
     * rule below is what holds the lip up.
     */
    private static boolean bowlFits(WorldGenLevel level, Window window,
                                    BlockPos centre, int r) {
        if (level.isOutsideBuildHeight(centre.below(2))
                || level.isOutsideBuildHeight(centre.above(3))) {
            return false;
        }
        if (!window.containsDisc(centre.getX(), centre.getZ(), r + 1)) {
            return false;
        }
        if (!discIsLevel(level, centre, r)) {
            return false;
        }
        if (!RuinGround.isDry(level, centre, r + 1, 0, 2)) {
            return false;
        }
        return rimRests(level, centre, r);
    }

    /**
     * The one geometry law: over the water disc the ground's top solid block
     * sits exactly at water Y, with ground under it and the mirror open
     * above. This is also the footing over the disc - a one-block crust over
     * a cave answers no.
     */
    private static boolean discIsLevel(WorldGenLevel level, BlockPos centre, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                BlockPos at = centre.offset(dx, 0, dz);
                if (level.getBlockState(at).isAir()
                        || !level.getFluidState(at).is(Fluids.EMPTY)) {
                    return false;
                }
                if (level.getBlockState(at.below()).isAir()
                        || !level.getFluidState(at.below()).is(Fluids.EMPTY)) {
                    return false;
                }
                if (!level.getBlockState(at.above()).isAir()
                        || !level.getBlockState(at.above(2)).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The rim rule: every rim column must find solid within
     * {@link #RIM_DEPTH} below its rim cell, and no fluid anywhere it would
     * stand. A rim column hanging over nothing refuses the candidate, which
     * is what pulls a basin back from the very edge one block at a time.
     */
    private static boolean rimRests(WorldGenLevel level, BlockPos centre, int r) {
        int outer = r + 1;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= r * r || d2 > outer * outer) {
                    continue;
                }
                BlockPos rim = centre.offset(dx, 0, dz);
                if (!level.getFluidState(rim).is(Fluids.EMPTY)) {
                    return false;
                }
                if (!level.getBlockState(rim).isAir()) {
                    continue;  // the terrain carries its own lip
                }
                boolean rests = false;
                for (int down = 1; down <= RIM_DEPTH; down++) {
                    BlockPos under = rim.below(down);
                    if (!level.getFluidState(under).is(Fluids.EMPTY)) {
                        break;
                    }
                    if (!level.getBlockState(under).isAir()) {
                        rests = true;
                        break;
                    }
                }
                if (!rests) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * One bowl: water where {@code dx*dx + dz*dz <= r*r}, a liner under every
     * water cell, and the rim ring where {@code r*r < d2 <= (r+1)*(r+1)} - the
     * worked lip, polished, the {@code OctiaBeacon} plinth register. Never the
     * frame panel: panels are hull and obelisk masonry, and a basin is
     * groundwork. The ring is also the {@link #EDGE_GAP}: every cardinal
     * neighbour of a water cell is water or ring, by arithmetic.
     */
    private static boolean planBowl(WorldGenLevel level, Plan plan, BlockPos centre, int r) {
        int outer = r + 1;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > outer * outer) {
                    continue;
                }
                BlockPos at = centre.offset(dx, 0, dz);
                if (d2 <= r * r) {
                    if (!plan.water(level, at, false)
                            || !plan.solid(level, at.below(),
                                    Blocks.ANDESITE.defaultBlockState())) {
                        return false;
                    }
                } else if (!planRimColumn(level, plan, at)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * One rim column: polished at water Y, and where the terrain does not
     * reach it, plain andesite down to the first solid - the little retaining
     * wall that lets a rim stand on a lip.
     */
    private static boolean planRimColumn(WorldGenLevel level, Plan plan, BlockPos rim) {
        if (!plan.solid(level, rim, Blocks.POLISHED_ANDESITE.defaultBlockState())) {
            return false;
        }
        if (!level.getBlockState(rim).isAir()) {
            return true;  // the swap of the ground's own top cell is the whole write
        }
        for (int down = 1; down <= RIM_DEPTH; down++) {
            BlockPos under = rim.below(down);
            if (!level.getFluidState(under).is(Fluids.EMPTY)) {
                return false;
            }
            if (!level.getBlockState(under).isAir()) {
                return true;
            }
            if (!plan.solid(level, under, Blocks.ANDESITE.defaultBlockState())) {
                return false;
            }
        }
        // Hanging over nothing - rimRests refuses such a candidate before it
        // gets here, so reaching this line is a disagreement, not a shrug.
        return false;
    }

    /**
     * One trough cell: water at Y, liner below, edging at Y both sides where
     * the terrain there is not already solid. A mouth cell may substitute a
     * planned rim cell - the one designed exception to "no element overwrites
     * another".
     */
    private static boolean planTroughCell(WorldGenLevel level, Plan plan, BlockPos at,
                                          int ax, int az, boolean mouth) {
        if (!plan.water(level, at, mouth)
                || !plan.solid(level, at.below(), Blocks.ANDESITE.defaultBlockState())) {
            return false;
        }
        return planEdge(level, plan, at.offset(ax, 0, az))
                && planEdge(level, plan, at.offset(-ax, 0, -az));
    }

    /** One edging cell, only where the terrain is not already the seal. */
    private static boolean planEdge(WorldGenLevel level, Plan plan, BlockPos side) {
        if (!level.getFluidState(side).is(Fluids.EMPTY)) {
            return false;  // foreign fluid against the runnel declines the site
        }
        if (!level.getBlockState(side).isAir()) {
            return true;
        }
        return plan.solid(level, side, Blocks.ANDESITE.defaultBlockState());
    }

    /**
     * One riser: an andesite cap at the closing segment's water Y, and
     * andesite down the face to the lower floor's own top course.
     */
    private static boolean planRiser(WorldGenLevel level, Plan plan, Riser riser) {
        BlockPos cap = riser.cap();
        if (!plan.solid(level, cap, Blocks.ANDESITE.defaultBlockState())) {
            return false;
        }
        for (int y = cap.getY() - 1; y > riser.lowerWaterY(); y--) {
            if (!plan.solid(level, new BlockPos(cap.getX(), y, cap.getZ()),
                    Blocks.ANDESITE.defaultBlockState())) {
                return false;
            }
        }
        return true;
    }

    /** A terrace step: the cap column, and the water level of the floor below it. */
    private record Riser(BlockPos cap, int lowerWaterY) {
    }

    /** An intermediate bowl: its centre at water Y, and its downhill mouth. */
    private record Pool(BlockPos centre, BlockPos mouth) {
    }

    /** The safe write window, as a rectangle of block columns. */
    private record Window(int minX, int minZ, int maxX, int maxZ) {

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean containsDisc(int cx, int cz, int r) {
            return contains(cx - r, cz - r) && contains(cx + r, cz + r);
        }
    }

    /**
     * The plan: every cell the carve will touch, checked as it is entered.
     * Three disjoint sets - masonry, sources, air - and the entry methods are
     * where the agreement rules live: water-on-water at the same Y agrees,
     * solid-on-solid agrees (the later element's register wins), the junction
     * mouth is the one water-over-solid, and everything else is a
     * disagreement that fails the survey. A cell where the level holds fluid
     * fails it too, so a vanilla lake, a vanilla trickle or a neighbouring
     * chunk's earlier watershed declines the site rather than unsealing
     * anything.
     */
    private static final class Plan {

        private final Map<BlockPos, BlockState> masonry = new LinkedHashMap<>();
        private final Map<Long, BlockPos> sources = new LinkedHashMap<>();
        private final List<BlockPos> airs = new ArrayList<>();

        private static long column(BlockPos pos) {
            return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
        }

        boolean water(WorldGenLevel level, BlockPos pos, boolean mouth) {
            BlockPos already = sources.get(column(pos));
            if (already != null) {
                // Water-on-water at the same Y agrees; at a different Y it is
                // two courses fighting over one column, and the site refuses.
                return already.getY() == pos.getY();
            }
            if (masonry.containsKey(pos)) {
                if (!mouth) {
                    return false;
                }
                masonry.remove(pos);
            }
            if (!level.getFluidState(pos).is(Fluids.EMPTY)) {
                return false;
            }
            sources.put(column(pos), pos);
            return true;
        }

        boolean solid(WorldGenLevel level, BlockPos pos, BlockState state) {
            BlockPos wet = sources.get(column(pos));
            if (wet != null && wet.getY() == pos.getY()) {
                return false;  // solid where water is planned is a disagreement
            }
            if (!level.getFluidState(pos).is(Fluids.EMPTY)) {
                return false;
            }
            masonry.put(pos, state);
            return true;
        }

        /** Air over every mirror, Y+1 and Y+2, and nothing solid may want those cells. */
        boolean sealAir() {
            for (BlockPos pos : sources.values()) {
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos above = pos.above(dy);
                    if (masonry.containsKey(above)) {
                        return false;
                    }
                    airs.add(above);
                }
            }
            return true;
        }

        /** The plan's true bounding box, offered to the structure check. */
        boolean clearOfStructures(WorldGenLevel level) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : masonry.keySet()) {
                minX = Math.min(minX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                minY = Math.min(minY, pos.getY());
                maxX = Math.max(maxX, pos.getX());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            for (BlockPos pos : sources.values()) {
                minX = Math.min(minX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                minY = Math.min(minY, pos.getY());
                maxX = Math.max(maxX, pos.getX());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            int cx = Math.floorDiv(minX + maxX, 2);
            int cz = Math.floorDiv(minZ + maxZ, 2);
            return RuinGround.clearOfStructures(level, new BlockPos(cx, minY, cz),
                    Math.max(cx - minX, maxX - cx),
                    Math.max(cz - minZ, maxZ - cz), 4);
        }

        /**
         * The write, which cannot fail. Order is the law, and it is
         * {@code RuinGround.put}'s own javadoc applied: flags do not stop a
         * live level running {@code onPlace}, so order does - all solid
         * first, then all water, then the air clears. On a WorldGenRegion the
         * order is belt and braces; on a live level - the carve seam under a
         * gametest, a future /place - it is what keeps a source from ever
         * existing unsealed for even one tick.
         */
        void write(WorldGenLevel level) {
            for (Map.Entry<BlockPos, BlockState> entry : masonry.entrySet()) {
                RuinGround.put(level, entry.getKey(), entry.getValue());
            }
            for (BlockPos pos : sources.values()) {
                RuinGround.put(level, pos, Blocks.WATER.defaultBlockState());
            }
            for (BlockPos pos : airs) {
                RuinGround.put(level, pos, Blocks.AIR.defaultBlockState());
            }
        }
    }
}
