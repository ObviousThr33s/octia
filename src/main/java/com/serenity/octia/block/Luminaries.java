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

    private Luminaries() {
    }

    /**
     * Draws this block's halo, if it is lit at all.
     *
     * <p>Safe to call from any block's {@code animateTick}. An unlit block does
     * no work beyond one comparison.
     *
     * <p><b>An enchant mote does not take a velocity, and getting that backwards
     * is what the first version of this did.</b> {@code addParticle}'s last
     * three arguments are a speed for most particle types, but
     * {@code EnchantParticle} reads them as an <i>offset to where the mote
     * appears</i> and then flies it to the position it was spawned at. So the
     * position argument is the <b>destination</b>, not the origin.
     *
     * <p>Vanilla spells it out: the enchanting table calls this at the table's
     * own coordinates and passes the offset to a bookshelf, and what you see is
     * a glyph leaving the shelf and arriving at the table. Passing a negative
     * offset as though it were a pull - which is what was shipped - draws motes
     * that start near the shell and drift outward, away from the block. It looks
     * deliberate, it survived every test in {@code HaloTest} because the
     * arithmetic it checks is unchanged, and it was caught the first time
     * somebody watched a lit panel.
     *
     * <p>So: the block's centre is the destination, and the mote's shell offset
     * is handed over whole. A mote appears out on the shell and falls in until
     * it is absorbed at the andesite.
     */
    public static void halo(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // The hour is read here and decided in Halo, because Halo is the half a
        // test can reach - this method draws particles, and a headless server
        // never draws one. The day turns in light rather than in a bird: see
        // Halo.Hour for why a rooster is not available to this world.
        Halo.Hour hour = Halo.hour(level.getDayTime());
        int motes = Halo.motes(state.getLightEmission(), hour);
        for (int i = 0; i < motes; i++) {
            Halo.Mote mote = Halo.at(random.nextDouble(), random.nextDouble(),
                    Halo.bend(hour, random.nextDouble()));
            level.addParticle(ParticleTypes.ENCHANT,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    mote.x(),
                    mote.y(),
                    mote.z());
        }
    }
}
