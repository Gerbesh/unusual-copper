package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.MiningLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** One server-wide cooperative budget, no chunk tickets and no worker access to Minecraft. */
public final class MiningScheduler {
    private record Key(ServerLevel level,BlockPos pos){}
    private static final LinkedHashMap<Key,UUID> queue=new LinkedHashMap<>();
    public static void request(ServerLevel level,CopperMachineBlockEntity m){queue.put(new Key(level,m.getBlockPos().immutable()),m.runtimeIdentity);}
    @SubscribeEvent public static void stop(ServerStoppingEvent e){queue.clear();}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){queue.keySet().removeIf(k->k.level==e.getLevel());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;long deadline=System.nanoTime()+1_000_000L;int nodes=512,attempts=Math.min(64,queue.size());
        while(attempts-->0&&nodes>0&&!queue.isEmpty()&&System.nanoTime()<deadline){var it=queue.entrySet().iterator();var entry=it.next();it.remove();var key=entry.getKey();if(!key.level.hasChunkAt(key.pos))continue;
            if(!(key.level.getBlockEntity(key.pos) instanceof CopperMachineBlockEntity m)||m.mining==null||m.isRemoved()||!m.runtimeIdentity.equals(entry.getValue()))continue;
            int allowance=Math.min(64,nodes);nodes-=allowance;
            try{MiningLogic.process(m,key.level,allowance,deadline);}catch(RuntimeException ex){m.mining.faulted=true;m.mining.jobs.clear();m.status(MachineStatus.MINING_FAULT);m.setChanged();io.github.gerbesh.unusualcopper.UnusualCopper.LOGGER.error("Mining station paused at {}",m.getBlockPos(),ex);}
        }
    }
    private MiningScheduler(){}
}
