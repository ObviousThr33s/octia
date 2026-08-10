package com.serenity.octia.crew;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a cleric is told, and what it is allowed to answer.
 *
 * <p>Two strings and no more. Everything a 3B model gets wrong about a long
 * prompt, it gets wrong here first: the briefing repeats the vocabulary in the
 * exact form the parser accepts, gives one example of each, and forbids
 * everything else in the plainest sentence available. The report is a handful
 * of short lines with no prose, because a small model asked to read a paragraph
 * will answer with a paragraph.
 *
 * <p>The report is deliberately not a world dump. A crew member knows where it
 * is, whether it is day, what it is standing on, who is nearby and in which
 * direction, and what was said recently — which is roughly what a person
 * glancing at the screen knows, and is enough for every order in the
 * vocabulary. Adding inventory, biome, and nearby mobs made the answers worse
 * on the 3B, which is the only reason worth having for leaving them out.
 */
final class Situation {

    /** How far away a player is still worth mentioning. */
    private static final double NOTICE = 32.0;

    /** How many recent chat lines are shown. More than this crowds out the state. */
    static final int CHAT_MEMORY = 4;

    private Situation() {
    }

    /**
     * The standing briefing, unchanged between questions.
     *
     * <p>Sent as the system message every time rather than kept in a
     * conversation, because there is no conversation: each question is
     * independent, which is what makes a slow cleric harmless and a restarted
     * endpoint invisible.
     *
     * <p>One line below is worth reading twice. The vocabulary says
     * {@code follow <player>} and the example under it says a bare
     * {@code follow}. That does parse - {@code Order.parse} yields FOLLOW with
     * an empty arg and {@code CrewPlayer.target} reads an empty arg as "whoever
     * is nearest" - so the example is legal. But it is the only example that
     * does not match its own vocabulary line, and a 3B copying the examples
     * will learn to drop the name. Decide that deliberately before editing
     * either line.
     */
    static String briefing(String seatName) {
        return """
                You are %s, a crew member on a Minecraft server. You have a body and can act.
                Reply with EXACTLY ONE order and nothing else. No explanation, no punctuation, no quotes.

                Orders you may give:
                say <words>              speak to the other players
                follow <player>          walk to that player
                go north|south|east|west walk that way
                look <player>            turn to face them
                jump                     jump once
                hold                     do nothing

                Examples of a whole reply:
                say hello, glad to be aboard
                follow
                go north
                hold"""
                .formatted(seatName);
    }

    /** The state of the world as this crew member can see it, right now. */
    static String of(CrewPlayer body, List<String> chatter) {
        ServerLevel level = body.serverLevel();
        BlockPos at = body.blockPosition();
        StringBuilder out = new StringBuilder(256);

        out.append("you are at ").append(at.getX()).append(' ').append(at.getY()).append(' ').append(at.getZ())
                .append(" in the ").append(level.dimension().location().getPath())
                .append(", health ").append((int) body.getHealth()).append('/').append((int) body.getMaxHealth())
                .append(level.isDay() ? ", daytime" : ", night")
                .append('\n');

        out.append("standing on ").append(groundName(level, at)).append('\n');

        String neighbours = neighbours(body);
        out.append(neighbours.isEmpty() ? "nobody is nearby\n" : neighbours);

        if (!chatter.isEmpty()) {
            out.append("recent chat:\n");
            for (String line : chatter) {
                out.append("  ").append(line).append('\n');
            }
        }

        out.append("your last order: ").append(body.order());
        return out.toString();
    }

    /** Every player worth mentioning, nearest first, with distance and heading. */
    private static String neighbours(CrewPlayer body) {
        StringBuilder out = new StringBuilder();
        body.serverLevel().players().stream()
                .filter(other -> other != body)
                .filter(other -> body.distanceToSqr(other) <= NOTICE * NOTICE)
                .sorted((a, b) -> Double.compare(body.distanceToSqr(a), body.distanceToSqr(b)))
                .limit(4)
                .forEach(other -> out.append(other instanceof CrewPlayer ? "crewmate " : "player ")
                        .append(other.getGameProfile().getName())
                        .append(" is ").append((int) Math.sqrt(body.distanceToSqr(other)))
                        .append(" blocks ").append(headingTo(body, other))
                        .append('\n'));
        return out.toString();
    }

    /**
     * Which way another player lies, in the words the vocabulary uses.
     *
     * <p>Said as a compass direction rather than as coordinates on purpose: the
     * only movement order that takes a direction takes exactly these four words,
     * so a cleric that reads "8 blocks north" can answer "go north" without
     * doing arithmetic. Small models are poor at arithmetic and good at echoes.
     */
    private static String headingTo(CrewPlayer body, ServerPlayer other) {
        double dx = other.getX() - body.getX();
        double dz = other.getZ() - body.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        return dz > 0 ? "south" : "north";
    }

    /**
     * The block underfoot, named the way a player would say it.
     *
     * <p>No lowercasing here on purpose. A ResourceLocation validates its path
     * at construction to [a-z0-9/._-], so a registry key's path is already
     * lowercase and the toLowerCase call this replaced could never change a
     * character. Nothing is null either: BuiltInRegistries.BLOCK is a defaulted
     * registry and answers minecraft:air for anything it does not hold.
     */
    private static String groundName(ServerLevel level, BlockPos at) {
        BlockState below = level.getBlockState(at.below());
        return BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath();
    }
}
