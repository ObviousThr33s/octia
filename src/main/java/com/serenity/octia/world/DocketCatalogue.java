package com.serenity.octia.world;

import com.serenity.octia.world.Docket.Listing;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * What is listed on the docket, and what builds it.
 *
 * <p>{@link Docket} answers <i>what is due here</i> out of arithmetic alone. This
 * is the other half: which listing corresponds to which configured feature, and
 * therefore what actually gets built when a berth is honoured.
 *
 * <h2>Discovered at runtime, frozen before the first chunk</h2>
 *
 * <p>"Load them dynamically" cannot mean a table that changes while chunks
 * generate. Features run on several generation workers at once, and a catalogue
 * that could be written to mid-generation would have to be locked on a path
 * taken thousands of times a second - and worse, two chunks of one world could
 * then disagree about what was due. So the catalogue is built once, {@linkplain
 * #freeze frozen}, and is thereafter read-only for the life of the server. The
 * dynamic half is that <b>the set of contributors is discovered</b> before the
 * freeze; it is not that the set can change after it.
 *
 * <p>The published list is held in a {@code volatile} field and replaced whole
 * rather than mutated, so a worker either sees the old list or the new one and
 * never a half-built one.
 *
 * <h2>Nothing is listed yet, and that is deliberate</h2>
 *
 * <p>{@link #SHIPPED} is empty. The carrier and the arithmetic land first and
 * change nothing in any world; migrating a real feature off its
 * {@code rarity_filter} is a separate step because it moves what generates and
 * needs the seed seam measured rather than assumed.
 *
 * <p><b>And the contribution seam is not open.</b> Letting another mod list
 * something means writing Octia's mod id into files inside <i>their</i> mod -
 * a datapack namespace, or an entrypoint key. AGENTS.md II promises this mod
 * will be renamed, and {@code tools/rename-mod.ps1} moves only
 * {@code data/$oldModId} while grepping file <i>contents</i> for survivors. So
 * after a rename third-party listings would vanish <b>and the tool would report
 * that no stray references remain</b>. The seam opens when the name is final,
 * and not before.
 */
public final class DocketCatalogue {

    /**
     * One listing, and the configured feature that builds it.
     *
     * <p>The feature is held as a {@link ResourceKey} rather than a resolved
     * holder, because a holder belongs to one registry access and a server can
     * reload its datapacks underneath us. The key is looked up at place time
     * against whatever registry the level actually has.
     */
    public record Entry(Listing listing, ResourceKey<ConfiguredFeature<?, ?>> feature) {

        public Entry {
            if (listing == null) {
                throw new IllegalArgumentException("an entry needs a listing");
            }
            if (feature == null) {
                throw new IllegalArgumentException("'" + listing.id() + "' names no feature to build");
            }
        }
    }

    /**
     * What this mod itself lists. Empty on purpose - see the class note.
     *
     * <p>When the waystation moves here, its listing is
     * {@code perChunks 900} to match the {@code rarity_filter} it leaves behind,
     * lane {@code LANDMARK}, and it keeps its existing
     * {@code configured_feature/waystation.json}.
     */
    public static final List<Entry> SHIPPED = List.of();

    // Replaced whole, never mutated. A worker sees one complete list or the
    // other, and freeze() is the only writer.
    private static volatile List<Entry> published = List.of();
    private static volatile Map<String, Entry> byId = Map.of();
    private static volatile List<Listing> listings = List.of();

    private DocketCatalogue() {
    }

    /**
     * Settle the catalogue for this server's life.
     *
     * <p>Refuses a duplicate id rather than letting the last one win: two
     * listings with one id would draw on one anchor, berth in the same place,
     * and then race to be the one that built there.
     *
     * @throws IllegalArgumentException on a duplicate id
     */
    public static void freeze(List<Entry> entries) {
        List<Entry> settled = new ArrayList<>(entries == null ? List.of() : entries);
        Map<String, Entry> index = new LinkedHashMap<>();
        List<Listing> names = new ArrayList<>(settled.size());

        for (Entry entry : settled) {
            if (index.putIfAbsent(entry.listing().id(), entry) != null) {
                throw new IllegalArgumentException(
                        "'" + entry.listing().id() + "' is listed twice; one id is one anchor is one berth");
            }
            names.add(entry.listing());
        }

        published = List.copyOf(settled);
        byId = Map.copyOf(index);
        listings = List.copyOf(names);
    }

    /** Everything listed, in the order it was frozen. */
    public static List<Entry> published() {
        return published;
    }

    /**
     * Just the listings, which is all {@link Docket#inChunk} needs.
     *
     * <p>Held rather than rebuilt per call: this is read once per chunk per
     * generation worker, and allocating a list there would be the only
     * allocation on an otherwise arithmetic path.
     */
    public static List<Listing> listings() {
        return listings;
    }

    /** The entry with this id, or {@code null} if nothing is listed under it. */
    public static Entry byId(String id) {
        return byId.get(id);
    }

    /**
     * Empty the catalogue.
     *
     * <p>Called when a server stops, so that a single-player client that opens
     * one world and then another does not carry the first world's listings into
     * the second.
     */
    public static void clear() {
        published = List.of();
        byId = Map.of();
        listings = List.of();
    }
}
