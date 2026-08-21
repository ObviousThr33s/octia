package com.serenity.octia.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;

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

    /**
     * Whether a box of this size, standing on this floor, is clear of every
     * vanilla structure.
     *
     * <p><b>Why this is not the footing check.</b> A village is made of blocks
     * that {@link #hasFooting} is perfectly happy with - a path is solid, a
     * field is solid, a roof is solid. The ground under a village says yes.
     * What has to be asked instead is whether somebody already built here, and
     * the only thing that knows that is the structure that claimed the site.
     *
     * <p><b>It asks the start, not the blocks.</b> Octia's features are
     * registered at {@code SURFACE_STRUCTURES}, which is the step villages
     * place in too, so whether the houses are down yet when this runs is a
     * question about ordering inside one decoration step - exactly the kind of
     * coupling that breaks silently on a version bump. The structure
     * <em>starts</em> are settled two chunk statuses earlier and are already
     * final here, so reading those is immune to it. A village that has not laid
     * a single plank yet still answers.
     *
     * <p><b>Y is doing more work here than X and Z.</b> The test is against the
     * start's whole bounding box, which for a village is the hull of the houses,
     * the paths, the fields and the gaps between them - blunt on purpose, since
     * an obelisk in the village square is as wrong as one through a roof. That
     * same bluntness applied to a stronghold or an ancient city would blank
     * enormous areas of surface, and the only thing preventing it is that their
     * boxes do not reach the height a surface ruin occupies. So the vertical
     * span is not a detail of the test, it <em>is</em> the test. Anyone tempted
     * to drop it and compare footprints will watch ruin density collapse.
     *
     * <p>The same fact means this is close to a no-op for trial chambers and
     * trail ruins, which in 1.21.1 generate at {@code underground_structures}
     * and sit well below anything built here. That is correct - the surface
     * above a chamber is not spoken for - but it is not what "declines inside a
     * trial chamber" sounds like, so it is written down rather than assumed.
     *
     * @param floor    the block the build stands on
     * @param height   how far above {@code floor} it reaches
     * @return true if nothing is there, and the ruin may have the site
     */
    static boolean clearOfStructures(WorldGenLevel level, BlockPos floor,
                                     int radiusX, int radiusZ, int height) {
        // Scoped to the region during generation. The unscoped manager reads
        // through the ServerLevel, and ServerLevel.getChunk from a generation
        // worker joins a future on the server thread - which is the deadlock,
        // not a crash. Off the worldgen path there is no region and the level
        // is the right thing to ask; both callers of that branch, /place and a
        // gametest, are on the server thread already.
        //
        // That branch is not free, and the cost is not obvious from here: the
        // reads below pass require=true, so against a live level they do not
        // merely look, they GENERATE any chunk that is missing. Fine for
        // /place, where the player is standing in loaded terrain. Not fine in a
        // loop at world load, which is one of the reasons the guaranteed spawn
        // derelict goes around this entirely - see DerelictFeature.seat.
        StructureManager structures = level instanceof WorldGenRegion region
                ? level.getLevel().structureManager().forWorldGenRegion(region)
                : level.getLevel().structureManager();

        // ONE chunk, the one this is standing in, and the footprint's corners
        // are deliberately not consulted.
        //
        // startsForStructure reads twice: the references held by the chunk
        // asked about, and then the chunk that owns each start it finds. A
        // chunk's references are filled from every start within eight chunks,
        // so asking a chunk one over lets the second read reach nine - and a
        // WorldGenRegion at FEATURES declares dependencies for distances zero
        // through eight only. Past that it does not block or return null, it
        // throws: "Requested chunk unavailable during world generation". A
        // mineshaft or a stronghold in the wrong place would take the server
        // down mid-generation, on some seeds and not others, in a path no
        // gametest can reach.
        //
        // This is exactly why vanilla's own applyBiomeDecoration only ever asks
        // about the chunk it is decorating. What the narrower question misses
        // is a structure that clips the two- or three-block sliver of footprint
        // spilling over the border while missing this chunk entirely - which
        // for anything village-sized is not a case that exists.
        BoundingBox footprint = BoundingBox.fromCorners(
                new BlockPos(floor.getX() - radiusX, floor.getY(), floor.getZ() - radiusZ),
                new BlockPos(floor.getX() + radiusX, floor.getY() + height, floor.getZ() + radiusZ));

        for (StructureStart start : structures.startsForStructure(new ChunkPos(floor), structure -> true)) {
            if (start.getBoundingBox().intersects(footprint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the box a ruin is about to occupy is free of fluid.
     *
     * <p>{@link #hasFooting} only ever looked at the plane a ruin stands on,
     * which is not the same question. A wreck on a dry seabed shelf, or on the
     * shore of a lake that rises over its top course, passes a floor check and
     * still ends up underwater. This asks about the volume that is going to be
     * built in, which is the thing that actually has to be dry.
     *
     * <p><b>Three questions stand over a ruin's site now, and they are separate
     * on purpose.</b> {@link #hasFooting} asks whether the ground holds the
     * build up, this asks whether the build ends up under water, and
     * {@link #clearOfStructures} asks whether somebody already built here. A dry
     * seabed shelf answers yes to the first and no to the second; a village
     * square answers yes to the first two and no to the third. They are
     * deliberately not folded into one call, so a feature asks only the ones it
     * has a reason to care about - the obelisk asks all three, the arch asks the
     * footing and the structures and never the water.
     *
     * <p>The waystation asks the footing and the water and <em>not</em> the
     * structures, and that one is a gap rather than a decision:
     * {@link TemplateRuinFeature} arrived on a branch where
     * {@code clearOfStructures} did not exist, so it was never offered the
     * question. Nothing has measured how often an authored ruin lands in a
     * village. Written down rather than quietly fixed, because changing what a
     * feature refuses changes where it generates, and that wants a walked world
     * behind it.
     */
    static boolean isDry(WorldGenLevel level, BlockPos centre, int radius, int below, int above) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -below; dy <= above; dy++) {
                    if (!level.getFluidState(centre.offset(dx, dy, dz)).is(Fluids.EMPTY)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Walks down from a position to the first free space above solid ground,
     * dropping through air <em>and</em> fluid on the way.
     *
     * <p>This is {@code OctiaBeacon.groundAt}'s idea, which is why an ocean
     * spawn gets a mast standing on the seabed rather than a column of panels
     * bobbing at the surface. Written here against block reads only - no
     * heightmap - because a feature runs during generation where the non-{@code _WG}
     * heightmaps are not maintained, and that lesson has been paid for once.
     *
     * @param drop how far down to look before giving up
     * @return the floor position, or null if nothing solid is within reach
     */
    static BlockPos descend(WorldGenLevel level, BlockPos from, int drop) {
        BlockPos p = from;
        for (int i = 0; i < drop; i++) {
            if (level.isOutsideBuildHeight(p.below())) {
                return null;
            }
            BlockState below = level.getBlockState(p.below());
            if (!below.isAir() && level.getFluidState(p.below()).is(Fluids.EMPTY)) {
                return p;
            }
            p = p.below();
        }
        return null;
    }

    /** Whether this position is standing in fluid. */
    static boolean submerged(WorldGenLevel level, BlockPos pos) {
        return !level.getFluidState(pos).is(Fluids.EMPTY);
    }

    /**
     * A free position around a centre, allowing fluid.
     *
     * <p>The dry {@link #scatter} refuses anything wet, which is right on land
     * and wrong on a seabed - vanilla's own ocean ruins bury suspicious sand
     * under water, and a wreck on the floor of the sea should be diggable for
     * the same reason.
     */
    static BlockPos scatterWet(WorldGenLevel level, RandomSource random, BlockPos centre,
                               int min, int max) {
        for (int tries = 0; tries < SCATTER_TRIES; tries++) {
            int dx = random.nextInt(max * 2 + 1) - max;
            int dz = random.nextInt(max * 2 + 1) - max;
            if (Math.abs(dx) < min && Math.abs(dz) < min) {
                continue;
            }
            BlockPos floor = descend(level, centre.offset(dx, 2, dz), 8);
            if (floor != null) {
                return floor;
            }
        }
        return null;
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
        return dig(level, random, centre, min, max, attempts, false);
    }

    /** As above, but a wet site digs on the seabed instead of refusing it. */
    static int dig(WorldGenLevel level, RandomSource random, BlockPos centre,
                   int min, int max, int attempts, boolean wet) {
        int placed = 0;
        for (int i = 0; i < attempts; i++) {
            BlockPos spot = wet
                    ? scatterWet(level, random, centre, min, max)
                    : scatter(level, random, centre, min, max);
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
                brush.setLootTable(OctiaLoot.RUIN_DIG, random.nextLong());
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
