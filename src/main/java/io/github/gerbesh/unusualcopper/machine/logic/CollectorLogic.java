package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/** Collects existing entities into owned output slots, one whole stack per wave. */
public final class CollectorLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.workArea() == null) { machine.status(MachineStatus.NO_AREA); return; }
        if (level.getGameTime() % 20 == 0) AreaWorkScheduler.request(level, machine);
    }
    public static int cost(double distance) {
        return ServerConfig.collectorBaseCost() + (int)Math.ceil(ServerConfig.collectorDistanceCost() * Math.min(1, Math.max(0, distance) / 32));
    }
    public static void process(CopperMachineBlockEntity machine, ServerLevel level) {
        machine.inspected = machine.grown = 0;
        if (!machine.energyEnabled()) { machine.status(MachineStatus.REDSTONE_PAUSED); return; }
        var area = machine.workArea();
        if (area == null || !AreaAccess.geometryValid(level, machine.getBlockPos(), area)) { machine.status(MachineStatus.NO_AREA); return; }
        if (!ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) { machine.status(MachineStatus.PROTECTED); return; }
        var candidates = EntityIndexes.items(level, machine, ServerConfig.collectorCandidates());
        MachineStatus reason = MachineStatus.NO_TARGETS;
        for (var entity : candidates) {
            machine.inspected++;
            if (entity == null) continue;
            var pos = entity.blockPosition();
            if (!entity.isAlive() || level.getEntity(entity.getUUID()) != entity || entity.getClass() != ItemEntity.class || entity.getItem().isEmpty() || entity.hasPickUpDelay() || !area.contains(pos)) continue;
            if (!level.hasChunkAt(pos)) { reason = MachineStatus.UNLOADED; continue; }
            if (!ProtectionPolicy.allowed(level, pos, machine.owner())) { reason = MachineStatus.PROTECTED; continue; }
            var data = new CompoundTag(); entity.addAdditionalSaveData(data);
            if ((data.hasUUID("Owner") && !data.getUUID("Owner").equals(machine.owner())) || (data.hasUUID("Thrower") && !data.getUUID("Thrower").equals(machine.owner()))) { reason = MachineStatus.PROTECTED; continue; }
            if (!machine.canFitOutput(entity.getItem())) { reason = MachineStatus.OUTPUT_FULL; continue; }
            int price = cost(Math.sqrt(entity.distanceToSqr(machine.getBlockPos().getX() + .5, machine.getBlockPos().getY() + .5, machine.getBlockPos().getZ() + .5)));
            if (machine.energy.stored() < price) { reason = MachineStatus.NO_ENERGY; continue; }
            if (machine.collectOutput(entity, price)) { machine.grown = 1; reason = MachineStatus.WORKING; break; }
        }
        machine.status(reason); machine.setChanged();
    }
}
