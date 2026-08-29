package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.serenity.octia.world.Docket.Berth;
import com.serenity.octia.world.Docket.Lane;
import com.serenity.octia.world.Docket.Listing;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The docket's arithmetic, proved without a world.
 *
 * <p>Everything here runs on plain Java. {@link Docket} imports no Minecraft and
 * no Fabric, which is what lets the density identity, the stability guarantees
 * and the uniformity of the draw be asserted at {@code gradlew build} rather
 * than argued in a comment. The in-world half - siting, refusal, seating - is
 * {@code DocketGameTest}'s job, per AGENTS.md III.
 */
@DisplayName("what is due, and where, is arithmetic")
class DocketTest {

    /** The waystation's real rarity today, so the identity is proved against a live number. */
    private static final int WAYSTATION_PER_CHUNKS = 900;

    private static final Listing WAYSTATION =
            Listing.of("octia:waystation", WAYSTATION_PER_CHUNKS, Lane.LANDMARK, 5, true);
    private static final Listing DERELICT =
            Listing.of("octia:derelict", 800, Lane.LANDMARK, 7, false);
    private static final Listing WATERSHED =
            Listing.of("octia:watershed", 520, Lane.WATER, 6, false);

    // ---- density -----------------------------------------------------------

    @Test
    @DisplayName("density matches the rarity_filter it replaces, to within 1%")
    void densityMatchesTheRarityItReplaces() {
        // A listing berths in one subcell in every perChunks/64, and the berth
        // falls in one named chunk of 64. Per chunk that is 1/perChunks - which
        // is exactly what rarity_filter perChunks already gives. Measured at the
        // subcell, because walking all 64 chunks to find the one answer is 64x
        // the work for the same number.
        long berthed = 0;
        long subcells = 0;
        for (long seed = 0; seed < 40; seed++) {
            for (int sx = -25; sx <= 25; sx++) {
                for (int sz = -25; sz <= 25; sz++) {
                    subcells++;
                    if (Docket.berth(seed, sx, sz, WAYSTATION) != null) {
                        berthed++;
                    }
                }
            }
        }

        // Expected rate per subcell, in parts per million so the assertion needs
        // no floating point of its own.
        long expectedPpm = 1_000_000L * Docket.CHUNKS_PER_SUBCELL / WAYSTATION_PER_CHUNKS;
        long actualPpm = 1_000_000L * berthed / subcells;
        long drift = Math.abs(actualPpm - expectedPpm);
        assertTrue(drift * 100 <= expectedPpm,
                "expected ~" + expectedPpm + " ppm of subcells berthed, got " + actualPpm
                        + " over " + subcells + " subcells");
    }

    @Test
    @DisplayName("a cell's expected berths is 1024/perChunks, which is the rarity over 1024 chunks")
    void theCellIdentityHolds() {
        assertEquals(1024, Docket.SUBCELLS_PER_CELL * Docket.CHUNKS_PER_SUBCELL,
                "a cell is 512 blocks, a chunk is 16, so a cell is 32x32 chunks");
        assertEquals(Sightlines.SPACING, Docket.SUBCELL * 4,
                "the subcell must divide the sightlines cell exactly, or it is a second grid");
    }

    // ---- stability, which is what makes install and uninstall safe ---------

    @Test
    @DisplayName("adding a listing moves no existing berth")
    void addingAListingMovesNoExistingBerth() {
        List<Listing> before = List.of(WAYSTATION, DERELICT);
        List<Listing> after = List.of(WAYSTATION, DERELICT, WATERSHED);

        for (int sx = -160; sx < 160; sx++) {
            for (int sz = -160; sz < 160; sz++) {
                for (Listing listing : before) {
                    assertEquals(Docket.berth(7L, sx, sz, listing), Docket.berth(7L, sx, sz, listing),
                            "the draw is not even stable against itself");
                }
            }
        }

        // The load-bearing claim: a listing's berth depends on its own anchor
        // and nothing else that is listed. This goes red the moment somebody
        // "optimises" the draw into a shared budget.
        assertEquals(berthsOver(before, 40), berthsOver(before, 40));
        List<String> keptBefore = berthsOver(before, 40);
        List<String> keptAfter = berthsOver(after, 40).stream()
                .filter(s -> !s.startsWith("octia:watershed")).toList();
        assertEquals(keptBefore, keptAfter, "installing a listing moved somebody else's berth");
    }

    @Test
    @DisplayName("removing a listing moves no surviving berth")
    void removingAListingMovesNoSurvivingBerth() {
        // The uninstall case, and the one a stored queue cannot pass without a
        // migration. Somebody drops a contributor from an existing world and the
        // rest of the world must not shift under them.
        List<String> withAll = berthsOver(List.of(WAYSTATION, DERELICT, WATERSHED), 40).stream()
                .filter(s -> !s.startsWith("octia:derelict")).toList();
        List<String> without = berthsOver(List.of(WAYSTATION, WATERSHED), 40);
        assertEquals(withAll, without);
    }

    @Test
    @DisplayName("the order the listings arrive in does not matter")
    void theOrderOfListingsDoesNotMatter() {
        List<Listing> one = List.of(WAYSTATION, DERELICT, WATERSHED);
        List<Listing> other = List.of(WATERSHED, WAYSTATION, DERELICT);
        assertEquals(berthsOver(one, 60), berthsOver(other, 60),
                "the catalogue's iteration order reached the world");
    }

    // ---- the draw itself ---------------------------------------------------

    @Test
    @DisplayName("a berth stays inside the subcell that drew it")
    void berthsStayInsideTheirOwnSubcell() {
        int escapes = 0;
        for (long seed = 0; seed < 8; seed++) {
            for (int sx = -180; sx < 180; sx++) {
                for (int sz = -180; sz < 180; sz++) {
                    Berth b = Docket.berth(seed, sx, sz, WAYSTATION);
                    if (b == null) {
                        continue;
                    }
                    if (Docket.subcell(b.x()) != sx || Docket.subcell(b.z()) != sz) {
                        escapes++;
                    }
                }
            }
        }
        assertEquals(0, escapes, "a berth left its own subcell, so a chunk cannot claim it");
    }

    @Test
    @DisplayName("every one of a subcell's 64 chunks is reachable, and near enough evenly")
    void everyChunkOfASubcellIsReachable() {
        // The test that catches a draw which clamps a jittered position instead
        // of choosing a chunk: a clamped scheme leaves edge chunks permanently
        // empty and nothing else notices.
        long[] seen = new long[64];
        long total = 0;
        for (long seed = 0; seed < 200; seed++) {
            for (int sx = -40; sx < 40; sx++) {
                for (int sz = -40; sz < 40; sz++) {
                    Berth b = Docket.berth(seed, sx, sz, WAYSTATION);
                    if (b == null) {
                        continue;
                    }
                    int seat = (Math.floorMod(b.x() >> 4, 8) << 3) | Math.floorMod(b.z() >> 4, 8);
                    seen[seat]++;
                    total++;
                }
            }
        }

        assertTrue(total > 20_000, "not enough berths drawn to say anything: " + total);
        for (int seat = 0; seat < 64; seat++) {
            assertTrue(seen[seat] > 0, "chunk " + seat + " of a subcell is never used");
        }

        // Chi-square against uniform, in integers. 63 degrees of freedom; the
        // 0.001 critical value is about 103, so 150 is a wide net that still
        // catches a systematically dead or doubled bucket.
        long expected = total / 64;
        long chi = 0;
        for (long count : seen) {
            long d = count - expected;
            chi += d * d / expected;
        }
        assertTrue(chi < 150, "the chunk within a subcell is not uniform: chi-square " + chi);
    }

    @Test
    @DisplayName("two berths of one listing are never in the same chunk")
    void twoBerthsOfOneListingAreNeverInTheSameChunk() {
        Set<Long> chunks = new HashSet<>();
        for (int sx = -120; sx < 120; sx++) {
            for (int sz = -120; sz < 120; sz++) {
                Berth b = Docket.berth(11L, sx, sz, DERELICT);
                if (b == null) {
                    continue;
                }
                long key = ((long) (b.x() >> 4) << 32) ^ (b.z() >> 4);
                assertTrue(chunks.add(key), "two derelicts due in one chunk at " + b.x() + "," + b.z());
            }
        }
    }

    @Test
    @DisplayName("every berth is claimed by exactly one chunk")
    void everyBerthIsClaimedByExactlyOneChunk() {
        // A berth no chunk answers with is content that exists in the function
        // and never in the world - the failure BeamlineTest.everyStationIsClaimed
        // exists to catch, applied here.
        List<Listing> all = List.of(WAYSTATION, DERELICT, WATERSHED);
        int claimed = 0;
        int drawn = 0;
        for (int sx = -18; sx < 18; sx++) {
            for (int sz = -18; sz < 18; sz++) {
                for (Listing listing : all) {
                    Berth b = Docket.berth(3L, sx, sz, listing);
                    if (b == null) {
                        continue;
                    }
                    drawn++;
                    List<Berth> due = Docket.inChunk(3L, b.x() >> 4, b.z() >> 4, all);
                    if (due.contains(b)) {
                        claimed++;
                    }
                }
            }
        }
        assertTrue(drawn > 100, "not enough berths to prove anything: " + drawn);
        assertEquals(drawn, claimed, "a berth was drawn that its own chunk does not answer with");
    }

    @Test
    @DisplayName("a lane holds at most one berth per chunk")
    void aLaneHoldsAtMostOneBerthPerChunk() {
        // Two LANDMARK listings deliberately, so arbitration has something to do.
        List<Listing> all = List.of(WAYSTATION, DERELICT, WATERSHED);
        for (int cx = -400; cx < 400; cx++) {
            for (int cz = -12; cz < 12; cz++) {
                List<Berth> due = Docket.inChunk(5L, cx, cz, all);
                Set<Lane> lanes = new HashSet<>();
                for (Berth b : due) {
                    assertTrue(lanes.add(b.lane()),
                            "two " + b.lane() + " berths in chunk " + cx + "," + cz);
                }
            }
        }
    }

    @Test
    @DisplayName("the subcell grid covers negative space")
    void docketsCoverNegativeSpace() {
        // floorDiv on negatives is where this family of bugs lives, which is why
        // SightlinesTest has the same test for its own grid.
        assertEquals(-1, Docket.subcell(-1));
        assertEquals(-1, Docket.subcell(-128));
        assertEquals(-2, Docket.subcell(-129));
        assertEquals(0, Docket.subcell(0));
        assertEquals(0, Docket.subcell(127));
        assertEquals(1, Docket.subcell(128));

        int found = 0;
        for (int sx = -60; sx < 0; sx++) {
            for (int sz = -60; sz < 0; sz++) {
                if (Docket.berth(2L, sx, sz, DERELICT) != null) {
                    found++;
                }
            }
        }
        assertTrue(found > 100, "almost nothing berths in negative space: " + found);
    }

    // ---- the promises the file makes about itself --------------------------

    @Test
    @DisplayName("the draw is integer-only, and the source says so too")
    void theDrawIsIntegerOnly() throws Exception {
        String dir = System.getProperty("octia.projectDir");
        assertNotNull(dir, "octia.projectDir was not set; see tasks.test in build.gradle.kts");
        Path source = Path.of(dir, "src", "main", "java", "com", "serenity", "octia", "world", "Docket.java");
        assertTrue(Files.exists(source), "cannot find " + source);

        // Comments talk about doubles; code must not contain one. Strip block
        // comments and line comments before looking, or this asserts the
        // opposite of what it means.
        String code = Files.readString(source, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\n]*", "");

        for (String banned : List.of("double", "float", "Math.log", "Math.pow", "Math.sqrt", "Math.random")) {
            assertFalse(code.contains(banned),
                    "Docket.java contains '" + banned + "'. A one-ULP difference between two JVMs is a "
                            + "save that generates differently on two machines.");
        }
    }

    @Test
    @DisplayName("the same question from two threads gets the same answer")
    void theDrawIsThreadStable() throws Exception {
        List<Listing> all = List.of(WAYSTATION, DERELICT, WATERSHED);
        AtomicReference<List<Berth>> fromOther = new AtomicReference<>();
        List<Berth> mine = new ArrayList<>();

        Thread other = new Thread(() -> {
            List<Berth> theirs = new ArrayList<>();
            for (int cx = 0; cx < 4000; cx++) {
                theirs.addAll(Docket.inChunk(9L, cx, 17, all));
            }
            fromOther.set(theirs);
        });
        other.start();
        for (int cx = 0; cx < 4000; cx++) {
            mine.addAll(Docket.inChunk(9L, cx, 17, all));
        }
        other.join();

        assertFalse(mine.isEmpty(), "nothing was due anywhere, so this proves nothing");
        assertEquals(mine, fromOther.get(), "two threads disagreed about what is due");
    }

    // ---- listings refuse rather than clamp ---------------------------------

    @Test
    @DisplayName("a rarity outside the expressible range is refused, not clamped")
    void aBadRarityIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> Listing.of("octia:too_dense", Docket.MIN_PER_CHUNKS - 1, Lane.PROP, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> Listing.of("octia:too_sparse", Docket.MAX_PER_CHUNKS + 1, Lane.PROP, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> Listing.of("", 900, Lane.PROP, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> Listing.of("octia:flat", 900, Lane.PROP, 0, false));
    }

    @Test
    @DisplayName("an anchor comes from the id, so two installs agree without a registry")
    void anchorsAreDerivedFromTheId() {
        assertEquals(Docket.anchorOf("octia:waystation"), WAYSTATION.anchor());
        assertEquals(Listing.of("a:b", 900, Lane.PROP, 1, false).anchor(),
                Listing.of("a:b", 512, Lane.WATER, 3, true).anchor(),
                "the anchor must depend on the id alone, or a re-tune moves the berth");
        assertTrue(Docket.anchorOf("octia:a") != Docket.anchorOf("octia:b"));
    }

    @Test
    @DisplayName("nothing listed means nothing due, and it costs nothing to ask")
    void anEmptyCatalogueIsDueNothing() {
        assertTrue(Docket.inChunk(1L, 0, 0, List.of()).isEmpty());
        assertTrue(Docket.inChunk(1L, 0, 0, null).isEmpty());
        assertNull(Docket.berth(1L, 0, 0, Listing.of("octia:never", Docket.MAX_PER_CHUNKS, Lane.PROP, 1, false)),
                "the sparsest listing should miss this particular subcell");
    }

    // ---- helper ------------------------------------------------------------

    /** Every berth over a square of subcells, as sortable strings, for set comparison. */
    private static List<String> berthsOver(List<Listing> listings, int radius) {
        List<String> out = new ArrayList<>();
        for (int sx = -radius; sx < radius; sx++) {
            for (int sz = -radius; sz < radius; sz++) {
                for (Listing listing : listings) {
                    Berth b = Docket.berth(13L, sx, sz, listing);
                    if (b != null) {
                        out.add(b.listingId() + "@" + b.x() + "," + b.z());
                    }
                }
            }
        }
        String[] sorted = out.toArray(new String[0]);
        Arrays.sort(sorted);
        return List.of(sorted);
    }
}
