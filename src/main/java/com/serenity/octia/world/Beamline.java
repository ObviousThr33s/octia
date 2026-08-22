package com.serenity.octia.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the thread bends, something is thrown off straight ahead.
 *
 * <p>A bending magnet does not only turn a beam - it makes the beam radiate, and
 * the light leaves along the tangent, which is the direction the beam was
 * travelling before the bend. At a light source that tangent is a beamline, and
 * the end of every beamline is the room where the interesting thing is. The ring
 * is the machine; the tangents point at what the machine is for.
 *
 * <p>This lattice has bends already. A node whose incoming leg arrives on one
 * heading and whose outgoing leg leaves on another is a bend, and the tangent is
 * the incoming heading carried straight on past the node. A <b>station</b> is a
 * point on that tangent, a few hundred blocks beyond the node.
 *
 * <p><b>What it buys, and it is the whole reason to build it.</b> An obelisk is
 * laid along its leg with a slot bored down its length. Sight through the slot,
 * along the leg, past the arch standing on the node at the end of it - and a
 * station is exactly where that line goes. The mod has had a thing you can sight
 * along since the lattice landed, and until now there was nothing at the far end
 * of the sighting. This is what goes there.
 *
 * <p><b>No ring required.</b> Rings are the machine and {@link Ring} measures
 * them, but a bend does not need to be part of a circuit to radiate: every turn
 * in the lattice throws a tangent whether the thread ever comes back or not.
 * That also keeps this a purely local function - four neighbour hashes and no
 * walk - which is what a chunk-generation worker needs.
 *
 * <p><b>A node can throw more than one.</b> Every cell has exactly one leg out
 * and anywhere from none to three legs in, so a busy node radiates once per
 * incoming beam that turns there, and a node nothing points at radiates nothing.
 *
 * <p>Pure Java, like the rest of the lattice. {@code BeamlineTest} is the gate.
 */
public final class Beamline {

    /** Nearest a station sits to the node that threw it. */
    public static final int DRIFT_MIN = 160;

    /** Furthest. Kept inside a cell so a station belongs to its own bend. */
    public static final int DRIFT_MAX = 384;

    /** Keeps the drift draw off every other consumer of the seed. */
    private static final long STATION_SALT = 0x5_7A7_10_5L;

    private Beamline() {
    }

    /**
     * The far end of one beamline.
     *
     * @param at    where it lands, in blocks
     * @param along the heading the beam was on when it bent - the tangent
     * @param cellX the cell of the node that threw it
     * @param cellZ the same
     * @param drift how far past the node it sits, in blocks
     */
    public record Station(Sightlines.Node at, Sightlines.Heading along, int cellX, int cellZ, int drift) {
    }

    /**
     * Every station thrown by the bends at one cell's node.
     *
     * <p>Empty is the common answer for a node nothing points at, and for one the
     * thread runs straight through: no bend, no radiation.
     */
    public static List<Station> thrownBy(long seed, int cellX, int cellZ) {
        Sightlines.Heading out = Sightlines.leg(seed, cellX, cellZ).step();
        Sightlines.Node node = Sightlines.node(seed, cellX, cellZ);
        List<Station> stations = new ArrayList<>(1);

        for (Sightlines.Heading arriving : Sightlines.Heading.values()) {
            // The neighbour that would arrive on this heading is the one behind
            // it, and it only arrives if its own leg points here.
            int fromX = cellX - arriving.dx();
            int fromZ = cellZ - arriving.dz();
            if (Sightlines.leg(seed, fromX, fromZ).step() != arriving) {
                continue;
            }
            if (arriving == out) {
                continue;
            }
            int drift = drift(seed, cellX, cellZ, arriving);
            stations.add(new Station(carryOn(Sightlines.node(seed, fromX, fromZ), node, drift),
                    arriving, cellX, cellZ, drift));
        }
        return stations;
    }

    /**
     * The station nearest a position, or null if none is within range.
     *
     * <p>What a feature asks. Scans the cells whose nodes could throw a station
     * into range and no others - a station sits at most {@link #DRIFT_MAX} plus
     * the lattice jitter from its own node, so the reach is small and fixed.
     */
    public static Station nearest(long seed, int x, int z, double range) {
        int reach = (int) Math.ceil((range + DRIFT_MAX + Sightlines.JITTER + Sightlines.SPACING / 2.0)
                / Sightlines.SPACING);
        int cellX = Sightlines.cell(x);
        int cellZ = Sightlines.cell(z);

        Station best = null;
        double nearest = range * range;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (Station station : thrownBy(seed, cellX + dx, cellZ + dz)) {
                    double ax = station.at().x() - (double) x;
                    double az = station.at().z() - (double) z;
                    double away = ax * ax + az * az;
                    if (away <= nearest) {
                        nearest = away;
                        best = station;
                    }
                }
            }
        }
        return best;
    }

    /**
     * The tangent: the leg's own direction, carried on past the node.
     *
     * <p><b>The leg's direction, not the cardinal it is nearest to.</b> Two
     * nodes a step apart each wander up to {@link Sightlines#JITTER}, so a leg
     * bends as much as thirty degrees off its cardinal. Carrying the cardinal
     * on instead of the line puts the far end up to <b>172 blocks</b> off the
     * line somebody is sighting along - measured over 39,808 stations, and
     * enough to lose a ruin in the trees. Carrying the line puts it dead ahead,
     * within the half block that rounding to a coordinate costs.
     */
    private static Sightlines.Node carryOn(Sightlines.Node from, Sightlines.Node node, int drift) {
        double dx = node.x() - (double) from.x();
        double dz = node.z() - (double) from.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length == 0) {
            return node;
        }
        return new Sightlines.Node(
                node.x() + (int) Math.round(dx / length * drift),
                node.z() + (int) Math.round(dz / length * drift));
    }

    /**
     * The station that belongs to one chunk, or null if none lands in it.
     *
     * <p>What a feature asks, and the shape is chosen so that the answer needs
     * no coordination. A station lands in exactly one chunk, so the chunk that
     * contains it is the one that builds it: no two chunks can claim the same
     * station, nothing has to remember what has been built, and a generation
     * worker never writes outside its own region - which is the constraint that
     * ruled out simply moving a wreck to the nearest station.
     *
     * <p><b>One per chunk, and the rest are dropped.</b> Two stations in one
     * sixteen-block square is rare - see docs/TRAJECTORY.traj XV - and the
     * alternative is two hulls in the same chunk overlapping each other. The
     * first in scan order wins, and scan order is fixed, so the answer is a
     * function of the seed like everything else in the lattice.
     */
    public static Station inChunk(long seed, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int cellX = Sightlines.cell(minX);
        int cellZ = Sightlines.cell(minZ);
        int reach = (int) Math.ceil((DRIFT_MAX + Sightlines.JITTER + Sightlines.SPACING / 2.0)
                / Sightlines.SPACING);

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (Station station : thrownBy(seed, cellX + dx, cellZ + dz)) {
                    if (station.at().x() >> 4 == chunkX && station.at().z() >> 4 == chunkZ) {
                        return station;
                    }
                }
            }
        }
        return null;
    }

    /** How far past the node this beamline reaches. A draw, held between the bounds. */
    private static int drift(long seed, int cellX, int cellZ, Sightlines.Heading arriving) {
        long h = Sightlines.hash(seed, cellX, cellZ, STATION_SALT + arriving.ordinal());
        return DRIFT_MIN + (int) Math.floorMod(h, DRIFT_MAX - DRIFT_MIN + 1);
    }
}
