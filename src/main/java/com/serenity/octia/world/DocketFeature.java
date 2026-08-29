package com.serenity.octia.world;

import com.mojang.serialization.Codec;
import com.serenity.octia.world.Docket.Berth;
import com.serenity.octia.world.Docket.Listing;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The carrier: it asks the docket what is due, vets the ground itself, and only
 * then lets somebody else build.
 *
 * <p>One placed feature stands in for all of them. It carries no
 * {@code rarity_filter}, because a rarity in front of a cell gate throws away
 * the one chunk that matters - {@link ArchFeature} already learned that - and no
 * {@code in_square}, because the berth's own coordinates are the answer and an
 * offset origin would make the containment test lie.
 *
 * <h2>Octia vets, the delegate builds</h2>
 *
 * <p>Siting is not the contributor's job and is not optional. Every berth is put
 * through the same questions in the same order, and a delegate that would refuse
 * a bad site is <b>never asked</b> rather than trusted to say no:
 *
 * <ol>
 *   <li><b>envelope</b> - the footprint must fit {@link Massing#REACH}, or the
 *       build could reach outside its own chunk</li>
 *   <li><b>surface</b> - {@link RuinGround#surfaceNear} at the <i>berth's</i>
 *       column, not the chunk origin. {@code ArchFeature} recorded why: the
 *       origin's height is the surface at the chunk's corner, and fifteen blocks
 *       sideways is easily sixty blocks of height</li>
 *   <li><b>footing</b> - {@link RuinGround#hasFooting}</li>
 *   <li><b>dry</b>, only if the listing asked to be</li>
 *   <li><b>clear of structures</b> - on the berth's own chunk and never widened.
 *       It has no flag and cannot be skipped</li>
 * </ol>
 *
 * <h2>The delegate gets its own randomness, and this is the load-bearing part</h2>
 *
 * <p>{@code context.random()} is never drawn from and never passed on. Each
 * delegate is handed a {@link RandomSource} seeded from
 * {@code (seed, subcell, listing)}. Three separate defects follow from getting
 * this wrong, and none of them would have shown up as a crash:
 *
 * <ul>
 *   <li>a <b>refused</b> berth would still have spent draws, silently shifting
 *       the draw order of every feature decorating after it</li>
 *   <li>two listings berthed in one chunk would <b>couple through the shared
 *       stream</b>. {@code TemplateRuinFeature} draws its rotation before its
 *       footing check can refuse, so a neighbour's terrain would have changed
 *       your ruin's rotation and its loot seed</li>
 *   <li>a delegate's internals would depend on how many chunks decorated before
 *       it, which is not a thing a seed should mean</li>
 * </ul>
 */
public class DocketFeature extends Feature<NoneFeatureConfiguration> {

    /** How far up and down {@link RuinGround#surfaceNear} may look for real ground. */
    private static final int SURFACE_REACH = 64;

    private static final long DELEGATE_SALT = 0x0DE_1E6A7EL;

    /** What happened to a berth. Counted, never logged - see {@link #tally}. */
    public enum Verdict {
        /** The delegate was called and said yes. */
        SEATED,
        /** The delegate said no. Its own business, and not a fault. */
        DECLINED,
        /** Nothing is listed under that id. The catalogue changed under a world. */
        UNLISTED,
        /** The footprint does not fit inside a chunk. Refused before the delegate. */
        REFUSED_ENVELOPE,
        /** No real ground within {@value #SURFACE_REACH} of the berth's column. */
        REFUSED_SURFACE,
        /** The ground will not hold it. */
        REFUSED_FOOTING,
        /** It asked to be dry and the site is not. */
        REFUSED_WET,
        /** Something is already there. */
        REFUSED_STRUCTURE
    }

    // In memory, per listing, and never persisted. The first refusal telemetry
    // in this mod: until now a feature that declined every site it was offered
    // looked exactly like one that was never offered a site. Read directly with
    // no tick, which is what lets a gametest assert on it.
    private static final Map<String, AtomicLongArray> TALLY = new ConcurrentHashMap<>();

    public DocketFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!OctiaWorldgen.active()) {
            return false;
        }

        List<Listing> listings = DocketCatalogue.listings();
        if (listings.isEmpty()) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        List<Berth> due = Docket.inChunk(level.getSeed(), origin.getX() >> 4, origin.getZ() >> 4, listings);
        if (due.isEmpty()) {
            return false;
        }

        boolean any = false;
        for (Berth berth : due) {
            DocketCatalogue.Entry entry = DocketCatalogue.byId(berth.listingId());
            if (entry == null) {
                count(berth.listingId(), Verdict.UNLISTED);
                continue;
            }
            // Seeded per berth, so a second berth in this chunk cannot move the
            // first one's draws and a refusal costs nothing.
            RandomSource seeded = RandomSource.create(Sightlines.hash(level.getSeed(),
                    Docket.subcell(berth.x()), Docket.subcell(berth.z()),
                    DELEGATE_SALT ^ entry.listing().anchor()));
            any |= seat(level, context.chunkGenerator(), seeded, berth, entry);
        }
        return any;
    }

    /**
     * Vet one berth and, if the ground answers, let its delegate build there.
     *
     * <p>Public and taking everything it needs, so a gametest can drive it on a
     * plot it controls. A plot never contains a berth for a world seed that
     * changes every run, so the alternative would be a test that could only ever
     * assert "nothing happened" - the same seam {@code ArchFeature.raise} and
     * {@code WatershedFeature.carve} were split out for.
     *
     * @return whether anything was actually built
     */
    public static boolean seat(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                              Berth berth, DocketCatalogue.Entry entry) {
        Listing listing = entry.listing();
        String id = listing.id();

        if (listing.footprint() > Massing.REACH) {
            // Refused before the delegate is even looked up, so a listing that
            // could reach outside its chunk never gets the chance.
            count(id, Verdict.REFUSED_ENVELOPE);
            return false;
        }

        // surfaceNear answers with the free space you would STAND in, not the
        // block you would stand on. Every other caller in this package knows
        // that and passes below(1) to hasFooting - ArchFeature, ObeliskFeature,
        // TemplateRuinFeature and WatershedFeature all do. This one did not, and
        // refused perfectly good ground on a stone floor because it asked
        // whether the air above it was solid.
        BlockPos column = new BlockPos(berth.x(), 0, berth.z());
        BlockPos standing = RuinGround.surfaceNear(level, column, SURFACE_REACH, SURFACE_REACH);
        if (standing == null) {
            count(id, Verdict.REFUSED_SURFACE);
            return false;
        }

        if (!RuinGround.hasFooting(level, standing.below(), listing.footprint())) {
            count(id, Verdict.REFUSED_FOOTING);
            return false;
        }

        if (listing.dry() && !RuinGround.isDry(level, standing, listing.footprint(), 1, 2)) {
            count(id, Verdict.REFUSED_WET);
            return false;
        }

        // No flag, and deliberately last: it is the most expensive question and
        // the one nothing is allowed to skip. Widened to nothing - the berth's
        // own chunk is the whole extent a feature may claim.
        if (!RuinGround.clearOfStructures(level, standing,
                listing.footprint(), listing.footprint(), listing.footprint())) {
            count(id, Verdict.REFUSED_STRUCTURE);
            return false;
        }

        Holder<ConfiguredFeature<?, ?>> holder = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(entry.feature());

        boolean built = holder.value().place(level, generator, random, standing);
        count(id, built ? Verdict.SEATED : Verdict.DECLINED);
        return built;
    }

    private static void count(String id, Verdict verdict) {
        TALLY.computeIfAbsent(id, k -> new AtomicLongArray(Verdict.values().length))
                .incrementAndGet(verdict.ordinal());
    }

    /**
     * How many times this listing met this verdict, since the tally was last
     * cleared. Read directly, with no tick and no packet.
     */
    public static long tally(String id, Verdict verdict) {
        AtomicLongArray counts = TALLY.get(id);
        return counts == null ? 0L : counts.get(verdict.ordinal());
    }

    /** Forget every count. Called when a server stops, and between gametests. */
    public static void clearTally() {
        TALLY.clear();
    }
}
