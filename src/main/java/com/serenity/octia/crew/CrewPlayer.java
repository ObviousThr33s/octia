package com.serenity.octia.crew;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A cleric with a body: a real {@link ServerPlayer} that no client is driving.
 *
 * <p>Everything the game knows about players applies to one of these. It is in
 * the player list, it appears in the tab list, it loads the chunks around it, it
 * is pushed by boats and hurt by lava, and every other client — including
 * everyone who joined over LAN — sees it because the ordinary entity tracker
 * sends it to them. That is the whole point: not a mob wearing a player skin,
 * but a player, so that "log on" means what it says.
 *
 * <p><b>Why this class ticks itself.</b> Vanilla splits a player's tick in two.
 * {@code ServerPlayer.tick} is the cheap bookkeeping half and is called by the
 * level for every entity. The half that actually moves the body —
 * {@code ServerPlayer.doTick}, which calls {@code Player.tick} and from there
 * gravity, collision, and {@code travel} — is called from the network listener,
 * once per packet tick. A crew member has no network listener that ticks (see
 * {@link CrewGangway}), so without the call below one would stand in the air
 * where it was placed, frozen, forever. Confirmed by reading the bytecode of
 * both methods in the mapped jar: exactly one of them reaches
 * {@code Player.tick}, so calling both here ticks the body once, not twice.
 */
public final class CrewPlayer extends ServerPlayer {

    /** How close a follower gets before it stops walking. Two blocks is a shove. */
    private static final double FOLLOW_STOP = 3.0;

    /** How far a crew member will look for a player it was not given by name. */
    private static final double NEAREST_RANGE = 48.0;

    /**
     * How often the body's position is pushed back into the packet listener and
     * the chunk map. Every tick is wasteful, never is a crew member who walks
     * out of their own loaded chunks and stops being tracked.
     */
    private static final int RESYNC_TICKS = 10;

    private final Cleric cleric;
    private Order order = Order.HOLD;

    /**
     * Set once when leaving. Read in exactly one place, {@link #die}, so that a
     * body that dies twice — or dies after being dismissed — queues one log-off
     * and not two. It does not guard dismissal: a second {@code Crew.logOff}
     * refuses on its own, because by then the seat is out of the muster map and
     * the body is already removed from the world.
     */
    private boolean loggingOff;

    private CrewPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, Cleric cleric) {
        super(server, level, profile, information());
        this.cleric = cleric;
    }

    /**
     * What a crew member claims about its client, since it does not have one.
     *
     * <p>Two fields here are decisions rather than defaults. {@code allowsListing}
     * is true because a crew member nobody can see in the tab list has not
     * visibly logged on. The view distance is two chunks rather than vanilla's
     * default because every crew member is a real player and real players load
     * the world around them — a mustered bench of eight at the default distance
     * is eight more loaded regions, and the crew have nothing to look at.
     */
    private static ClientInformation information() {
        return new ClientInformation(
                "en_us",
                2,
                ChatVisiblity.FULL,
                true,
                0,
                HumanoidArm.RIGHT,
                false,
                true);
    }

    /**
     * Puts a cleric aboard, at the given spot, and returns the body.
     *
     * <p>The order of the last few lines is not arbitrary.
     * {@code placeNewPlayer} drops the player at the world spawn (or wherever
     * their saved data says), so the teleport has to come after it; the health
     * reset and {@code unsetRemoved} undo the state a freshly loaded, possibly
     * long-dead saved player can arrive in, since a crew member's data file
     * outlives the crew member; and the game mode is set last so it survives
     * both.
     *
     * @param name the coined seat name; must be legal, see {@link SeatName}
     */
    static CrewPlayer seat(MinecraftServer server, ServerLevel level, Vec3 where,
                           float yaw, String name, Cleric cleric) {
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        CrewPlayer body = new CrewPlayer(server, level, profile, cleric);

        server.getPlayerList().placeNewPlayer(
                new CrewGangway(),
                body,
                new CommonListenerCookie(profile, 0, body.clientInformation(), false));

        body.teleportTo(level, where.x, where.y, where.z, yaw, 0.0F);
        body.setHealth(body.getMaxHealth());
        body.unsetRemoved();
        body.setGameMode(GameType.SURVIVAL);
        body.connection.resetPosition();
        return body;
    }

    // ---- The tick ------------------------------------------------------------

    @Override
    public void tick() {
        // Inputs first: the tick that follows is the one that consumes them.
        steer();

        if (this.server.getTickCount() % RESYNC_TICKS == 0) {
            this.connection.resetPosition();
            this.serverLevel().getChunkSource().move(this);
        }

        super.tick();
        this.doTick();
    }

    /** Turns the standing order into this tick's movement inputs. */
    private void steer() {
        float forward = 0.0F;
        boolean jump = false;

        switch (order.verb()) {
            case GO -> {
                Order.Heading heading = Order.Heading.of(order.arg());
                if (heading == null) {
                    order = Order.HOLD;
                } else {
                    face(heading.yaw(), 0.0F);
                    forward = 1.0F;
                }
            }
            case FOLLOW -> {
                ServerPlayer target = target(order.arg());
                if (target == null) {
                    order = Order.HOLD;
                } else {
                    lookAt(target);
                    if (distanceToSqr(target) > FOLLOW_STOP * FOLLOW_STOP) {
                        forward = 1.0F;
                    }
                }
            }
            // LOOK finishes the moment it is carried out, exactly like SAY and
            // JUMP below, and the clear to HOLD at the end of this branch is
            // what makes that true. Order used to carry an isOneShot() saying
            // otherwise - it counted SAY and JUMP only, so it had contradicted
            // this branch since the crew landed. Nothing but its own test ever
            // called it, so it was removed rather than corrected. The three
            // branches below that clear HOLD unconditionally - LOOK, JUMP, SAY -
            // are now the only statement of which orders are one-shot. GO and
            // FOLLOW clear it too, but only on a heading that will not parse or
            // a target that is gone, which is a failure and not a completion. A
            // caller that needs to ask before acting is what would justify
            // bringing the predicate back.
            case LOOK -> {
                ServerPlayer target = target(order.arg());
                if (target != null) {
                    lookAt(target);
                }
                order = Order.HOLD;
            }
            case JUMP -> {
                jump = onGround();
                order = Order.HOLD;
            }
            case SAY -> {
                speak(order.arg());
                order = Order.HOLD;
            }
            case HOLD -> {
            }
        }

        // Straight onto the input fields, and NOT through ServerPlayer's own
        // setPlayerInput.
        //
        // That method looks like exactly the right door - it is what the server
        // calls when a client sends its inputs - and it is a no-op here. Its
        // whole body sits inside `if (this.isPassenger())`, because vanilla only
        // ever needs it for steering a boat or a horse: a real player walking on
        // the ground moves on the client and reports absolute positions, so the
        // server never simulates a walk. A crew member has no client to do that,
        // so it writes the inputs LivingEntity.aiStep is about to read.
        //
        // This cost a gametest to find and nothing else would have found it. The
        // build was clean, the crew logged on, the tab list filled up, and every
        // one of them stood perfectly still.
        this.xxa = 0.0F;
        this.zza = forward;
        setJumping(jump);
    }

    /**
     * Says a line into chat, exactly as the vanilla client would render it.
     *
     * <p>Sent as a system message rather than a signed player message on
     * purpose: a signed chat message needs a real session key from a real
     * login, and forging one is both impossible offline and the wrong shape.
     * The vanilla {@code chat.type.text} key gives it the same
     * {@code <name> line} rendering everyone already reads as speech.
     */
    private void speak(String line) {
        if (line.isEmpty()) {
            return;
        }
        this.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.text", getDisplayName(), Component.literal(line)),
                false);
    }

    /**
     * The player an order is about.
     *
     * <p>An empty name means "whoever is nearest", which is the sane reading of
     * a model that said {@code follow} and forgot the rest. A name that nobody
     * answers to falls back the same way rather than cancelling the order —
     * small models misspell, and a crew member that follows the only person in
     * the room is right more often than one that stops.
     *
     * <p><b>A crewmate can be named, but is never the fallback.</b>
     * {@code getPlayerByName} does not filter crew — they are ordinary entries
     * in the player list — so {@code follow phi_4} resolves to another crew
     * member, and {@link Situation} lists crewmates by name in the report, so a
     * cleric is handed exactly what it needs to do it. {@link #nearest()}
     * deliberately skips crew, so the no-name path never does. Whether crew
     * following crew is wanted is a design question, and this is where it would
     * be decided.
     */
    private ServerPlayer target(String name) {
        if (!name.isEmpty()) {
            ServerPlayer named = this.server.getPlayerList().getPlayerByName(name);
            if (named != null && named != this) {
                return named;
            }
        }
        return nearest();
    }

    /** The closest player that is not crew, and not this body. */
    private ServerPlayer nearest() {
        ServerPlayer best = null;
        double bestDist = NEAREST_RANGE * NEAREST_RANGE;
        for (ServerPlayer other : this.serverLevel().players()) {
            if (other == this || other instanceof CrewPlayer) {
                continue;
            }
            double d = distanceToSqr(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private void lookAt(Entity what) {
        double dx = what.getX() - getX();
        double dz = what.getZ() - getZ();
        double dy = what.getEyeY() - getEyeY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, flat) * (180.0 / Math.PI)));
        face(yaw, pitch);
    }

    /**
     * Turns head and body together.
     *
     * <p>The previous rotations are left alone so the client interpolates the
     * turn instead of snapping it — a crew member that pivots over three ticks
     * reads as alive, and one that teleports its head does not.
     */
    private void face(float yaw, float pitch) {
        setYRot(Mth.wrapDegrees(yaw));
        setYHeadRot(Mth.wrapDegrees(yaw));
        setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
    }

    // ---- Leaving -------------------------------------------------------------

    /**
     * There is no respawn screen for someone with no client, so death is a
     * disconnect.
     *
     * <p>Deferred to the server's task queue rather than done here: this runs
     * inside the level's entity tick, and removing a player from the player list
     * mid-iteration is the kind of concurrent modification that surfaces as a
     * crash three ticks later in unrelated code.
     */
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (loggingOff) {
            return;
        }
        loggingOff = true;
        MinecraftServer server = this.server;
        // Crew.of is not null-checked, and that is an assumption rather than a
        // fact of the type: it answers null for any server not between
        // SERVER_STARTED and SERVER_STOPPING. It holds because die() runs
        // inside a server tick and the task queued here is drained in the same
        // server loop, long before stopServer fires SERVER_STOPPING and clears
        // the muster. A death queued from off-tick would make this an NPE on
        // the way down.
        server.execute(() -> Crew.of(server).logOff(this, "died"));
    }

    void markLoggingOff() {
        this.loggingOff = true;
    }

    // ---- What the crew driver needs -----------------------------------------

    public Cleric cleric() {
        return cleric;
    }

    public Order order() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order == null ? Order.HOLD : order;
    }

    public String seatName() {
        return getGameProfile().getName();
    }
}
