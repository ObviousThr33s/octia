package com.serenity.octia.world;

import com.serenity.octia.Octia;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.block.AndesiteFramePanelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.world.level.block.Blocks;

/**
 * The obvious half of the switch.
 *
 * <p>A toggle nobody can see is a toggle nobody trusts. When Octia is enabled
 * for a save, the first load of the Overworld raises a lit mast at spawn: a
 * column of andesite frame panels under a ship core, standing on bedrock-proof
 * footing. Switch the mod off at world creation and the spawn is bare vanilla.
 * The difference is visible from the moment the world opens, without a command,
 * an inventory check, or a log line.
 *
 * <p>This is placement at first load, not chunk generation, and it stays that
 * way: the beacon is one mast at one place per save, which is a first-load fact
 * rather than a terrain fact. The chunk-generation half has since landed
 * separately - {@link OctiaWorldgen} registers the feature type and the biome
 * modification, the configured and placed features are datapack JSON, and the
 * per-save switch is applied inside the feature because Fabric's biome
 * modifications are per-launch. Read that class before repeating this note's
 * old claim that nothing generates. See docs/ROADMAP.md.
 */
public final class OctiaBeacon {

    /** Tall enough to clear trees and read as deliberate rather than accidental. */
    private static final int HEIGHT = 12;

    private OctiaBeacon() {
    }

    /** Half-width of the plinth. A 5x5 pad reads as built, not as an accident. */
    private static final int PAD = 2;

    /** What the beacon is called in {@link RuinRegistry}. Not a feature path. */
    public static final String BEACON = "beacon";

    /**
     * Finds honest ground at the save's spawn column and raises the beacon
     * there.
     *
     * <p>Seed-independence lives in {@link Landfall#groundIn}, which used to be
     * a method here. WORLD_SURFACE alone is not enough: it stops on the first
     * non-air block, which over an ocean is the water surface and under a
     * jungle is a leaf. MOTION_BLOCKING_NO_LEAVES ignores foliage, and the
     * walk-down afterwards drops through any fluid until it stands on something
     * solid. An ocean spawn therefore gets a beacon on the seabed with its mast
     * breaking the surface, rather than a column of panels bobbing in open
     * water. It moved because a sky world needs that walk to be able to answer
     * <em>nothing is here</em>, and a method that can say no is a different
     * method.
     */
    public static void raise(ServerLevel level) {
        // The real spawn, and it only became real recently. This used to be
        // called from ServerWorldEvents.LOAD, which fires while levels are still
        // being created - before Minecraft chooses a spawn in prepareLevels - so
        // it answered the default column at the world origin. On seeds that
        // spawn near origin nothing looked wrong; on seed 1, spawn is
        // (112, 67, 176) and the mast went up 209 blocks from where the player
        // arrives. Placement moved to SERVER_STARTED and the saves are the
        // receipt: [0.2.1] and [0.2.2] carry their mast at x=0 z=0, [0.2.3]
        // onward carry it at spawn. claimBeacon fires once per save, so the
        // early worlds keep what they got.
        //
        // Landfall, not groundAt, and the difference only shows on a sky world:
        // groundAt cannot answer "there is nothing here" - it walks to the
        // bottom of the world and hands back what it found there, which over a
        // void is a mast standing at y=-63 under an empty sky. Landfall looks
        // for real ground, moves the spawn to it if it has to, and makes an
        // island only when there is none within reach. On ordinary terrain it
        // finds ground in the spawn column on the first read and behaves exactly
        // as this line did before, spawn untouched.
        BlockPos base = Landfall.secure(level);
        build(level, base);
        // Recorded, not merely logged. A log line is gone the next time the file
        // rolls, and the debug map needs to know where this went for the life of
        // the save.
        OctiaWorldOption.get(level.getServer()).recordBeaconAt(base);

        // Also into the landmark registry. The option already knows where the
        // beacon is and will go on being the authority on that, but anything
        // asking "what has Octia put in this world" should get one answer, and
        // a mast at spawn is the first landmark there is.
        RuinRegistry.report(level, BEACON, base);
        Octia.LOGGER.info("Octia: beacon raised at {} ({} panels tall, {}x{} plinth).",
                base, HEIGHT, PAD * 2 + 1, PAD * 2 + 1);
    }


    /**
     * The beacon itself, as blocks. Public and level-agnostic so a GameTest can
     * build one inside a test structure and assert on it without needing a
     * world spawn — the placement rules and the thing being placed are separate
     * problems and are tested separately.
     */
    public static void build(ServerLevel level, BlockPos base) {
        // A plinth, so the beacon stands proud of whatever it landed on and is
        // legible as a made thing from a distance.
        for (int dx = -PAD; dx <= PAD; dx++) {
            for (int dz = -PAD; dz <= PAD; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
                // Clear whatever stood here, so grass and snow do not hide the edge.
                level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
            }
        }

        // The ring: eight frame panels around the core's footing. This is also the
        // hull shape ShipCoreBlock recognises, so the beacon is a moored ship and
        // not merely a decoration — a point of interest with a reason to exist.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                level.setBlockAndUpdate(base.offset(dx, 0, dz), panel());
            }
        }

        // The mast.
        for (int dy = 0; dy < HEIGHT; dy++) {
            level.setBlockAndUpdate(base.above(dy), panel());
        }

        // The crown: a core in a crow's nest.
        //
        // The ring is not decoration. ShipCoreBlock derives its own status from its
        // surroundings, so a core set to CALLED and left bare immediately reads itself
        // back as ADRIFT - and ADRIFT is light level 0, which would have made the top of
        // a beacon the darkest part of it. The gametest caught exactly that. Ringing the
        // crown moors the ship, and a moored core lights.
        BlockPos crown = base.above(HEIGHT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                level.setBlockAndUpdate(crown.offset(dx, 0, dz), panel());
            }
        }
        level.setBlockAndUpdate(crown, OctiaBlocks.SHIP_CORE.defaultBlockState());
    }

    /** STYLED is the light-15 state, so the mast is its own lantern. */
    private static net.minecraft.world.level.block.state.BlockState panel() {
        return OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                .setValue(AndesiteFramePanelBlock.LIGHT, PanelLight.STYLED);
    }
}
