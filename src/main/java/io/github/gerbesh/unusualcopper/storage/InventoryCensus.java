package io.github.gerbesh.unusualcopper.storage;

import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** One bounded page per tick, shared by all queried keys on this endpoint. */
final class InventoryCensus {
    private static final int KEY_LIMIT=8192, SLOT_LIMIT=1_000_000;
    private final Map<ItemKey,Long> counts=new HashMap<>();
    private final Map<net.minecraft.world.item.Item,Set<ItemKey>> variants=new HashMap<>();
    private InventoryChanges.Token first,second;
    private long firstRevision,secondRevision,lastPage=Long.MIN_VALUE,completedAt=Long.MIN_VALUE;
    private int cursor,slots=-1;
    private boolean complete,unsupported;
    void reset(){counts.clear();variants.clear();cursor=0;slots=-1;complete=unsupported=false;completedAt=Long.MIN_VALUE;}
    private boolean current(InventoryChanges.Token a,InventoryChanges.Token b,int size){return first==a&&second==b&&a.valid()&&b.valid()&&firstRevision==a.revision()&&secondRevision==b.revision()&&slots==size;}
    StorageEndpoint.Count count(IItemHandler handler,ItemKey key,InventoryChanges.Token a,InventoryChanges.Token b,long tick){
        int size=handler.getSlots();if(size<0||size>SLOT_LIMIT)return new StorageEndpoint.Count(0,false);
        if(!current(a,b,size)||(complete||unsupported)&&tick-completedAt>=20){reset();first=a;second=b;firstRevision=a.revision();secondRevision=b.revision();slots=size;}
        if(!complete&&!unsupported&&lastPage!=tick){
            lastPage=tick;int end=(int)Math.min((long)slots,(long)cursor+HandlerStorageEndpoint.SLOT_BUDGET);
            for(;cursor<end;cursor++){
                var stack=handler.getStackInSlot(cursor).copy();if(stack.isEmpty())continue;var identity=ItemKey.of(stack);
                if(!counts.containsKey(identity)&&counts.size()>=KEY_LIMIT){unsupported=true;completedAt=tick;break;}
                variants.computeIfAbsent(stack.getItem(),ignored->new HashSet<>()).add(identity);
                counts.merge(identity,(long)stack.getCount(),Long::sum);
            }
            if(!current(a,b,size)){reset();return new StorageEndpoint.Count(0,false);}
            if(cursor==slots){complete=true;completedAt=tick;}
        }
        return new StorageEndpoint.Count(key==null?0L:counts.getOrDefault(key,0L),complete);
    }
    StorageEndpoint.Count countItem(IItemHandler handler,net.minecraft.world.item.Item item,java.util.function.Predicate<net.minecraft.world.item.ItemStack> accepted,InventoryChanges.Token a,InventoryChanges.Token b,long tick){
        var status=count(handler,null,a,b,tick);long amount=0;
        if(status.complete())for(var key:variants.getOrDefault(item,Set.of()))if(accepted.test(key.stack(1)))amount+=counts.getOrDefault(key,0L);
        return new StorageEndpoint.Count(amount,status.complete());
    }
}
