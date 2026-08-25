package com.serenity.octia.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A wreck at the far end of a beamline.
 *
 * <p>{@link Beamline} says where a bend in the lattice throws its tangent and
 * how far along it the station sits. This is what puts something there, and the
 * something is an ordinary derelict - the same hull, dig and dressing
 * {@link DerelictFeature} builds anywhere else. What changes is only that the
 * site was chosen by the lattice rather than by a coin flip.
 *
 * <p><b>Why it matters that it is this ruin and not a new one.</b> An obelisk
 * lies along its leg with a slot bored down its length, and a station is exactly
 * where that line goes. Sight through the slot, walk, and there is a wreck. The
 * sighting has existed since the lattice landed with nothing at the end of it;
 * this is the end of it.
 *
 * <p><b>One chunk, one station, and that is what makes it safe.</b> A generation
 * worker may only write near its own chunk, so a feature cannot answer "there is
 * a station 300 blocks that way" by building there. Instead the chunk that
 * <em>contains</em> a station is the one that builds it - see
 * {@link Beamline#inChunk}. No two chunks can claim the same station, nothing
 * needs to remember what has been built, and every write stays where it belongs.
 *
 * <p><b>The placement modifiers do not choose the column here.</b> They only
 * bring this class a chunk; {@code derelict_station.json} asks once per chunk
 * with no rarity filter at all, because the rarity is in the lattice - a station
 * lands in about one chunk in 1,575, measured. The column is then the station's
 * own, dropped to the surface heightmap and handed to
 * {@link DerelictFeature#raise} exactly as the wild wreck's column is.
 *
 * <p>It yields to structures like the wild one does. A wreck at a station is
 * meant, but not enough to put it in somebody's village square - and the tangent
 * that pointed at it is still true whether or not the wreck could take the site.
 *
 * <p><b>The hull is the wild one's; what it carries is not.</b> A station is
 * about one chunk in 1,575 and it is the only ruin a player can be led to, so a
 * station wreck that gives up exactly what a field wreck gives up teaches that
 * following a sightline was not worth the walk. So after the wreck stands, this
 * lays one cache of its own pointed at {@link OctiaLoot#STATION_STORE} - see
 * {@link #cache}. The wreck body stays {@link DerelictFeature}'s single
 * definition; only the reward is this feature's.
 */
public class BeamlineDerelictFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * How far past the hull the cache sits, in blocks, along the beam.
     *
     * <p>Inside the four-block ring the wreck already scatters digs into, so
     * this adds no reach at all to what the feature writes - which is the only
     * number here that is a rule rather than a taste. The rest of it is
     * provisional - owner tunes by walking the world.
     */
    private static final int CACHE_OFF = 3;

    /**
     * How far up and down the cache looks for ground.
     *
     * <p>Down as far as a core can possibly have ended up, because the wreck
     * walks down through air and water before it seats and the cache has to
     * land beside the hull rather than in the sky above where the hull would
     * have been. Up only two, so an overhang answers with the floor a player
     * would stand on.
     */
    private static final int CACHE_UP = 2;

    public BeamlineDerelictFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Same gate as every other Octia feature, and for the same reason: the
        // biome modification is per-launch, the switch is per-save, and the
        // moment of placement is the only honest place to apply it.
        if (!OctiaWorldgen.active()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Beamline.Station station = Beamline.inChunk(level.getSeed(),
                origin.getX() >> 4, origin.getZ() >> 4);
        if (station == null) {
            return false;
        }

        // WORLD_SURFACE_WG, matching the heightmap the wild derelict's placement
        // modifier uses. Everything below the surface is DerelictFeature's job:
        // it walks down through air and fluid to real ground and then sinks by
        // the wreck's age, and doing any of that here would be a second
        // definition of where a hull sits.
        int x = station.at().x();
        int z = station.at().z();
        BlockPos column = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);

        if (!DerelictFeature.raise(level, context.random(), column, true)) {
            return false;
        }

        // After the hull, and only after: the cache looks for the ground the
        // wreck is standing on, and until the wreck is standing that ground is
        // whatever the terrain left. Drawing from the same RandomSource here is
        // safe for the same reason - every draw the wreck makes is already
        // spent, so nothing about the hull moves.
        cache(level, context.random(), station, column);
        return true;
    }

    /**
     * One barrel of station stores, laid past the hull along the beam.
     *
     * <p><b>Why the feature lays this and the ruin dressing does not.</b>
     * {@code Habitation.dress} already puts a barrel at some wrecks, and
     * re-pointing that one would have been the smaller change - but it is gated
     * on a draw against the wreck's age, replaced by a decorated pot when the
     * wreck is ancient, and skipped altogether when the wreck is submerged. A
     * promise routed through three refusals is not a promise, and a guarantee
     * at a one-in-1,575 site has to be laid by the thing that knows the site is
     * rare, which is this class. The alternative was a parameter threaded from
     * here through {@code DerelictFeature.raise} into the dressing: three files
     * this lane does not own, changed to move one boolean, and at the end of it
     * a wreck's own barrel and a station's cache would be the same object -
     * two different things that happen to look alike.
     *
     * <p><b>Where it goes is read from the lattice, never chosen here.</b> The
     * offset runs along {@link Beamline.Station#along()}, the tangent the beam
     * was on when it bent, so the cache sits a little further along the line the
     * player sighted down to arrive - the last thing on the beam. A hard-coded
     * cardinal would have put it somewhere the world has no opinion about.
     *
     * <p><b>It can decline, and the decline is honest.</b> {@code surfaceNear}
     * refuses a position standing in fluid, so a submerged station wreck gets no
     * cache and therefore no rig. That is the same held-back decision the store
     * tables make - a submerged wreck wants a marine palette nobody has drawn
     * yet - rather than a bug, and it is written here so the next person to ask
     * "why did that station have nothing" does not have to find out by reading
     * {@code RuinGround}.
     */
    private static void cache(WorldGenLevel level, RandomSource random,
            Beamline.Station station, BlockPos column) {
        BlockPos spot = where(level, station, column);
        if (spot == null) {
            return;
        }

        RuinGround.put(level, spot, Blocks.BARREL.defaultBlockState());

        // Read back rather than assumed, and that covers both cases without a
        // branch. On empty ground the write above made a fresh barrel and this
        // is its block entity; on a square the dressing already put a barrel on,
        // the write changed nothing and this is the older one, which then gets
        // pointed at the station table instead of the ruin table it had.
        if (level.getBlockEntity(spot) instanceof RandomizableContainerBlockEntity store) {
            store.setLootTable(OctiaLoot.STATION_STORE);
            store.setLootTableSeed(random.nextLong());
        }
    }

    /**
     * The square the cache stands on: past the hull along the beam, or failing
     * that the hull's own column.
     *
     * <p>The fallback is not tidiness. Past the hull may be a cliff, a void
     * edge, or open air, and at a site this rare "no ground three blocks that
     * way" is not a reason to hand a player nothing - the column itself is the
     * one square the wreck has already proved is standing on something.
     */
    private static BlockPos where(WorldGenLevel level, Beamline.Station station, BlockPos column) {
        Sightlines.Heading along = station.along();
        BlockPos beyond = column.offset(along.dx() * CACHE_OFF, 0, along.dz() * CACHE_OFF);

        BlockPos found = RuinGround.surfaceNear(level, beyond, CACHE_UP, DerelictFeature.searchDepth());
        return found != null
                ? found
                : RuinGround.surfaceNear(level, column, CACHE_UP, DerelictFeature.searchDepth());
    }
}
