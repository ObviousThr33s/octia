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
 * <p>This is placement at first load, not chunk generation. That is deliberate
 * for now: real worldgen means a configured feature, a placed feature, and a
 * biome modification, and Fabric's biome modifications are global rather than
 * per-save, so gating them on a per-world flag is a larger job than the switch
 * itself. Raising the mast proves the flag end to end and is honest about what
 * it is. See NEXT.md.
 */
public final class OctiaBeacon {

    /** Tall enough to clear trees and read as deliberate rather than accidental. */
    private static final int HEIGHT = 12;

    private OctiaBeacon() {
    }

    /** Half-width of the plinth. A 5x5 pad reads as built, not as an accident. */
    private static final int PAD = 2;

    /**
     * Finds honest ground at the given column and raises the beacon there.
     *
     * <p>Seed-independence lives in this method. WORLD_SURFACE alone is not
     * enough: it stops on the first non-air block, which over an ocean is the
     * water surface and under a jungle is a leaf. MOTION_BLOCKING_NO_LEAVES
     * ignores foliage, and the walk-down afterwards drops through any fluid
     * until it stands on something solid. An ocean spawn therefore gets a
     * beacon on the seabed with its mast breaking the surface, rather than a
     * column of panels bobbing in open water.
     */
    public static void raise(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();

        // The spawn chunk is not guaranteed resident the instant the level loads,
        // and reading a heightmap out of an unloaded chunk answers with nonsense.
        level.getChunk(spawn);

        BlockPos base = groundAt(level, spawn);
        build(level, base);
        // Recorded, not merely logged. A log line is gone the next time the file
        // rolls, and the debug map needs to know where this went for the life of
        // the save.
        OctiaWorldOption.get(level.getServer()).recordBeaconAt(base);
        Octia.LOGGER.info("Octia: beacon raised at {} ({} panels tall, {}x{} plinth).",
                base, HEIGHT, PAD * 2 + 1, PAD * 2 + 1);
    }

    /** The first solid block at this column, ignoring foliage and dropping through fluid. */
    public static BlockPos groundAt(ServerLevel level, BlockPos column) {
        BlockPos p = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        while (p.getY() > level.getMinBuildHeight() + 1
                && (level.getBlockState(p.below()).isAir()
                    || !level.getFluidState(p.below()).isEmpty())) {
            p = p.below();
        }
        return p;
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
