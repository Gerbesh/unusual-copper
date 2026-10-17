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
        if(kind==MachineKind.COPPER_SENTINEL && player.getItemInHand(hand).is(ModContent.SENTINEL_FOCUS.get())) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity m) m.installSentinelFocus(player,hand);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(kind==MachineKind.COPPER_FLUID_TANK && !level.isClientSide && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity tank
                && tank.canAccess(player)) {
            var held=player.getItemInHand(hand);
            net.minecraft.world.level.material.Fluid fluid=held.is(net.minecraft.world.item.Items.WATER_BUCKET)?net.minecraft.world.level.material.Fluids.WATER
                    :held.is(net.minecraft.world.item.Items.LAVA_BUCKET)?net.minecraft.world.level.material.Fluids.LAVA:net.minecraft.world.level.material.Fluids.EMPTY;
            if(fluid!=net.minecraft.world.level.material.Fluids.EMPTY) {
                int filled=tank.water.fill(new net.minecraftforge.fluids.FluidStack(fluid,1000),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if(filled==1000 && !player.getAbilities().instabuild) {
                    held.shrink(1);
                    ItemStack bucket=new ItemStack(net.minecraft.world.item.Items.BUCKET);
                    if(!player.getInventory().add(bucket))player.drop(bucket,false);
                }
                if(filled==1000)return InteractionResult.SUCCESS;
            } else if(held.is(net.minecraft.world.item.Items.BUCKET)){
                var drained=tank.water.drain(1000,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                net.minecraft.world.item.Item bucketItem=drained.getFluid()==net.minecraft.world.level.material.Fluids.WATER?net.minecraft.world.item.Items.WATER_BUCKET
                        :drained.getFluid()==net.minecraft.world.level.material.Fluids.LAVA?net.minecraft.world.item.Items.LAVA_BUCKET:null;
                if(bucketItem!=null && drained.getAmount()==1000) {
                    tank.water.drain(1000,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if(!player.getAbilities().instabuild)held.shrink(1);
                    ItemStack full=new ItemStack(bucketItem);
                    if(!player.getInventory().add(full))player.drop(full,false);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        if (player.getItemInHand(hand).is(ModContent.FACE_SELECTOR.get()) || player.getItemInHand(hand).is(ModContent.AREA_WAND.get())) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine && machine.canAccess(player)) NetworkHooks.openScreen((ServerPlayer)player, machine, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine) machine.setOwner(placer.getUUID());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine) {
            if(machine.brewer!=null){var held=new java.util.ArrayList<>(machine.brewer.recovery.items());var job=machine.brewer.job;if(job!=null&&!job.current.isEmpty())for(int i=0;i<job.batch;i++)held.add(job.current.copyWithCount(1));if(!held.isEmpty())popResource(level,pos,io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(held));}
            if(machine.mining!=null&&level instanceof ServerLevel miningLevel&&machine.mining.experience>0){popExperience(miningLevel,pos,machine.mining.experience);machine.mining.experience=0;}
            if(machine.mining!=null&&!machine.mining.recovery.empty())popResource(level,pos,io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(machine.mining.recovery.items()));
            if(machine.sentinel!=null && machine.sentinel.focused) popResource(level,pos,new ItemStack(ModContent.SENTINEL_FOCUS.get()));
            if (machine.boundCargoCount() > 0) popResource(level, pos, io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(machine.boundCargo()));
            if (machine.reaper != null && machine.reaper.lootCount() > 0) popResource(level, pos, io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(machine.reaper.loot()));
            if(!machine.harvest.empty())popResource(level,pos,io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(machine.harvest.items()));
            if(machine.arborist!=null) {
                var held=new java.util.ArrayList<>(machine.arborist.recovery.items()); var job=machine.arborist.job;
                if(job!=null) { if(!job.tool.isEmpty()) held.add(job.tool); if(!job.saplings.isEmpty()) held.add(job.saplings); }
                if(!held.isEmpty()) popResource(level,pos,io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(held));
            }
            if(machine.kind==MachineKind.PRECISION_ASSEMBLER) {
                ItemStack[] grid=new ItemStack[9];
                for(int i=0;i<9;i++)grid[i]=machine.assemblyGrid.getStackInSlot(i).copy();
                Containers.dropContents(level,pos,new SimpleContainer(grid));
            }
            if(machine.hasIndustrialExtra()) {
                ItemStack extra=machine.industrialExtra.getStackInSlot(0).copy();
                if(!extra.isEmpty())Containers.dropContents(level,pos,new SimpleContainer(extra));
            }
            ItemStack[] contents = new ItemStack[4];
            for (int i = 0; i < 4; i++) contents[i] = machine.inventory.getStackInSlot(i).copy();
            Containers.dropContents(level, pos, new SimpleContainer(contents));
            if(!machine.battery.getStackInSlot(0).isEmpty()) {
                var portable=machine.battery.getStackInSlot(0).copy();machine.battery.setStackInSlot(0,ItemStack.EMPTY);
                Containers.dropContents(level,pos,new SimpleContainer(portable));
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
