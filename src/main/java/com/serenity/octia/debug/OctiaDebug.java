package com.serenity.octia.debug;

import java.util.ArrayList;
import java.util.List;

import com.serenity.octia.Octia;
import com.serenity.octia.ship.ShipMoorings;
import com.serenity.octia.world.OctiaWorldOption;

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
 * <p><b>Read the honest scope first.</b> This mod does not generate anything
 * during chunk generation yet — {@code OctiaBeacon}'s own note says so: the
 * beacon is placed at first load, and real worldgen would mean a configured
 * feature, a placed feature, and a biome modification. So "where the generations
 * are" today means exactly two things, and the overlay must not imply a third:
 *
 * <ul>
 *   <li>the <b>beacon</b> — one per save, at spawn, if the world was created
 *       with Octia switched on;</li>
 *   <li>every <b>mooring</b> in {@link ShipMoorings} — the beacon's own core
 *       among them, plus every hull a player has completed since.</li>
 * </ul>
 *
 * <p>Moorings are the interesting set because the store is deliberately
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
     */
    public record Snapshot(boolean enabled, boolean beaconRaised, boolean beaconKnown,
                           long beaconAt, List<Long> moorings) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Snapshot> TYPE =
                new CustomPacketPayload.Type<>(Octia.id("debug_snapshot"));

        /**
         * Five fields rather than an Optional for the beacon, because the pair
         * of primitives needs only codecs that certainly exist, and because a
         * sentinel would have to pick a "impossible" packed position — and 0,
         * the obvious choice, is the block at world origin.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, Snapshot::enabled,
                        ByteBufCodecs.BOOL, Snapshot::beaconRaised,
                        ByteBufCodecs.BOOL, Snapshot::beaconKnown,
                        ByteBufCodecs.VAR_LONG, Snapshot::beaconAt,
                        ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), Snapshot::moorings,
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
     * Registers both payload types. Must run on the common initialiser, not the
     * client one: a payload type known to only one side is a disconnect on the
     * first packet, and a dedicated server needs the C2S type registered to be
     * able to receive at all.
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

    /** Snapshots the save and sends it to one player. Server thread only. */
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
                moorings));
    }
}
