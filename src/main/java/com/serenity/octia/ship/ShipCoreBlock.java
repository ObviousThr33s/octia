package com.serenity.octia.ship;

import com.mojang.serialization.MapCodec;
import com.serenity.octia.OctiaBlocks;

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
     * dig, and a dig is what calls a ship.
     *
     * <p><b>The seam, named so it is not improvised.</b> This one condition is
     * the entire definition of "dig". The moment a second answer is wanted -
     * another mod's dig, a datapacked block, a pot that has already been
     * emptied - the hook is a block tag read here, not a longer instanceof
     * chain and not an event. Nothing needs it today, so nothing is built for
     * it; this note exists so the next hand adds the tag rather than a second
     * call site.
     */
    public static boolean digSiteInRange(BlockGetter level, BlockPos core) {
        for (BlockPos p : BlockPos.betweenClosed(
                core.offset(-CALL_RADIUS, -CALL_RADIUS, -CALL_RADIUS),
                core.offset(CALL_RADIUS, CALL_RADIUS, CALL_RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof BrushableBlock || state.is(Blocks.DECORATED_POT)) {
                return true;
            }
        }
        return false;
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ShipStatus status = reconcile(level, pos, null);
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
        player.displayClientMessage(Component.literal("  " + moorings.count() + " moored across all eras")
                .withStyle(ChatFormatting.DARK_GRAY), false);

        level.playSound(null, pos, status == ShipStatus.CALLED
                        ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.BLOCKS, 0.6f, status.isMoored() ? 1.0f : 0.6f);
        return InteractionResult.SUCCESS;
    }
}
