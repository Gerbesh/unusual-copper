package io.github.gerbesh.unusualcopper.armor;

import java.util.*;

/** One shared counter for block candidates and safe diagnostic shape reads. */
public final class ArmorScanBudget {
    private final int total,personal;
    private final Map<UUID,Integer> wearers=new HashMap<>();
    private int used;
    public ArmorScanBudget(int total,int personal){if(total<1||personal<1)throw new IllegalArgumentException("Scan budget");this.total=total;this.personal=personal;}
    public boolean take(UUID wearer){
        Objects.requireNonNull(wearer);int own=wearers.getOrDefault(wearer,0);
        if(used>=total||own>=personal)return false;
        wearers.put(wearer,own+1);used++;return true;
    }
    public int used(){return used;}
    public int remainingFor(UUID wearer){return Math.min(total-used,personal-wearers.getOrDefault(wearer,0));}
    public boolean remaining(UUID wearer){return remainingFor(wearer)>0;}
    /** Keep a shape ray atomic while sharing the remaining capacity fairly. */
    public static int fairSlice(int total,int configured,int wearers){
        if(total<64||configured<64||wearers<1)throw new IllegalArgumentException("Fair scan slice");
        return Math.min(configured,Math.max(64,total/wearers));
    }
    public static final class Rotation {
        private final int start,size;private int firstDeferred=-1,lastWorked=-1;
        public Rotation(int cursor,int size){if(size<1)throw new IllegalArgumentException("Empty scan queue");this.size=size;start=Math.floorMod(cursor,size);}
        public int at(int index){if(index<0||index>=size)throw new IndexOutOfBoundsException();return (start+index)%size;}
        public void deferred(int index){if(firstDeferred<0)firstDeferred=index;}
        public void worked(int index){lastWorked=index;}
        public int next(){return start+(firstDeferred>=0?firstDeferred:Math.max(1,lastWorked+1));}
    }
}
