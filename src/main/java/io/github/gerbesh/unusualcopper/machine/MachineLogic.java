package io.github.gerbesh.unusualcopper.machine;

import net.minecraft.server.level.ServerLevel;

public interface MachineLogic {
    void tick(CopperMachineBlockEntity machine, ServerLevel level);
}
