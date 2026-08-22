package com.serenity.octia.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tangents, and the one property the whole idea rests on: a station is on
 * the line you would be sighting along.
 *
 * <p>An obelisk lies along its leg with a slot bored down its length. If a
 * station is not on the continuation of that leg's line then sighting through
 * the slot points at nothing, the arch at the far node is the end of the view,
 * and the feature is decoration. {@code stationsSitOnTheLineTheyLeft} is
 * therefore the test that matters; the rest are its supporting cast.
 */
class BeamlineTest {

    private static final int SEEDS = 6;
    private static final int SPAN = 12;

    /**
     * Within half a block of the line, which is what rounding a tangent to
     * integer coordinates costs and nothing more.
     *
     * <p>It was 172 blocks before the tangent carried the leg's own direction
     * instead of the cardinal nearest to it - see {@code Beamline.carryOn}. That
     * version passed every other test in this file.
     */
    @Test
    @DisplayName("a station sits on the line of the leg that fed it")
    void stationsSitOnTheLineTheyLeft() {
        double worst = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    for (Beamline.Station station : Beamline.thrownBy(seed, cx, cz)) {
                        Sightlines.Leg feed = Sightlines.leg(seed,
                                cx - station.along().dx(), cz - station.along().dz());
                        worst = Math.max(worst,
                                feed.distanceToLine(station.at().x(), station.at().z()));
                    }
                }
            }
        }
        assertTrue(worst < 1.0, "a station sat " + worst + " blocks off the line it should be on");
    }

    @Test
    @DisplayName("a station is past the node, never behind it")
    void pastTheNode() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx += 2) {
                for (int cz = -SPAN; cz <= SPAN; cz += 2) {
                    Sightlines.Node node = Sightlines.node(seed, cx, cz);
                    for (Beamline.Station station : Beamline.thrownBy(seed, cx, cz)) {
                        Sightlines.Node from = Sightlines.node(seed,
                                cx - station.along().dx(), cz - station.along().dz());
                        double travelX = node.x() - (double) from.x();
                        double travelZ = node.z() - (double) from.z();
                        double onX = station.at().x() - (double) node.x();
                        double onZ = station.at().z() - (double) node.z();
                        assertTrue(travelX * onX + travelZ * onZ > 0,
                                "a station was thrown back the way the beam came");

                        double away = Math.hypot(onX, onZ);
                        assertTrue(away >= Beamline.DRIFT_MIN - 1 && away <= Beamline.DRIFT_MAX + 1,
                                "a station sat " + away + " blocks out");
                    }
                }
            }
        }
    }

    /**
     * No bend, no radiation. A beam that arrives on the same heading the node
     * sends it out on has not turned, and a straight section throws nothing.
     */
    @Test
    @DisplayName("a thread running straight through a node throws nothing there")
    void noBendNoStation() {
        int straightSeen = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx++) {
                for (int cz = -SPAN; cz <= SPAN; cz++) {
                    Sightlines.Heading out = Sightlines.leg(seed, cx, cz).step();
                    boolean straightIn = Sightlines.leg(seed, cx - out.dx(), cz - out.dz()).step() == out;
                    if (!straightIn) {
                        continue;
                    }
                    straightSeen++;
                    for (Beamline.Station station : Beamline.thrownBy(seed, cx, cz)) {
                        assertTrue(station.along() != out,
                                "a straight-through beam threw a station at " + cx + "," + cz);
                    }
                }
            }
        }
        assertTrue(straightSeen > 0, "no straight-through node was found, so this test proved nothing");
    }

    /**
     * What a feature will actually call has to agree with what the lattice
     * contains. Brute force over a wide window is the reference; {@code nearest}
     * is the shortcut, and a shortcut that disagrees is a bug that would only
     * show up as ruins missing from the edges of the search.
     */
    @Test
    @DisplayName("nearest finds what a full sweep finds")
    void nearestAgreesWithASweep() {
        double range = 700;
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int x = -3000; x <= 3000; x += 617) {
                for (int z = -3000; z <= 3000; z += 719) {
                    Beamline.Station shortcut = Beamline.nearest(seed, x, z, range);
                    Beamline.Station swept = sweep(seed, x, z, range);
                    if (swept == null) {
                        assertNull(shortcut, "nearest found a station a full sweep did not");
                        continue;
                    }
                    assertEquals(swept.at(), shortcut == null ? null : shortcut.at(),
                            "nearest missed the closest station at " + x + "," + z);
                }
            }
        }
    }

    /**
     * What the feature calls. A chunk answers with a station only if a station
     * really lands in it, and the station it answers with is one the lattice
     * actually threw - the two claims a wreck at a station rests on.
     */
    @Test
    @DisplayName("inChunk answers with a station that is in that chunk")
    void inChunkIsInTheChunk() {
        int found = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int chunkX = -60; chunkX <= 60; chunkX++) {
                for (int chunkZ = -60; chunkZ <= 60; chunkZ++) {
                    Beamline.Station station = Beamline.inChunk(seed, chunkX, chunkZ);
                    if (station == null) {
                        continue;
                    }
                    found++;
                    assertEquals(chunkX, station.at().x() >> 4, "a station was claimed by the wrong chunk");
                    assertEquals(chunkZ, station.at().z() >> 4, "a station was claimed by the wrong chunk");
                    assertTrue(Beamline.thrownBy(seed, station.cellX(), station.cellZ()).contains(station),
                            "inChunk invented a station its own cell does not throw");
                }
            }
        }
        assertTrue(found > 0, "no chunk in the sample held a station, so this test proved nothing");
    }

    /**
     * Every station is claimable. A station that no chunk answers with is a
     * beamline pointing at ground nothing will ever build on - the failure would
     * be invisible in game and total in effect.
     */
    @Test
    @DisplayName("every station is found by the chunk that contains it")
    void everyStationIsClaimed() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int cx = -SPAN; cx <= SPAN; cx += 2) {
                for (int cz = -SPAN; cz <= SPAN; cz += 2) {
                    for (Beamline.Station station : Beamline.thrownBy(seed, cx, cz)) {
                        Beamline.Station claimed = Beamline.inChunk(seed,
                                station.at().x() >> 4, station.at().z() >> 4);
                        assertTrue(claimed != null,
                                "the chunk holding a station answered with nothing");
                        // Two stations in one chunk is rare and only one gets
                        // built - see Beamline.inChunk - so the claim may be the
                        // other one. What may never happen is no claim at all.
                    }
                }
            }
        }
    }

    /** The reference: every station within a generous cell window, closest first. */
    private static Beamline.Station sweep(long seed, int x, int z, double range) {
        List<Beamline.Station> all = new ArrayList<>();
        int cellX = Sightlines.cell(x);
        int cellZ = Sightlines.cell(z);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                all.addAll(Beamline.thrownBy(seed, cellX + dx, cellZ + dz));
            }
        }
        Beamline.Station best = null;
        double nearest = range * range;
        for (Beamline.Station station : all) {
            double ax = station.at().x() - (double) x;
            double az = station.at().z() - (double) z;
            double away = ax * ax + az * az;
            if (away <= nearest) {
                nearest = away;
                best = station;
            }
        }
        return best;
    }
}
