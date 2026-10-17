package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.armor.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorSnapshotPacketTest {
    private static ArmorNetwork.Snapshot sample(List<ArmorNetwork.Piece> pieces){return new ArmorNetwork.Snapshot(UUID.randomUUID(),4,true,1,100,1000,8,1,1L<<ArmorModuleType.AQUA_LUNGS.ordinal(),0,new ArmorRuntime.FlightStatus(true,930,35),pieces,ArmorNetwork.Result.OK);}
    @Test void roundTripHasOnlyBoundedEquipmentMetadataAndPreferences(){
        var row=new ArmorNetwork.Row(ArmorModuleType.AQUA_LUNGS,new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.UNDERWATER,25,false,2),2);
        var piece=new ArmorNetwork.Piece("unusualcopper:programmable_helmet_tier_3",3,3,List.of(row));
        var value=sample(List.of(piece,new ArmorNetwork.Piece("",0,0,List.of()),new ArmorNetwork.Piece("",0,0,List.of()),new ArmorNetwork.Piece("",0,0,List.of())));
        var b=new FriendlyByteBuf(Unpooled.buffer());ArmorNetwork.Codec.encode(value,b);assertEquals(value,ArmorNetwork.Codec.decode(b));assertEquals(0,b.readableBytes());b.release();
    }
    @Test void oversizedArraysAndDuplicateModulesAreRejected(){
        var b=new FriendlyByteBuf(Unpooled.buffer());ArmorNetwork.Codec.encode(sample(List.of()),b);assertThrows(IllegalArgumentException.class,()->ArmorNetwork.Codec.decode(b));b.release();
        var row=new ArmorNetwork.Row(ArmorModuleType.AQUA_LUNGS,new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.AUTO,0,false));
        var piece=new ArmorNetwork.Piece("unusualcopper:programmable_helmet_tier_3",3,3,List.of(row,row));
        var second=new FriendlyByteBuf(Unpooled.buffer());ArmorNetwork.Codec.encode(sample(List.of(piece,piece,piece,piece)),second);assertThrows(IllegalArgumentException.class,()->ArmorNetwork.Codec.decode(second));second.release();
    }
    @Test void moduleLevelCannotExceedChassisOnTheWire(){
        var row=new ArmorNetwork.Row(ArmorModuleType.MINING_SERVO,new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.AUTO,0,false),3);
        var piece=new ArmorNetwork.Piece("unusualcopper:programmable_leggings_tier_1",1,1,List.of(row));
        var b=new FriendlyByteBuf(Unpooled.buffer());
        ArmorNetwork.Codec.encode(sample(List.of(piece,piece,piece,piece)),b);
        assertThrows(IllegalArgumentException.class,()->ArmorNetwork.Codec.decode(b));b.release();
        assertThrows(IllegalArgumentException.class,()->new ArmorNetwork.Row(ArmorModuleType.FALL_DAMPER,row.settings(),2));
    }
}
