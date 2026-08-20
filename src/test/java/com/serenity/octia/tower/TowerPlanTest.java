package com.serenity.octia.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tower is authored by a person rather than derived from the seed, which makes
 * this the only input in the mod that is not trusted. So the tests below are as
 * much about what is <em>refused</em> as about what is built.
 *
 * <p>Tested here rather than as a GameTest because {@link TowerPlan} has no
 * Minecraft on its imports, deliberately: the gates run headless and can never
 * defend how a tower looks, so everything that can be proven was pushed to this
 * side of the line. What a description turns into is exactly the part a machine
 * can check, and it is checked exhaustively.
 */
class TowerPlanTest {

    /** Five columns, in the measure the arches use. */
    private static final List<String> BAY = List.of(
            "..*..",
            ".###.",
            "#+#+#",
            "##O##");

    @Test
    @DisplayName("a drawing keeps the shape it was drawn in")
    void parseKeepsTheShape() {
        TowerPlan plan = TowerPlan.parse(BAY);
        assertEquals(5, plan.width());
        assertEquals(4, plan.height());
        assertEquals(TowerPlan.Cell.BEACON, plan.at(2, 0));
        assertEquals(TowerPlan.Cell.CORE, plan.at(2, 3));
        assertEquals(TowerPlan.Cell.EMPTY, plan.at(0, 0));
    }

    /**
     * Rows arrive top first because that is how they are drawn, and the world
     * counts up from the ground. Getting this backwards builds every tower
     * upside down, and a symmetric test drawing would never notice.
     */
    @Test
    @DisplayName("the top row of the drawing is the top of the tower")
    void rowsInvertIntoHeight() {
        TowerPlan plan = TowerPlan.parse(BAY);
        TowerPlan.Placement core = plan.core();
        assertEquals(2, core.x());
        assertEquals(0, core.y(), "the core is on the bottom row, so it is at y zero");

        // The beacon is drawn on the top row, so it must come out highest.
        List<TowerPlan.Placement> beacons = plan.placements(1).stream()
                .filter(p -> p.cell() == TowerPlan.Cell.BEACON)
                .toList();
        assertEquals(1, beacons.size());
        assertEquals(plan.height() - 1, beacons.get(0).y());
    }

    @Test
    @DisplayName("empty cells put nothing in the world")
    void emptyCellsArePlacedAsNothing() {
        List<TowerPlan.Placement> placed = TowerPlan.parse(BAY).placements(1);
        assertTrue(placed.stream().noneMatch(p -> p.cell() == TowerPlan.Cell.EMPTY));
        // Six of BAY's twenty cells are empty: four flanking the beacon on the
        // top row, one either side of the second. So fourteen reach the world.
        assertEquals(14, placed.size());
    }

    @Test
    @DisplayName("blocks come out bottom row first, so a tower builds upward")
    void placementsRunFromTheGroundUp() {
        List<TowerPlan.Placement> placed = TowerPlan.parse(BAY).placements(1);
        int previous = -1;
        for (TowerPlan.Placement p : placed) {
            assertTrue(p.y() >= previous, "y went backwards at " + p);
            previous = p.y();
        }
    }

    /**
     * The slab is the third axis the drawing does not have. Everything extrudes
     * except the core: there is one anchorage, and a column of them would be
     * several ships stacked, each of which {@code ShipCoreBlock} would survey
     * separately.
     */
    @Test
    @DisplayName("depth extrudes the drawing but never duplicates the core")
    void depthExtrudesEverythingButTheCore() {
        TowerPlan plan = TowerPlan.parse(BAY);
        List<TowerPlan.Placement> flat = plan.placements(1);
        List<TowerPlan.Placement> slab = plan.placements(4);

        long cores = slab.stream().filter(p -> p.cell() == TowerPlan.Cell.CORE).count();
        assertEquals(1, cores, "the core extruded into a column");

        // Everything except the one core is four deep.
        assertEquals((flat.size() - 1) * 4 + 1, slab.size());
        assertTrue(slab.stream().allMatch(p -> p.z() >= 0 && p.z() < 4));
    }

    @Test
    @DisplayName("a tower needs exactly one anchorage")
    void exactlyOneCore() {
        TowerPlan.Malformed none = assertThrows(TowerPlan.Malformed.class,
                () -> TowerPlan.parse(List.of("###", "###")));
        assertTrue(none.getMessage().contains("has 0"));

        assertThrows(TowerPlan.Malformed.class,
                () -> TowerPlan.parse(List.of("O#O", "###")));
    }

    /**
     * The vocabulary is closed on purpose. This is the input that will one day
     * arrive from a client, and a description that could name an arbitrary block
     * would be a way to write arbitrary blocks into somebody else's world.
     */
    @Test
    @DisplayName("a character outside the vocabulary is refused, and named")
    void unknownGlyphsAreRefused() {
        TowerPlan.Malformed bad = assertThrows(TowerPlan.Malformed.class,
                () -> TowerPlan.parse(List.of("#O#", "#X#")));
        assertTrue(bad.getMessage().contains("'X'"), bad.getMessage());
        assertTrue(bad.getMessage().contains("row 1"), bad.getMessage());
    }

    @Test
    @DisplayName("nothing is parsed that exceeds the caps")
    void theCapsHold() {
        assertThrows(TowerPlan.Malformed.class, () -> TowerPlan.parse(List.of()));
        assertThrows(TowerPlan.Malformed.class, () -> TowerPlan.parse(List.of("", "")));

        String wide = "O" + "#".repeat(TowerPlan.MAX_WIDTH);
        assertThrows(TowerPlan.Malformed.class, () -> TowerPlan.parse(List.of(wide)));

        List<String> tall = new java.util.ArrayList<>();
        tall.add("O");
        for (int i = 0; i < TowerPlan.MAX_HEIGHT; i++) {
            tall.add("#");
        }
        assertThrows(TowerPlan.Malformed.class, () -> TowerPlan.parse(tall));
    }

    @Test
    @DisplayName("depth outside its range is refused rather than clamped")
    void depthIsBounded() {
        TowerPlan plan = TowerPlan.parse(BAY);
        assertThrows(TowerPlan.Malformed.class, () -> plan.placements(0));
        assertThrows(TowerPlan.Malformed.class, () -> plan.placements(-1));
        assertThrows(TowerPlan.Malformed.class, () -> plan.placements(TowerPlan.MAX_DEPTH + 1));

        // The ceiling itself is legal, and extrudes everything but the core.
        assertEquals((14 - 1) * TowerPlan.MAX_DEPTH + 1,
                plan.placements(TowerPlan.MAX_DEPTH).size());
    }

    /**
     * Trailing spaces do not survive most of the ways text moves between two
     * machines, and a dot is how a person draws a hole they mean. Both have to
     * read as empty or a tower changes shape in transit.
     */
    @Test
    @DisplayName("short rows pad, and a dot is the same as a space")
    void whitespaceSurvivesTheJourney() {
        TowerPlan dots = TowerPlan.parse(List.of("..O..", "#####"));
        TowerPlan spaces = TowerPlan.parse(List.of("  O", "#####"));
        assertEquals(dots.width(), spaces.width());
        assertEquals(dots.placements(1), spaces.placements(1));
    }

    @Test
    @DisplayName("one string with newlines reads the same as a list of rows")
    void theTwoParsersAgree() {
        assertEquals(TowerPlan.parse(BAY).placements(2),
                TowerPlan.parse(String.join("\n", BAY)).placements(2));
    }

    /** A bay that will actually grow: beacon over the crop, bed under it, water two along. */
    private static final List<String> FARM = List.of(
            "..*..",
            "..\"..",
            "~.=.~",
            "##O##");

    @Test
    @DisplayName("a farm that obeys the game's rules reports nothing")
    void aGoodFarmIsQuiet() {
        TowerPlan plan = TowerPlan.parse(FARM);
        assertTrue(plan.isFarm());
        assertTrue(plan.agronomy().isEmpty(), plan.agronomy().toString());
    }

    /**
     * <b>The mistake this whole check exists for.</b> A lamp is
     * {@code PanelLight.GENERIC}, light 7. Block light falls one per step, so a
     * crop beside one sees 6 - below {@code SURVIVAL_LIGHT} where it stands, let
     * alone {@code GROWTH_LIGHT}. A tower lit entirely by lamps grows nothing at
     * any distance, and looks perfectly reasonable while doing it.
     */
    @Test
    @DisplayName("a farm lit by lamps is flagged, because seven never grows anything")
    void lampsCannotGrowCrops() {
        List<TowerPlan.Finding> found = TowerPlan.parse(List.of(
                "..+..",
                "..\"..",
                "~.=.~",
                "##O##")).agronomy();
        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).what().contains("beacon"), found.get(0).what());
    }

    @Test
    @DisplayName("a bed with no water in reach is flagged")
    void bedsNeedWater() {
        List<TowerPlan.Finding> found = TowerPlan.parse(List.of(
                "..*..",
                "..\"..",
                "..=..",
                "##O##")).agronomy();
        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).what().contains("water"));
    }

    @Test
    @DisplayName("a crop with nothing under it is flagged")
    void cropsNeedABed() {
        List<TowerPlan.Finding> found = TowerPlan.parse(List.of(
                "..*..",
                "..\"..",
                ".....",
                "##O##")).agronomy();
        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).what().contains("bed"));
    }

    /**
     * The exact numbers {@code FarmBlock.isNearWater} applies, read out of the
     * 1.21.1 jar: a box from {@code offset(-4, 0, -4)} to {@code offset(4, 1, 4)}.
     * Four reaches, five does not, and the vertical span is asymmetric - level or
     * one above, never below. Pinned because an off-by-one here builds farms that
     * look watered and are not.
     */
    @Test
    @DisplayName("hydration reaches exactly four columns, level or one above")
    void hydrationMatchesTheJar() {
        assertTrue(TowerPlan.parse(List.of("....*....", "~...=....", "####O####"))
                .agronomy().isEmpty(), "four columns should reach");
        assertEquals(1, TowerPlan.parse(List.of(".....*....", "~....=....", "#####O####"))
                .agronomy().size(), "five columns should not");

        assertTrue(TowerPlan.parse(List.of("..*..", "~....", "..=..", "##O##"))
                .agronomy().isEmpty(), "water one course above should reach");
        assertEquals(1, TowerPlan.parse(List.of("..*..", "..=..", "~....", "##O##"))
                .agronomy().size(), "water one course below should not");
    }

    @Test
    @DisplayName("a tower with no agriculture in it is not a farm and is not judged as one")
    void plainTowersAreNotFarms() {
        TowerPlan plain = TowerPlan.parse(List.of("..*..", ".###.", "##O##"));
        assertFalse(plain.isFarm());
        assertTrue(plain.agronomy().isEmpty());
    }

    /**
     * Five to a bay is the measure {@code ArchFeature} already builds in -
     * keystone plus four. It is reported rather than enforced: a parser that
     * refused a tower over proportion would be deciding a matter of taste.
     */
    @Test
    @DisplayName("the five-column rhythm is reported, not imposed")
    void theRhythmIsAdvisory() {
        assertTrue(TowerPlan.parse(BAY).inRhythm());
        assertEquals(1, TowerPlan.parse(BAY).bays());

        TowerPlan wide = TowerPlan.parse(List.of("##O########"));
        assertFalse(wide.inRhythm(), "eleven columns is not on the measure");
        assertEquals(3, wide.bays());
        assertEquals(11, wide.width());
    }
}
