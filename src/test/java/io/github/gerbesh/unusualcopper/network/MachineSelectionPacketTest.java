package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineSelectionPacketTest {
    private static final ResourceLocation POTION=new ResourceLocation("minecraft:fire_resistance");
    private static BrewerNetwork.Settings preview(int count,int vessel){return new BrewerNetwork.Settings(POTION,vessel,count,false,0);}
    @Test void recipeInspectionRoundTripKeepsQuantityVesselAndVisibleKeys(){
        var v=new BrewerNetwork.Inspect(17,UUID.randomUUID(),preview(64,2),List.of(new BrewerNetwork.Key(POTION,0),new BrewerNetwork.Key(POTION,2)));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{BrewerNetwork.Inspect.encode(v,b);assertEquals(v,BrewerNetwork.Inspect.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void inspectionRejectsUnboundedPagesAndMalformedVariants(){
        var id=UUID.randomUUID();var keys=Collections.nCopies(13,new BrewerNetwork.Key(POTION,0));
        assertThrows(IllegalArgumentException.class,()->new BrewerNetwork.Inspect(1,id,preview(1,0),keys));
        for(int n:new int[]{0,-1,4097,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->new BrewerNetwork.Inspect(1,id,preview(n,0),List.of()));
        assertThrows(IllegalArgumentException.class,()->new BrewerNetwork.Key(POTION,3));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeVarInt(1);b.writeUUID(id);b.writeResourceLocation(POTION);b.writeByte(0);b.writeVarInt(1);b.writeBoolean(false);b.writeByte(0);b.writeByte(255);assertThrows(IllegalArgumentException.class,()->BrewerNetwork.Inspect.decode(b));}finally{b.release();}
    }
    @Test void targetSelectionCarriesExactAreaAndMachineInstance(){
        var area=WorkArea.between(new BlockPos(-4,62,1),new BlockPos(4,70,7));
        var v=new ReaperNetwork.Choose(12,UUID.randomUUID(),area,new ReaperSettings(20,12,true,true,"minecraft:cow,minecraft:sheep","",""));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{ReaperNetwork.Choose.encode(v,b);assertEquals(v,ReaperNetwork.Choose.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test void visualTargetSelectionCannotSmuggleCategoriesOrInvalidThresholds(){
        var area=new WorkArea(BlockPos.ZERO,BlockPos.ZERO);var id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new ReaperNetwork.Choose(1,id,area,new ReaperSettings(0,1,true,true,"minecraft:cow","","")));
        assertThrows(IllegalArgumentException.class,()->new ReaperNetwork.Choose(1,id,area,new ReaperSettings(0,0,true,true,"","forge:animals","")));
        assertThrows(IllegalArgumentException.class,()->new ReaperNetwork.Choose(1,id,area,new ReaperSettings(0,0,true,true,"","","monster")));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeVarInt(1);b.writeUUID(id);b.writeBlockPos(BlockPos.ZERO);b.writeBlockPos(new BlockPos(16,0,0));assertThrows(IllegalArgumentException.class,()->ReaperNetwork.Choose.decode(b));}finally{b.release();}
    }
}
