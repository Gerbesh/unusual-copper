package io.github.gerbesh.unusualcopper.armor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One allocation can fund continuous work and at most 64 reactive queries in a tick. */
public final class ArmorTickReceipt {
    private record Use(BooleanSupplier applied,Runnable after){}
    private final List<Use> uses=new ArrayList<>();
    private boolean applied;
    public boolean add(BooleanSupplier effect,Runnable after){
        Objects.requireNonNull(effect);
        if(uses.size()>=64)return false;
        uses.add(new Use(effect,after));return true;
    }
    public void applied(){applied=true;}
    public boolean untouched(){return !applied&&uses.isEmpty();}
    public int confirmed(int cost){return applied||uses.stream().anyMatch(v->v.applied.getAsBoolean())?cost:0;}
    public void after(){for(var use:uses)if(use.after!=null&&use.applied.getAsBoolean())use.after.run();}
}
