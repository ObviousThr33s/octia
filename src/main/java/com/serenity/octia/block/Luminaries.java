package com.serenity.octia.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every lit block this mod owns wears a halo of enchant motes.
 *
 * <p>One rule, applied to light rather than to a list: anything of ours that
 * emits light gets the halo, and anything dark does not. A frame panel switched
 * from dark to generic starts glittering because its light level changed, not
 * because it was named here, and the next luminary registered gets it for the
 * cost of one line in its {@code animateTick}.
 *
 * <p><b>Client-side, and free.</b> {@code animateTick} is called by the client
 * on random blocks near the camera and never runs on a server, so this costs no
 * packets, no ticks on a dedicated server, and nothing at all for a player who
 * is looking the other way. That is also why nothing here is covered by a
 * {@code @GameTest}: the gates run headless, and a headless server never draws
 * a particle. What can be tested is the arithmetic, and {@link Halo} is where
 * it was put so that it could be - see {@code HaloTest}.
 *
 * <p><b>What it does not reach.</b> The mast at spawn and the obelisk crowns
 * are built out of vanilla blocks, so their light is not ours to dress without
 * a mixin, and {@code OctiaClient} already says why this mod does not take one.
 * The halo is on Octia's own blocks or it is nowhere.
 */
public final class Luminaries {

    /** How hard a mote falls toward the block it belongs to. Slow: this is a drift. */
    private static final double PULL = 0.22;

    private Luminaries() {
    }

    /**
     * Draws this block's halo, if it is lit at all.
     *
     * <p>Safe to call from any block's {@code animateTick}. An unlit block does
     * no work beyond one comparison.
     */
    public static void halo(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int motes = Halo.motes(state.getLightEmission());
        for (int i = 0; i < motes; i++) {
            Halo.Mote mote = Halo.at(random.nextDouble(), random.nextDouble(), random.nextDouble());
            level.addParticle(ParticleTypes.ENCHANT,
                    pos.getX() + 0.5 + mote.x(),
                    pos.getY() + 0.5 + mote.y(),
                    pos.getZ() + 0.5 + mote.z(),
                    -mote.x() * PULL,
                    -mote.y() * PULL,
                    -mote.z() * PULL);
        }
    }
}
