package com.serenity.octia.crew;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The muster: who is aboard, and what tends them.
 *
 * <p>One of these exists per running server — integrated, dedicated, or the
 * headless one a GameTest boots. It owns the bench, the crew, and the tick that
 * turns an answer into a body moving.
 *
 * <p><b>The shape of a tick.</b> Nothing here waits on anything. Each crew
 * member is checked for an answer that has landed since last tick; if one has,
 * it is parsed into an order and becomes the standing order. If the cleric is
 * idle and due, a fresh question goes out and the tick moves on without it. If
 * there is no cleric to ask — nothing serving, or nothing bound — {@link Tender}
 * supplies the order instead, which is the offline half of the arrangement and
 * the reason the crew works with the whole bench powered down.
 *
 * <p><b>On LAN.</b> Crew members are ordinary server-side players, so they
 * require nothing of anyone connecting. Open a world to LAN and every guest
 * sees them in the tab list and in the world without installing Octia at all;
 * only the host needs the mod, because only the host is running the server that
 * seats them.
 */
public final class Crew {

    /** One muster per server. Keyed weakly enough by clearing it on stop. */
    private static final Map<MinecraftServer, Crew> ACTIVE = new ConcurrentHashMap<>();

    /** How often a silent bench is asked again, in ticks. Thirty seconds. */
    private static final int REPROBE_TICKS = 600;

    /** How far from the caller a mustered crew is fanned out. */
    private static final double RING = 1.6;

    private final MinecraftServer server;
    private final CrewConfig config;
    private final ClericBench bench;

    /** Seat name, lowercased, to body. Insertion-ordered so listings are stable. */
    private final Map<String, CrewPlayer> aboard = new LinkedHashMap<>();

    /** The last few things real players said, oldest first. */
    private final Deque<String> chatter = new ArrayDeque<>();

    private final int pollTicks;

    private Crew(MinecraftServer server, CrewConfig config) {
        this.server = server;
        this.config = config;
        this.bench = new ClericBench(config.endpoints);
        this.pollTicks = Math.max(20, config.pollSeconds * 20);
    }

    // ---- Wiring --------------------------------------------------------------

    /**
     * Registers the crew with the server lifecycle. Called once, from
     * {@code Octia.onInitialize}.
     */
    public static void bootstrap() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Crew crew = new Crew(server, CrewConfig.load());
            ACTIVE.put(server, crew);
            // Probing at start means /octia crew has a real answer the first time
            // anyone asks, rather than "not probed yet".
            crew.bench.probe();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Crew crew = ACTIVE.remove(server);
            if (crew != null) {
                crew.dismissAll("the server is closing");
                crew.bench.close();
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Crew crew = ACTIVE.get(server);
            if (crew != null) {
                crew.tick();
            }
        });

        // What the crew can hear. Only real players: crew speech is broadcast as
        // a system message and never lands here, which is what keeps eight
        // clerics from talking each other into a loop.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            Crew crew = ACTIVE.get(sender.server);
            if (crew != null && !(sender instanceof CrewPlayer)) {
                // signedContent, not decoratedContent: the raw line the player
                // typed, without the <name> wrapper or any chat decoration a
                // mod has hung on it. The cleric is being told what was said.
                crew.heard(sender.getGameProfile().getName() + ": " + message.signedContent());
            }
        });

        CrewCommands.register();
    }

    /** The muster for this server, or null before it has started. */
    public static Crew of(MinecraftServer server) {
        return ACTIVE.get(server);
    }

    // ---- The tick ------------------------------------------------------------

    private void tick() {
        long tick = server.getTickCount();

        if (!aboard.isEmpty() && bench.reach() == null && tick % REPROBE_TICKS == 0) {
            bench.probe();
        }

        for (CrewPlayer body : List.copyOf(aboard.values())) {
            if (body.isRemoved() || body.hasDisconnected()) {
                // Something else took them out of the world; keep the book honest.
                aboard.remove(body.seatName().toLowerCase(Locale.ROOT));
                continue;
            }

            Cleric cleric = body.cleric();
            bindIfPossible(cleric);

            String said = cleric.takeReply();
            if (said != null) {
                Order order = Order.parse(said);
                if (order.verb() == Order.Verb.HOLD) {
                    cleric.countUnanswered();
                }
                body.setOrder(order);
            }

            if (cleric.isDue(tick)) {
                cleric.ask(Situation.briefing(body.seatName()),
                        Situation.of(body, recentChatter()),
                        tick, pollTicks);
            } else if (!cleric.isServed() && tick % pollTicks == 0) {
                body.setOrder(Tender.next(body, tick));
            }
        }
    }

    /**
     * Gives a model to a crew member that does not have one.
     *
     * <p>Crew mustered while the bench was down are seated with no cleric behind
     * them. When something starts serving later, they pick up a model on the
     * next tick rather than needing to be dismissed and summoned again — the
     * bench waking up should look like the crew waking up.
     */
    private void bindIfPossible(Cleric cleric) {
        if (!cleric.model().isEmpty()) {
            return;
        }
        ClericBench.Reach reach = bench.reach();
        if (reach == null) {
            return;
        }
        Set<String> spoken = new LinkedHashSet<>();
        for (CrewPlayer other : aboard.values()) {
            if (!other.cleric().model().isEmpty()) {
                spoken.add(other.cleric().model());
            }
        }
        for (String model : reach.models()) {
            if (SeatName.isChatCleric(model) && !spoken.contains(model)) {
                cleric.bind(model);
                Octia.LOGGER.info("Octia crew: bound {} to a seat that was running on the tender", model);
                return;
            }
        }
    }

    // ---- Summoning and dismissing -------------------------------------------

    /**
     * Seats one crew member.
     *
     * @param model the model id the bench knows, or empty for the offline tender
     * @return the body, or null if the name was illegal or already aboard
     */
    public CrewPlayer summon(String seatName, String model, ServerLevel level, Vec3 where, float yaw) {
        if (!SeatName.isLegal(seatName) || aboard.containsKey(seatName.toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (server.getPlayerList().getPlayerByName(seatName) != null) {
            return null;
        }

        CrewPlayer body = CrewPlayer.seat(server, level, where, yaw, seatName, new Cleric(bench, model));
        aboard.put(seatName.toLowerCase(Locale.ROOT), body);

        Octia.LOGGER.info("Octia crew: {} aboard ({})", seatName,
                model.isEmpty() ? "offline tender" : model);
        return body;
    }

    /**
     * Seats one crew member per cleric the bench can serve, or — if nothing is
     * serving — one per name in the configured roster.
     *
     * @return the seat names taken, in the order they were taken
     */
    public List<String> muster(ServerLevel level, Vec3 where, float yaw) {
        ClericBench.Reach reach = bench.reach();
        List<String> models = new ArrayList<>();
        if (reach != null) {
            reach.models().stream().filter(SeatName::isChatCleric).forEach(models::add);
        }
        boolean served = !models.isEmpty();
        if (!served) {
            models.addAll(config.roster);
        }

        List<String> seated = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>(aboard.keySet());
        for (String model : models) {
            if (aboard.size() >= config.maxCrew) {
                break;
            }
            String name = SeatName.coin(model, taken);
            // Fan them out around whoever called the muster. Eight players seated
            // on one block resolve their collision by shoving each other across
            // the room, which looks like a bug and is very hard to unsee.
            double angle = seated.size() * 1.6;
            Vec3 spot = where.add(Math.cos(angle) * RING, 0.0, Math.sin(angle) * RING);
            CrewPlayer body = summon(name, served ? model : "", level, spot, yaw);
            if (body != null) {
                taken.add(name.toLowerCase(Locale.ROOT));
                seated.add(name);
            }
        }
        return seated;
    }

    /** Takes one crew member out of the world. */
    public boolean logOff(CrewPlayer body, String why) {
        if (aboard.remove(body.seatName().toLowerCase(Locale.ROOT)) == null && body.isRemoved()) {
            return false;
        }
        body.markLoggingOff();
        server.getPlayerList().remove(body);
        Octia.LOGGER.info("Octia crew: {} logged off ({})", body.seatName(), why);
        return true;
    }

    /** @return how many left */
    public int dismissAll(String why) {
        List<CrewPlayer> all = List.copyOf(aboard.values());
        for (CrewPlayer body : all) {
            logOff(body, why);
        }
        aboard.clear();
        return all.size();
    }

    public CrewPlayer find(String seatName) {
        return aboard.get(seatName.toLowerCase(Locale.ROOT));
    }

    public Collection<CrewPlayer> aboard() {
        return aboard.values();
    }

    public int maxCrew() {
        return config.maxCrew;
    }

    public ClericBench bench() {
        return bench;
    }

    // ---- What the crew has heard --------------------------------------------

    private void heard(String line) {
        chatter.addLast(line);
        while (chatter.size() > Situation.CHAT_MEMORY) {
            chatter.removeFirst();
        }
    }

    private List<String> recentChatter() {
        return List.copyOf(chatter);
    }

    /** Announces a seating in chat, so logging on is something you watch happen. */
    static void announce(MinecraftServer server, Component line) {
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    /** The players who are not crew — everyone the crew is actually here for. */
    static List<ServerPlayer> people(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof CrewPlayer)) {
                out.add(player);
            }
        }
        return out;
    }
}
