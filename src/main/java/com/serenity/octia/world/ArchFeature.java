package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An arch, standing on a {@link Sightlines} node, squared to the leg leaving it.
 *
 * <p><b>This answers the open item the threads shipped with.</b> A leg pointed
 * at a waypoint that was guaranteed to have nothing on it: placement was a
 * per-chunk rarity roll that knew nothing about the lattice, so following a
 * thread converged on an empty field. That is a promise the world does not keep,
 * and a player only has to hit it once. The node is now a place. You walk
 * <em>through</em> the arch, and the way you are facing when you do is the way
 * the thread runs.
 *
 * <p><b>Keystone plus four.</b> The ring is five stones - a keystone over two
 * voussoirs either side - stepping up from the springers to the middle:
 *
 * <pre>
 *              [K]          keystone, STYLED, light 15
 *          [v]     [v]      voussoirs, GENERIC, light 7
 *      [s]             [s]  springers, dark, on top of the piers
 *      [p]             [p]
 *      [p]     ^       [p]  the opening: three wide, and you face down the leg
 *      [p]             [p]
 * </pre>
 *
 * <p>Five is the smallest span that reads as an arch rather than a doorway, and
 * it is odd, which is what lets a single keystone sit on the centre line. The
 * opening it leaves is three wide - a gate, not a squeeze.
 *
 * <p><b>The arches stand and the obelisks do not.</b> {@link ObeliskFeature}
 * breaks between one in eight and four in eight of its own, on purpose, so that
 * what is still upright carries information. Nothing here erodes and nothing
 * here is ever broken. That asymmetry is the point: a node is maintained, the
 * road between nodes is not, and the one structure in this mod that is always
 * whole is the one that marks a place somebody meant.
 *
 * <p><b>How it finds its site, and why that is not a placement modifier.</b>
 * Nothing in the placed-feature vocabulary can say "the node of this cell" -
 * modifiers see a chunk and a random, never a function of the world seed. So the
 * placed feature offers this one chunk per chunk with no rarity filter at all,
 * and {@link #place} declines every chunk whose cell's node is somewhere else.
 * That is one cheap arithmetic test per chunk, and it accepts once per 1,024 of
 * them. A rarity filter in front of it would be strictly wrong: it would throw
 * away the one chunk that matters and leave nodes bare at random.
 */
public class ArchFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * The arch ring: a keystone and four voussoirs, so five stones across.
     *
     * <p>Written as the half-span because everything here is measured out from
     * the centre line, and because an even span could not carry a keystone at
     * all - there would be a joint where the middle stone belongs.
     */
    private static final int HALF_SPAN = 2;

    /** How tall the piers stand before the ring starts. */
    private static final int MIN_PIER = 4;
    private static final int MAX_PIER = 6;

    /**
     * How far up and down the node's column is searched for ground.
     *
     * <p>Wide, and it has to be. This feature is handed the chunk's corner by
     * its heightmap modifier and then walks up to fifteen blocks on each axis to
     * reach the node, which on amplified terrain is easily sixty blocks of
     * height. The obelisk and the derelict build where they were dropped and
     * need no such search.
     */
    private static final int GROUND_REACH = 64;

    public ArchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** The ring's half-span, for the gametests that walk the shape. */
    public static int halfSpan() {
        return HALF_SPAN;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        Sightlines.Leg leg = Sightlines.legAt(level.getSeed(), origin.getX(), origin.getZ());
        Sightlines.Node node = leg.from();

        // The gate. Every chunk in the cell is offered this feature and exactly
        // one of them holds the node; the other 1,023 stop here. Comparing
        // section coordinates rather than dividing by sixteen because negative
        // coordinates make integer division lie about which chunk they are in.
        if (SectionPos.blockToSectionCoord(node.x()) != SectionPos.blockToSectionCoord(origin.getX())
                || SectionPos.blockToSectionCoord(node.z()) != SectionPos.blockToSectionCoord(origin.getZ())) {
            return false;
        }

        // The origin's Y is the surface at the chunk's corner, not at the node.
        // Those are the same column only by accident.
        BlockPos surface = RuinGround.surfaceNear(level,
                new BlockPos(node.x(), origin.getY(), node.z()), GROUND_REACH, GROUND_REACH);
        if (surface == null) {
            return false;
        }

        return raise(level, surface.below(1), leg.heading(), context.random());
    }

    /**
     * Builds an arch on a known footing, facing a known way.
     *
     * <p>Public and separate from {@link #place} because the two are genuinely
     * different jobs - <em>where a node is</em> and <em>what an arch looks
     * like</em> - and because the gate above makes the shape untestable
     * otherwise: a gametest plot would have to happen to contain a node, which
     * for a world seed that changes every run it never does. The tests drive
     * this directly with a heading they chose.
     *
     * @param base    the block the piers stand on, one below the walking surface
     * @param heading which way the leg runs; the arch is squared across it
     * @return whether the ground took it
     */
    public static boolean raise(WorldGenLevel level, BlockPos base,
                                Sightlines.Heading heading, RandomSource random) {
        boolean alongX = heading == Sightlines.Heading.EAST || heading == Sightlines.Heading.WEST;

        int pier = MIN_PIER + random.nextInt(MAX_PIER - MIN_PIER + 1);
        int top = pier + 1 + HALF_SPAN;

        // One block thick along the thread and five across it, so the footing is
        // a strip rather than a square. A square check here would reject a
        // perfectly good ledge for ground the arch never touches.
        int radiusX = alongX ? 0 : HALF_SPAN;
        int radiusZ = alongX ? HALF_SPAN : 0;
        if (!RuinGround.hasFooting(level, base.below(1), radiusX, radiusZ, top + 1)) {
            return false;
        }

        piers(level, base, alongX, pier);
        ring(level, base, alongX, pier);
        return true;
    }

    /** The two legs it stands on, dark, from the ground to the springing. */
    private static void piers(WorldGenLevel level, BlockPos base, boolean alongX, int pier) {
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= pier; y++) {
                RuinGround.put(level, at(base, alongX, side * HALF_SPAN, y),
                        OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }

    /**
     * Keystone plus four, stepping up to the centre.
     *
     * <p>The light is the whole legibility of the thing at range: the keystone
     * is {@link PanelLight#STYLED} at 15 and the voussoirs either side are
     * {@link PanelLight#GENERIC} at 7, so what carries across a valley at night
     * is a bright point with two dimmer ones under it - a shape, not a dot.
     * The springers stay dark, which is what stops the whole ring reading as one
     * smear of light and keeps the step visible.
     */
    private static void ring(WorldGenLevel level, BlockPos base, boolean alongX, int pier) {
        for (int c = -HALF_SPAN; c <= HALF_SPAN; c++) {
            // Each stone sits one course higher than the one outside it, which
            // is what makes the ring an arch instead of a lintel.
            int y = pier + 1 + (HALF_SPAN - Math.abs(c));

            PanelLight light = switch (Math.abs(c)) {
                case 0 -> PanelLight.STYLED;
                case 1 -> PanelLight.GENERIC;
                default -> PanelLight.NONE;
            };

            RuinGround.put(level, at(base, alongX, c, y),
                    OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                            .setValue(AndesiteFramePanelBlock.LIGHT, light));
        }
    }

    /**
     * A position in the arch's own frame: {@code c} across the thread, at height
     * {@code y}. Nothing sits off the centre line along the thread - the arch is
     * one block thick, and the frame has no third axis for that reason.
     */
    private static BlockPos at(BlockPos base, boolean alongX, int c, int y) {
        return base.offset(alongX ? 0 : c, y, alongX ? c : 0);
    }
}
