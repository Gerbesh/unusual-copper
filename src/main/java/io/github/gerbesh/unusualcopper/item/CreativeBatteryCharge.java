package io.github.gerbesh.unusualcopper.item;

import net.minecraft.nbt.CompoundTag;

/** Infinite source with a real, bounded reservation batch for once-only armor receipts. */
public final class CreativeBatteryCharge {
    private CreativeBatteryCharge() {}
    public static int available(CompoundTag tag,int capacity) {
        if(!BatteryChargeLedger.valid(tag,capacity))return 0;
        return BatteryChargeLedger.batch(tag)==null?capacity:BatteryChargeLedger.available(tag,capacity);
    }
    public static boolean reserve(CompoundTag tag,int capacity,int amount) {
        if(tag==null||amount<=0||amount>capacity||!BatteryChargeLedger.valid(tag,capacity))return false;
        // Never refill an active batch: its UUID and held energy belong to existing receipts.
        if(BatteryChargeLedger.batch(tag)==null)BatteryChargeLedger.assign(tag,capacity,capacity);
        return BatteryChargeLedger.reserve(tag,capacity,amount);
    }
    public static boolean settle(CompoundTag tag,int capacity) {
        if(!BatteryChargeLedger.settle(tag,capacity))return false;
        BatteryChargeLedger.assign(tag,capacity,capacity);
        return true;
    }
}
