package io.github.gerbesh.unusualcopper.network;
import io.github.gerbesh.unusualcopper.network.ItemTransitNetwork.Trail;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemTransitPacketTest {
    private static final ResourceLocation DIM=new ResourceLocation("minecraft:overworld");
    @Test void bentPathKeepsSourceToDestinationOrder(){var t=new Trail(DIM,12,List.of(BlockPos.ZERO,new BlockPos(1,0,0),new BlockPos(1,1,0),new BlockPos(1,1,1)));var b=new FriendlyByteBuf(Unpooled.buffer());try{Trail.encode(t,b);assertEquals(t,Trail.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void refusesTeleportingAndUnboundedPaths(){assertThrows(IllegalArgumentException.class,()->new Trail(DIM,1,List.of(BlockPos.ZERO,new BlockPos(2,0,0),new BlockPos(3,0,0))));assertThrows(IllegalArgumentException.class,()->new Trail(DIM,1,Collections.nCopies(259,BlockPos.ZERO)));var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeResourceLocation(DIM);b.writeVarInt(1);b.writeVarInt(Integer.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->Trail.decode(b));}finally{b.release();}}
    @Test void coordinatesAreCopiedAndFlightLifetimeIsBounded(){var p=new BlockPos.MutableBlockPos(0,0,0);var t=new Trail(DIM,1,List.of(p,new BlockPos(1,0,0),new BlockPos(2,0,0)));p.set(100,0,0);assertEquals(BlockPos.ZERO,t.path().get(0));assertEquals(20,t.duration());var path=new ArrayList<BlockPos>();for(int i=0;i<258;i++)path.add(new BlockPos(i,0,0));assertEquals(160,new Trail(DIM,1,path).duration());assertThrows(UnsupportedOperationException.class,()->t.path().clear());}
}
