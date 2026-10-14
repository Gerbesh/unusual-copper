package io.github.gerbesh.unusualcopper.energy;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EnergyPipeBlockEntity extends BlockEntity {
    public EnergyPipeBlockEntity(BlockPos pos, BlockState state) { super(ModContent.PIPE_ENTITY.get(), pos, state); }
    @Override public void onLoad() { super.onLoad(); EnergyNetworks.add(level, worldPosition); }
    @Override public void onChunkUnloaded() { EnergyNetworks.remove(level, worldPosition); super.onChunkUnloaded(); }
    @Override public void setRemoved() { EnergyNetworks.remove(level, worldPosition); super.setRemoved(); }
}
