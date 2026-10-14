package io.github.gerbesh.unusualcopper.energy;

import io.github.gerbesh.unusualcopper.block.AbstractPipeBlock;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EnergyPipeBlock extends AbstractPipeBlock {
    protected boolean connects(LevelAccessor level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (!level.hasChunkAt(other)) return false;
        if (level.getBlockState(other).getBlock() instanceof EnergyPipeBlock) return true;
        if (level.getBlockEntity(other) instanceof CopperMachineBlockEntity machine) {
            SideMode mode = machine.sideMode(side.getOpposite());
            return mode == SideMode.ENERGY_INPUT || mode == SideMode.ENERGY_OUTPUT;
        }
        return false;
    }
    @Override protected void markDirty(LevelAccessor level) { EnergyNetworks.dirty(level); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EnergyPipeBlockEntity(pos, state); }
}
