package com.serenity.octia.life;

import com.serenity.octia.Octia;
import com.serenity.octia.world.OctiaWorldOption;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Keep-inventory, held on for the life of an Octia save.
 *
 * <p>Death in Octia is meant to cost you a <i>life</i>, not a backpack. Those are
 * different currencies and only one of them is interesting: losing a life is a
 * fact about the run, losing an inventory is a twenty-minute walk back to a pile
 * of items that may already have despawned. The walk teaches nothing, so this
 * mod declines to charge for it. Everything the player carries survives; what
 * death spends is counted elsewhere.
 *
 * <p><b>Held, not merely set.</b> Setting the rule once at world load is not
 * enough - {@code /gamerule keepInventory false} would quietly undo the whole
 * design, and a save that was ever opened without the mod comes back with the
 * vanilla answer. So the rule is re-asserted every tick. The cost of that is one
 * boolean read against a value already in cache; the assignment only happens on
 * the tick where somebody actually changed it, which is also the only tick that
 * logs. A player who types the command sees it revert inside 50ms with a line in
 * the log saying why.
 *
 * <p><b>Retroactive by construction.</b> There is no migration step and no
 * version stamp to bump. The rule is a property of the running server, not of
 * the save file, so a world created in 2026-07 that has never heard of this
 * class gets it the moment it is next opened - the same code path a brand-new
 * save takes. Nothing is written into {@code level.dat} that an older build
 * would choke on, which also means opening such a save without this mod leaves
 * it exactly as vanilla found it.
 *
 * <p><b>Gated on the per-save switch,</b> like the beacon and the derelict. A
 * world created with Octia turned off is promised vanilla behaviour, and
 * silently rewriting one of its game rules would break that promise in the one
 * place a player is least likely to look. If this should ever become
 * unconditional, {@link #shouldHold} is the single line to change.
 *
 * <p><b>No mixin, on purpose,</b> for the reason {@code OctiaClient} gives: a
 * mixin is a class of breakage, and this mechanic is not worth one. The
 * portability that buys is the point here. {@link GameRules} has been stable
 * since 1.13, so the only loader-specific surface in this file is the three
 * event registrations, and each has a direct counterpart elsewhere:
 *
 * <ul>
 *   <li>{@code ServerWorldEvents.LOAD} - NeoForge {@code LevelEvent.Load}</li>
 *   <li>{@code ServerTickEvents.END_SERVER_TICK} - NeoForge {@code ServerTickEvent.Post}</li>
 *   <li>{@code ServerLifecycleEvents.SERVER_STOPPED} - NeoForge {@code ServerStoppedEvent}</li>
 * </ul>
 *
 * <p>A port is three import lines and three signatures. That is deliberate: the
 * modpack at {@code D:\Serenity\OctiaModpack} is NeoForge while this repo targets
 * Fabric, and anything in the death path is exactly what a future port would
 * want to carry across unchanged.
 */
public final class KeepInventory {

    /**
     * Whether this server's save asked for Octia at all.
     *
     * <p>The single line that decides gating. Return {@code true} unconditionally
     * to make keep-inventory apply to every world regardless of the create-screen
     * switch.
     */
    private static boolean shouldHold(MinecraftServer server) {
        return OctiaWorldOption.get(server).enabled();
    }

    /**
     * True once this server has been told, so the "holding" line is logged once
     * per launch rather than once per world load. Cleared on SERVER_STOPPED for
     * the same reason {@code OctiaWorldgen}'s flag is: a second world opened in
     * the same launch must not inherit the first one's answer.
     */
    private static boolean announced;

    private KeepInventory() {
    }

    public static void bootstrap() {
        // LOAD, so the rule is already true before the first tick and before any
        // player can join - not because a player could die that early, but so
        // that /gamerule keepInventory reads true the first time anyone asks
        // rather than only after a tick has passed.
        ServerWorldEvents.LOAD.register((server, level) -> {
            if (level != server.overworld() || !shouldHold(server)) {
                return;
            }
            if (hold(server) || !announced) {
                announced = true;
                Octia.LOGGER.info("Octia: keep-inventory held on. Death costs a life, not a backpack.");
            }
        });

        // The hold. See the class note on why this is a tick and not a one-shot.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!shouldHold(server)) {
                return;
            }
            if (hold(server)) {
                Octia.LOGGER.info("Octia: keepInventory was set false; put back. It is not a switch in this mod.");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> announced = false);
    }

    /**
     * Puts the rule back if something moved it.
     *
     * @return true only when this call actually changed it, so the caller can log
     *         the correction without logging every tick
     */
    private static boolean hold(MinecraftServer server) {
        GameRules.BooleanValue rule = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        if (rule.get()) {
            return false;
        }
        // set(.., server) rather than the package-private raw setter: the server
        // argument is what notifies listeners, and skipping it leaves anything
        // watching the rule believing the old value.
        rule.set(true, server);
        return true;
    }
}
