package io.github.gerbesh.unusualcopper.fluid;
import io.github.gerbesh.unusualcopper.block.AbstractPipeBlock;
import io.github.gerbesh.unusualcopper.itempipe.PipeMode;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class FluidPipeBlock extends AbstractPipeBlock {
    @Override protected boolean connects(LevelAccessor level,BlockPos pos,Direction side){
        BlockPos next=pos.relative(side);
        if(!level.hasChunkAt(next))return false;
        var entity=level.getBlockEntity(pos);
        FluidPipeBlockEntity pipe=entity instanceof FluidPipeBlockEntity f?f:null;
        if(pipe!=null&&pipe.mode(side)==PipeMode.DISABLED)return false;
        var neighbor=level.getBlockEntity(next);
        if(neighbor instanceof FluidPipeBlockEntity other)
            return (pipe==null||pipe.mode(side)==PipeMode.AUTO)&&other.mode(side.getOpposite())==PipeMode.AUTO;
        return neighbor!=null&&neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER,side.getOpposite()).isPresent();
    }
    @Override protected void markDirty(LevelAccessor level){FluidNetworks.dirty(level);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FluidPipeBlockEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        if(placer instanceof Player player&&level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)pipe.owner(player.getUUID());
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.getItemInHand(hand).is(ModContent.FACE_SELECTOR.get()))return InteractionResult.PASS;
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe&&pipe.canAccess(player))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("fluid.unusualcopper.pipe_status",
                    pipe.buffer.getFluidAmount(),pipe.buffer.getCapacity()),true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
