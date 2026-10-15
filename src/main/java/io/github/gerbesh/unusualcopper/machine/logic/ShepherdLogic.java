package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import java.util.ArrayList;

public final class ShepherdLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.workArea() == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (level.getGameTime() >= machine.nextWaveTick) AreaWorkScheduler.request(level, machine);
    }
    public static void process(CopperMachineBlockEntity machine, ServerLevel level) {
        if (!machine.energyEnabled()) { machine.status(MachineStatus.REDSTONE_PAUSED); return; }
        if (level.getGameTime() < machine.nextWaveTick) return;
        var area = machine.workArea();
        if (area == null || !AreaAccess.geometryValid(level, machine.getBlockPos(), area)) { machine.status(MachineStatus.NO_AREA); return; }
        machine.nextWaveTick = level.getGameTime() + ServerConfig.shepherdInterval(); machine.inspected = machine.grown = 0; machine.setChanged();
        if (!ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) { machine.status(MachineStatus.PROTECTED); return; }
        var affected = new ArrayList<Integer>(); MachineStatus reason = MachineStatus.NO_BABIES;
        for (var entity : EntityIndexes.mobs(level, machine, ServerConfig.shepherdCandidates())) {
            machine.inspected++;
            if (!(entity instanceof AgeableMob animal) || !animal.isAlive() || level.getEntity(animal.getUUID()) != animal || !area.contains(animal.blockPosition())) continue;
            if (animal instanceof net.minecraft.world.entity.npc.AbstractVillager || animal.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) continue;
            if (!level.hasChunkAt(animal.blockPosition())) { reason = MachineStatus.UNLOADED; continue; }
            if (!ProtectionPolicy.allowed(level, animal.blockPosition(), machine.owner()) || !AnimalGrowthAdapters.mayAffect(animal, machine.owner(), machine.allowOwnTamed)) { reason = MachineStatus.PROTECTED; continue; }
            var adapter = AnimalGrowthAdapters.adapter(animal); if (adapter == null) { reason = MachineStatus.UNSUPPORTED_ANIMAL; continue; }
            int before = adapter.age(animal); if (before >= 0) continue;
            int cost = ServerConfig.shepherdCost(); if (!machine.energy.spend(cost)) { reason = MachineStatus.NO_ENERGY; continue; }
            int next = (int)Math.min(0L, (long)before + ServerConfig.shepherdAdvance());
            adapter.setAge(animal, next);
            if (adapter.age(animal) > before) { machine.grown++; affected.add(animal.getId()); }
            else { machine.energy.refundSpent(cost); reason = MachineStatus.NO_GROWTH; }
        }
        machine.status(machine.grown > 0 ? MachineStatus.WORKING : reason);
        if (!affected.isEmpty()) io.github.gerbesh.unusualcopper.network.MachineEffects.shepherd(level, machine, affected);
        machine.setChanged();
    }
}
