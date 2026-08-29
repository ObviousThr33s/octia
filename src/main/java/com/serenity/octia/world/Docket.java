package com.serenity.octia.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is scheduled to appear where, as arithmetic.
 *
 * <p>Before this, five features rolled five independent per-chunk dice -
 * {@code rarity_filter} 800, 520, 900, 800 and 260 - with no shared budget and
 * nothing deconflicting them. Two could land in one chunk and neither would
 * know. This is the first thing in the mod that can answer <b>"what is due
 * here"</b> before anything is built.
 *
 * <p><b>It is a pure function and it stores nothing.</b> No {@code SavedData},
 * no per-chunk record, no cross-thread read. Worldgen runs on many workers at
 * once and {@code docs/THREADS.md} VII already refused stored per-chunk state by
 * name; a queue that had to be written to would have to be locked. The same
 * seed and the same catalogue give the same answer on every thread, every
 * machine and every reload, because the answer was never kept anywhere to
 * disagree with.
 *
 * <h2>The unit is a subcell, and there is no such thing as an island</h2>
 *
 * <p>This was the first thing the design got wrong. {@link Isle} is the radius
 * profile of the one emergency island {@link Landfall} raises when a spawn
 * column comes up empty; the world's islands are a continuous swell field with
 * no id, no extent and no enumeration, and nothing in this mod can answer "which
 * island am I on". {@code docs/ISLANDS.md} X measures 0.0% empty chunks out of
 * 1,681, so there is not even a gap to separate one from the next.
 *
 * <p>So the unit is the one quantiser this mod owns: {@link Sightlines#cell},
 * divided 4x4 into <b>subcells of {@value #SUBCELL} blocks</b>. That is an
 * integer subdivision and never a second grid - {@link Sightlines#SPACING} is
 * untouched. A subcell is exactly 8x8 = {@value #CHUNKS_PER_SUBCELL} chunks, and
 * because 128 is a multiple of 16 a chunk lies wholly inside exactly one.
 *
 * <h2>Density is preserved by identity, not by tuning</h2>
 *
 * <p>A listing gates on {@code floorMod(gate, perChunks) < 64}, so it berths in
 * one subcell in every {@code perChunks / 64}, and the berth falls in one named
 * chunk out of 64. Per chunk that is exactly {@code 1 / perChunks}; over a
 * cell's 1,024 chunks it is {@code 1024 / perChunks} - <b>which is what a
 * {@code rarity_filter} of {@code perChunks} already gives over the same
 * ground</b>. Nothing has to be re-tuned and no number has to be chosen, which
 * is what {@code ROADMAP} V and {@code RUIN-PANEL} IV both ask for.
 *
 * <h2>No floating point, anywhere in this file</h2>
 *
 * <p>Not carefulness about {@code StrictMath} - absence. There is no
 * {@code double} and no {@code float} in the draw at all, because a one-ULP
 * disagreement between two JVMs is a save that generates differently on two
 * machines, and no amount of care makes a transcendental safe to put in a
 * worldgen path. {@code DocketTest} asserts the absence at source level.
 *
 * @see DocketCatalogue for what is listed
 * @see DocketFeature for the carrier that vets a berth and seats it
 */
public final class Docket {

    /** The side of a subcell, in blocks. An integer quarter of {@link Sightlines#SPACING}. */
    public static final int SUBCELL = 128;

    /** 8x8. A subcell is 128 blocks and a chunk is 16, so this is exact. */
    public static final int CHUNKS_PER_SUBCELL = 64;

    /** 4x4. {@link Sightlines#SPACING} / {@link #SUBCELL}. */
    public static final int SUBCELLS_PER_CELL = 16;

    /**
     * The densest a listing may be. One berth per 64 chunks is one per subcell,
     * which is the most this scheme can express - {@code floorMod(g, 64) < 64}
     * is every subcell, and asking for denser would silently saturate rather
     * than obey. Refused loudly instead.
     */
    public static final int MIN_PER_CHUNKS = CHUNKS_PER_SUBCELL;

    /** Sparse enough that a berth is a rumour. Bounded so the modulus stays sane. */
    public static final int MAX_PER_CHUNKS = 65536;

    // Three salts, because three independent questions are asked of one
    // (seed, subcell, listing). Deriving all three from ONE hash was the first
    // draft and it is subtly wrong: `floorMod(h, 900)` is a function of every
    // bit of h, so conditioning on the gate biases whichever bits the position
    // is then read out of. The bias is small and a chi-square over 64 chunk
    // indices is exactly the test that would find it. Three mixes cost nothing
    // here and remove the whole question.
    private static final long BERTH_SALT = 0x00_D0C_4E7L;
    private static final long PLACE_SALT = 0x0B_E5_7A6EL;
    private static final long SEAT_SALT = 0x5EA7_1A6L;

    private Docket() {
    }

    /**
     * Which lane a listing competes in. At most one berth per lane per chunk
     * survives, so two landmarks never land on each other while a landmark and
     * a path still may.
     */
    public enum Lane {
        /** Something you walk to and see from away. Ruins, stations, obelisks. */
        LANDMARK,
        /** Water that had to be dug or held. Watersheds, springs. */
        WATER,
        /** Ground that leads somewhere. Stairways, tracks. */
        PATH,
        /** Small dressing that never has to be the reason you came. */
        PROP
    }

    /**
     * One thing that may be due somewhere.
     *
     * @param id        what it is, namespaced by whoever listed it
     * @param anchor    the listing's stable place in the hash space, from {@link #anchorOf}
     * @param perChunks one berth per this many chunks, matching the rarity it replaces
     * @param lane      what it competes with
     * @param footprint how far from the berth it may reach, in blocks
     * @param dry       whether a wet site is refused. Narrows; never defaults on
     */
    public record Listing(String id, long anchor, int perChunks, Lane lane, int footprint, boolean dry) {

        public Listing {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("a listing needs an id");
            }
            if (perChunks < MIN_PER_CHUNKS || perChunks > MAX_PER_CHUNKS) {
                // Refused rather than clamped. A clamped rarity is a density
                // that silently disagrees with the number somebody wrote down.
                throw new IllegalArgumentException(
                        "perChunks is " + MIN_PER_CHUNKS + ".." + MAX_PER_CHUNKS + " and '" + id
                                + "' asks for " + perChunks);
            }
            if (footprint < 1) {
                throw new IllegalArgumentException("'" + id + "' has a footprint of " + footprint);
            }
            if (lane == null) {
                throw new IllegalArgumentException("'" + id + "' names no lane");
            }
        }

        /** A listing with its anchor derived from its id, which is the only way to build one. */
        public static Listing of(String id, int perChunks, Lane lane, int footprint, boolean dry) {
            return new Listing(id, anchorOf(id), perChunks, lane, footprint, dry);
        }
    }

    /**
     * A berth: one listing, due at one block column.
     *
     * <p>The coordinates are absolute block coordinates, not an offset. An
     * offset origin is what makes a containment test lie, which is why the
     * placed feature carries no {@code in_square}.
     */
    public record Berth(String listingId, int x, int z, Lane lane) {
    }

    /** Which subcell a block coordinate is in. */
    public static int subcell(int coord) {
        return Math.floorDiv(coord, SUBCELL);
    }

    /**
     * A listing's stable position in the hash space, from its id alone.
     *
     * <p>Derived rather than assigned, so that installing two listings in either
     * order gives both the same anchors, and so that nobody has to keep a
     * registry of numbers in step with a registry of names.
     */
    public static long anchorOf(String id) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) {
            h ^= id.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /**
     * Everything due in this chunk, already arbitrated, in the order it should
     * be seated.
     *
     * <p><b>This consults one subcell and never a neighbour.</b> That is not
     * thrift, it is the {@code WorldGenRegion} rule: a feature may only read
     * chunks 0..8 from the one being generated, and a
     * scheme that had to look at the subcell next door to know what is due here
     * would break it the moment a berth landed near an edge. Because the berth's
     * chunk is drawn <i>directly</i> rather than jittered out of a position, the
     * question never arises.
     *
     * <p>Empty is the common answer and costs three mixes per listing.
     */
    public static List<Berth> inChunk(long seed, int chunkX, int chunkZ, List<Listing> listings) {
        if (listings == null || listings.isEmpty()) {
            return List.of();
        }

        // A subcell is 8 chunks on a side. floorDiv, not >>, because a chunk at
        // a negative coordinate is where this family of bugs lives - see
        // SightlinesTest.cellsCoverNegativeSpace, which exists for the same
        // reason.
        int subX = Math.floorDiv(chunkX, 8);
        int subZ = Math.floorDiv(chunkZ, 8);
        int seatHere = (Math.floorMod(chunkX, 8) << 3) | Math.floorMod(chunkZ, 8);

        List<Berth> due = new ArrayList<>(2);
        for (Listing listing : listings) {
            Berth berth = berth(seed, subX, subZ, listing);
            if (berth != null && seatOf(berth) == seatHere) {
                due.add(berth);
            }
        }

        return due.size() < 2 ? List.copyOf(due) : arbitrate(seed, subX, subZ, due, listings);
    }

    /**
     * The one berth this listing has in this subcell, or {@code null} for none.
     *
     * <p>The subcell is the unit the draw actually works in, and
     * {@link #inChunk} is a filter over this. It is public because a density or
     * uniformity proof that had to walk all 64 chunks of every subcell to find
     * the one answer would be 64 times the work for the same number, and a test
     * nobody runs proves nothing.
     *
     * <p>Note the berth's chunk is <b>drawn</b>, not clamped from a jittered
     * position. Every one of the subcell's 64 chunks is therefore reachable with
     * equal probability, and two berths of one listing can never share a chunk
     * because a listing has at most one berth per subcell and a chunk belongs to
     * exactly one subcell. Both are asserted rather than argued.
     */
    public static Berth berth(long seed, int subX, int subZ, Listing listing) {
        long gate = Sightlines.hash(seed, subX, subZ, BERTH_SALT ^ listing.anchor());
        if (Math.floorMod(gate, listing.perChunks()) >= CHUNKS_PER_SUBCELL) {
            return null;
        }
        long place = Sightlines.hash(seed, subX, subZ, PLACE_SALT ^ listing.anchor());
        int seat = (int) ((place >>> 8) & 63);
        int chunkX = (subX << 3) + (seat >>> 3);
        int chunkZ = (subZ << 3) + (seat & 7);
        return new Berth(listing.id(),
                (chunkX << 4) + (int) ((place >>> 20) & 15),
                (chunkZ << 4) + (int) ((place >>> 36) & 15),
                listing.lane());
    }

    /** Which of a subcell's 64 chunks a berth sits in, 0..63. */
    private static int seatOf(Berth berth) {
        return (Math.floorMod(berth.x() >> 4, 8) << 3) | Math.floorMod(berth.z() >> 4, 8);
    }

    /**
     * At most one berth per lane, and a stable order.
     *
     * <p>The ranking key is per-listing and depends on nothing else that is due,
     * so removing a listing removes exactly its own entry and moves no other -
     * the surviving order is a stable subsequence. That is what makes installing
     * and uninstalling a contributor safe on a world that already exists, and
     * {@code DocketTest} asserts it over 100,000 cells. A shared budget would
     * lose the property immediately, which is why there is not one.
     */
    private static List<Berth> arbitrate(long seed, int subX, int subZ,
                                         List<Berth> due, List<Listing> listings) {
        Map<String, Long> anchors = new HashMap<>();
        for (Listing listing : listings) {
            anchors.put(listing.id(), listing.anchor());
        }

        List<Berth> ranked = new ArrayList<>(due);
        ranked.sort(Comparator
                .comparingLong((Berth b) -> Sightlines.hash(seed, subX, subZ, SEAT_SALT ^ anchors.get(b.listingId())))
                .reversed()
                // Ties on the anchor, never on the id's natural order: two ids
                // that hash alike must still resolve the same way on every
                // machine, and String ordering is the same everywhere but says
                // nothing about which was listed first.
                .thenComparingLong(b -> anchors.get(b.listingId())));

        Map<Lane, Berth> kept = new EnumMap<>(Lane.class);
        List<Berth> out = new ArrayList<>(ranked.size());
        for (Berth berth : ranked) {
            if (kept.putIfAbsent(berth.lane(), berth) == null) {
                out.add(berth);
            }
        }
        return List.copyOf(out);
    }
}
