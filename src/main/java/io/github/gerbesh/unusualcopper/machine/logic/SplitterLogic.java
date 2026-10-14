package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import io.github.gerbesh.unusualcopper.config.ServerConfig;

public final class SplitterLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m, ServerLevel level) {
        var input = m.inventory.getStackInSlot(0);
        if (input.isEmpty()) { m.status(MachineStatus.IDLE); return; }
        if (!input.is(ModContent.ESSENCE.get())) { m.status(MachineStatus.INVALID_INPUT); return; }
        int cost = ServerConfig.essenceEnergy();
        int count = Math.min(ServerConfig.splitterRate(), Math.min(input.getCount(), (m.energy.capacity() - m.energy.stored()) / cost));
        if (count == 0) { m.status(MachineStatus.BUFFER_FULL); return; }
        m.energy.add(count * cost, false);
        input.shrink(count);
        m.status(MachineStatus.WORKING);
        m.setChanged();
    }
}
