package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;

public final class CultivatorLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.workArea() == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (level.getGameTime() % ServerConfig.cultivatorInterval() == 0) AreaWorkScheduler.request(level, machine);
    }
    public static void process(CopperMachineBlockEntity machine, ServerLevel level) {
        if (!machine.energyEnabled()) { machine.status(MachineStatus.REDSTONE_PAUSED); return; }
        var area = machine.workArea(); if (area == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (!AreaAccess.geometryValid(level, machine.getBlockPos(), area)) { machine.status(MachineStatus.NO_AREA); return; }
        machine.inspected = machine.grown = 0; MachineStatus reason = MachineStatus.NO_TARGETS;
        if (!ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) { machine.status(MachineStatus.PROTECTED); return; }
        for (int work = 0; work < ServerConfig.cultivatorChecks(); work++) {
            var pos = area.at(machine.scanCursor); machine.inspected++;
            if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) { reason = MachineStatus.UNLOADED; machine.scanCursor = (machine.scanCursor + 1) % area.volume(); continue; }
            if (!ProtectionPolicy.allowed(level, pos, machine.owner())) { reason = MachineStatus.PROTECTED; machine.scanCursor = (machine.scanCursor + 1) % area.volume(); continue; }
            var before = level.getBlockState(pos); var result = GrowthAdapters.inspect(level, pos, before);
            if (result != GrowthAdapters.Result.READY) {
                if (result == GrowthAdapters.Result.CONDITIONS || result == GrowthAdapters.Result.MATURE) reason = MachineStatus.NO_GROWTH;
                else if (before.getBlock() instanceof BonemealableBlock || before.is(Blocks.SUGAR_CANE) || before.is(Blocks.CACTUS)) reason = MachineStatus.UNSUPPORTED;
                machine.scanCursor = (machine.scanCursor + 1) % area.volume(); continue;
            }
            int cost = ServerConfig.cultivatorCost();
            if (machine.energy.stored() < cost) { reason = MachineStatus.NO_ENERGY; break; }
            boolean changed = false;
            if (ForgeHooks.onCropsGrowPre(level, pos, before, true)) {
                changed = GrowthAdapters.grow(level, pos, before);
                if (changed) { machine.energy.spend(cost); machine.grown++; ForgeHooks.onCropsGrowPost(level, pos, before); }
            }
            if (machine.growthMode == 0 || !changed || GrowthAdapters.inspect(level, pos, level.getBlockState(pos)) != GrowthAdapters.Result.READY) machine.scanCursor = (machine.scanCursor + 1) % area.volume();
            if (!changed) reason = MachineStatus.NO_GROWTH;
        }
        machine.status(machine.grown > 0 ? MachineStatus.WORKING : reason); machine.setChanged();
    }
}
