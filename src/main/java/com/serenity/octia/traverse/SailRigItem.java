package com.serenity.octia.traverse;

import com.serenity.octia.world.Sightlines;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A hand-made sail-frame: andesite ribs over cloth, held, not worn.
 *
 * <p>Hold it and step off an edge. Past a couple of blocks of fall the sail
 * opens on its own, the drop becomes a glide forward along the look, and the
 * void becomes a medium you commit to - one-way, because the rig only ever
 * goes down. Height is regained by stairs, on foot. A mis-glide into the void
 * is priced by keep-inventory - memory, not gear - and is not this file's
 * ledger.
 *
 * <p><b>The law this is written under.</b> The 8/23 session flew origin to
 * X=2224 at about 33 metres a second; loaded chunks climbed monotonically to
 * 2,690 - fast horizontal flight outruns the chunk unloader - MSPT hit 70
 * against a 50 budget, and the autosave stalled. So: no mechanic in this mod
 * may enable sustained fast horizontal travel. Descent is free; with the rig
 * stowed nothing here touches falling at all. The rig's own gentle sink is
 * its choice, not the law's demand - the law caps crossing, not dropping.
 *
 * <p><b>The range proof.</b> The rig cannot gain altitude - {@code glide}
 * clamps vertical velocity to a strict sink - so a glide's horizontal range is
 * bounded by {@code (HARD_CAP / SINK_MAX) * height = 3.75 * height}, and
 * height comes back only by climbing. Travel under sail is height-limited
 * fuel, not sustained flight; at 9 blocks a second the chunk crossing rate is
 * 0.56 a second, against the 33 metres a second of the storm.
 *
 * <p><b>Deployed is derived, never stored.</b> No keybind, no network channel,
 * no data component, no NBT, no SavedData. Whether the sail is open is
 * recomputed every tick from state vanilla already syncs: rig in a hand,
 * airborne, not in fluid, not carried by another mechanic, fall past the
 * threshold. Both sides compute the same answer from the same synced inputs,
 * which is what makes no-channel possible - and the latch costs nothing,
 * because capping fallDistance to {@code SOFT_FALL} each tick keeps it above
 * the deploy threshold until ground, water, or stowing ends the glide.
 *
 * <p><b>A hand, not a chest slot.</b> Wearing would need Equipable machinery
 * and an armour model, which is render territory this push does not enter -
 * and a committed hand is the right price for committing to the void.
 * Anywhere else in the inventory the rig is inert.
 *
 * <p><b>Where the clamps run, and why the order holds.</b> This is
 * {@code Item#inventoryTick}, unbranched by side. In the mapped 1.21.1
 * {@code Player.aiStep} - read from the bytecode, not assumed -
 * {@code Inventory.tick} runs before {@code LivingEntity.aiStep}, and travel
 * runs inside the latter. So the displacement each tick is made from the
 * already-clamped vector: the invariant holds on actual motion, not just the
 * stored field. Travel's own gravity and drag then land the vector below the
 * band, where the next tick's clamp meets it. Steady state executes
 * {@code SINK_MAX} down and at most {@code HARD_CAP} across, per tick, on
 * both sides.
 *
 * <p><b>The trust model is the elytra's, stated rather than papered over.</b>
 * Player movement authority is the controlling client: the client runs these
 * clamps and is thereby shaped, and the server runs the identical clamps on
 * its mirror and owns fallDistance, so fall damage stays honest. Never
 * {@code hurtMarked} - a velocity packet per tick fights prediction. A hacked
 * client that skips the clamp is held only by vanilla's own move-speed
 * checks, exactly as a hacked elytra is. Out of scope, and said so here.
 */
public class SailRigItem extends Item {

    public SailRigItem(Properties properties) {
        super(properties);
    }

    /**
     * The whole wiring: a player, a hand that owns the tick, and the sail.
     *
     * <p>No side branch - both sides run identical math, which is the entire
     * no-channel trick. No sound and no particles this push.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId,
            boolean isSelected) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!handOwns(player, stack)) {
            return;
        }
        sail(player);
    }

    /**
     * Whether this stack is the one copy of the rig that ticks.
     *
     * <p>Reference identity against the hands, the vanilla map-and-compass
     * idiom - a rig in a backpack, a chest, a cursor, or an item frame is
     * inert. And if this stack is the offhand one while the main hand also
     * holds a rig, the answer is no: the main hand's copy owns the tick, so
     * two rigs push once, not twice.
     */
    public static boolean handOwns(Player player, ItemStack stack) {
        if (stack == player.getMainHandItem()) {
            return true;
        }
        if (stack == player.getOffhandItem()) {
            return !(player.getMainHandItem().getItem() instanceof SailRigItem);
        }
        return false;
    }

    /**
     * Whether the sail is open this tick, read from the entity.
     *
     * <p>One rationale carries the whole carried-clause: the rig yields to
     * every other traversal state - creative flight, spectator, elytra,
     * riptide, vehicles - and deploys only into a plain fall. Fluids keep
     * their own physics and reset fallDistance themselves, so water and lava
     * stow it too. Everything here is state vanilla already syncs, which is
     * what lets both sides agree without a channel.
     */
    public static boolean deployed(Player player) {
        boolean carried = player.getAbilities().flying
                || player.isSpectator()
                || player.isFallFlying()
                || player.isAutoSpinAttack()
                || player.isPassenger();
        return SailRig.deploys(player.onGround(),
                player.isInWaterOrBubble() || player.isInLava(),
                carried,
                player.fallDistance);
    }

    /**
     * One deployed tick: clamp the motion, cap the memory.
     *
     * <p>The fallDistance line is the fall-damage story in one place. Each
     * deployed tick remembers at most {@code SOFT_FALL}, so a landing under
     * sail carries at worst {@code SOFT_FALL + 2 * SINK_MAX = 2.74}, under
     * vanilla's threshold of 3.0 - always free. Stowing mid-air stops the
     * clamps and accumulation resumes from {@code SOFT_FALL}, so a bailed
     * glide is charged fairly for the rest of the plummet.
     */
    public static void sail(Player player) {
        if (!deployed(player)) {
            return;
        }

        Vec3 in = player.getDeltaMovement();
        Vec3 look = player.getLookAngle();
        SailRig.Motion out = SailRig.glide(new SailRig.Motion(in.x, in.y, in.z), look.x, look.z);

        // OPTIONAL, and shipped dormant at STEER_ASSIST_RADIANS = 0.0: a nudge
        // of the glide toward the Sightlines leg underfoot, so sails and
        // stairs would follow the same threads. Nothing is drawn and no salt
        // is spent - the bearing is read from the lattice, not rolled. The
        // honest architecture note: the seed lives on the server and player
        // physics lives on the controlling client, so this nudge is inert for
        // real players until a seed channel exists, which this push forbids.
        // steer preserves horizontal magnitude exactly, so even enabling it
        // cannot break the cap or the band.
        if (SailRig.STEER_ASSIST_RADIANS > 0 && player.level() instanceof ServerLevel server) {
            Sightlines.Leg leg = Sightlines.legAt(server.getSeed(),
                    player.getBlockX(), player.getBlockZ());
            out = SailRig.steer(out,
                    leg.to().x() - leg.from().x(),
                    leg.to().z() - leg.from().z(),
                    SailRig.STEER_ASSIST_RADIANS);
        }

        player.setDeltaMovement(out.x(), out.y(), out.z());
        player.fallDistance = SailRig.remembered(player.fallDistance);
    }
}
