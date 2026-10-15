package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.SentinelLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded candidate waves and expiring target ownership; no entity or chunk loading. */
public final class SentinelScheduler {
    private record Lock(BlockPos origin,UUID runtime,long until) {}
    private static final Map<ServerLevel,State> LEVELS=new IdentityHashMap<>();
    private static final class State {
        final Map<Long,TreeMap<BlockPos,WeakReference<CopperMachineBlockEntity>>> chunks=new HashMap<>();
        final LinkedHashMap<BlockPos,UUID> queue=new LinkedHashMap<>();
        final Map<UUID,Lock> locks=new HashMap<>();
    }
    private static State state(ServerLevel level) { return LEVELS.computeIfAbsent(level,ignored->new State()); }
    public static void register(ServerLevel level,CopperMachineBlockEntity m) {
        state(level).chunks.computeIfAbsent(ChunkPos.asLong(m.getBlockPos()),ignored->new TreeMap<>()).put(m.getBlockPos().immutable(),new WeakReference<>(m));
    }
    public static boolean permitted(ServerLevel level,CopperMachineBlockEntity m) {
        var group=state(level).chunks.get(ChunkPos.asLong(m.getBlockPos())); if(group==null) { register(level,m); group=state(level).chunks.get(ChunkPos.asLong(m.getBlockPos())); }
        int accepted=0; var iterator=group.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next(); var other=entry.getValue().get();
            if(other==null||other.isRemoved()||!level.hasChunkAt(entry.getKey())||level.getBlockEntity(entry.getKey())!=other) { iterator.remove(); continue; }
            if(++accepted>ServerConfig.sentinelChunkLimit()) return false;
            if(other==m) return true;
        }
        return false;
    }
    public static boolean available(ServerLevel level,CopperMachineBlockEntity m,UUID target) {
        var lock=state(level).locks.get(target); return lock==null||lock.until<=level.getGameTime()||lock.runtime.equals(m.runtimeIdentity);
    }
    public static void lock(ServerLevel level,CopperMachineBlockEntity m,UUID target) {
        var locks=state(level).locks;
        locks.put(target,new Lock(m.getBlockPos().immutable(),m.runtimeIdentity,level.getGameTime()+ServerConfig.sentinelInterval()));
    }
    public static void request(ServerLevel level,CopperMachineBlockEntity m) { state(level).queue.put(m.getBlockPos().immutable(),m.runtimeIdentity); }
    public static void forget(ServerLevel level,CopperMachineBlockEntity m) {
        var s=LEVELS.get(level); if(s==null) return;
        s.queue.remove(m.getBlockPos()); var group=s.chunks.get(ChunkPos.asLong(m.getBlockPos()));
        if(group!=null) { var ref=group.get(m.getBlockPos()); if(ref!=null&&ref.get()==m) group.remove(m.getBlockPos()); if(group.isEmpty()) s.chunks.remove(ChunkPos.asLong(m.getBlockPos())); }
        s.locks.entrySet().removeIf(e->e.getValue().runtime.equals(m.runtimeIdentity));
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) { if(e.getLevel() instanceof ServerLevel level) LEVELS.remove(level); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        var s=LEVELS.get(level); if(s==null) return;
        s.locks.entrySet().removeIf(entry->entry.getValue().until<=level.getGameTime());
        int budget=ServerConfig.sentinelBudget(),attempts=Math.min(128,s.queue.size());
        while(!s.queue.isEmpty() && attempts-->0 && budget>=ServerConfig.sentinelCandidates()) {
            var iterator=s.queue.entrySet().iterator(); var entry=iterator.next(); iterator.remove();
            if(!level.hasChunkAt(entry.getKey())||!(level.getBlockEntity(entry.getKey()) instanceof CopperMachineBlockEntity m)||m.isRemoved()||!m.runtimeIdentity.equals(entry.getValue())||m.sentinel==null) continue;
            budget-=ServerConfig.sentinelCandidates(); SentinelLogic.process(m,level);
        }
    }
    private SentinelScheduler() {}
}
