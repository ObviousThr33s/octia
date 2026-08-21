package com.serenity.octia.crew;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.serenity.octia.Octia;
import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.RuinRegistry;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Someone on the road. Friendly, reserved, and nobody's crew.
 *
 * <p>The ruins are always empty - {@code Habitation} places things people left
 * and never people - and that emptiness is what makes this land. If wrecks came
 * with occupants, meeting someone would be scenery. Because they never do, the
 * only person you ever meet is one who is also passing through.
 *
 * <p><b>Almost all of this was already built.</b> {@link CrewPlayer} is a real
 * {@code ServerPlayer}, so a wayfarer is in the tab list and visible to LAN
 * guests who have never installed Octia; {@link Cleric} runs a model
 * asynchronously and never blocks a tick; {@link Order} reduces whatever it says
 * to six verbs. A stranger is not new machinery. It is a crew member nobody
 * mustered, told something different, and left alone.
 *
 * <p><b>They are chaos, and the design is built for that rather than against
 * it.</b> A local 3B model asked what to do next will sometimes answer with
 * something strange, and the answer is not to constrain the model harder - it is
 * to make strange answers harmless. Three things do that:
 *
 * <ul>
 *   <li><b>No hands.</b> A {@code CrewPlayer} has no client sending block-break
 *       or item-use packets, so a wayfarer physically cannot mine, build, steal
 *       or attack. Whatever it decides, the world is not at risk.
 *   <li><b>A narrower vocabulary than the crew's.</b> {@link #permit} drops
 *       FOLLOW and JUMP even when a model produces them. A stranger that trails
 *       you home is not reserved, and one that hops is not mysterious.
 *   <li><b>The band.</b> Distance is not left to the model at all - see
 *       {@link #band}. Reserve that depends on a language model remembering to
 *       be reserved is not reserve.
 * </ul>
 *
 * <p>What is left of the chaos is the part worth having: what they say, and when
 * they say nothing.
 */
public final class Wayfarer {

    /** One per server, and at most one wayfarer at a time in it. */
    private static final Map<MinecraftServer, Wayfarer> ACTIVE = new ConcurrentHashMap<>();

    /** Ticks between looks. Nothing here is urgent. */
    private static final int INTERVAL = 20;

    /** One in this many looks brings someone, once the conditions are met. */
    private static final int ARRIVAL_IN = 40;

    /** Ticks before another may arrive after one leaves. Roughly four minutes. */
    private static final int COOLDOWN = 4800;

    /** How far out they appear, and the ring they keep once here. */
    private static final int ARRIVE_MIN = 16;
    private static final int ARRIVE_MAX = 24;
    private static final double BAND_NEAR = 6.0;
    private static final double BAND_FAR = 30.0;

    /** Unwatched for this long and they are gone. Twenty seconds. */
    private static final int LINGER = 400;

    /** How far a landmark may be and still be what drew somebody here. */
    private static final double LANDMARK_RANGE = 96.0;

    /** How often the cleric is asked, in ticks. Slower than the crew: they talk less. */
    private static final int ASK_TICKS = 200;

    /**
     * Names a traveller might have. Plain, unplaceable, and deliberately not
     * model ids - a stranger called {@code qwen2_5} is a bug report, not a
     * person. Sixteen characters or fewer and legal per {@link SeatName}.
     */
    private static final String[] NAMES = {
        "Ash", "Bell", "Corvin", "Dray", "Esker", "Fen", "Garrow", "Hale",
        "Ivo", "Kestrel", "Lark", "Marrow", "Nettle", "Orrin", "Pike", "Quill",
        "Reed", "Slate", "Thistle", "Vesper", "Wren", "Yarrow",
    };

    private final MinecraftServer server;
    private final RandomSource random = RandomSource.create();

    private CrewPlayer body;
    private long unseenSince;
    private long nextArrival;

    private Wayfarer(MinecraftServer server) {
        this.server = server;
    }

    // ---- Wiring --------------------------------------------------------------

    public static void bootstrap() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ACTIVE.put(server, new Wayfarer(server)));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Wayfarer wayfarer = ACTIVE.remove(server);
            if (wayfarer != null) {
                wayfarer.leave("the server is closing");
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Wayfarer wayfarer = ACTIVE.get(server);
            if (wayfarer != null && server.getTickCount() % INTERVAL == 0) {
                wayfarer.tick(server.getTickCount());
            }
        });
    }

    /** The wayfarer state for this server, or null before it has started. */
    public static Wayfarer of(MinecraftServer server) {
        return ACTIVE.get(server);
    }

    // ---- The tick ------------------------------------------------------------

    private void tick(long tick) {
        if (body != null && (body.isRemoved() || body.hasDisconnected())) {
            body = null;
            nextArrival = tick + COOLDOWN;
            return;
        }
        if (body == null) {
            if (tick >= nextArrival && random.nextInt(ARRIVAL_IN) == 0) {
                arrive(tick);
            }
            return;
        }
        tend(tick);
    }

    /**
     * Brings someone, if the world is the sort of place someone would appear in.
     *
     * <p>Night and open sky, because a traveller who materialises in your mine
     * at noon is a spawn bug wearing a coat. Away from spawn, because the road
     * is not somebody's front garden.
     */
    private void arrive(long tick) {
        List<ServerPlayer> people = people();
        if (people.isEmpty()) {
            return;
        }
        ServerPlayer person = people.get(random.nextInt(people.size()));
        ServerLevel level = person.serverLevel();

        if (level.isDay() || !level.canSeeSky(person.blockPosition().above())) {
            return;
        }
        if (person.blockPosition().closerThan(level.getSharedSpawnPos(), 64)) {
            return;
        }

        // Near a landmark if there is one within sight of the player, otherwise
        // simply near the player. An obelisk is a thing travellers walk toward
        // - that is what the lit crown is for - so meeting one at the foot of
        // it is the encounter the fiction actually wants, and meeting one in an
        // empty field is the fallback rather than the design.
        BlockPos anchor = landmarkNear(person.blockPosition());
        BlockPos spot = arrivalSpot(level, anchor == null ? person.blockPosition() : anchor, random);
        if (spot == null) {
            return;
        }

        String name = freeName();
        if (name == null) {
            return;
        }

        WayfarerLedger ledger = WayfarerLedger.get(server);
        boolean known = ledger.hasMet(name);

        Cleric cleric = new Cleric(bench(), modelForStranger());
        body = CrewPlayer.seat(server, level, Vec3.atBottomCenterOf(spot), 0.0F, name, cleric);
        unseenSince = tick;

        ledger.remember(name, person.blockPosition());
        Octia.LOGGER.info("Octia: {} is on the road near {} ({}).",
                name, spot.toShortString(), known ? "met before" : "a stranger");
    }

    /**
     * Keeps the distance, asks occasionally, and leaves when nobody is looking.
     */
    private void tend(long tick) {
        ServerPlayer near = nearestPerson();

        if (near == null || near.distanceToSqr(body) > BAND_FAR * BAND_FAR) {
            if (tick - unseenSince > LINGER) {
                leave("nobody was watching");
            }
            return;
        }
        unseenSince = tick;

        Order held = band(body, near);
        if (held != null) {
            body.setOrder(held);
            return;
        }

        Cleric cleric = body.cleric();
        String said = cleric.takeReply();
        if (said != null) {
            body.setOrder(permit(Order.parse(said)));
        }

        if (cleric.isDue(tick)) {
            cleric.ask(Situation.wayfarerBriefing(body.seatName(), placeMet()),
                    Situation.of(body, List.of()), tick, ASK_TICKS);
        } else if (!cleric.isServed()) {
            // The offline half. A stranger with no cleric behind it is a
            // stranger who says nothing, which is in character rather than
            // broken - it is the same person, quieter.
            body.setOrder(Order.HOLD);
        }
    }

    /**
     * The distance rule, which is not the model's to decide.
     *
     * <p>Too close and they step back; too far and they are already leaving.
     * Reserve that depends on a language model remembering to be reserved is not
     * reserve, so this runs first and overrides anything the cleric said.
     *
     * @return the order that keeps the band, or null to let the cleric speak
     */
    static Order band(CrewPlayer body, ServerPlayer person) {
        double away = Math.sqrt(person.distanceToSqr(body));
        if (away >= BAND_NEAR) {
            return null;
        }
        double dx = body.getX() - person.getX();
        double dz = body.getZ() - person.getZ();
        String heading = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? "east" : "west")
                : (dz > 0 ? "south" : "north");
        return new Order(Order.Verb.GO, heading);
    }

    /**
     * What a stranger is allowed to do with what a model said.
     *
     * <p>The crew's vocabulary minus the two verbs that break the character.
     * FOLLOW makes a stranger a companion, which is the opposite of passing
     * through; JUMP makes them a puppet. Everything unrecognised was already a
     * hold before it reached here - see {@code Order.parse} - so this only has
     * to remove, never to guess.
     */
    static Order permit(Order order) {
        return switch (order.verb()) {
            case SAY, GO, LOOK, HOLD -> order;
            case FOLLOW, JUMP -> Order.HOLD;
        };
    }

    /**
     * An obelisk the player is already near, or null.
     *
     * <p>Only obelisks. A derelict is somewhere a ship failed and a waystation is
     * somewhere people stopped, but an obelisk is a marker raised to be walked
     * toward - it is the one landmark that implies a road, and a road is where
     * you meet somebody.
     */
    private BlockPos landmarkNear(BlockPos from) {
        return RuinRegistry.get(server).nearest(from, OctiaWorldgen.OBELISK, LANDMARK_RANGE);
    }

    /** Somewhere solid, a fair way off, that the person is not looking at. */
    static BlockPos arrivalSpot(ServerLevel level, BlockPos from, RandomSource random) {
        for (int tries = 0; tries < 8; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int reach = ARRIVE_MIN + random.nextInt(ARRIVE_MAX - ARRIVE_MIN + 1);
            int x = from.getX() + (int) Math.round(Math.cos(angle) * reach);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * reach);

            BlockPos column = new BlockPos(x, from.getY(), z);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos ground = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(ground.getY() - from.getY()) > 8) {
                continue;
            }
            if (!level.getFluidState(ground).isEmpty() || !level.getBlockState(ground).isAir()) {
                continue;
            }
            return ground;
        }
        return null;
    }

    // ---- Leaving -------------------------------------------------------------

    /** Takes the wayfarer out of the world, quietly. */
    public void leave(String why) {
        if (body == null) {
            return;
        }
        Crew.of(server).logOff(body, why);
        Octia.LOGGER.info("Octia: {} went on ({}).", body.seatName(), why);
        body = null;
        nextArrival = server.getTickCount() + COOLDOWN;
    }

    // ---- Odds and ends -------------------------------------------------------

    /** The biome of the last meeting, named plainly, or empty if never met. */
    private String placeMet() {
        WayfarerLedger ledger = WayfarerLedger.get(server);
        BlockPos where = ledger.where(body.seatName());
        if (where == null) {
            return "";
        }
        return body.serverLevel().getBiome(where).unwrapKey()
                .map(key -> key.location().getPath().replace('_', ' '))
                .orElse("");
    }

    /**
     * The real players: everyone in the list who is not a body we are driving.
     *
     * <p>Kept here rather than borrowed. {@code Crew} had exactly this method
     * and it was removed while this class was being written, which is the
     * argument against depending on a package-private helper for something this
     * central. Excluding {@code CrewPlayer} also excludes the wayfarer itself,
     * which is wanted: a stranger is not company for itself.
     */
    private List<ServerPlayer> people() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof CrewPlayer)) {
                out.add(player);
            }
        }
        return out;
    }

    private ServerPlayer nearestPerson() {
        ServerPlayer best = null;
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer person : people()) {
            if (person.serverLevel() != body.serverLevel()) {
                continue;
            }
            double away = person.distanceToSqr(body);
            if (away < nearest) {
                nearest = away;
                best = person;
            }
        }
        return best;
    }

    /** A name nobody in the world is currently using. */
    private String freeName() {
        for (int tries = 0; tries < NAMES.length; tries++) {
            String name = NAMES[random.nextInt(NAMES.length)];
            if (server.getPlayerList().getPlayerByName(name) == null && SeatName.isLegal(name)) {
                return name;
            }
        }
        return null;
    }

    private ClericBench bench() {
        Crew crew = Crew.of(server);
        return crew == null ? null : crew.bench();
    }

    /**
     * A model to speak through, or empty for silence.
     *
     * <p>Whatever the bench is serving, first come. A stranger has no claim on a
     * particular model and no seat to keep, which is the difference between it
     * and a crew member - if the bench is busy or absent it simply says less.
     */
    private String modelForStranger() {
        ClericBench bench = bench();
        if (bench == null) {
            return "";
        }
        ClericBench.Reach reach = bench.reach();
        if (reach == null) {
            return "";
        }
        for (String model : reach.models()) {
            if (SeatName.isChatCleric(model)) {
                return model;
            }
        }
        return "";
    }

    /** For tests and for {@code /octia crew} to be able to say who is out there. */
    public String present() {
        return body == null ? "" : body.seatName().toLowerCase(Locale.ROOT);
    }
}
