package io.github.gerbesh.unusualcopper.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class BatteryChargeLedgerTest {
    private CompoundTag battery(int amount){var tag=new CompoundTag();tag.putInt("CuE",amount);tag.putString("CustomName","Kept");return tag;}
    @Test void continuousAndEventsShareOnePhysicalDebitAndCannotOverspend(){
        var tag=battery(4000);
        assertTrue(BatteryChargeLedger.reserve(tag,10000,280));
        assertTrue(BatteryChargeLedger.reserve(tag,10000,2000));
        assertTrue(BatteryChargeLedger.reserve(tag,10000,600));
        assertEquals(4000,tag.getInt("CuE"));assertEquals(1120,BatteryChargeLedger.available(tag,10000));
        assertFalse(BatteryChargeLedger.reserve(tag,10000,1121));assertEquals(1120,BatteryChargeLedger.available(tag,10000));
        assertTrue(BatteryChargeLedger.settle(tag,10000));assertEquals(1120,tag.getInt("CuE"));
        assertFalse(tag.contains(BatteryChargeLedger.RESERVED));assertFalse(BatteryChargeLedger.settle(tag,10000));
        assertEquals("Kept",tag.getString("CustomName"));
    }
    @Test void serializedReservationsRemainUnavailableAfterTransferOrRestart() throws IOException {
        var tag=battery(9000);assertTrue(BatteryChargeLedger.reserve(tag,10000,3500));
        var bytes=new ByteArrayOutputStream();NbtIo.write(tag,new DataOutputStream(bytes));
        var restored=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(5500,BatteryChargeLedger.available(restored,10000));
        assertTrue(BatteryChargeLedger.settle(restored,10000));assertEquals(5500,restored.getInt("CuE"));
    }
    @Test void canceledEventReleasesOnlyItsOwnReservedAmount(){
        var tag=battery(5000);BatteryChargeLedger.reserve(tag,10000,280);BatteryChargeLedger.reserve(tag,10000,2000);
        var batch=BatteryChargeLedger.batch(tag);
        assertTrue(BatteryChargeLedger.release(tag,10000,2000,batch));assertEquals(4720,BatteryChargeLedger.available(tag,10000));
        assertFalse(BatteryChargeLedger.release(tag,10000,281,batch));
        BatteryChargeLedger.settle(tag,10000);assertEquals(4720,tag.getInt("CuE"));
    }
    @Test void aReceiptCannotRefundAReplacedBatteryOrANewBatch(){
        var first=battery(5000);var second=battery(5000);
        BatteryChargeLedger.reserve(first,10000,600);BatteryChargeLedger.reserve(second,10000,600);
        var old=BatteryChargeLedger.batch(first);
        assertFalse(BatteryChargeLedger.release(second,10000,600,old));assertEquals(4400,BatteryChargeLedger.available(second,10000));
        BatteryChargeLedger.settle(first,10000);BatteryChargeLedger.reserve(first,10000,600);
        assertFalse(BatteryChargeLedger.release(first,10000,600,old));assertEquals(3800,BatteryChargeLedger.available(first,10000));
    }
    @Test void ordinaryChargingAndDischargingUseEffectiveChargeWithoutResurrection(){
        var tag=battery(4000);BatteryChargeLedger.reserve(tag,10000,2800);
        BatteryChargeLedger.assign(tag,10000,BatteryChargeLedger.available(tag,10000)+100);
        assertEquals(1300,BatteryChargeLedger.available(tag,10000));assertFalse(tag.contains(BatteryChargeLedger.RESERVED));
        BatteryChargeLedger.assign(tag,10000,0);assertFalse(tag.contains("CuE"));assertEquals("Kept",tag.getString("CustomName"));
    }
    @Test void malformedReservationNeverIncreasesAvailableEnergy(){
        var tag=battery(4000);
        for(int bad:new int[]{-1,4001,Integer.MAX_VALUE}){
            tag.putInt(BatteryChargeLedger.RESERVED,bad);assertFalse(BatteryChargeLedger.valid(tag,10000));
            assertEquals(0,BatteryChargeLedger.available(tag,10000));assertFalse(BatteryChargeLedger.reserve(tag,10000,1));
            assertFalse(BatteryChargeLedger.settle(tag,10000));assertEquals(4000,tag.getInt("CuE"));
        }
        tag.putString(BatteryChargeLedger.RESERVED,"2800");assertEquals(0,BatteryChargeLedger.available(tag,10000));
    }
}
