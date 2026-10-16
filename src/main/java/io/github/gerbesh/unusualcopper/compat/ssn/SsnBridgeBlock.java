package io.github.gerbesh.unusualcopper.compat.ssn;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public final class SsnBridgeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE=BlockStateProperties.LIT;
    public static final BooleanProperty ATTACHED=BooleanProperty.create("attached");
    private static final java.util.Set<String> CABLES=java.util.Set.of("kabel","storage_kabel","filter_kabel","import_kabel","import_filter_kabel","export_kabel","process_kabel");
    static boolean attached(net.minecraft.world.level.LevelAccessor level,BlockPos pos,Direction side){var neighbor=pos.relative(side);if(!level.hasChunkAt(neighbor))return false;var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(neighbor).getBlock());return id!=null&&id.getNamespace().equals("storagenetwork")&&CABLES.contains(id.getPath());}
    public SsnBridgeBlock(){super(BlockBehaviour.Properties.of().strength(3).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false).setValue(ATTACHED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,ACTIVE,ATTACHED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var side=context.getClickedFace().getOpposite();return defaultBlockState().setValue(FACING,side).setValue(ATTACHED,attached(context.getLevel(),context.getClickedPos(),side));}
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState neighbor,net.minecraft.world.level.LevelAccessor level,BlockPos pos,BlockPos other){return state.setValue(ATTACHED,attached(level,pos,state.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SsnBridgeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ModContent.SSN_BRIDGE_ENTITY.get(),SsnBridgeBlockEntity::tick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(placer instanceof Player&&level.getBlockEntity(pos) instanceof SsnBridgeBlockEntity bridge)bridge.owner(placer.getUUID());}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        var held=player.getItemInHand(hand);if(held.is(ModContent.AREA_WAND.get())||held.is(ModContent.FACE_SELECTOR.get()))return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(level instanceof ServerLevel server)||!(level.getBlockEntity(pos) instanceof SsnBridgeBlockEntity bridge)||!bridge.canAccess(player)||!ProtectionPolicy.allowed(server,pos,player.getUUID()))return InteractionResult.FAIL;
        if(player.isShiftKeyDown()){bridge.redstoneMode=(bridge.redstoneMode+1)%3;bridge.setChanged();player.displayClientMessage(Component.translatable("gui.unusualcopper.redstone_mode."+bridge.redstoneMode),true);}
        else player.displayClientMessage(Component.translatable(bridge.enabled()?SsnAccess.supported()?bridge.storage(player.getUUID()).isAvailable()?"bridge.unusualcopper.connected":"bridge.unusualcopper.disconnected":"bridge.unusualcopper.unsupported":"status.unusualcopper.redstone_paused"),true);
        return InteractionResult.SUCCESS;
    }
}
