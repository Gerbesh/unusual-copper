package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.MachineEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;

public final class CultivatorLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.workArea() == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (level.getGameTime() >= machine.nextWaveTick) AreaWorkScheduler.request(level, machine);
    }
    public static void process(CopperMachineBlockEntity machine, ServerLevel level) {
        long now=level.getGameTime();
        // Failures retry at the normal interval; only a progressing wave resumes next tick.
        machine.nextWaveTick=now+ServerConfig.cultivatorInterval();
        if (!machine.energyEnabled()) { machine.status(MachineStatus.REDSTONE_PAUSED); return; }
        var area = machine.workArea(); if (area == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (!AreaAccess.geometryValid(level, machine.getBlockPos(), area)) { machine.status(MachineStatus.NO_AREA); return; }
        if (!ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) { machine.status(MachineStatus.PROTECTED); return; }
        machine.harvest.drain(machine,8);
        if(!machine.harvest.empty() || machine.boundCargoCount()>0) {machine.status(MachineStatus.OUTPUT_FULL);return;}
        if(machine.growthMode==2 && machine.boundInventory()==null){machine.status(MachineStatus.NO_STORAGE);return;}
        var wave=machine.cultivation;wave.prepare(area,machine.getBlockPos());
        if(!wave.active) {
            machine.inspected=machine.grown=machine.harvested=0;
            wave.active=true;wave.front=Math.max(0,wave.radius(machine.scanCursor)-.7F);
        }
        wave.beginBatch();float from=wave.front;
        MachineStatus reason=MachineStatus.NO_TARGETS;boolean progressed=false,blocked=false;
        int checks=Math.min(ServerConfig.cultivatorChecks(),area.volume());
        for (int work = 0; work < checks; work++) {
            var pos=wave.at(machine.scanCursor);machine.inspected++;
            if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.below()))reason=MachineStatus.UNLOADED;
            else if(!ProtectionPolicy.allowed(level,pos,machine.owner()))reason=MachineStatus.PROTECTED;
            else {
                var before=level.getBlockState(pos);var result=GrowthAdapters.inspect(level,pos,before);
                if(machine.growthMode==2 && CropHarvest.plan(before)!=null) {
                    reason=CropHarvest.harvest(machine,level,pos,before);
                    blocked=reason==MachineStatus.NO_ENERGY || reason==MachineStatus.OUTPUT_FULL
                            || reason==MachineStatus.NO_STORAGE || reason==MachineStatus.UNLOADED || reason==MachineStatus.NEED_SEEDS;
                } else if(result!=GrowthAdapters.Result.READY) {
                    if(result==GrowthAdapters.Result.CONDITIONS || result==GrowthAdapters.Result.MATURE)reason=MachineStatus.NO_GROWTH;
                    else if(before.getBlock() instanceof BonemealableBlock || before.is(Blocks.SUGAR_CANE) || before.is(Blocks.CACTUS))reason=MachineStatus.UNSUPPORTED;
                } else if(machine.energy.stored()<ServerConfig.cultivatorCost()) {
                    reason=MachineStatus.NO_ENERGY;blocked=true;
                } else {
                    boolean changed=false;
                    if(ForgeHooks.onCropsGrowPre(level,pos,before,true)) {
                        changed=GrowthAdapters.grow(level,pos,before);
                        if(changed) {
                            machine.energy.spend(ServerConfig.cultivatorCost());machine.grown++;wave.changed(pos,0);
                            ForgeHooks.onCropsGrowPost(level,pos,before);
                        }
                    }
                    if(!changed)reason=MachineStatus.NO_GROWTH;
                }
            }
            if(blocked)break;
            progressed=true;wave.front=Math.max(wave.front,wave.radius(machine.scanCursor)+.7F);
            if(++machine.scanCursor>=area.volume()) {machine.scanCursor=0;wave.active=false;break;}
        }
        var changes=wave.changes();
        if(progressed || !changes.isEmpty())MachineEffects.cultivator(level,machine,from,wave.front,changes);
        if(wave.active && !blocked)machine.nextWaveTick=now+1;
        // "To maturity" repeats complete field sweeps instead of repeatedly hitting one plant.
        if(!wave.active && machine.growthMode==1 && machine.grown>0)machine.nextWaveTick=now+1;
        machine.status(!changes.isEmpty()?MachineStatus.WORKING:reason);machine.setChanged();
    }
}
