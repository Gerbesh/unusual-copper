package io.github.gerbesh.unusualcopper.armor;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;

/** Main-thread idempotency and bounded event admission, independent of Minecraft entities. */
public final class ArmorEventJournal {
    private final Map<Object,Boolean> events=new WeakHashMap<>();
    private final Map<Object,Integer> counts=new WeakHashMap<>();
    public boolean contains(Object event){return events.containsKey(event);}
    public void markQuery(Object event){events.put(event,Boolean.TRUE);}
    public boolean reserve(Object owner,Object event,int amount,IntPredicate payment){
        if(owner==null||event==null||amount<=0||amount>10000||contains(event)||counts.getOrDefault(owner,0)>=64)return false;
        // Claim before calling payment, so a reentrant callback cannot authorize it again.
        events.put(event,Boolean.TRUE);counts.merge(owner,1,Integer::sum);boolean success=false;
        try{success=payment.test(amount);return success;}
        finally{if(!success){events.remove(event);counts.computeIfPresent(owner,(key,n)->n<=1?null:n-1);}}
    }
    public void endBatch(){counts.clear();}
    public void clear(){events.clear();counts.clear();}
}
