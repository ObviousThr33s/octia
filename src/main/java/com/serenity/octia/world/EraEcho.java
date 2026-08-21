package com.serenity.octia.world;

import com.serenity.octia.ship.ShipCoreBlock;
import com.serenity.octia.ship.ShipMoorings;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Standing where a ship is, in a world where it is not.
 *
 * <p><b>This is the design's own claim, finally audible.</b>
 * {@link ShipMoorings} is keyed by {@link BlockPos} and nothing else - no
 * dimension - and {@code ShipGameTest.mooringsAreDimensionAgnostic} pins that as
 * the spine the whole era stack rests on: a ship exists at the same coordinates
 * across every layer. It was true, it was tested, and until now absolutely
 * nothing in the game acted as though it were. A property nobody can perceive is
 * a comment with a test attached.
 *
 * <p>So: walk the Nether to the coordinates of a ship moored in the Overworld
 * and the air there carries motes and a low tone. Nothing is placed, nothing is
 * stored, no packet is invented. The answer was already in the save.
 *
 * <p><b>The condition is exact.</b> An echo happens where the store says a ship
 * is moored and <em>this</em> level has no core at that position. Standing next
 * to your own ship is not an echo, it is a ship; the effect is reserved for the
 * case that is actually strange, which is coordinates that are occupied
 * somewhere you are not.
 *
 * <p><b>A column, never a point.</b> The era stack does not share a vertical
 * range - the Overworld begins at -64 and the Nether at 0 - so a ship moored at
 * y=-58 has a Y that simply does not exist one layer over. What crosses between
 * eras is the column: the X and the Z, which every dimension has. Both the test
 * for an echo and the drawing of it work at the player's own height for that
 * reason, and an earlier version that asked whether the mooring's exact position
 * was loaded switched the entire feature off across the Overworld-to-Nether
 * boundary, silently, which is the one crossing it exists for.
 *
 * <p><b>On cost.</b> The design brief's pillar is that this mod stays cheap
 * enough to sit inside a 300-mod pack, which is why the core does not tick and
 * its survey runs on demand. This obeys the same rule: it wakes on an interval
 * rather than every tick, and its work is a distance check per mooring per
 * player - a handful of numbers against a store that holds tens of entries, not
 * a scan of anything. Nothing is read out of an unloaded chunk.
 */
public final class EraEcho {

    /** Ticks between looks. Forty is two seconds, and it is not a tight loop. */
    private static final int INTERVAL = 40;

    /** How near a player must be, horizontally, to feel it. */
    private static final int RADIUS = 12;

    /** Motes are drawn through this many blocks of air around the player's eye. */
    private static final int COLUMN_BELOW = 2;
    private static final int COLUMN_ABOVE = 3;

    /** One look in this many carries the tone. The rest are silent. */
    private static final int SOUND_IN = 4;

    private EraEcho() {
    }

    public static void bootstrap() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % INTERVAL != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BlockPos felt = find(player.serverLevel(), player.blockPosition());
                if (felt != null) {
                    show(player.serverLevel(), felt, player.blockPosition(),
                            server.getTickCount() % (INTERVAL * SOUND_IN) == 0);
                }
            }
        });
    }

    /**
     * The nearest moored column that has no ship in it here, or null.
     *
     * <p>Separated from the effect on purpose. Whether an echo <em>should</em>
     * happen is a decision about the store and the world and can be asserted;
     * particles cannot. {@code EraEchoGameTest} tests this and leaves the motes
     * to be judged by eye.
     */
    public static BlockPos find(ServerLevel level, BlockPos where) {
        ShipMoorings moorings = ShipMoorings.get(level.getServer());

        BlockPos best = null;
        double nearest = Double.MAX_VALUE;

        for (BlockPos moored : moorings.positions()) {
            double dx = moored.getX() + 0.5 - (where.getX() + 0.5);
            double dz = moored.getZ() + 0.5 - (where.getZ() + 0.5);
            double flat = dx * dx + dz * dz;
            if (flat > RADIUS * RADIUS || flat >= nearest) {
                continue;
            }

            // Never touch a chunk that is not already here. Asking for a
            // blockstate loads one, and a mod that generates terrain because
            // somebody walked past is not a cheap mod.
            //
            // The COLUMN, not the position. Dimensions do not share a vertical
            // range: the Overworld starts at -64 and the Nether at 0, so a ship
            // moored at y=-58 has a Y that does not exist in the Nether at all.
            // Gating on the full position quietly switched the whole feature off
            // across the one boundary it was written for, and the only visible
            // symptom was nothing happening.
            if (!level.hasChunk(moored.getX() >> 4, moored.getZ() >> 4)) {
                continue;
            }

            // A ship you are standing next to is not an echo of a ship - but
            // that question can only be asked where the position exists. Where
            // it does not, there is certainly no core in it.
            if (!level.isOutsideBuildHeight(moored)
                    && level.getBlockState(moored).getBlock() instanceof ShipCoreBlock) {
                continue;
            }

            nearest = flat;
            best = moored;
        }
        return best;
    }

    /**
     * What it feels like: a shaft of motes at those coordinates, and sometimes
     * a tone under it.
     *
     * <p>Drawn at the player's own height rather than the mooring's, because the
     * mooring's Y belongs to a different world's terrain and may be inside
     * bedrock or a hundred blocks up. What carries across the era stack is the
     * column, not the altitude.
     */
    private static void show(ServerLevel level, BlockPos moored, BlockPos player, boolean audible) {
        double x = moored.getX() + 0.5;
        double z = moored.getZ() + 0.5;

        for (int dy = -COLUMN_BELOW; dy <= COLUMN_ABOVE; dy++) {
            level.sendParticles(ParticleTypes.END_ROD,
                    x, player.getY() + dy + 0.5, z,
                    1, 0.08, 0.25, 0.08, 0.0);
        }

        if (audible) {
            // Quiet, low, and from the block rather than the player, so it has a
            // direction to walk toward.
            level.playSound(null, moored.getX(), player.getY(), moored.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.22f, 0.55f);
        }
    }
}
