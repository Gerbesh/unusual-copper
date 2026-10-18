package io.github.gerbesh.unusualcopper.item;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreativeBatteryChargeTest {
    @Test void pristineSourceIsFullAndEachCompletedBatchReplenishes() {
        assertEquals(10000,CreativeBatteryCharge.available(null,10000));
        var tag=new CompoundTag();tag.putString("CustomName","Admin");
        for(int i=0;i<20;i++) {
            assertTrue(CreativeBatteryCharge.reserve(tag,10000,10000));
            assertEquals(0,CreativeBatteryCharge.available(tag,10000));
            assertFalse(CreativeBatteryCharge.reserve(tag,10000,1));
            assertTrue(CreativeBatteryCharge.settle(tag,10000));
            assertEquals(10000,CreativeBatteryCharge.available(tag,10000));
            assertFalse(CreativeBatteryCharge.settle(tag,10000));
        }
        assertEquals("Admin",tag.getString("CustomName"));
    }
    @Test void refundAndSerializedActiveBatchKeepTheirReceiptIdentity() {
        var tag=new CompoundTag();assertTrue(CreativeBatteryCharge.reserve(tag,10000,2000));
        var receipt=BatteryChargeLedger.batch(tag);var restored=tag.copy();
        assertEquals(8000,CreativeBatteryCharge.available(restored,10000));
        assertTrue(CreativeBatteryCharge.reserve(restored,10000,3000));
        assertEquals(receipt,BatteryChargeLedger.batch(restored));
        assertTrue(BatteryChargeLedger.release(restored,10000,2000,receipt));
        assertEquals(7000,CreativeBatteryCharge.available(restored,10000));
        assertTrue(CreativeBatteryCharge.settle(restored,10000));
        assertTrue(CreativeBatteryCharge.reserve(restored,10000,100));
        assertFalse(BatteryChargeLedger.release(restored,10000,100,receipt));
    }
    @Test void malformedHoldsFailClosedAndOrdinaryBatteriesRemainFinite() {
        var tag=new CompoundTag();tag.putInt(BatteryChargeLedger.RESERVED,-1);
        assertEquals(0,CreativeBatteryCharge.available(tag,10000));
        assertFalse(CreativeBatteryCharge.reserve(tag,10000,1));
        assertFalse(CreativeBatteryCharge.settle(tag,10000));
        var ordinary=new CompoundTag();ordinary.putBoolean("Infinite",true);
        assertEquals(0,BatteryChargeLedger.available(ordinary,10000));
        assertFalse(BatteryChargeLedger.reserve(ordinary,10000,1));
    }
}
