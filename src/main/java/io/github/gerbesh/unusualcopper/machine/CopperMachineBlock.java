package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class CopperMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public final MachineKind kind;
    public CopperMachineBlock(MachineKind kind) {
        super(BlockBehaviour.Properties.of().strength(3.5F, 6F).sound(SoundType.COPPER).requiresCorrectToolForDrops()
                .noOcclusion().lightLevel(state -> state.getValue(ACTIVE) ? 7 : 0));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CopperMachineBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModContent.MACHINE_ENTITY.get(), (world, pos, blockState, be) -> CopperMachineBlockEntity.tick((ServerLevel)world, pos, blockState, be));
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(ModContent.FACE_SELECTOR.get()) || player.getItemInHand(hand).is(ModContent.AREA_WAND.get())) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine && machine.canAccess(player)) NetworkHooks.openScreen((ServerPlayer)player, machine, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine) machine.setOwner(placer.getUUID());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine) {
            if (machine.boundCargoCount() > 0) popResource(level, pos, io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(machine.boundCargo()));
            if (machine.reaper != null && machine.reaper.lootCount() > 0) popResource(level, pos, io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(machine.reaper.loot()));
            ItemStack[] contents = new ItemStack[4];
            for (int i = 0; i < 4; i++) contents[i] = machine.inventory.getStackInSlot(i).copy();
            Containers.dropContents(level, pos, new SimpleContainer(contents));
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
