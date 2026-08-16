package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An obelisk: a large andesite prism, standing over a dig, laid along a thread.
 *
 * <p><b>Why a second ruin at all.</b> One structure type is a curiosity; two are
 * a culture. The derelict says a ship came and failed - which only means
 * something if there is separate evidence that somebody was here on purpose.
 * An obelisk is that evidence: masonry raised deliberately over a dig, in the
 * same andesite frame panels the hulls are built from, by whoever was calling
 * ships in the first place.
 *
 * <p><b>It was a column and it is now a prism.</b> One block square is a fence
 * post. It read as a marker only because nothing else in the world was that
 * shape, and at any distance where an obelisk is supposed to do its job - the
 * thing you see across a valley and decide to walk toward - a 1x1 column is one
 * pixel wide and gone. A {@value #ACROSS}x{@value #ALONG} prism reaching
 * {@value #MIN_HEIGHT} to {@value #MAX_HEIGHT} blocks is a silhouette, and a
 * silhouette has an orientation, which is what the rest of this class is about.
 *
 * <p><b>It points.</b> {@link Sightlines} lays a lattice of nodes over the world
 * and gives each one a next node; the prism's long axis lies along the leg
 * between them, and the {@link #slot} bored through it at eye level is a line of
 * sight down that leg. Stand at one end, look through, and you are looking the
 * way the thread runs. Nothing in a vanilla world does this - the note on
 * {@code Sightlines} has the survey of the saves that establishes it - so the
 * alignment is the one piece of evidence in the terrain that these were placed
 * by somebody rather than scattered.
 *
 * <p><b>The thread shows in what is still standing, not in what exists.</b>
 * Distance to the leg decides how likely an obelisk is to have stayed up, and
 * nothing else: {@value #BROKEN_IN_NEAR} in {@value #ODDS} within
 * {@link Sightlines#CORRIDOR} blocks of the line, {@value #BROKEN_IN_FAR} in
 * {@value #ODDS} beyond it.
 *
 * <p><b>How strong that signal actually is, measured rather than asserted.</b>
 * A leg is a line through a cell 512 blocks across, so the corridor is a thin
 * ribbon: {@code Sweep} puts it at <b>12.66% of the ground</b> over sixteen
 * million sampled points. Run the odds through that and a standing obelisk is
 * only about <b>20% likely to be on a thread</b> - better than the 12.66% base
 * rate, and nowhere near proof. Roughly <b>45% of all obelisks are broken</b>.
 *
 * <p>So "the lit ones trace the route" is a tendency and not an inference, and
 * this javadoc used to claim more than the arithmetic supports. What a player
 * can actually do is read a <em>run</em> of them - several standing obelisks
 * lying along one bearing is strong, any single one is weak. Whether that is the
 * design or whether the corridor wants widening is open; see ROADMAP.
 *
 * <p>It is deliberately not a filter on placement: an obelisk that could only
 * generate inside a corridor would make {@code /place feature octia:obelisk}
 * fail nine times in ten and would quietly re-tune the density in
 * {@code placed_feature/obelisk.json} by a factor nobody wrote down.
 *
 * <p><b>Deliberately not a ship.</b> There is no core in it and there never
 * should be. {@link com.serenity.octia.ship.ShipCoreBlock} is the mod's one
 * piece of machinery and the moorings store is its spine; a landmark that
 * quietly moored itself would put positions into that store that no player ever
 * built and no player can unbuild. An obelisk is masonry. It stays out of the
 * store entirely.
 */
public class ObeliskFeature extends Feature<NoneFeatureConfiguration> {

    /** Footprint across the thread, and along it. Odd, so there is a centre line. */
    private static final int ACROSS = 3;
    private static final int ALONG = 5;

    /** Shortest and tallest a standing obelisk gets, before the break. */
    private static final int MIN_HEIGHT = 9;
    private static final int MAX_HEIGHT = 13;

    /** How far the plinth reaches past the prism on every side. */
    private static final int PLINTH = 1;

    /**
     * The sighting slot: a one-wide gap bored the whole length of the prism, at
     * the height of a player's eyes standing on the plinth. Two courses rather
     * than one because a single course is at eye level only when the ground
     * under your feet is exactly the ground under the plinth, which outside a
     * gametest it never is.
     */
    private static final int SLOT_LOW = 2;
    private static final int SLOT_HIGH = 3;

    /** Digs sit off the plinth and inside a comfortable walk of the foot. */
    private static final int DIG_MIN = 4;
    private static final int DIG_MAX = 6;

    /** How likely a break is, on and off the thread. Out of {@value #ODDS}. */
    private static final int ODDS = 8;
    private static final int BROKEN_IN_NEAR = 1;
    private static final int BROKEN_IN_FAR = 4;

    /**
     * A broken one is never cut below this. The slot has to survive the break -
     * a stump whose sighting line has gone is a rock, and the whole point of the
     * broken variant is that it still says which way the thread ran, it just no
     * longer says it in light.
     */
    private static final int MIN_BROKEN_HEIGHT = SLOT_HIGH + 2;

    public ObeliskFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /**
     * The prism's footprint and the height of its slot, for the gametests that
     * have to walk the shape rather than assume it. Same reasoning as
     * {@code DerelictFeature.sink()}: a test that hard-codes 3 and 5 passes
     * against the wrong building the day either constant moves.
     */
    public static int across() {
        return ACROSS;
    }

    public static int along() {
        return ALONG;
    }

    public static int slotLow() {
        return SLOT_LOW;
    }

    public static int slotHigh() {
        return SLOT_HIGH;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();

        // Seated one down so the plinth is flush with the ground rather than
        // perched on it. Same reasoning as the derelict: found, not installed.
        BlockPos base = context.origin().below(1);

        Sightlines.Leg leg = Sightlines.legAt(level.getSeed(), base.getX(), base.getZ());
        boolean alongX = leg.heading() == Sightlines.Heading.EAST
                || leg.heading() == Sightlines.Heading.WEST;
        int toward = leg.heading() == Sightlines.Heading.EAST
                || leg.heading() == Sightlines.Heading.SOUTH ? 1 : -1;

        int half = ALONG / 2;
        int side = ACROSS / 2;
        int radiusX = (alongX ? half : side) + PLINTH;
        int radiusZ = (alongX ? side : half) + PLINTH;

        // The height is settled before the footing is checked, not after, so the
        // headroom asked for is the headroom about to be used. Getting that
        // backwards is what let an obelisk pass the check and then lose its
        // crown to the build ceiling.
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        boolean broken = random.nextInt(ODDS)
                < (leg.distanceToLine(base.getX(), base.getZ()) <= Sightlines.CORRIDOR
                        ? BROKEN_IN_NEAR : BROKEN_IN_FAR);
        if (broken) {
            height = Math.max(MIN_BROKEN_HEIGHT, height - (3 + random.nextInt(4)));
        }

        if (!RuinGround.hasFooting(level, base.below(1), radiusX, radiusZ, height + 1)) {
            return false;
        }

        plinth(level, base, radiusX, radiusZ);
        prism(level, base, alongX, height, broken);
        if (!broken) {
            stripe(level, base, alongX, toward, height);
        }
        slot(level, base, alongX);
        if (broken) {
            erode(level, random, base, alongX, height);
        }

        RuinGround.dig(level, random, base, DIG_MIN, DIG_MAX, 2 + random.nextInt(3));

        if (broken) {
            fallen(level, random, base);
        }
        return true;
    }

    /** A pad one block proud of the prism, so it reads as founded, not stuck in. */
    private static void plinth(WorldGenLevel level, BlockPos base, int radiusX, int radiusZ) {
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                RuinGround.put(level, base.offset(dx, 0, dz),
                        OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }

    /**
     * The prism itself, solid, from the plinth up.
     *
     * <p>The top course is the crown at {@link PanelLight#STYLED}, light 15 and
     * the brightest thing the mod owns, over a collar of {@link PanelLight#GENERIC}.
     * A broken one gets neither, and that asymmetry is the whole contract: a lit
     * mass on the horizon is a promise that something is still standing, and a
     * stump must not be able to make it.
     */
    private static void prism(WorldGenLevel level, BlockPos base, boolean alongX,
                              int height, boolean broken) {
        int half = ALONG / 2;
        int side = ACROSS / 2;

        for (int y = 1; y <= height; y++) {
            PanelLight light = broken ? PanelLight.NONE
                    : (y == height ? PanelLight.STYLED
                            : (y == height - 1 ? PanelLight.GENERIC : PanelLight.NONE));

            for (int a = -half; a <= half; a++) {
                for (int c = -side; c <= side; c++) {
                    RuinGround.put(level, base.offset(alongX ? a : c, y, alongX ? c : a),
                            OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                                    .setValue(AndesiteFramePanelBlock.LIGHT, light));
                }
            }
        }
    }

    /**
     * A band up the face that points down the thread.
     *
     * <p>The crown says "something is standing here" to anyone who can see the
     * top of it. This says "and it runs that way" to somebody who has already
     * walked up to it, which is a different job and wants a different mark -
     * visible from one side only, at ground level, on the end the leg leaves by.
     * The slot's mouth is bored through the middle of it afterwards, so what is
     * left is a frame around the hole you are meant to look through.
     *
     * <p>Only a standing one gets it. {@link PanelLight#GENERIC} is light 7, so
     * a stripe on a broken obelisk would be a lit mark on a ruin that is
     * supposed to have lost its light - the caller skips this rather than
     * passing a flag, because an unlit stripe is indistinguishable from the
     * prism it is cut into and would be a no-op with a comment on it.
     */
    private static void stripe(WorldGenLevel level, BlockPos base, boolean alongX,
                               int toward, int height) {
        int end = (ALONG / 2) * toward;
        int top = Math.min(height - 2, SLOT_HIGH + 2);

        for (int y = 1; y <= top; y++) {
            RuinGround.put(level, base.offset(alongX ? end : 0, y, alongX ? 0 : end),
                    OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                            .setValue(AndesiteFramePanelBlock.LIGHT, PanelLight.GENERIC));
        }
    }

    /**
     * The line of sight: bored last, through everything already written, so
     * nothing can fill it back in.
     */
    private static void slot(WorldGenLevel level, BlockPos base, boolean alongX) {
        int half = ALONG / 2;
        for (int y = SLOT_LOW; y <= SLOT_HIGH; y++) {
            for (int a = -half; a <= half; a++) {
                RuinGround.put(level, base.offset(alongX ? a : 0, y, alongX ? 0 : a),
                        Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * What the weather took off a broken one. The top course only, and never far
     * enough down to reach the slot - see {@link #MIN_BROKEN_HEIGHT}.
     */
    private static void erode(WorldGenLevel level, RandomSource random, BlockPos base,
                              boolean alongX, int height) {
        int half = ALONG / 2;
        int side = ACROSS / 2;

        for (int a = -half; a <= half; a++) {
            for (int c = -side; c <= side; c++) {
                if (random.nextInt(2) == 0) {
                    RuinGround.put(level, base.offset(alongX ? a : c, height, alongX ? c : a),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** The pieces that came down, lying near the foot where they landed. */
    private static void fallen(WorldGenLevel level, RandomSource random, BlockPos base) {
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos spot = RuinGround.scatter(level, random, base, DIG_MIN, DIG_MIN + 2);
            if (spot != null) {
                RuinGround.put(level, spot, OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }
}
