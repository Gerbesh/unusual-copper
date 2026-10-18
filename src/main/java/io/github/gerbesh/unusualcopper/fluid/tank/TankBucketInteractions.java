package io.github.gerbesh.unusualcopper.fluid.tank;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Exactly one whole bucket through the same backend used by pipes. */
public final class TankBucketInteractions {
    private TankBucketInteractions(){}
    public static InteractionResult use(CopperMachineBlockEntity tank,Player player,InteractionHand hand){
        var held=player.getItemInHand(hand);
        if(!(held.getItem() instanceof BucketItem)||!(tank.getLevel() instanceof ServerLevel level))return InteractionResult.PASS;
        if(!ProtectionPolicy.allowed(level,tank.getBlockPos(),player.getUUID()))return message(player,"protected");
        if(tank.owner()==null)tank.setOwner(player.getUUID());
        if(!(tank.fluidStorage() instanceof TankNetworks.Storage storage))return message(player,"protected");
        var contained=FluidUtil.getFluidContained(held);
        if(contained.isPresent()&&contained.get().getAmount()==1000){
            var fluid=contained.get();
            var empty=held.getCraftingRemainingItem();if(empty.isEmpty())empty=new ItemStack(Items.BUCKET);
            if(!storage.fillWhole(fluid))
                return message(player,TankNetworks.view(tank).state()==TankView.State.PROTECTED?"protected":storage.isFluidValid(0,fluid)?"bucket_room":"incompatible");
            if(!player.getAbilities().instabuild){
                held.shrink(1);if(!player.getInventory().add(empty))player.drop(empty,false);
            }
            sound(level,tank,fluid,false);
            return InteractionResult.SUCCESS;
        }
        if(held.is(Items.BUCKET)){
            var fluid=storage.drain(1000,IFluidHandler.FluidAction.SIMULATE);
            if(fluid.getAmount()!=1000)return message(player,"bucket_amount");
            var full=FluidUtil.getFilledBucket(fluid);if(full.isEmpty())return message(player,"no_bucket");
            if(!storage.drainWhole(fluid))return message(player,"bucket_amount");
            if(!player.getAbilities().instabuild)held.shrink(1);
            if(!player.getInventory().add(full))player.drop(full,false);sound(level,tank,fluid,true);return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    private static InteractionResult message(Player p,String key){p.displayClientMessage(Component.translatable("tank.unusualcopper."+key),true);return InteractionResult.CONSUME;}
    private static void sound(ServerLevel level,CopperMachineBlockEntity tank,net.minecraftforge.fluids.FluidStack fluid,boolean fillingBucket){
        var action=fillingBucket?net.minecraftforge.common.SoundActions.BUCKET_FILL:net.minecraftforge.common.SoundActions.BUCKET_EMPTY;
        var sound=fluid.getFluid().getFluidType().getSound(fluid,action);
        if(sound==null)sound=fillingBucket?net.minecraft.sounds.SoundEvents.BUCKET_FILL:net.minecraft.sounds.SoundEvents.BUCKET_EMPTY;
        level.playSound(null,tank.getBlockPos(),sound,net.minecraft.sounds.SoundSource.BLOCKS,.7F,1F);
    }
}
