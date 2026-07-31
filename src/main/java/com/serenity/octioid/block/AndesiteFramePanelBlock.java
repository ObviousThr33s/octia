package com.serenity.octioid.block;

import com.mojang.serialization.MapCodec;
import com.serenity.octioid.ship.ShipCoreBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A full cube of andesite framing around a panel that may or may not be lit.
 *
 * <p>The palette is the one the design brief fixes: polished andesite, with the
 * accent carried by the light rather than by a second stone. Right-click cycles
 * {@link PanelLight#NONE} to {@code GENERIC} to {@code STYLED} and back, so one
 * block covers structural fill and both lighting tiers without three registry
 * entries or three items in the creative tab.
 *
 * <p>The light level is not read here — it is supplied at registration by
 * {@code Properties.lightLevel}, which is the only place the game will accept
 * it. See {@code OctioidBlocks}.
 */
public class AndesiteFramePanelBlock extends Block {

    public static final EnumProperty<PanelLight> LIGHT = EnumProperty.create("light", PanelLight.class);

    /**
     * Required since 1.20.5: every block type serialises itself. {@code simpleCodec}
     * covers any block whose only constructor argument is its Properties.
     */
    public static final MapCodec<AndesiteFramePanelBlock> CODEC = simpleCodec(AndesiteFramePanelBlock::new);

    public AndesiteFramePanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIGHT, PanelLight.NONE));
    }

    @Override
    protected MapCodec<AndesiteFramePanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    /**
     * A panel is part of a ship's hull, and the core sits at the centre of the
     * ring it forms. Vanilla neighbour updates never reach diagonals, so the
     * panel tells the core rather than waiting to be asked - otherwise placing
     * the final corner panel completes a hull that nothing notices.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        ShipCoreBlock.reconcileAdjacentCores(level, pos, null);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this)) {
            // This position is leaving the hull. Say so explicitly rather than
            // relying on whether the chunk has been written yet.
            ShipCoreBlock.reconcileAdjacentCores(level, pos, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        // Let the client predict the swing; the server owns the state change.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        PanelLight next = state.getValue(LIGHT).next();
        level.setBlockAndUpdate(pos, state.setValue(LIGHT, next));

        // Pitch rises with the light so the cycle is audible without looking.
        float pitch = 0.8f + (next.lightLevel() / 15.0f) * 0.6f;
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, pitch);

        return InteractionResult.SUCCESS;
    }
}
