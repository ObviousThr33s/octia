package com.serenity.octia.crew;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * The way aboard for someone with no client.
 *
 * <p>{@code PlayerList.placeNewPlayer} is the only door into the player list —
 * it loads the player's data, adds the entity to the level, tells every other
 * client someone joined, and puts a name in the tab list. It takes a
 * {@link Connection}. A cleric has no socket, so this is a Connection with no
 * channel behind it: every packet written to it is dropped on the floor, and
 * every method that would have reached for the netty channel is answered here
 * instead.
 *
 * <p><b>Why override rather than fake a channel.</b> The usual trick is to give
 * the connection an {@code EmbeddedChannel} so the vanilla code paths run
 * unchanged, but {@code Connection.channel} is private and reaching it needs an
 * access widener — a build-wide change, remapped separately in dev and in the
 * published jar, for one field. Every method vanilla actually calls on the way
 * through {@code placeNewPlayer} turns out to be public and overridable, so
 * none of that is needed. Checked by reading the mapped 1.21.1 jar rather than
 * by remembering it; see AGENTS.md V.
 *
 * <p><b>Why {@code send} is a no-op and not a queue.</b> Vanilla's own
 * {@code send} queues a packet whenever the connection is not yet live, to be
 * flushed once it is. This connection is never live, so the queue would grow
 * for every chunk, entity move, and time-of-day update for the life of the
 * crew member — a leak that looks exactly like an ordinary memory climb. Drop
 * them instead. Nobody is listening.
 *
 * <p><b>Why this connection is never ticked.</b> It is not registered with
 * {@code ServerConnectionListener}, so the server never calls {@link #tick()},
 * so {@code ServerCommonPacketListenerImpl.keepConnectionAlive} never runs.
 * That is load-bearing: keep-alive would go unanswered and disconnect the whole
 * crew for timeout after thirty seconds. The corollary is that
 * {@code ServerPlayer.doTick} never gets called either, which is why
 * {@link CrewPlayer} calls it by hand.
 */
final class CrewGangway extends Connection {

    /**
     * The address the server logs and shows to admins. Loopback with port 0 is
     * honest — this connection begins and ends inside this process — and it is
     * never opened, resolved, or connected to.
     */
    private static final SocketAddress ABOARD = new InetSocketAddress("127.0.0.1", 0);

    CrewGangway() {
        super(PacketFlow.SERVERBOUND);
    }

    // ---- Nothing is ever sent ------------------------------------------------

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
    }

    @Override
    public void flushChannel() {
    }

    // ---- Nothing is ever received --------------------------------------------

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
    }

    @Override
    public void configurePacketHandler(ChannelPipeline pipeline) {
    }

    @Override
    public void setupCompression(int threshold, boolean validate) {
    }

    @Override
    public void tick() {
    }

    // ---- Answers that would otherwise have asked the channel ------------------

    /**
     * True, though there is nothing to be connected to.
     *
     * <p>The alternative is a connection the server believes is already dead
     * while a player sits on the end of it, and the disagreement surfaces as
     * packets quietly diverted into vanilla's pending queue. Saying yes and
     * dropping the packets keeps one story.
     */
    @Override
    public boolean isConnected() {
        return true;
    }

    /** In-process is exactly what this is; it also spares the crew compression. */
    @Override
    public boolean isMemoryConnection() {
        return true;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ABOARD;
    }

    @Override
    public String getLoggableAddress(boolean logIps) {
        return "aboard";
    }

    // ---- Leaving is Crew's business, not the connection's ---------------------

    @Override
    public void disconnect(Component reason) {
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public void setReadOnly() {
    }
}
