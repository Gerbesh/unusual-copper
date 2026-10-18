package io.github.gerbesh.unusualcopper.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorSensePacketTest {
    @Test void boundedOwnerMetadataRoundTripsWithoutInventoryOrNbt(){
        var target=new ArmorSenseSnapshot.Target(new BlockPos(-1,64,4),"unusualcopper:solar_infuser",100,1000,1,3);
        var value=new ArmorSenseSnapshot("minecraft:overworld",3,2,2,4,ArmorSenseSnapshot.ScanState.SEARCHING,192,925,List.of(new BlockPos(0,-30,0)),target);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());ArmorSenseSnapshot.Codec.encode(value,buffer);
        assertEquals(value,ArmorSenseSnapshot.Codec.decode(buffer));assertEquals(0,buffer.readableBytes());buffer.release();
    }
    @Test void oversizedCountIsRejectedBeforeAllocatingAList(){
        var b=new FriendlyByteBuf(Unpooled.buffer());b.writeUtf("minecraft:overworld");
        for(int i=0;i<6;i++)b.writeVarInt(0);b.writeVarInt(0);b.writeVarInt(0);b.writeVarInt(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->ArmorSenseSnapshot.Codec.decode(b));b.release();
    }
    @Test void inactiveVisorCannotCarryOrePositionsAndScannerOneCannotCarryPorts(){
        assertThrows(IllegalArgumentException.class,()->new ArmorSenseSnapshot("minecraft:overworld",0,1,0,0,ArmorSenseSnapshot.ScanState.READY,1,1,List.of(BlockPos.ZERO),null));
        var target=new ArmorSenseSnapshot.Target(BlockPos.ZERO,"minecraft:stone",1,10,1,3);
        assertThrows(IllegalArgumentException.class,()->new ArmorSenseSnapshot("minecraft:overworld",0,0,1,0,ArmorSenseSnapshot.ScanState.OFF,0,0,List.of(),target));
        assertThrows(IllegalArgumentException.class,()->new ArmorSenseSnapshot.Target(BlockPos.ZERO,"minecraft:stone",1,10,-1,0));
        assertThrows(IllegalArgumentException.class,()->new ArmorSenseSnapshot.Target(BlockPos.ZERO,"minecraft:stone",0,0,1,7));
    }
    @Test void deniedDiagnosticsCarryNoTargetAndSurviveTheWire(){
        var value=new ArmorSenseSnapshot("minecraft:overworld",1,0,1,0,ArmorSenseSnapshot.ScanState.OFF,0,0,List.of(),null,ArmorSenseSnapshot.DiagnosticState.PROTECTED);
        var b=new FriendlyByteBuf(Unpooled.buffer());ArmorSenseSnapshot.Codec.encode(value,b);assertEquals(value,ArmorSenseSnapshot.Codec.decode(b));b.release();
        var target=new ArmorSenseSnapshot.Target(BlockPos.ZERO,"minecraft:stone",0,0,-1,0);
        assertThrows(IllegalArgumentException.class,()->new ArmorSenseSnapshot("minecraft:overworld",1,0,1,0,ArmorSenseSnapshot.ScanState.OFF,0,0,List.of(),target,ArmorSenseSnapshot.DiagnosticState.PROTECTED));
    }
}
