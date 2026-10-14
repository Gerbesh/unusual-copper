package io.github.gerbesh.unusualcopper.itempipe;

import io.github.gerbesh.unusualcopper.block.AbstractPipeBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkHooks;

public final class ItemPipeBlock extends AbstractPipeBlock {
    @Override protected boolean connects(LevelAccessor level, BlockPos pos, Direction side) {
        var target = pos.relative(side);
        if (!level.hasChunkAt(target)) return false;
        var pipe = level.getBlockEntity(pos) instanceof ItemPipeBlockEntity entity ? entity : null;
        if (pipe != null && pipe.effective(side) == PipeMode.DISABLED) return false;
        var neighbor = level.getBlockEntity(target);
        if (neighbor instanceof ItemPipeBlockEntity other) return (pipe == null || pipe.rule(side).mode == PipeMode.AUTO) && other.rule(side.getOpposite()).mode == PipeMode.AUTO;
        if (neighbor instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
            var state = level.getBlockState(target);
            if (state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    && !level.hasChunkAt(target.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)))) return false;
        }
        return neighbor != null && neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).isPresent();
    }
    @Override protected void markDirty(LevelAccessor level) { ItemNetworks.dirty(level); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ItemPipeBlockEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) pipe.owner(player.getUUID());
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(io.github.gerbesh.unusualcopper.registry.ModContent.FACE_SELECTOR.get())) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe && pipe.canAccess(player)) {
            if (player.isCrouching() && player.getItemInHand(hand).isEmpty()) pipe.recover(player);
            else NetworkHooks.openScreen(server, pipe, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe && pipe.cargoCount() > 0)
            popResource(level, pos, io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(pipe.cargo()));
        super.onRemove(state, level, pos, next, moving);
    }
}
