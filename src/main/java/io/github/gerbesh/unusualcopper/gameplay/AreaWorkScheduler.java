package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.machine.logic.CultivatorLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

public final class AreaWorkScheduler {
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, UUID>> QUEUES = new IdentityHashMap<>();
    private AreaWorkScheduler() {}
    public static void request(ServerLevel level, CopperMachineBlockEntity machine) { QUEUES.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(machine.getBlockPos().immutable(), machine.runtimeIdentity); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) QUEUES.remove(level); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var queue = QUEUES.get(level); if (queue == null) return;
        int budget = ServerConfig.areaBudget();
        while (!queue.isEmpty() && budget >= ServerConfig.cultivatorChecks()) {
            // Growth hooks may enqueue another machine. Retire the iterator before invoking them.
            var iterator = queue.entrySet().iterator();
            var entry = iterator.next(); iterator.remove(); budget -= ServerConfig.cultivatorChecks();
            if (!level.hasChunkAt(entry.getKey())) continue;
            if (level.getBlockEntity(entry.getKey()) instanceof CopperMachineBlockEntity machine && !machine.isRemoved() && machine.kind == MachineKind.AURA_CULTIVATOR && machine.runtimeIdentity.equals(entry.getValue())) CultivatorLogic.process(machine, level);
        }
    }
}
