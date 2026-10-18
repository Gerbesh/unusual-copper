package io.github.gerbesh.unusualcopper.fluid.tank;

import net.minecraft.core.BlockPos;
import java.util.List;

/** Physical per-cube quantities are authoritative; group totals are never persisted as extra fluid. */
public final class TankVolumes {
    public static final int PER_BLOCK=16_000;
    public record Plan(int moved,int[] amounts){
        public Plan{amounts=amounts.clone();}
        @Override public int[] amounts(){return amounts.clone();}
    }
    private TankVolumes(){}
    public static int capacity(int count){
        if(count<1||count>TankTopology.MAX_BLOCKS)throw new IllegalArgumentException("Tank count");return count*PER_BLOCK;
    }
    public static int total(int[] amounts){
        capacity(amounts.length);long sum=0;
        for(int amount:amounts){if(amount<0)throw new IllegalArgumentException("Negative fluid");sum+=amount;}
        if(sum>Integer.MAX_VALUE)throw new IllegalArgumentException("Unrepresentable fluid total");return (int)sum;
    }
    public static Plan fill(int[] before,int offered){
        int total=total(before);var after=before.clone();int remaining=Math.min(Math.max(0,offered),Math.max(0,capacity(before.length)-total));
        int moved=remaining;
        for(int i=0;i<after.length&&remaining>0;i++){
            int add=Math.min(remaining,Math.max(0,PER_BLOCK-after[i]));after[i]+=add;remaining-=add;
        }
        return new Plan(moved-remaining,after);
    }
    public static Plan drain(int[] before,int requested){
        int remaining=Math.min(Math.max(0,requested),total(before)),moved=remaining;var after=before.clone();
        for(int i=0;i<after.length&&remaining>0;i++){int take=Math.min(remaining,after[i]);after[i]-=take;remaining-=take;}
        return new Plan(moved,after);
    }
    /** Shared horizontal level, including irregular footprints. Overfull legacy tanks remain full. */
    public static float fillAt(List<BlockPos> members,BlockPos member,int total){
        capacity(members.size());if(total<0||!members.contains(member))throw new IllegalArgumentException("Tank visual member");
        long below=members.stream().filter(p->p.getY()<member.getY()).count();
        long layer=members.stream().filter(p->p.getY()==member.getY()).count();
        return (float)Math.max(0,Math.min(1,(total-below*PER_BLOCK)/(double)(layer*PER_BLOCK)));
    }
    public static double surface(List<BlockPos> members,int total){
        capacity(members.size());if(total<0)throw new IllegalArgumentException("Negative tank visual");
        var layers=new java.util.TreeMap<Integer,Integer>();for(var p:members)layers.merge(p.getY(),1,Integer::sum);
        long remaining=total;
        for(var layer:layers.entrySet()){
            long amount=(long)layer.getValue()*PER_BLOCK;
            if(remaining<=amount)return layer.getKey()+remaining/(double)amount;
            remaining-=amount;
        }
        return layers.lastKey()+1D;
    }
}
