package com.serenity.octia.world;

import com.serenity.octia.Octia;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.PanelLight;
import com.serenity.octia.block.AndesiteFramePanelBlock;
import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipStatus;

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

    /** Raises the mast at the level's spawn, once per save. */
    public static void raise(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, spawn);

        for (int dy = 0; dy < HEIGHT; dy++) {
            BlockPos at = base.above(dy);
            // The panel cycles dark / generic / styled; STYLED is the lit one, so the
            // mast is its own light source and reads at night as well as at noon.
            level.setBlockAndUpdate(at,
                    OctiaBlocks.ANDESITE_FRAME_PANEL.defaultBlockState()
                            .setValue(AndesiteFramePanelBlock.LIGHT, PanelLight.STYLED));
        }

        BlockPos crown = base.above(HEIGHT);
        level.setBlockAndUpdate(crown,
                OctiaBlocks.SHIP_CORE.defaultBlockState().setValue(ShipCoreBlock.STATUS, ShipStatus.CALLED));

        // Footing, so a mast raised over sand or water still stands on something.
        level.setBlockAndUpdate(base.below(), Blocks.POLISHED_ANDESITE.defaultBlockState());

        Octia.LOGGER.info("Octia: mast raised at {} ({} panels, core called).", crown, HEIGHT);
    }
}
