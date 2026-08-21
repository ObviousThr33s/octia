package com.serenity.octia.debug;

import java.util.ArrayList;
import java.util.List;

import com.serenity.octia.Octia;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.world.OctiaWorldOption;
import com.serenity.octia.world.OctiaWorldgen;
import com.serenity.octia.world.RuinRegistry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the debug map is looking at.
 *
 * <p><b>Read the honest scope first.</b> Three things, and the overlay must not
 * imply a fourth:
 *
 * <ul>
 *   <li>the <b>beacon</b> — one per save, at spawn, if the world was created
 *       with Octia switched on;</li>
 *   <li>every <b>mooring</b> in {@link ShipMoorings} — the beacon's own core
 *       among them, plus every hull a player has completed since;</li>
 *   <li>nearby <b>obelisks</b> from {@link RuinRegistry} — landmarks worldgen
 *       raised, which the mod could not point at until the registry existed
 *       because a feature leaves no record of itself.</li>
 * </ul>
 *
 * <p><b>The three are not the same kind of claim, and the readout says so.</b>
 * The moorings are every mooring in the save. The obelisks are the ones near
 * enough to be worth sending and no more than a capped count of those - the
 * registry grows with exploration and a well-travelled world holds thousands, so
 * shipping all of them twice a second would be a large packet and an unreadable
 * box. Derelicts and waystations are recorded too and are deliberately not drawn:
 * a debug map that plots everything is a texture.
 *
 * <p>Moorings remain the interesting set because that store is deliberately
 * dimension-agnostic: one file for the whole save, keyed by position and nothing
 * else. The overlay therefore plots positions that may belong to another
 * dimension entirely, and says so rather than pretending they are all here.
 *
 * <p><b>Pull, not push.</b> The server sends a snapshot when a player joins and
 * whenever a client asks. It does not stream: a debug overlay nobody has open
 * should cost nothing, and the client asks only while it is open. Nothing here
 * is authoritative — it is a view, and a stale one between refreshes.
 */
public final class OctiaDebug {

    /**
     * The widest the debug map can be zoomed out, in blocks from the middle to
     * an edge. Must not be smaller than the last entry of
     * {@code OctiaDebugOverlay.RANGES}, or the map would show empty space it
     * could have filled.
     */
    public static final int OBELISK_REACH = 1024;

    /**
     * Most obelisks worth sending. Past this the box is a smear, not a map.
     *
     * <p>Public alongside the reach because the overlay's readout has to say
     * which of the two it is showing - "12 within 1024b" and "64+, capped" are
     * different claims, and a readout that presented a truncated list as a total
     * would be the kind of quiet lie the rest of this overlay exists to avoid.
     */
    public static final int OBELISK_LIMIT = 64;

    /** How the client asks for a fresh snapshot. Carries nothing. */
    public record Request() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Octia.id("debug_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                StreamCodec.unit(new Request());

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * The answer.
     *
     * @param enabled      whether Octia is switched on for this save at all
     * @param beaconRaised whether the beacon has gone up
     * @param beaconKnown  whether this save recorded WHERE it went up — false on
     *                     every world created before that was written down, which
     *                     is not the same as there being no beacon
     * @param beaconAt     packed beacon position, meaningless unless beaconKnown
     * @param moorings     every moored position in the save, packed, unordered
     * @param obelisks     obelisks near the asking player, packed - NOT all of
     *                     them; see {@link #send} for why this one is bounded
     *                     when the moorings are not
     */
    public record Snapshot(boolean enabled, boolean beaconRaised, boolean beaconKnown,
                           long beaconAt, List<Long> moorings, List<Long> obelisks)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Snapshot> TYPE =
                new CustomPacketPayload.Type<>(Octia.id("debug_snapshot"));

        /**
         * A pair of primitives rather than an Optional for the beacon, because
         * the pair needs only codecs that certainly exist, and because a
         * sentinel would have to pick an "impossible" packed position — and 0,
         * the obvious choice, is the block at world origin.
         *
         * <p><b>Six fields is the ceiling.</b> {@code StreamCodec.composite}
         * stops at six in 1.21.1, checked against the jar, so this record is
         * full. A seventh thing worth drawing means either folding the beacon's
         * two fields into one codec or nesting a sub-record - not adding a
         * parameter and hoping.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, Snapshot::enabled,
                        ByteBufCodecs.BOOL, Snapshot::beaconRaised,
                        ByteBufCodecs.BOOL, Snapshot::beaconKnown,
                        ByteBufCodecs.VAR_LONG, Snapshot::beaconAt,
                        ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), Snapshot::moorings,
                        ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), Snapshot::obelisks,
                        Snapshot::new);

        @Override
        public CustomPacketPayload.Type<Snapshot> type() {
            return TYPE;
        }

        /** The beacon position, or null if this save never recorded one. */
        public BlockPos beacon() {
            return beaconKnown ? BlockPos.of(beaconAt) : null;
        }
    }

    private OctiaDebug() {
    }

    /**
     * Registers both payload types, the server's receiver for {@link Request},
     * and the unasked snapshot a joining player gets. Must run on the common
     * initialiser, not the client one: a payload type known to only one side is
     * a disconnect on the first packet, and a dedicated server needs the C2S
     * type registered - and this receiver installed - to answer at all.
     */
    public static void bootstrap() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.TYPE, Snapshot.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) ->
                context.player().server.execute(() -> send(context.player())));

        // A joining player gets one unasked, so an overlay opened before the
        // first request has something to draw rather than an empty box.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> send(handler.getPlayer())));
    }

    /**
     * Snapshots the save and sends it to one player. Server thread only.
     *
     * <p><b>Seam.</b> This is the whole push side of the debug view, and nothing
     * outside this class calls it today - the visibility is the only thing that
     * makes it a seam rather than a private helper. Anything that wants the map
     * to react rather than poll (a mooring being made, a beacon being raised)
     * calls this with the player it concerns; the class note above is the
     * standing argument for why nothing does yet.
     */
    public static void send(ServerPlayer player) {
        if (player == null) {
            return;
        }
        OctiaWorldOption option = OctiaWorldOption.get(player.server);
        BlockPos beacon = option.beaconAt();

        List<Long> moorings = new ArrayList<>();
        for (BlockPos pos : ShipMoorings.get(player.server).positions()) {
            moorings.add(pos.asLong());
        }

        ServerPlayNetworking.send(player, new Snapshot(
                option.enabled(),
                option.beaconRaised(),
                beacon != null,
                beacon == null ? 0L : beacon.asLong(),
                moorings,
                obelisksNear(player)));
    }

    /**
     * Obelisks worth drawing for this player, and only those.
     *
     * <p><b>Bounded, where the moorings are not, and the difference is not an
     * inconsistency.</b> Moorings are ships players built, so a save holds a
     * handful and sending all of them costs nothing. {@link RuinRegistry} is
     * filled by worldgen and grows with exploration - a well-travelled world
     * holds thousands - so the same treatment would put a packet of that size on
     * the wire twice a second for as long as the map is open.
     *
     * <p>So it is cut twice: to the widest range the map can show, because
     * anything further can never be drawn, and then to a count, because a
     * thousand obelisks inside that range would be an unreadable box as well as
     * a large packet. {@code OBELISK_LIMIT} marks are already more than the eye
     * can separate at this scale.
     */
    private static List<Long> obelisksNear(ServerPlayer player) {
        List<Long> out = new ArrayList<>();
        BlockPos at = player.blockPosition();

        for (BlockPos obelisk : RuinRegistry.get(player.server).of(OctiaWorldgen.OBELISK)) {
            double dx = obelisk.getX() - at.getX();
            double dz = obelisk.getZ() - at.getZ();
            if (dx * dx + dz * dz > (double) OBELISK_REACH * OBELISK_REACH) {
                continue;
            }
            out.add(obelisk.asLong());
            if (out.size() >= OBELISK_LIMIT) {
                break;
            }
        }
        return out;
    }
}
