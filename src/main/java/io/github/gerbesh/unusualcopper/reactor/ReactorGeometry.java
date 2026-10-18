package io.github.gerbesh.unusualcopper.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.stream.IntStream;

public final class ReactorGeometry {
    private ReactorGeometry(){}
    public static List<BlockPos> positions(BlockPos anchor){return IntStream.range(0,36).mapToObj(i->anchor.offset(i%3,i/9,(i/3)%3)).toList();}
    /** Eight distinct lower sockets, one per perimeter block; corners have a single outlet. */
    public static int outlet(BlockPos anchor,BlockPos pos,Direction face){
        int x=pos.getX()-anchor.getX(),y=pos.getY()-anchor.getY(),z=pos.getZ()-anchor.getZ();if(y!=0)return -1;
        if(z==0&&face==Direction.NORTH&&x<2&&x>=0)return x;
        if(x==2&&face==Direction.EAST&&z<2&&z>=0)return 2+z;
        if(z==2&&face==Direction.SOUTH&&x>0&&x<=2)return 6-x;
        if(x==0&&face==Direction.WEST&&z>0&&z<=2)return 8-z;
        return -1;
    }
    public static boolean outside(BlockPos anchor,BlockPos pos,Direction face){
        var next=pos.relative(face);return next.getX()<anchor.getX()||next.getX()>anchor.getX()+2||next.getY()<anchor.getY()||next.getY()>anchor.getY()+3||next.getZ()<anchor.getZ()||next.getZ()>anchor.getZ()+2;
    }
}
