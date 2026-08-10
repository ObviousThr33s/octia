package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.block.PanelLight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An obelisk: a lit marker, standing over a dig, with no ship in it.
 *
 * <p><b>Why a second ruin at all.</b> One structure type is a curiosity; two are
 * a culture. The derelict says a ship came and failed - which only means
 * something if there is separate evidence that somebody was here on purpose.
 * An obelisk is that evidence: a mast raised deliberately over a dig, in the
 * same andesite, by whoever was calling ships in the first place.
 *
 * <p><b>Deliberately not a ship.</b> There is no core in it and there never
 * should be. {@link com.serenity.octia.ship.ShipCoreBlock} is the mod's one
 * piece of machinery and the moorings store is its spine; a landmark that
 * quietly moored itself would put positions into that store that no player ever
 * built and no player can unbuild. An obelisk is masonry. It is legible at
 * night because of the lit crown, it is worth walking to because of the dig at
 * its foot, and it stays out of the store entirely.
 *
 * <p>The crown is {@link PanelLight#STYLED}, light 15, which is the brightest
 * thing the mod owns. That is the point of it - an obelisk is meant to be the
 * thing you see across a valley and decide to walk toward.
 */
public class ObeliskFeature extends Feature<NoneFeatureConfiguration> {

    /** Shortest and tallest a standing obelisk gets, before the break. */
    private static final int MIN_HEIGHT = 5;
    private static final int MAX_HEIGHT = 9;

    /** How far the plinth reaches from the column. A 3x3 pad reads as built. */
    private static final int PLINTH = 1;

    /** Digs sit off the plinth and inside a comfortable walk of the foot. */
    private static final int DIG_MIN = 2;
    private static final int DIG_MAX = 4;

    /** Chance in four that an obelisk has snapped rather than stands whole. */
    private static final int BROKEN_IN_FOUR = 1;

    public ObeliskFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
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

        if (!RuinGround.hasFooting(level, base.below(1), PLINTH)) {
            return false;
        }
        // A half-submerged obelisk is a worse object than no obelisk. Checked
        // over the plinth and the first courses rather than the whole shaft:
        // the top of a tall one is free to stand in open air over water, which
        // is a fine silhouette, but its foot is not.
        if (!RuinGround.isDry(level, base, PLINTH, 0, MIN_HEIGHT)) {
            return false;
        }

        plinth(level, base);

        RuinAge age = RuinAge.roll(random);

        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        // An ancient one has always come down. Tying the break to the age means
        // the silhouette and the dressing tell the same story instead of two.
        boolean broken = age == RuinAge.ANCIENT || random.nextInt(4) < BROKEN_IN_FOUR;
        if (broken) {
            // A snapped obelisk loses its crown, so it also loses the light.
            // That asymmetry is worth keeping: a lit spire on the horizon is a
            // promise that something is standing, and a broken one should not
            // be able to make it.
            height = Math.max(2, height - (2 + random.nextInt(3)));
        }

        column(level, random, base, height, broken);
        RuinGround.dig(level, random, base, DIG_MIN, DIG_MAX, 2 + random.nextInt(3));

        if (broken) {
            fallen(level, random, base);
        }
        Habitation.dress(level, random, base, age);

        // Recorded so something can be told where the obelisks are. This one
        // matters most: an obelisk is a landmark by design - the lit crown
        // exists to be seen across a valley and walked toward - and until now
        // nothing but a player's eyes could find one.
        RuinRegistry.report(level.getLevel(), OctiaWorldgen.OBELISK, base);
        return true;
    }

    /** A 3x3 pad at the foot, so the column reads as founded rather than stuck in. */
    private static void plinth(WorldGenLevel level, BlockPos base) {
        for (int dx = -PLINTH; dx <= PLINTH; dx++) {
            for (int dz = -PLINTH; dz <= PLINTH; dz++) {
                RuinGround.put(level, base.offset(dx, 0, dz),
                        OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }

    /**
     * The shaft, unbroken from the plinth up, crowned if it still stands.
     *
     * <p>A broken one leans. The courses above the lean point step one block to
     * the side and carry on, which in a world made of cubes is what a topple
     * looks like: the stone did not fall over, it slid. A perfectly vertical
     * stump reads as unfinished rather than as ruined, and every broken obelisk
     * looking identical was the same complaint the mast had before it became a
     * hexahedron.
     */
    private static void column(WorldGenLevel level, RandomSource random,
                               BlockPos base, int height, boolean broken) {
        // Where it gave way, and which way it went. Never the first course -
        // something has to still be standing on the plinth or it is rubble.
        int lean = broken ? 2 + random.nextInt(Math.max(1, height - 2)) : height + 1;
        Direction fell = Direction.Plane.HORIZONTAL.getRandomDirection(random);

        BlockPos shaft = base;
        for (int y = 1; y <= height; y++) {
            if (y == lean) {
                shaft = shaft.relative(fell);
            }
            boolean crown = !broken && y == height;
            PanelLight light = crown ? PanelLight.STYLED
                    : (y == height - 1 && !broken ? PanelLight.GENERIC : PanelLight.NONE);

            RuinGround.put(level, shaft.above(y),
                    OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                            .setValue(AndesiteFramePanelBlock.LIGHT, light));
        }
    }

    /** The piece that came down, lying near the foot where it landed. */
    private static void fallen(WorldGenLevel level, RandomSource random, BlockPos base) {
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos spot = RuinGround.scatter(level, random, base, DIG_MIN, DIG_MIN + 1);
            if (spot != null) {
                RuinGround.put(level, spot, OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState());
            }
        }
    }
}
