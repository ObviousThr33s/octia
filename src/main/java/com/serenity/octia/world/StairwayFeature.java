package com.serenity.octia.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * A switchback stair: worn andesite flights cut into a terrace riser or a
 * void-edge cliff, turning back on themselves as they climb.
 *
 * <p><b>Why an ascent half exists at all.</b> The sail-rig owns descent, and
 * descent is free - you commit to the drop and the world pays nothing for it.
 * Ascent is the half that had to be answered in stone, because the 8/23 session
 * settled how it must not be answered: sustained fast horizontal travel outruns
 * the chunk unloader, and no mechanic in this mod may enable it. A stairway is
 * that law wearing masonry. Height is regained on foot, one block per step, and
 * nobody has ever outrun a chunk unloader on a staircase.
 *
 * <p><b>History in the ground.</b> The wear mirrors {@link ObeliskFeature}'s
 * break odds exactly, and means the same thing: the thread shows in what is
 * still whole. Distance to the {@link Sightlines} leg decides the stairway's
 * <em>condition</em>, never its existence - {@value #WORN_IN_NEAR} in
 * {@value #ODDS} within {@link Sightlines#CORRIDOR} blocks of the line,
 * {@value #WORN_IN_FAR} in {@value #ODDS} beyond it. A maintained stair near
 * the thread, a truncated stub far from it, and
 * {@code /place feature octia:stairway} succeeds anywhere the survey passes.
 * Making the corridor a gate instead would quietly re-tune the density by a
 * factor nobody wrote down, which is the same trap the obelisk's javadoc names.
 *
 * <p><b>One site, one chunk.</b> Everything is built from the single owning
 * chunk: {@link #anchorFor} slides the anchor along both axes until the whole
 * write box lies inside the chunk of the origin, so no write ever leaves the
 * safe window and no two chunks can disagree about one stairway. The box is at
 * most 7 x 9 columns, so the clamp always succeeds. A clamp that moved the
 * anchor is deliberately not re-probed - the survey is the verifier, and a
 * stair the clamp pushed fully inside the hill becomes a carved trench-stair,
 * which is an acceptable reading of "cut into the riser".
 *
 * <p><b>It never reports to {@link RuinRegistry}.</b> A stairway is a path, not
 * a landmark - you find one by arriving at the ground that needed it, not by
 * asking where they are. The note on {@code OctiaWorldgen}'s ARCH constant is
 * the precedent: a kind string for a feature that never reports would advertise
 * a key that answers with an empty list.
 *
 * <p><b>No new salt.</b> The hash-under-salt pattern in {@link Sightlines} and
 * {@code Beamline} exists for answers two chunks must agree on. This feature
 * has none - one site, one chunk, every roll local - so all randomness comes
 * from the context's own random, the obelisk's idiom, and inventing a salt here
 * would be ceremony pretending to be coordination.
 *
 * <p><b>The placed JSON's chance is 260, and JSON carries no comments, so the
 * note lives here: provisional - owner tunes by walking the world.</b> Half
 * the obelisk's 520, opened rather than tightened, because the acceptance test
 * below refuses most ground the obelisk would take - flat is the common case -
 * and the real gating is the riser test, not the coarse dial.
 *
 * <p><b>Two Octia features can roll the same chunk.</b> An obelisk and a
 * stairway are unguarded against each other, exactly as the obelisk and the
 * derelict are unguarded against each other today. Named rather than solved -
 * that is an estate-wide question, not this feature's.
 */
public class StairwayFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * Steps per flight. Each step rises one block, so this is also the rise per
     * flight. provisional - owner tunes by walking the world
     */
    private static final int FLIGHT_STEPS = 5;

    /**
     * Fewer than two flights is not a switchback - there is no turn.
     * provisional - owner tunes by walking the world
     */
    private static final int MIN_FLIGHTS = 2;

    /**
     * Total rise bounded at twenty. provisional - owner tunes by walking the
     * world
     */
    private static final int MAX_FLIGHTS = 4;

    /** Each flight sits two blocks further into the face than the one below. */
    private static final int DEPTH_PER_FLIGHT = 2;

    /** Horizontal distance of the uphill probe. */
    private static final int PROBE = 6;

    /** How far up a probe's surface scan looks. */
    private static final int RISE_REACH = 24;

    /** How far down it looks. */
    private static final int DROP_REACH = 8;

    /**
     * Acceptance: less rise than this at {@value #PROBE} blocks is flat
     * ground, refused. This is the real gate; the JSON rarity is not.
     * provisional - owner tunes by walking the world
     */
    private static final int MIN_RISE = 8;

    /**
     * Deepest masonry allowed under any step. {@code FLIGHT_STEPS - 1} and not
     * a coincidence: on a sheer face met at depth two, the first flight's last
     * step stands exactly this far off the ground at the foot. The survey's
     * support walk reads one cell past this, because masonry {@value #UNDERPIN}
     * deep still needs something under it to rest on.
     */
    private static final int UNDERPIN = 4;

    /**
     * Air carved above every walking surface - stepping up moves the head
     * through the third cell.
     */
    private static final int HEADROOM = 3;

    /**
     * How likely wear is, on and off the thread. Out of {@value #ODDS}, the
     * mirror of {@code ObeliskFeature}'s break odds and carrying the same
     * meaning: the thread shows in what is still whole. provisional - owner
     * tunes by walking the world
     */
    private static final int ODDS = 8;
    private static final int WORN_IN_NEAR = 1;
    private static final int WORN_IN_FAR = 4;

    /**
     * The cardinals in a fixed order, so the acceptance scan's strict maximum
     * is a deterministic tie-break rather than an accident of iteration.
     */
    private static final Direction[] CARDINALS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public StairwayFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /**
     * The shape's numbers, for the gametests that have to walk what was built
     * rather than assume it. Same reasoning as {@code ObeliskFeature.across()}:
     * a test that hard-codes five passes against the wrong stair the day the
     * constant moves.
     */
    public static int flightSteps() {
        return FLIGHT_STEPS;
    }

    public static int maxFlights() {
        return MAX_FLIGHTS;
    }

    public static int headroom() {
        return HEADROOM;
    }

    public static int underpin() {
        return UNDERPIN;
    }

    /**
     * Which way is uphill, and how much rise the probe found there.
     *
     * <p>One probe test serves both named site types: a terrace riser and the
     * cliff above a void edge are each a measurable rise in some cardinal.
     */
    private record Ascent(Direction up, int rise) {
    }

    /**
     * A position in the stairway's own frame. {@code a} runs along the first
     * flight's travel, {@code d} steps into the face, {@code h} climbs. The
     * anchor is the cell of step (0,0): the free-surface cell at the foot,
     * where approaching feet stand on ground one below it.
     */
    private static BlockPos frame(BlockPos anchor, Direction up, Direction run0,
                                  int a, int d, int h) {
        return anchor.relative(run0, a).relative(up, d).above(h);
    }

    /** The way you walk while climbing flight {@code k}: back and forth. */
    private static Direction travel(Direction run0, int flight) {
        return (flight & 1) == 0 ? run0 : run0.getOpposite();
    }

    /**
     * Where step {@code step} of flight {@code flight} sits. Public so the
     * gametests walk the shape through the same arithmetic that built it,
     * instead of re-deriving the formulas and passing against a different
     * stair.
     */
    public static BlockPos stepAt(BlockPos anchor, Direction up, Direction run0,
                                  int flight, int step) {
        int a = (flight & 1) == 0 ? step : FLIGHT_STEPS - 1 - step;
        return frame(anchor, up, run0, a, DEPTH_PER_FLIGHT * flight,
                flight * FLIGHT_STEPS + step);
    }

    /**
     * One cell of flight {@code flight}'s landing, {@code cell} 0 to 2 walking
     * into the face. The bay column sits past the flight's last step, level
     * with it, and the next flight's first step is one up in the adjacent
     * column - continuity is exact, and the top landing's far cell delivers
     * onto the crest.
     */
    public static BlockPos landingAt(BlockPos anchor, Direction up, Direction run0,
                                     int flight, int cell) {
        int bay = (flight & 1) == 0 ? FLIGHT_STEPS : -1;
        return frame(anchor, up, run0, bay, DEPTH_PER_FLIGHT * flight + cell,
                (flight + 1) * FLIGHT_STEPS - 1);
    }

    /**
     * The box every write falls inside: {@code a} in [-1, {@value
     * #FLIGHT_STEPS}], {@code d} in [0, 2 * flights], {@code h} from the
     * deepest underpin to the shoring cell over the top landing's headroom.
     */
    public static BoundingBox writeBox(BlockPos anchor, Direction up, Direction run0,
                                       int flights) {
        BlockPos one = frame(anchor, up, run0, -1, 0, -UNDERPIN);
        BlockPos two = frame(anchor, up, run0, FLIGHT_STEPS, DEPTH_PER_FLIGHT * flights,
                flights * FLIGHT_STEPS - 1 + HEADROOM + 1);
        return BoundingBox.fromCorners(one, two);
    }

    /**
     * The chunk clamp: the origin's column, slid along both axes until the
     * whole write box lies inside the chunk of {@code origin}. Pure XZ
     * arithmetic, Y passed through - the box's world extent is separable per
     * axis because both frame axes are cardinals. Chunk identity goes through
     * {@link SectionPos#blockToSectionCoord}, never division, because integer
     * division lies about negative coordinates - {@code ArchFeature}'s note.
     * The box is at most 7 x 9 columns, so the clamp always succeeds.
     */
    public static BlockPos anchorFor(BlockPos origin, Direction up, Direction run0,
                                     int flights) {
        BoundingBox box = writeBox(origin, up, run0, flights);
        int minX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX()));
        int minZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ()));
        return origin.offset(
                shiftInto(box.minX(), box.maxX(), minX, minX + 15),
                0,
                shiftInto(box.minZ(), box.maxZ(), minZ, minZ + 15));
    }

    /** How far one axis must slide so [lo, hi] sits inside [min, max]. */
    private static int shiftInto(int lo, int hi, int min, int max) {
        if (lo < min) {
            return min - lo;
        }
        if (hi > max) {
            return max - hi;
        }
        return 0;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();

        // The wide form: the heightmap may have put the origin on a canopy or
        // worse, and eight blocks either way finds the real foot or refuses.
        BlockPos surface0 = RuinGround.surfaceNear(level, context.origin(), 8, 8);
        if (surface0 == null) {
            return false;
        }

        // The acceptance. Flat ground is refused here, which is the real gate
        // the JSON rarity is not.
        Ascent asc = ascent(level, surface0);
        if (asc == null) {
            return false;
        }

        // Square over-cover of an oriented box - the safe direction to be
        // wrong in, per the obelisk's own comment. A stairway into somebody's
        // village square is as wrong as an obelisk there, and it is refused
        // before any roll.
        if (!RuinGround.clearOfStructures(level, surface0.below(1),
                FLIGHT_STEPS, FLIGHT_STEPS, MAX_FLIGHTS * FLIGHT_STEPS + HEADROOM)) {
            return false;
        }

        int plan = Math.max(MIN_FLIGHTS,
                Math.min(MAX_FLIGHTS, asc.rise() / FLIGHT_STEPS));

        // Corridor weight, never a gate - exactly the obelisk's construction.
        // Distance to the leg decides the stairway's condition: a worn one
        // keeps some strict prefix of its planned flights, and the built part
        // is still sound.
        Sightlines.Leg leg = Sightlines.legAt(level.getSeed(), surface0.getX(), surface0.getZ());
        boolean worn = random.nextInt(ODDS)
                < (leg.distanceToLine(surface0.getX(), surface0.getZ()) <= Sightlines.CORRIDOR
                        ? WORN_IN_NEAR : WORN_IN_FAR);
        int target = worn ? 1 + random.nextInt(plan - 1) : plan;

        Direction run0 = random.nextBoolean()
                ? asc.up().getClockWise() : asc.up().getCounterClockWise();

        // Clamped for the target - a smaller box fits a fortiori. If the clamp
        // moved the column, the foot's height is re-read where the foot now
        // is; the probed column's answer does not transfer sideways.
        BlockPos anchor = anchorFor(surface0, asc.up(), run0, target);
        if (anchor.getX() != surface0.getX() || anchor.getZ() != surface0.getZ()) {
            anchor = RuinGround.surfaceNear(level, anchor, 8, 8);
            if (anchor == null) {
                return false;
            }
        }

        // The survey ladder: the obelisk's settle-height-before-footing
        // principle, plus one concession to blocky terrain - a crest two
        // blocks shorter than planned should shorten the stair, not erase it.
        // Found, not installed. At most four read-only surveys, bounded.
        for (int f = target; f >= 1; f--) {
            if (raise(level, anchor, asc.up(), run0, f, worn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which cardinal is uphill, and by how much.
     *
     * <p>Each cardinal is probed {@value #PROBE} blocks out with a surface scan
     * reaching {@value #RISE_REACH} up and {@value #DROP_REACH} down. Strict
     * greater-than keeps the first maximum in {@link #CARDINALS} order, so the
     * tie-break is deterministic. All four probes failing means a pillar in
     * the void; a best rise under {@value #MIN_RISE} means flat ground. Both
     * answer null, and the site is refused.
     */
    private static Ascent ascent(WorldGenLevel level, BlockPos surface0) {
        Direction bestUp = null;
        int bestRise = Integer.MIN_VALUE;
        for (Direction dir : CARDINALS) {
            BlockPos probe = RuinGround.surfaceNear(level,
                    surface0.relative(dir, PROBE), RISE_REACH, DROP_REACH);
            if (probe == null) {
                continue;
            }
            int rise = probe.getY() - surface0.getY();
            if (rise > bestRise) {
                bestRise = rise;
                bestUp = dir;
            }
        }
        if (bestUp == null || bestRise < MIN_RISE) {
            return null;
        }
        return new Ascent(bestUp, bestRise);
    }

    /**
     * Builds a stairway on a known anchor, climbing a known way.
     *
     * <p>Public and separate from {@link #place} for the reason
     * {@code ArchFeature.raise} gives verbatim: <em>where a site is</em> and
     * <em>what a stairway looks like</em> are different jobs, and the terrain
     * gate makes the shape untestable otherwise - the gametests drive this
     * directly with axes they chose. No {@code RandomSource} parameter,
     * because nothing inside is a roll: the wear was decided by the caller.
     * No {@code active()} gate here either, matching {@code ArchFeature.raise}.
     *
     * @param anchor the cell of step (0,0) - the free surface at the foot
     * @param up     uphill, into the riser
     * @param run0   the first flight's travel, perpendicular to {@code up}
     * @param worn   whether the top landing's far cell sags to a slab
     * @return whether the ground took it
     */
    public static boolean raise(WorldGenLevel level, BlockPos anchor,
                                Direction up, Direction run0, int flights, boolean worn) {
        if (!survey(level, anchor, up, run0, flights)) {
            return false;
        }
        build(level, anchor, up, run0, flights, worn);
        return true;
    }

    /** Every step and landing cell, the cells a climber's feet actually use. */
    private static List<BlockPos> walkingCells(BlockPos anchor, Direction up,
                                               Direction run0, int flights) {
        List<BlockPos> cells = new ArrayList<>(flights * (FLIGHT_STEPS + 3));
        for (int k = 0; k < flights; k++) {
            for (int i = 0; i < FLIGHT_STEPS; i++) {
                cells.add(stepAt(anchor, up, run0, k, i));
            }
            for (int c = 0; c <= 2; c++) {
                cells.add(landingAt(anchor, up, run0, k, c));
            }
        }
        return cells;
    }

    /**
     * Read-only. Whether every walking cell is inside build height with its
     * headroom, fluid-free, and supported within {@value #UNDERPIN} cells of
     * masonry with no fluid on the way down.
     *
     * <p><b>Deliberately not {@code RuinGround.isDry}.</b> That helper checks
     * a square, this footprint is an oriented rectangle, and the over-cover
     * would refuse shoreline cliffs the stair never touches.
     *
     * <p><b>Deliberately not {@code RuinGround.hasFooting}.</b> The stairway
     * does not stand on one plane - each step carries its own footing
     * question, and the per-cell walk below <em>is</em> that question.
     */
    private static boolean survey(WorldGenLevel level, BlockPos anchor,
                                  Direction up, Direction run0, int flights) {
        for (BlockPos cell : walkingCells(anchor, up, run0, flights)) {
            if (!surveyCell(level, cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One cell's three questions. The support walk reads {@value #UNDERPIN}
     * plus one cells: the air prefix is what the underpin will fill, at most
     * {@value #UNDERPIN} deep, and the cell after it is what the masonry
     * rests on. Fluid anywhere in the walk refuses the cell - a pier standing
     * in water is a different object than a stair.
     */
    private static boolean surveyCell(WorldGenLevel level, BlockPos cell) {
        if (level.isOutsideBuildHeight(cell)
                || level.isOutsideBuildHeight(cell.above(HEADROOM))) {
            return false;
        }
        if (RuinGround.submerged(level, cell)) {
            return false;
        }
        BlockPos p = cell.below();
        for (int reach = 0; reach <= UNDERPIN; reach++) {
            if (level.isOutsideBuildHeight(p)) {
                return false;
            }
            if (!level.getFluidState(p).is(Fluids.EMPTY)) {
                return false;
            }
            if (!level.getBlockState(p).isAir()) {
                return true;
            }
            p = p.below();
        }
        return false;
    }

    /**
     * The writes, in a fixed order: underpins bottom-up per column, landings,
     * steps, the headroom carve, shoring, threshold.
     *
     * <p>The order is cosmetic in a {@code WorldGenRegion} but load-bearing on
     * a live level - {@code RuinGround.put}'s javadoc: a {@code ServerLevel}
     * still runs {@code onPlace} regardless of flags, so support is written
     * before the thing it supports.
     *
     * <p>Stairs face the way you walk while climbing, and HALF and SHAPE stay
     * default. Flag-2 writes in a region never recalculate SHAPE, and the
     * butt-jointed corners at the bays are the worn register, wanted.
     */
    private static void build(WorldGenLevel level, BlockPos anchor,
                              Direction up, Direction run0, int flights, boolean worn) {
        List<BlockPos> cells = walkingCells(anchor, up, run0, flights);

        for (BlockPos cell : cells) {
            underpin(level, cell);
        }

        for (int k = 0; k < flights; k++) {
            for (int c = 0; c <= 2; c++) {
                // The worn lip: the top landing's farthest cell crumbles to a
                // bottom slab - a half-block sag off the path, never on it.
                boolean lip = worn && k == flights - 1 && c == 2;
                RuinGround.put(level, landingAt(anchor, up, run0, k, c),
                        lip ? Blocks.ANDESITE_SLAB.defaultBlockState()
                                : Blocks.ANDESITE.defaultBlockState());
            }
        }

        for (int k = 0; k < flights; k++) {
            Direction facing = travel(run0, k);
            for (int i = 0; i < FLIGHT_STEPS; i++) {
                RuinGround.put(level, stepAt(anchor, up, run0, k, i),
                        Blocks.ANDESITE_STAIRS.defaultBlockState()
                                .setValue(StairBlock.FACING, facing));
            }
        }

        // The carve is what cuts the switchback into the face. No walking cell
        // shares a column with another, so no carve can erase a step.
        for (BlockPos cell : cells) {
            for (int h = 1; h <= HEADROOM; h++) {
                RuinGround.put(level, cell.above(h), Blocks.AIR.defaultBlockState());
            }
        }

        // Shoring: flag 2 fires no updates, so gravel over the carve would
        // hang forever. A lintel instead - floaters are outlawed.
        for (BlockPos cell : cells) {
            BlockPos over = cell.above(HEADROOM + 1);
            if (level.getBlockState(over).getBlock() instanceof FallingBlock) {
                RuinGround.put(level, over, Blocks.ANDESITE.defaultBlockState());
            }
        }

        // The threshold, cosmetic and unsurveyed: a slab at the foot if the
        // ground happens to take one, skipped silently otherwise.
        BlockPos threshold = frame(anchor, up, run0, -1, 0, 0);
        if (level.getBlockState(threshold).isAir()
                && !level.getBlockState(threshold.below()).isAir()) {
            RuinGround.put(level, threshold, Blocks.ANDESITE_SLAB.defaultBlockState());
        }
    }

    /**
     * Masonry under one cell, bottom-up, at most {@value #UNDERPIN} deep. The
     * survey already proved solid is there, so the loop always finds its rest.
     * No terraforming beyond this: a foundation, not a scar.
     */
    private static void underpin(WorldGenLevel level, BlockPos cell) {
        int depth = 0;
        while (depth < UNDERPIN && level.getBlockState(cell.below(depth + 1)).isAir()) {
            depth++;
        }
        for (int d = depth; d >= 1; d--) {
            RuinGround.put(level, cell.below(d), Blocks.ANDESITE.defaultBlockState());
        }
    }
}
