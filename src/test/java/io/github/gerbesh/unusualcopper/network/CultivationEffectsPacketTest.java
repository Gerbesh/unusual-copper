package io.github.gerbesh.unusualcopper.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CultivationEffectsPacketTest {
    private static final ResourceLocation DIMENSION=new ResourceLocation("minecraft","overworld");
    @Test void cropBatchRoundTripContainsOnlyGeometryAndPublicIconIds() {
        var wave=new MachineEffects.Cultivation(DIMENSION,BlockPos.ZERO,new BlockPos(1,0,1),new BlockPos(4,0,4),1,6,
                List.of(new MachineEffects.Plant(new BlockPos(1,0,1),0),new MachineEffects.Plant(new BlockPos(2,0,2),12),new MachineEffects.Plant(new BlockPos(3,0,3),-1)));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {MachineEffects.Cultivation.encode(wave,buffer);assertEquals(wave,MachineEffects.Cultivation.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally {buffer.release();}
    }
    @Test void rejectsUnboundedOrInvalidCosmeticBatches() {
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.Cultivation(DIMENSION,BlockPos.ZERO,BlockPos.ZERO,BlockPos.ZERO,Float.NaN,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.Cultivation(DIMENSION,BlockPos.ZERO,BlockPos.ZERO,BlockPos.ZERO,3,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.Cultivation(DIMENSION,BlockPos.ZERO,new BlockPos(24,0,24),new BlockPos(24,0,24),0,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.Cultivation(DIMENSION,BlockPos.ZERO,BlockPos.ZERO,BlockPos.ZERO,0,1,Collections.nCopies(9,new MachineEffects.Plant(BlockPos.ZERO,0))));
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.Cultivation(DIMENSION,BlockPos.ZERO,BlockPos.ZERO,BlockPos.ZERO,0,1,List.of(new MachineEffects.Plant(new BlockPos(1,0,0),0))));
    }
    @Test void deliveryRoundTripIsBoundedAndRejectsDistantInventories() {
        var delivery=new MachineEffects.CultivationDelivery(DIMENSION,BlockPos.ZERO,new BlockPos(10,0,4),12);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{MachineEffects.CultivationDelivery.encode(delivery,buffer);assertEquals(delivery,MachineEffects.CultivationDelivery.decode(buffer));}
        finally{buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.CultivationDelivery(DIMENSION,BlockPos.ZERO,new BlockPos(33,0,0),12));
        assertThrows(IllegalArgumentException.class,()->new MachineEffects.CultivationDelivery(DIMENSION,BlockPos.ZERO,BlockPos.ZERO,0));
    }
}
