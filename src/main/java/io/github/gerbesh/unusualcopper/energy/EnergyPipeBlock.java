package io.github.gerbesh.unusualcopper.energy;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EnergyPipeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public EnergyPipeBlock() {
        super(Properties.of().strength(2F, 4F).sound(SoundType.COPPER).noOcclusion());
        BlockState state = stateDefinition.any().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
        for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(BooleanProperty[]::new));
        builder.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }
    private boolean connects(LevelAccessor level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (!level.hasChunkAt(other)) return false;
        if (level.getBlockState(other).getBlock() instanceof EnergyPipeBlock) return true;
        if (level.getBlockEntity(other) instanceof CopperMachineBlockEntity machine) {
            SideMode mode = machine.sideMode(side.getOpposite());
            return mode == SideMode.ENERGY_INPUT || mode == SideMode.ENERGY_OUTPUT;
        }
        return false;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == net.minecraft.world.level.material.Fluids.WATER);
        for (Direction side : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connects(context.getLevel(), context.getClickedPos(), side));
        return state;
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            BlockState connected = state;
            for (Direction side : Direction.values()) connected = connected.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connects(level, pos, side));
            if (connected != state) level.setBlock(pos, connected, 2);
            EnergyNetworks.dirty(level);
        }
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
            level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        EnergyNetworks.dirty(level); return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connects(level, pos, side));
    }
    @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos other, boolean moving) { EnergyNetworks.dirty(level); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EnergyPipeBlockEntity(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = box(5, 5, 5, 11, 11, 11);
        for (Direction side : Direction.values()) if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) shape = Shapes.or(shape, switch (side) {
            case NORTH -> box(6, 6, 0, 10, 10, 5); case SOUTH -> box(6, 6, 11, 10, 10, 16);
            case EAST -> box(11, 6, 6, 16, 10, 10); case WEST -> box(0, 6, 6, 5, 10, 10);
            case UP -> box(6, 11, 6, 10, 16, 10); case DOWN -> box(6, 0, 6, 10, 5, 10);
        });
        return shape;
    }
}
