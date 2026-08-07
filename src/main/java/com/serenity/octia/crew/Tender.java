package com.serenity.octia.crew;

import java.util.Locale;

import net.minecraft.server.level.ServerPlayer;

/**
 * The offline tender: what a crew member does when no cleric answers.
 *
 * <p>This exists because of the standing rule that nothing here may depend on
 * something being up. The bench may be asleep, the endpoint may not be running,
 * the whole box may be off — a crew member still logs on, still walks, still
 * turns to look at you. What it does not do is <em>pretend</em>.
 *
 * <p><b>The tender never speaks.</b> It could be given a bag of greetings and
 * nobody would immediately notice, which is exactly the objection: a crew
 * member that chats without a model behind it is indistinguishable from one
 * that is genuinely being tended, and the moment those two look alike, "is the
 * bench up?" stops being answerable by looking at the game. Silence is the
 * honest signal, and {@code /octia crew} is where the answer lives.
 *
 * <p>Deterministic on purpose: same tick, same world, same order. A wandering
 * bot that cannot be reproduced is a bot whose bugs cannot be reproduced. The
 * only variation between crew members comes from their own name, so eight of
 * them pace out of step rather than marching in a block.
 */
final class Tender {

    /** Beyond this, the tender ignores a player entirely. */
    private static final double INTEREST = 24.0;

    /** Closer than this and following becomes crowding. */
    private static final double CLOSE = 4.0;

    /** Ticks spent on one leg of the patrol. Twenty ticks to the second. */
    private static final int LEG = 100;

    private Tender() {
    }

    /**
     * The order for this tick.
     *
     * <p>Called every poll interval, not every tick, so it is free to be a plain
     * function of the world rather than a state machine.
     */
    static Order next(CrewPlayer body, long tick) {
        ServerPlayer near = nearestPerson(body);
        if (near != null) {
            double distance = Math.sqrt(body.distanceToSqr(near));
            String name = near.getGameProfile().getName();
            return distance > CLOSE
                    ? new Order(Order.Verb.FOLLOW, name)
                    : new Order(Order.Verb.LOOK, name);
        }

        // Nobody about. Pace a slow square, offset per crew member so a mustered
        // bench does not march in formation.
        long phase = (tick / LEG) + Math.abs(body.getUUID().hashCode() % 4L);
        if (phase % 2 == 0) {
            return Order.HOLD;
        }
        Order.Heading heading = Order.Heading.values()[(int) ((phase / 2) % 4)];
        return new Order(Order.Verb.GO, heading.name().toLowerCase(Locale.ROOT));
    }

    /** The nearest player who is not themselves crew. */
    private static ServerPlayer nearestPerson(CrewPlayer body) {
        ServerPlayer best = null;
        double bestDist = INTEREST * INTEREST;
        for (ServerPlayer other : body.serverLevel().players()) {
            if (other instanceof CrewPlayer) {
                continue;
            }
            double d = body.distanceToSqr(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }
}
