package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Cached horizontal wave order: at most 256 columns, never a full-volume sort per tick. */
public final class CultivationSweep {
    private WorkArea area;
    private BlockPos origin;
    private List<BlockPos> columns=List.of();
    public boolean active;
    public float front;
    private final Map<BlockPos,Integer> changes=new LinkedHashMap<>();

    public void reset() { area=null; columns=List.of(); active=false; changes.clear(); front=0; }
    public void prepare(WorkArea selection,BlockPos machine) {
        if(selection.equals(area) && machine.equals(origin))return;
        reset(); area=selection; origin=machine.immutable();
        var sorted=new ArrayList<BlockPos>();
        for(int z=area.min().getZ();z<=area.max().getZ();z++)for(int x=area.min().getX();x<=area.max().getX();x++)
            sorted.add(new BlockPos(x,area.min().getY(),z));
        sorted.sort(Comparator.comparingDouble(this::distanceSquared).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        columns=List.copyOf(sorted);
    }
    private double distanceSquared(BlockPos p) { double x=(double)p.getX()-origin.getX(),z=(double)p.getZ()-origin.getZ(); return x*x+z*z; }
    public BlockPos at(int cursor) {
        int index=Math.floorMod(cursor,area.volume()),height=area.max().getY()-area.min().getY()+1;
        return columns.get(index/height).above(index%height);
    }
    public float radius(int cursor) { return (float)Math.sqrt(distanceSquared(at(cursor))); }
    public void beginBatch() { changes.clear(); }
    public void changed(BlockPos pos,int icon) { if(changes.size()<8 || changes.containsKey(pos))changes.put(pos.immutable(),icon); }
    public Map<BlockPos,Integer> changes() { return Map.copyOf(changes); }
}
