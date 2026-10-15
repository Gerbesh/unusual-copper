package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.storage.HandlerStorageEndpoint;
import net.minecraft.server.level.ServerLevel;

public final class ReaperLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.workArea() == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (level.getGameTime() >= machine.nextWaveTick) AreaWorkScheduler.request(level, machine);
    }
    public static void process(CopperMachineBlockEntity machine, ServerLevel level) {
        if (!machine.energyEnabled()) { machine.status(MachineStatus.REDSTONE_PAUSED); return; }
        if (level.getGameTime() < machine.nextWaveTick) return;
        var area = machine.workArea(); var state = machine.reaper; machine.grown = 0;
        if (area == null || !AreaAccess.geometryValid(level, machine.getBlockPos(), area)) { machine.status(MachineStatus.NO_AREA); return; }
        if (!ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) { machine.status(MachineStatus.PROTECTED); return; }
        state.drain(machine);
        if (state.lootCount() > 0 || machine.boundCargoCount() > 0 || (machine.boundInventory() != null && (!machine.inventory.getStackInSlot(2).isEmpty() || !machine.inventory.getStackInSlot(3).isEmpty()))) { machine.status(MachineStatus.OUTPUT_FULL); return; }
        boolean room = false; for (int slot = 2; slot <= 3; slot++) { var stack = machine.inventory.getStackInSlot(slot); if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), machine.inventory.getSlotLimit(slot))) room = true; }
        if (!room) { machine.status(MachineStatus.OUTPUT_FULL); return; }
        if (machine.boundInventory() != null) {
            if (!level.hasChunkAt(machine.boundInventory())) { machine.status(MachineStatus.UNLOADED); return; }
            var space = machine.boundEndpoint(level).space();
            if (space != HandlerStorageEndpoint.Space.AVAILABLE) { machine.status(space == HandlerStorageEndpoint.Space.SCANNING ? MachineStatus.SCANNING : MachineStatus.OUTPUT_FULL); return; }
        }
        var population = MobPopulations.ledger(level, machine.runtimeIdentity, machine.getBlockPos(), area, state.rule);
        machine.inspected = population.step(ServerConfig.reaperCandidates() - 1);
        if (!population.ready()) { machine.status(MachineStatus.SCANNING); return; }
        state.population = population.count();
        if (!state.active && state.population > state.trigger) state.active = true;
        if (state.population <= state.keep) state.active = false;
        machine.setChanged();
        if (!state.active) { machine.status(MachineStatus.POPULATION_LIMIT); return; }
        var target = population.target();
        if (!population.ready() || target == null || population.count() <= state.keep || !area.contains(target.blockPosition()) || !state.rule.matches(target)) { machine.status(MachineStatus.SCANNING); return; }
        machine.inspected++;
        if (!level.hasChunkAt(target.blockPosition()) || !ProtectionPolicy.allowed(level, target.blockPosition(), machine.owner())) { machine.status(MachineStatus.PROTECTED); machine.nextWaveTick = level.getGameTime() + ServerConfig.reaperInterval(); return; }
        int price = ServerConfig.reaperCost();
        if (machine.energy.stored() < price) { machine.status(MachineStatus.NO_ENERGY); return; }
        machine.nextWaveTick = level.getGameTime() + ServerConfig.reaperInterval();
        boolean killed = ReaperLoot.kill(machine, target, level, price);
        state.population = population.count(); if (state.population <= state.keep) state.active = false;
        machine.grown = killed ? 1 : 0; machine.status(killed ? MachineStatus.WORKING : MachineStatus.KILL_CANCELLED); machine.setChanged();
    }
}
