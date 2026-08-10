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

    /**
     * Ticks spent on one leg of the patrol. Twenty ticks to the second.
     *
     * <p><b>Sampled, not counted, and the sampling rate is not ours.</b>
     * {@code Crew.tick} consults the tender only on {@code tick % pollTicks == 0},
     * so the phase below advances by {@code pollTicks / LEG} per call, not by
     * one. At the default {@code poll_seconds} of 5 that ratio is exactly 1 and
     * the patrol alternates hold, walk, hold, walk as written. At any even
     * multiple of it - poll_seconds 10, 20, 30 - the ratio is even, the phase
     * parity never changes, and a crew member either never moves or never stops,
     * decided only by its own UUID offset. Nothing crashes and nothing logs; it
     * reads as half the crew being broken.
     */
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

        // Nobody about. Pace, offset per crew member so a mustered bench does
        // not march in formation.
        //
        // Not a square, despite how that used to read here: the headings come
        // out in Heading.values() order, which is NORTH, SOUTH, EAST, WEST, so
        // the figure traced is a cross - out and back along Z, then out and
        // back along X. Reordering the constants in Order.Heading silently
        // reshapes this patrol, and that is the only place that enum's
        // declaration order is load-bearing.
        //
        // The offset is stable across restarts because a crew member's UUID is
        // UUIDUtil.createOfflinePlayerUUID(seatName) - derived from the name,
        // never random. That is what makes the class javadoc's promise of
        // "same tick, same world, same order" actually true.
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
