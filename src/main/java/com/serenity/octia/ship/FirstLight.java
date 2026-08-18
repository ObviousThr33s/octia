package com.serenity.octia.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * The moment a hull becomes a ship, made perceptible.
 *
 * <p><b>The gap this closes.</b> Ring a core with the eighth panel and, until
 * now, the world said nothing. The blockstate flipped, the block's light went
 * from 0 to 7, and a packed long went into {@link ShipMoorings} - all true, all
 * invisible to somebody looking at the ground while they place a block. The
 * survey text exists, but only if you then choose to right-click the thing you
 * just finished. A player who rings a core and walks away was never told it
 * worked.
 *
 * <p><b>Nothing here is new information.</b> The transition was already computed
 * and already thrown away: {@link ShipMoorings#moor} has always returned "this
 * position was not already moored" and {@link ShipCoreBlock#reconcile} has always
 * discarded it. This listens to that boolean. It does not add state, it does not
 * survey anything a second time, and removing it removes exactly one call.
 *
 * <p><b>It fires once per position, not once per survey.</b> {@code reconcile}
 * runs on placement, on any neighbour change, and on every right-click - a hull
 * that stands for an hour is surveyed constantly. The latch is
 * {@code moor()}'s own set semantics, which is why this takes no flag of its
 * own: a position already in the store answers false and nothing happens.
 * Breaking a panel and replacing it does fire again, and should - the ship was
 * genuinely unmoored in between.
 *
 * <p><b>The vocabulary is borrowed, deliberately.</b> {@code BEACON_ACTIVATE} is
 * what {@link ShipCoreBlock} already plays for a moored survey, and
 * {@code END_ROD} is what {@code EraEcho} already uses for a mooring felt from
 * another world. A player who has heard either one has already been taught what
 * this means. Inventing a sound would have taught it twice.
 *
 * <p><b>Server-side and level-checked.</b> {@code reconcile} is already
 * server-only, but it takes a {@link Level} rather than a {@link ServerLevel},
 * and particles need the narrower type. The cast is also the guard that keeps
 * this out of worldgen: features write through a {@code WorldGenRegion}, which
 * is not a {@code ServerLevel} and does not run {@code onPlace} - so a world
 * generating four hundred derelicts does not play four hundred sounds.
 */
public final class FirstLight {

    /** Loud enough to hear from outside the ring, quieter than the survey chime. */
    private static final float VOLUME = 0.7f;

    /** Motes over the core. A column, not a puff - it reads as the ship answering. */
    private static final int MOTES = 24;
    private static final double SPREAD = 0.35;
    private static final double RISE = 0.08;

    private FirstLight() {
    }

    /**
     * A position has just become moored that was not moored before.
     *
     * @param level the level the core is in; ignored unless it is a server level
     * @param core  the ship core, which is where the sound and motes come from
     */
    public static void moored(Level level, BlockPos core) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }

        server.playSound(null, core, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS,
                VOLUME, 1.0f);

        // sendParticles rather than level.addParticle: the server has to be the
        // one to say so, because nothing about this originates on a client and a
        // client that guessed would show motes for somebody else's ship.
        server.sendParticles(ParticleTypes.END_ROD,
                core.getX() + 0.5, core.getY() + 1.0, core.getZ() + 0.5,
                MOTES, SPREAD, 0.5, SPREAD, RISE);
    }
}
