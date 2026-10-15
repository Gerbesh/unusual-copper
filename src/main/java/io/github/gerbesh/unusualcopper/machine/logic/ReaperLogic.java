package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.storage.StorageEndpoint;
import net.minecraft.server.level.ServerLevel;

public final class ReaperLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.workArea() == null) { cancel(machine,level); machine.status(MachineStatus.NO_AREA); return; }
        long now=level.getGameTime(); var windup=machine.reaper.windup;
        if (windup != null && !windup.plan().equals(ReaperState.Plan.of(machine))) cancel(machine,level);
        windup=machine.reaper.windup;
        if (windup != null ? now >= windup.strikeAt() : now >= Math.max(0,machine.nextWaveTick-ReaperState.WINDUP_TICKS)) AreaWorkScheduler.request(level, machine);
    }
    public static void cancel(CopperMachineBlockEntity machine,ServerLevel level) {
        var windup=machine.reaper.windup; machine.reaper.windup=null;
        if (windup != null) io.github.gerbesh.unusualcopper.network.MachineEffects.reaperCancel(level,machine,windup.action());
    }
    public static void process(CopperMachineBlockEntity machine, ServerLevel level) {
        if (!machine.energyEnabled()) { cancel(machine,level); machine.status(MachineStatus.REDSTONE_PAUSED); return; }
        long now=level.getGameTime(); var windup=machine.reaper.windup;
        if (windup != null && !windup.plan().equals(ReaperState.Plan.of(machine))) { cancel(machine,level); machine.status(MachineStatus.SCANNING); return; }
        if (windup != null ? now < windup.strikeAt() || now < machine.nextWaveTick : now < Math.max(0,machine.nextWaveTick-ReaperState.WINDUP_TICKS)) return;
        var area = machine.workArea(); var state = machine.reaper; machine.grown = 0;
        if (area == null || !AreaAccess.geometryValid(level, machine.getBlockPos(), area)) { cancel(machine,level); machine.status(MachineStatus.NO_AREA); return; }
        if (!ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) { cancel(machine,level); machine.status(MachineStatus.PROTECTED); return; }
        state.drain(machine);
        if (state.lootCount() > 0 || machine.boundCargoCount() > 0 || (machine.boundInventory() != null && (!machine.inventory.getStackInSlot(2).isEmpty() || !machine.inventory.getStackInSlot(3).isEmpty()))) { cancel(machine,level); machine.status(MachineStatus.OUTPUT_FULL); return; }
        boolean room = false; for (int slot = 2; slot <= 3; slot++) { var stack = machine.inventory.getStackInSlot(slot); if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), machine.inventory.getSlotLimit(slot))) room = true; }
        if (!room) { cancel(machine,level); machine.status(MachineStatus.OUTPUT_FULL); return; }
        if (machine.boundInventory() != null) {
            if (!level.hasChunkAt(machine.boundInventory())) { cancel(machine,level); machine.status(MachineStatus.UNLOADED); return; }
            var space = machine.boundEndpoint(level).space();
            if (space != StorageEndpoint.Space.AVAILABLE) { if(space!=StorageEndpoint.Space.SCANNING) cancel(machine,level); machine.status(space == StorageEndpoint.Space.SCANNING ? MachineStatus.SCANNING : MachineStatus.OUTPUT_FULL); return; }
        }
        var population = MobPopulations.ledger(level, machine.runtimeIdentity, machine.getBlockPos(), area, state.rule);
        machine.inspected = population.step(ServerConfig.reaperCandidates() - 1);
        if (!population.ready()) { machine.status(MachineStatus.SCANNING); return; }
        state.population = population.count();
        if (!state.active && state.population > state.trigger) state.active = true;
        if (state.population <= state.keep) state.active = false;
        machine.setChanged();
        if (!state.active) { cancel(machine,level); machine.status(MachineStatus.POPULATION_LIMIT); return; }
        var target = windup == null ? population.target() : level.getEntity(windup.target()) instanceof net.minecraft.world.entity.Mob mob ? mob : null;
        if (!population.ready() || target == null || population.count() <= state.keep || !area.contains(target.blockPosition()) || !state.rule.matches(target)) { cancel(machine,level); machine.status(MachineStatus.SCANNING); return; }
        machine.inspected++;
        if (!level.hasChunkAt(target.blockPosition()) || !ProtectionPolicy.allowed(level, target.blockPosition(), machine.owner())) { cancel(machine,level); machine.status(MachineStatus.PROTECTED); machine.nextWaveTick = now + ServerConfig.reaperInterval(); return; }
        int price = ServerConfig.reaperCost();
        if (machine.energy.stored() < price) { cancel(machine,level); machine.status(MachineStatus.NO_ENERGY); return; }
        if (windup == null) {
            var action=java.util.UUID.randomUUID();
            state.windup=new ReaperState.Windup(action,target.getUUID(),Math.max(now+ReaperState.WINDUP_TICKS,machine.nextWaveTick),ReaperState.Plan.of(machine));
            io.github.gerbesh.unusualcopper.network.MachineEffects.reaperWindup(level,machine,target,action);
            machine.status(MachineStatus.CHARGING); return;
        }
        state.windup=null; machine.nextWaveTick = now + ServerConfig.reaperInterval();
        boolean killed = ReaperLoot.kill(machine, target, level, price,windup.action());
        state.population = population.count(); if (state.population <= state.keep) state.active = false;
        machine.grown = killed ? 1 : 0; machine.status(killed ? MachineStatus.WORKING : MachineStatus.KILL_CANCELLED); machine.setChanged();
    }
}
