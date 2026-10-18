package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.core.BlockPos;
import java.util.*;

/** A finite center-first pass. Coordinates are cheap; the caller budgets every candidate. */
public final class ArmorScanCursor {
    private static final Map<Integer,List<BlockPos>> OFFSETS=new HashMap<>();
    private final BlockPos center;
    private final List<BlockPos> offsets;
    private int cursor;
    public ArmorScanCursor(BlockPos center,int radius){
        if(radius<1||radius>8)throw new IllegalArgumentException("Scan radius");
        this.center=center.immutable();offsets=OFFSETS.computeIfAbsent(radius,ArmorScanCursor::offsets);
    }
    private static List<BlockPos> offsets(int radius){
        var result=new ArrayList<BlockPos>();
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++)
            if(x*x+y*y+z*z<=radius*radius)result.add(new BlockPos(x,y,z));
        result.sort(Comparator.<BlockPos>comparingInt(p->p.getX()*p.getX()+p.getY()*p.getY()+p.getZ()*p.getZ())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(result);
    }
    public BlockPos next(){return complete()?null:center.offset(offsets.get(cursor++));}
    public BlockPos center(){return center;}
    public int checked(){return cursor;}
    public int total(){return offsets.size();}
    public boolean complete(){return cursor==offsets.size();}
}
