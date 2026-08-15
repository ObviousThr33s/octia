package com.serenity.octia.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * The ground-handling every Octia ruin needs, in one place.
 *
 * <p>Extracted when the second ruin type arrived and immediately wanted the
 * same four answers the first one had worked out: is this footing solid, where
 * is the local surface, where can something be dropped nearby, and how is a dig
 * put in the ground. Duplicating those would have meant duplicating the two
 * mistakes already paid for below.
 *
 * <p><b>No heightmaps here, deliberately.</b> The {@code _WG} heightmaps are
 * maintained on {@code ProtoChunk} and are empty once a chunk has finished
 * generating, so a feature that reads one answers bottom-of-world anywhere it
 * is invoked outside natural generation - {@code /place feature}, or a gametest.
 * A short local scan costs a handful of block reads and answers the same in
 * every context. The placement modifier owns the surface; this owns the metre
 * around it.
 */
final class RuinGround {

    /** How far up and down {@link #surfaceNear} looks. */
    private static final int SURFACE_UP = 4;
    private static final int SURFACE_DOWN = 3;

    /** Tries before a scatter gives up on finding anywhere free. */
    private static final int SCATTER_TRIES = 8;

    private RuinGround() {
    }

    /**
     * Whether a square of side {@code 2 * radius + 1} centred here is standing
     * on something.
     *
     * <p>Rejecting rather than terraforming is the point. A feature that
     * flattens ground to fit leaves a scar visible from three hundred blocks
     * away, and a ruin is supposed to look found, not installed.
     */
    static boolean hasFooting(WorldGenLevel level, BlockPos floor, int radius) {
        // Eight is the headroom a ruin is assumed to want above its floor. It
        // clears the derelict's cube - floor+3 at its top - with room to spare.
        // It used to be two short of the obelisk, whose column could reach
        // floor+10: seated near the build ceiling it passed here and then lost
        // its crown to it, because setBlock above build height is a silent
        // no-op - a spire left standing without the one block that says so,
        // which is the state ObeliskFeature reserves for a broken one. That is
        // why the overload below exists, and why the obelisk calls it with the
        // height it is actually about to build.
        return hasFooting(level, floor, radius, radius, 8);
    }

    /**
     * The same check over a rectangle, with the headroom stated rather than
     * assumed. A prism laid along a thread is longer than it is wide, and a
     * square check would either reject ground the narrow axis clears or accept
     * ground the long axis does not.
     */
    static boolean hasFooting(WorldGenLevel level, BlockPos floor,
                              int radiusX, int radiusZ, int headroom) {
        if (level.isOutsideBuildHeight(floor) || level.isOutsideBuildHeight(floor.above(headroom))) {
            return false;
        }
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                BlockPos under = floor.offset(dx, 0, dz);
                if (level.getBlockState(under).isAir()) {
                    return false;
                }
                if (!level.getFluidState(under).is(Fluids.EMPTY)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The first free space over solid ground in one column, or null. */
    static BlockPos surfaceNear(WorldGenLevel level, BlockPos column) {
        return surfaceNear(level, column, SURFACE_UP, SURFACE_DOWN);
    }

    /**
     * The same scan over a stated reach.
     *
     * <p>The wide form exists because one feature does not build where its
     * placement modifier dropped it. {@link ArchFeature} is offered a chunk's
     * corner and builds at the lattice node inside that chunk, up to fifteen
     * blocks away on both axes - and on amplified terrain fifteen blocks
     * sideways is easily sixty blocks of height. A four-block look either way
     * finds nothing and the arch declines a site that was perfectly good.
     *
     * <p>Top down, so an overhang answers with the surface you would stand on
     * rather than the roof of the cave under it.
     */
    static BlockPos surfaceNear(WorldGenLevel level, BlockPos column, int up, int down) {
        for (int y = up; y >= -down; y--) {
            BlockPos at = column.above(y);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            if (level.getBlockState(at.below()).isAir()) {
                continue;
            }
            if (!level.getFluidState(at).is(Fluids.EMPTY)) {
                continue;
            }
            return at;
        }
        return null;
    }

    /** A free surface position in a ring around a centre, or null. */
    static BlockPos scatter(WorldGenLevel level, RandomSource random, BlockPos centre,
                            int min, int max) {
        for (int tries = 0; tries < SCATTER_TRIES; tries++) {
            int dx = random.nextInt(max * 2 + 1) - max;
            int dz = random.nextInt(max * 2 + 1) - max;
            if (Math.abs(dx) < min && Math.abs(dz) < min) {
                continue;
            }
            BlockPos spot = surfaceNear(level, centre.offset(dx, 0, dz));
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Brushable ground around a centre, loaded with what a ruin carries.
     *
     * @return how many actually took, since a ruin on bare stone may get none
     */
    static int dig(WorldGenLevel level, RandomSource random, BlockPos centre,
                   int min, int max, int attempts) {
        int placed = 0;
        for (int i = 0; i < attempts; i++) {
            BlockPos spot = scatter(level, random, centre, min, max);
            if (spot == null) {
                continue;
            }
            // Sand or gravel to match what it sits on, so a dig looks like
            // disturbed ground rather than an imported block.
            BlockState below = level.getBlockState(spot.below());
            BlockState brushable = below.is(Blocks.SAND) || below.is(Blocks.RED_SAND)
                    ? Blocks.SUSPICIOUS_SAND.defaultBlockState()
                    : Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();

            put(level, spot, brushable);

            if (level.getBlockEntity(spot) instanceof BrushableBlockEntity brush) {
                brush.setLootTable(BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON, random.nextLong());
            }
            placed++;
        }
        return placed;
    }

    /**
     * Writes one block.
     *
     * <p>Flag 2 is clients-only, no neighbour updates. Note what this does
     * <em>not</em> control: {@code LevelChunk.setBlockState} calls
     * {@code onPlace} on the server regardless of flags, so a write into a live
     * {@code ServerLevel} still runs block logic while a write into a
     * {@code WorldGenRegion} does not. Features that care about the difference
     * have to order their writes, not pick a flag.
     */
    static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }
}
