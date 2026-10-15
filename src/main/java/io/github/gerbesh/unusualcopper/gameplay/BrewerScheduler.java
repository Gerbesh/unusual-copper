package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.BrewerLogic;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.*;

public final class BrewerScheduler {
    private record Request(UUID runtime,long queued,int priority) {}
    private static final class State {final Map<StorageIdentity,Map<BlockPos,WeakReference<CopperMachineBlockEntity>>> groups=new HashMap<>();final Map<BlockPos,StorageIdentity> assigned=new HashMap<>();final LinkedHashMap<BlockPos,Request> queue=new LinkedHashMap<>();}
    private static final Map<ServerLevel,State> LEVELS=new IdentityHashMap<>();
    private static State state(ServerLevel level){return LEVELS.computeIfAbsent(level,k->new State());}
    private static StorageIdentity canonical(StorageIdentity id){return new StorageIdentity(id.dimension(),id.pos(),net.minecraft.core.Direction.UP);}
    public static void register(ServerLevel level,CopperMachineBlockEntity m){
        var s=state(level);var pos=m.getBlockPos().immutable();var old=s.assigned.get(pos);StorageIdentity current=null;
        if(m.boundInventory()!=null&&pos.distSqr(m.boundInventory())<=32*32){var endpoint=m.boundEndpoint(level);if(endpoint!=null)current=canonical(endpoint.identity());}
        if(!Objects.equals(old,current)&&old!=null){var group=s.groups.get(old);if(group!=null){group.remove(pos);if(group.isEmpty())s.groups.remove(old);}s.assigned.remove(pos);}
        if(current!=null){s.assigned.put(pos,current);var group=s.groups.computeIfAbsent(current,k->new HashMap<>());var ref=group.get(pos);if(ref==null||ref.get()!=m)group.put(pos,new WeakReference<>(m));}
    }
    public static void forget(ServerLevel level,CopperMachineBlockEntity m){var s=LEVELS.get(level);if(s==null)return;var id=s.assigned.get(m.getBlockPos());var group=id==null?null:s.groups.get(id);var ref=group==null?null:group.get(m.getBlockPos());if(ref!=null&&ref.get()==m){group.remove(m.getBlockPos());s.assigned.remove(m.getBlockPos());if(group.isEmpty())s.groups.remove(id);}var queued=s.queue.get(m.getBlockPos());if(queued!=null&&queued.runtime.equals(m.runtimeIdentity))s.queue.remove(m.getBlockPos());}
    public static void request(ServerLevel level,CopperMachineBlockEntity m){register(level,m);state(level).queue.putIfAbsent(m.getBlockPos().immutable(),new Request(m.runtimeIdentity,level.getGameTime(),m.brewer.priority));}
    /** Held products and admitted jobs count before they enter a shared chest. */
    public static long reserved(ServerLevel level,CopperMachineBlockEntity machine,StorageIdentity identity,ItemKey key){
        register(level,machine);var group=state(level).groups.get(canonical(identity));if(group==null)return 0;
        long count=0;int visited=0;var iterator=group.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();var m=entry.getValue().get();if(m==null||m.isRemoved()||!level.hasChunkAt(entry.getKey())||level.getBlockEntity(entry.getKey())!=m){iterator.remove();continue;}
            if(++visited>256)return Long.MAX_VALUE; // Unknown total is never permission to overproduce.
            if(m.boundInventory()==null||m.getBlockPos().distSqr(m.boundInventory())>32*32)continue;var endpoint=m.boundEndpoint(level);if(endpoint==null||!endpoint.identity().sameInventory(identity))continue;
            if(m.brewer.job!=null&&key.matches(m.brewer.job.plan.output()))count+=m.brewer.job.batch;
            for(int i=2;i<=3;i++)if(key.matches(m.inventory.getStackInSlot(i)))count+=m.inventory.getStackInSlot(i).getCount();
            for(var stack:m.brewer.recovery.items())if(key.matches(stack))count+=stack.getCount();
            if(m.boundCargoSlot<0)for(var stack:m.boundCargo())if(key.matches(stack))count+=stack.getCount();
        }return count;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel()instanceof ServerLevel level)LEVELS.remove(level);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;var s=LEVELS.get(level);if(s==null||s.queue.isEmpty())return;
        var batch=s.queue.entrySet().stream().sorted(Comparator.<Map.Entry<BlockPos,Request>>comparingLong(e->-(20L*e.getValue().priority+level.getGameTime()-e.getValue().queued))).limit(ServerConfig.brewerBudget()).toList();
        for(var entry:batch){s.queue.remove(entry.getKey());if(!level.hasChunkAt(entry.getKey())||!(level.getBlockEntity(entry.getKey())instanceof CopperMachineBlockEntity m)||m.isRemoved()||m.brewer==null||!m.runtimeIdentity.equals(entry.getValue().runtime))continue;BrewerLogic.process(m,level);}
    }
    private BrewerScheduler(){}
}
