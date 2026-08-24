package com.serenity.octia.world;

import com.serenity.octia.Octia;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Somewhere to stand, guaranteed, before anybody arrives.
 *
 * <p><b>What this is for.</b> {@code octia:sky} generates the Overworld from
 * {@code minecraft:floating_islands}, and a floating-island world has one
 * failure mode nothing else in this mod has ever had to face: <em>the spawn
 * column can be empty</em>. Vanilla's spawn finder asks the biome source for a
 * climate target, that noise settings file declares none, and what is left is
 * the origin column and a heightmap that answers bottom-of-world. The player
 * then arrives in open air at y=-64 and is dead before the title fades. That is
 * not a rare seed; on a sky world it is most of them.
 *
 * <p><b>Why it is not gated on the world type.</b> Nothing here asks which
 * preset generated the level, and that is deliberate. The question it actually
 * answers is "is there ground under the spawn", which is a fact about the
 * world, not about how the world was made - so it is true of a sky world, of a
 * datapack somebody else wrote, and of the void world type, and it is
 * <em>false</em> of ordinary terrain, where {@link #groundIn} finds ground on
 * the first try and nothing below ever runs. A generator check would be a
 * second spelling of the same question that could disagree with the first.
 *
 * <p><b>The order it tries, and why moving is preferred to building.</b> The
 * spawn column, then rings outward, and only then an island of its own. Terrain
 * the world generated is always better than terrain this mod printed: it has
 * biome, surface rules, features and a shape somebody tuned. Building is the
 * answer of last resort, taken because the alternative - refusing, and logging
 * about it - hands the player a fall into the void as their first experience of
 * the mod. See {@code docs/ISLANDS.md} for the scarcity argument this is in
 * tension with: an island you are given is Skyblock's opening move, not a
 * softening of it.
 */
public final class Landfall {

    /**
     * How far out to look for terrain before making some. The far end is a long
     * walk, not a journey - past this the island search stops being "the spawn
     * is a little off" and starts being a different spawn.
     */
    private static final int[] RADII = {16, 32, 48, 64, 80, 96};

    /** Compass directions tried at each radius. */
    private static final int SPOKES = 8;

    /** Half-width of an island this class has to build itself. */
    private static final int MADE_RADIUS = 7;

    /**
     * Where a made island's grass sits.
     *
     * <p>Inside the band {@code minecraft:floating_islands} generates in
     * (min_y 0, height 256) and near the middle of it, so a made island reads
     * as one of the world's own rather than as a shelf under everything or a
     * platform above it. Well clear of cloud height, which is 192.
     */
    private static final int MADE_Y = 96;

    private Landfall() {
    }

    /**
     * Makes sure the save's spawn has ground under it, and answers where the
     * first free block above that ground is.
     *
     * <p>Moves the world spawn if it had to look elsewhere, and does not touch
     * it otherwise. On ordinary terrain that means this method reads one
     * heightmap and returns; the spawn a normal world chose is left exactly as
     * Minecraft chose it, which is the behaviour every save before sky worlds
     * existed already had.
     *
     * <p>Server thread only, and after {@code prepareLevels} - the spawn is not
     * real until then. {@link com.serenity.octia.Octia} calls this from
     * {@code SERVER_STARTED} for exactly that reason.
     *
     * @return the block position to build on: air, with something solid beneath
     */
    public static BlockPos secure(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();

        // The spawn chunk is not guaranteed resident the instant a level loads,
        // and a heightmap read out of an unloaded chunk answers with nonsense.
        level.getChunk(spawn);

        BlockPos here = groundIn(level, spawn);
        if (here != null) {
            return here;
        }

        Octia.LOGGER.info("Octia: nothing under spawn at {}. Looking for land.", spawn);

        BlockPos found = nearestGround(level, spawn);
        if (found != null) {
            level.setDefaultSpawnPos(found, 0.0F);
            Octia.LOGGER.info("Octia: landfall at {} - spawn moved {} blocks to meet it.",
                    found, (int) Math.sqrt(found.distSqr(spawn)));
            return found;
        }

        BlockPos made = raise(level, new BlockPos(spawn.getX(), MADE_Y, spawn.getZ()));
        level.setDefaultSpawnPos(made, 0.0F);
        Octia.LOGGER.info("Octia: no land within {} blocks of spawn. An island was made at {}.",
                RADII[RADII.length - 1], made);
        return made;
    }

    /**
     * The first free space over solid ground in one column, or null if the
     * column is empty all the way down.
     *
     * <p>This is {@code OctiaBeacon.groundAt}'s walk with the one thing that
     * method cannot say: <em>no</em>. That one stops at the bottom of the world
     * and hands back the position it stopped at, which on ordinary terrain is
     * never reached and on a sky world is a beacon built in the void. Null is
     * the whole difference, and it is why this is not a parameter on that
     * method.
     */
    public static BlockPos groundIn(ServerLevel level, BlockPos column) {
        // MOTION_BLOCKING_NO_LEAVES rather than WORLD_SURFACE: the latter stops
        // at the first non-air block, which over water is the surface and under
        // a jungle is a leaf. The walk below then drops through any fluid, so an
        // island with a pond on it answers with the pond's floor.
        BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

        int floor = level.getMinBuildHeight() + 1;
        while (p.getY() > floor && (level.getBlockState(p.below()).isAir()
                || !level.getFluidState(p.below()).isEmpty())) {
            p = p.below();
        }

        // Asked about the block, not about the y. Comparing against the floor
        // would call a legitimate build at the bottom of the world a void, and
        // the bedrock a normal Overworld has down there is exactly that case.
        if (level.getBlockState(p.below()).isAir() || !level.getFluidState(p.below()).isEmpty()) {
            return null;
        }
        return p;
    }

    /**
     * Rings outward from a column until one of them has ground in it.
     *
     * <p>Nearest first and eight spokes to a ring, the same search
     * {@code OctiaWorldgen.placeNearSpawn} walks, and for the same reason: an
     * answer that is close beats an answer that is tidy. Unlike that one this
     * takes the first hit rather than the first hit a feature will accept -
     * there is no feature yet, only the question of whether anything is there.
     */
    private static BlockPos nearestGround(ServerLevel level, BlockPos spawn) {
        for (int radius : RADII) {
            for (int i = 0; i < SPOKES; i++) {
                double angle = 2 * Math.PI * i / SPOKES;
                int x = spawn.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * radius);

                BlockPos column = new BlockPos(x, 0, z);
                level.getChunk(column);

                BlockPos ground = groundIn(level, column);
                if (ground != null) {
                    return ground;
                }
            }
        }
        return null;
    }

    /**
     * Builds one island, and answers the free block above its grass.
     *
     * <p>Public so a GameTest can build one inside a test structure and assert
     * on its shape without needing a void world, which is the same split
     * {@code OctiaBeacon.build} is public for: the rule that decides where
     * something goes and the thing that goes there are separate problems.
     *
     * @param surface the centre of the grass layer
     */
    public static BlockPos raise(ServerLevel level, BlockPos surface) {
        return raise(level, surface, MADE_RADIUS);
    }

    /**
     * The same island at a stated size.
     *
     * <p>The radius is a parameter for one reason and it is not configurability:
     * a gametest runs inside a structure box a few blocks across, and the
     * fifteen-block island a real spawn gets would spill into whatever test is
     * sitting next door. The shape is {@link Isle}'s at every size, so a small
     * one proves the same arithmetic.
     */
    public static BlockPos raise(ServerLevel level, BlockPos surface, int radius) {
        int thickness = Isle.thickness(radius);

        for (int depth = 0; depth < thickness; depth++) {
            int r = Isle.radiusAt(radius, depth);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!Isle.holds(radius, depth, dx, dz)) {
                        continue;
                    }
                    BlockPos at = surface.offset(dx, -depth, dz);
                    level.setBlockAndUpdate(at, soil(depth));
                }
            }
        }

        // The free block, which is what a caller wants to build on. The grass is
        // at `surface`; standing room is the block over it.
        return surface.above();
    }

    /** Grass, then soil, then stone - the layering any overworld surface has. */
    private static net.minecraft.world.level.block.state.BlockState soil(int depth) {
        if (depth == 0) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (depth <= Isle.SOIL) {
            return Blocks.DIRT.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }
}
