package io.github.gerbesh.unusualcopper.fluid.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;

/** Deterministic, bounded partition of one loaded, same-owner adjacency component. */
public final class TankTopology {
    public static final int MAX_BLOCKS=256;
    public static final Comparator<BlockPos> ORDER=Comparator.comparingInt((BlockPos p)->p.getY())
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
    private TankTopology(){}
    /** Empty cells belong to their nearest filled source; position order breaks equal-distance ties. */
    public static <T> List<List<BlockPos>> partition(Map<BlockPos,T> contents){
        if(contents.isEmpty()||contents.size()>MAX_BLOCKS)throw new IllegalArgumentException("Tank component size");
        var positions=contents.keySet().stream().sorted(ORDER).toList();
        var assigned=new HashMap<BlockPos,T>();var frontier=new ArrayDeque<BlockPos>();
        for(var p:positions)if(contents.get(p)!=null){assigned.put(p,contents.get(p));frontier.add(p);}
        while(!frontier.isEmpty()){
            var p=frontier.removeFirst();T fluid=assigned.get(p);
            for(var direction:Direction.values()){
                var next=p.relative(direction);
                if(contents.containsKey(next)&&!assigned.containsKey(next)&&contents.get(next)==null){
                    assigned.put(next,fluid);frontier.addLast(next);
                }
            }
        }
        var visited=new HashSet<BlockPos>();var result=new ArrayList<List<BlockPos>>();
        for(var start:positions)if(visited.add(start)){
            var group=new ArrayList<BlockPos>();frontier.add(start);
            while(!frontier.isEmpty()){
                var p=frontier.removeFirst();group.add(p);
                for(var d:Direction.values()){
                    var n=p.relative(d);
                    if(contents.containsKey(n)&&Objects.equals(assigned.get(start),assigned.get(n))&&visited.add(n))frontier.addLast(n);
                }
            }
            group.sort(ORDER);result.add(List.copyOf(group));
        }
        return List.copyOf(result);
    }
    public static int connections(BlockPos pos,Collection<BlockPos> members){
        int mask=0;for(var d:Direction.values())if(members.contains(pos.relative(d)))mask|=1<<d.ordinal();return mask;
    }
    public static long shape(Collection<BlockPos> members){
        long hash=0xcbf29ce484222325L;for(var p:members.stream().sorted(ORDER).toList()){hash^=p.asLong();hash*=0x100000001b3L;}return hash;
    }
    public static int corners(BlockPos pos,Collection<BlockPos> members){
        int result=0;
        for(int x=0;x<2;x++)for(int z=0;z<2;z++){
            var dx=x==0?Direction.WEST:Direction.EAST;var dz=z==0?Direction.NORTH:Direction.SOUTH;
            boolean a=members.contains(pos.relative(dx)),b=members.contains(pos.relative(dz));
            if(!a&&!b||a&&b&&!members.contains(pos.relative(dx).relative(dz)))result|=1<<(x*2+z);
        }
        return result;
    }
}
