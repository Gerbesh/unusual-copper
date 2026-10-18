package io.github.gerbesh.unusualcopper.reactor;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;

public final class ReactorBlock extends BaseEntityBlock {
    public static final BooleanProperty FORMED=BooleanProperty.create("formed");
    public static final BooleanProperty ACTIVE=net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    public ReactorBlock(){super(Properties.of().strength(8,1200).sound(SoundType.COPPER).noOcclusion().requiresCorrectToolForDrops().lightLevel(s->s.getValue(ACTIVE)?14:s.getValue(FORMED)?3:0));registerDefaultState(stateDefinition.any().setValue(FORMED,false).setValue(ACTIVE,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FORMED,ACTIVE);}
    @Override public RenderShape getRenderShape(BlockState state){return state.getValue(FORMED)?RenderShape.ENTITYBLOCK_ANIMATED:RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ReactorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ModContent.REACTOR_ENTITY.get(),ReactorBlockEntity::tick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack item){var r=ReactorBlockEntity.at(level,pos);if(!level.isClientSide&&r!=null){if(r.owner==null&&placer!=null)r.owner=placer.getUUID();r.tryForm();r.setChanged();}}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){var r=ReactorBlockEntity.at(level,pos);if(r!=null)r.tryForm();}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var part=ReactorBlockEntity.at(level,pos);if(part==null||!part.canAccess(player))return InteractionResult.FAIL;
        var core=part.core();if(core==null){part.tryForm();core=part.core();}
        if(player.getItemInHand(hand).is(ModContent.FACE_SELECTOR.get())&&core!=null){part.togglePort(hit.getDirection());io.github.gerbesh.unusualcopper.energy.EnergyNetworks.dirty(level);level.updateNeighborsAt(pos,this);player.displayClientMessage(net.minecraft.network.chat.Component.translatable((part.disabledFaces&(1<<hit.getDirection().ordinal()))==0?"reactor.port_open":"reactor.port_closed"),true);return InteractionResult.SUCCESS;}
        if(core!=null){
            if(FluidUtil.interactWithFluidHandler(player,hand,level,pos,hit.getDirection()))return InteractionResult.SUCCESS;
            NetworkHooks.openScreen((ServerPlayer)player,core,core.getBlockPos());return InteractionResult.SUCCESS;
        }
        if(part.payload()){NetworkHooks.openScreen((ServerPlayer)player,part,pos);return InteractionResult.SUCCESS;}
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(part.buildIssue),true);return InteractionResult.SUCCESS;
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        var r=ReactorBlockEntity.at(level,pos);if(r!=null&&r.isController()&&r.generated>0&&random.nextInt(40)==0)
            level.playLocalSound(pos.getX()+1.5,pos.getY()+2,pos.getZ()+1.5,net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,net.minecraft.sounds.SoundSource.BLOCKS,.22F,.7F,false);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        var drops=super.getDrops(state,builder);var entity=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(entity instanceof ReactorBlockEntity r&&r.payload())for(var item:drops)if(item.is(asItem())){item.getOrCreateTag().put("BlockEntityTag",r.memory());break;}
        return drops;
    }
    @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving){if(!old.is(next.getBlock())){var r=ReactorBlockEntity.at(level,pos);if(r!=null)r.unform();}super.onRemove(old,level,pos,next,moving);}
}
