package com.serenity.octia.ship;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.block.Luminaries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The ship's anchor. A Serenity-class core.
 *
 * <p>Ring it with andesite frame panels and it moors: its position enters
 * {@link ShipMoorings}, which is keyed by {@link BlockPos} alone, so the same
 * coordinates read as moored from every dimension. Leave a dig site nearby and
 * it is <b>called</b> - the ship answers the archaeology hook.
 *
 * <p><b>There is deliberately no BlockEntity.</b> The architecture makes the
 * position-keyed store the spine and every terminal a view onto it. A
 * BlockEntity here would hold a second copy of state the store already owns,
 * and the two would drift. The blockstate carries the status because the status
 * is a view, and the store carries the truth.
 *
 * <p><b>It does not tick either.</b> The call survey reads a 13x13x13 volume,
 * which is fine on demand and indefensible every tick - and the design brief's
 * pillar is that this mod stays cheap enough to sit inside a 300-mod pack. So
 * the survey runs on placement, on neighbour change, and when a player asks.
 *
 * <p><b>The consequence, stated plainly:</b> a dig site that appears outside
 * the hull ring does not call the ship on its own. Nothing notifies a block
 * from six blocks away in vanilla, and hooking every block placement in the
 * game to catch it would trade this mod's "additive, not invasive" pillar for a
 * convenience. Right-clicking the core re-surveys and picks the dig up. If that
 * ever needs to be automatic, the honest fix is an explicit opt-in tick with a
 * measured budget, not a silent global hook.
 */
public class ShipCoreBlock extends Block {

    public static final EnumProperty<ShipStatus> STATUS = EnumProperty.create("status", ShipStatus.class);

    public static final MapCodec<ShipCoreBlock> CODEC = simpleCodec(ShipCoreBlock::new);

    /**
     * How far the core hears a dig. Reads (2*6+1)^3 = 2197 blocks per survey -
     * cheap once, ruinous per tick. See the class note.
     */
    public static final int CALL_RADIUS = 6;

    /**
     * How long one player's readout stays silent after it speaks, in ticks.
     * The 8/23 player clicked the core 26 times in 14 seconds and got the same
     * three lines and a beacon chime every time; three seconds is the answer to
     * that, not a rate limit on asking - a silenced click still re-surveys.
     * Public because the gametest schedules against it.
     *
     * <p>provisional - owner tunes by walking the world
     */
    public static final int READOUT_COOLDOWN_TICKS = 60;

    /** Clockwise from north, 45 degrees apiece. Indexed by {@link #octant}. */
    private static final String[] OCTANTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /**
     * When each player last heard a readout, by game time.
     *
     * <p>Transient and static, on purpose. The class forbids itself a
     * BlockEntity and a cooldown is not worth a SavedData: it is three seconds
     * of politeness, not state the save owns. Written only from the server
     * thread - {@link #readout} is the sole writer and every dimension shares
     * that thread - so a plain HashMap is safe. The accepted residue: an entry
     * outlives its player's logout, the bound is the session's player count,
     * and the cost is sixteen bytes each, gone on restart. Priced and accepted
     * over a WeakHashMap's cleverness.
     */
    private static final Map<UUID, Long> LAST_READOUT = new HashMap<>();

    public ShipCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STATUS, ShipStatus.ADRIFT));
    }

    @Override
    protected MapCodec<ShipCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS);
    }

    /**
     * The halo, when the core is lit - which is to say when it is moored or
     * called, since an adrift core emits nothing. See {@link Luminaries}.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Luminaries.halo(state, level, pos, random);
    }

    /**
     * The hull: the eight horizontal neighbours in the core's own layer must
     * all be andesite frame panels. A ring, not a shell - small enough to build
     * by hand, strict enough that it cannot happen by accident.
     *
     * @param treatAsEmpty a position to evaluate as if it were already air, or
     *        null. This exists because a block being removed may or may not
     *        have been written to the chunk yet when {@code onRemove} runs, and
     *        depending on that ordering is how multiblocks end up
     *        intermittently wrong. The caller states its intent instead.
     */
    public static boolean hullIntact(BlockGetter level, BlockPos core, BlockPos treatAsEmpty) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos ring = core.offset(dx, 0, dz);
                if (ring.equals(treatAsEmpty)) {
                    return false;
                }
                if (!level.getBlockState(ring).is(OctiaBlocks.ANDESITE_FRAME_PANEL)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The archaeology hook. Any brushable block or decorated pot in range is a
     * dig, and a dig is what calls a ship. This returns the dig itself - the
     * first match in scan order - or null; "which dig" has one deterministic
     * answer, and the min-corner bias of that answer is not worth a
     * full-volume nearest scan.
     *
     * <p><b>The seam, named so it is not improvised.</b> This one condition is
     * the entire definition of "dig". The moment a second answer is wanted -
     * another mod's dig, a datapacked block, a pot that has already been
     * emptied - the hook is a block tag read here, not a longer instanceof
     * chain and not an event. Nothing needs it today, so nothing is built for
     * it; this note exists so the next hand adds the tag rather than a second
     * call site.
     */
    public static BlockPos findDig(BlockGetter level, BlockPos core) {
        for (BlockPos p : BlockPos.betweenClosed(
                core.offset(-CALL_RADIUS, -CALL_RADIUS, -CALL_RADIUS),
                core.offset(CALL_RADIUS, CALL_RADIUS, CALL_RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof BrushableBlock || state.is(Blocks.DECORATED_POT)) {
                // betweenClosed hands out one reused cursor, and the early
                // return abandons the iterator - no test can catch the
                // omission, so this comment is the guard.
                return p.immutable();
            }
        }
        return null;
    }

    /** Whether anything is calling. The name {@link #survey} reads on. */
    public static boolean digSiteInRange(BlockGetter level, BlockPos core) {
        return findDig(level, core) != null;
    }

    /** What the core is, right now, without changing anything. */
    public static ShipStatus survey(BlockGetter level, BlockPos pos, BlockPos treatAsEmpty) {
        if (!hullIntact(level, pos, treatAsEmpty)) {
            return ShipStatus.ADRIFT;
        }
        return digSiteInRange(level, pos) ? ShipStatus.CALLED : ShipStatus.MOORED;
    }

    /**
     * Re-surveys every core that a change at {@code source} could affect.
     *
     * <p>A core sits at the centre of its ring, so any panel in that ring has
     * the core among its own eight horizontal neighbours. Vanilla neighbour
     * updates only reach the six faces, never the diagonals, so without this a
     * player who places the final corner panel gets no mooring - the core is
     * never told. Eight block reads per panel change while no core is adjacent,
     * which is the common case; when one is, this costs a full survey - the
     * same survey a face-adjacent placement would have triggered anyway.
     */
    public static void reconcileAdjacentCores(Level level, BlockPos source, BlockPos treatAsEmpty) {
        if (level.isClientSide) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos candidate = source.offset(dx, 0, dz);
                if (level.getBlockState(candidate).getBlock() instanceof ShipCoreBlock) {
                    reconcile(level, candidate, treatAsEmpty);
                }
            }
        }
    }

    /**
     * Re-surveys and reconciles both the blockstate and the store.
     *
     * <p>Server-side only: the store lives on the server, and a client that
     * moored ships would desync immediately.
     */
    public static ShipStatus reconcile(Level level, BlockPos pos, BlockPos treatAsEmpty) {
        if (level.isClientSide) {
            return level.getBlockState(pos).getValue(STATUS);
        }

        ShipStatus status = survey(level, pos, treatAsEmpty);
        BlockState state = level.getBlockState(pos);
        // No hasProperty guard: every caller has already established this is a
        // core, and the client branch above reads STATUS without one.
        if (state.getValue(STATUS) != status) {
            level.setBlockAndUpdate(pos, state.setValue(STATUS, status));
        }

        ShipMoorings moorings = ShipMoorings.get(level.getServer());
        if (status.isMoored()) {
            // moor() answers whether this position was new. That answer was
            // computed and discarded here for as long as this method has
            // existed; FirstLight is the thing that finally listens to it, and
            // it is the whole of the "a hull just completed" signal. See its
            // javadoc for why no separate latch is needed.
            if (moorings.moor(pos)) {
                FirstLight.moored(level, pos);
            }
        } else {
            moorings.unmoor(pos);
        }
        return status;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        reconcile(level, pos, null);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        reconcile(level, pos, null);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Only when the core is genuinely gone, not when its own status changes.
        if (!newState.is(this) && !level.isClientSide) {
            ShipMoorings.get(level.getServer()).unmoor(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * The bearing-and-distance line, as a pure function so a test can hold it
     * to a literal. "calling from NE, 41 paces" - a pace is a block, and the
     * register stays wordy, never a coordinate. No trailing period and no
     * styling: the printer owns those, the tests own this string.
     *
     * <p>The degenerate column is named rather than mumbled: a dig straight up
     * or down the core's own column has dx and dz both zero, atan2(0, 0) would
     * lie "N", and the honest word is "above" or "below". A dy of zero cannot
     * occur there - the dig would be the core.
     */
    public static String calledLine(BlockPos core, BlockPos dig) {
        int dx = dig.getX() - core.getX();
        int dz = dig.getZ() - core.getZ();
        String word = dx == 0 && dz == 0
                ? (dig.getY() > core.getY() ? "above" : "below")
                : octant(dx, dz);
        // 3D Euclidean, whole blocks, never zero - the dig is never the core.
        long paces = Math.round(Math.sqrt(core.distSqr(dig)));
        return "calling from " + word + ", " + paces + (paces == 1 ? " pace" : " paces");
    }

    /**
     * atan2(dx, -dz), not the textbook order: Minecraft's north is negative Z -
     * the same convention as {@code Sightlines.Leg.bearing()} and the debug
     * overlay. floorMod folds the negative half-circle (a bearing of -180
     * rounds to -4, and floorMod 8 gives 4, "S"); Math.round's half-up puts an
     * exact 22.5-degree boundary in the counterclockwise-next octant,
     * deterministically.
     */
    private static String octant(int dx, int dz) {
        return OCTANTS[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8)];
    }

    /**
     * The readout: what a right-click says, and whether it says it at all.
     * Returns whether it spoke - that boolean is the testable decision.
     *
     * <p><b>Reconcile runs before and outside the gate.</b> The click is the
     * documented re-survey, so a silenced click still picks up a dig, updates
     * the blockstate, the store and the halo. Silence never means stale - and
     * a status change inside the window is also silenced, because the
     * charter's "re-print nothing" is unconditional, the blockstate and halo
     * still say it, and three seconds is the price.
     *
     * <p><b>Per player, not per core.</b> Two players at one core each speak;
     * one player hopping core A to core B inside sixty ticks is silenced at B.
     * Accepted and named, since two cores are never a three-second walk apart
     * in practice. A suppressed click writes nothing, so the window re-arms
     * from the last time this spoke: the 26-click player hears the readout
     * once per sixty ticks, not never.
     *
     * <p><b>Cost, priced.</b> A CALLED click runs two 13x13x13 scans -
     * reconcile's and {@link #findDig}'s. The class already prices the survey
     * "fine on demand, indefensible per tick", and the cooldown caps demand at
     * one readout per player per three seconds.
     */
    public static boolean readout(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return false;
        }

        ShipStatus status = reconcile(level, pos, null);

        long now = level.getGameTime();
        Long last = LAST_READOUT.get(player.getUUID());
        if (last != null && now - last < READOUT_COOLDOWN_TICKS) {
            return false;
        }
        LAST_READOUT.put(player.getUUID(), now);

        ShipMoorings moorings = ShipMoorings.get(level.getServer());

        // The mod's display name, spelled out. tools/rename-mod.ps1 rewrites
        // only the package, the class name and the MOD_ID literal, and its
        // survivor grep is case-sensitive on the lower-case id - so any
        // capitalised spelling of the name in Java survives a rename untouched.
        // That is why this one still said OCTIOID. It and the "Octia:" log
        // prefixes are the places a rename has to be finished by hand.
        player.displayClientMessage(Component.literal("OCTIA ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(status.getSerializedName().toUpperCase())
                        .withStyle(status.isMoored() ? ChatFormatting.AQUA : ChatFormatting.GRAY)), false);

        String detail = switch (status) {
            case ADRIFT -> "no hull. ring the core with andesite frame panels.";
            case MOORED -> "hull intact. no dig within " + CALL_RADIUS + " blocks.";
            case CALLED -> "hull intact. a dig is calling.";
        };
        player.displayClientMessage(Component.literal("  " + detail).withStyle(ChatFormatting.GRAY), false);

        if (status == ShipStatus.CALLED) {
            // The null guard is paranoia against anything moving between
            // reconcile's survey and this second scan inside one server-thread
            // call; it costs one branch and cannot print a lie.
            BlockPos dig = findDig(level, pos);
            if (dig != null) {
                player.displayClientMessage(Component.literal("  " + calledLine(pos, dig))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        player.displayClientMessage(Component.literal("  " + moorings.count() + " moored across all eras")
                .withStyle(ChatFormatting.DARK_GRAY), false);

        level.playSound(null, pos, status == ShipStatus.CALLED
                        ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.BLOCKS, 0.6f, status.isMoored() ? 1.0f : 0.6f);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The interaction succeeds whether or not it speaks - the arm swings
        // either way, and the silenced click has still re-surveyed.
        readout(level, pos, player);
        return InteractionResult.SUCCESS;
    }
}
