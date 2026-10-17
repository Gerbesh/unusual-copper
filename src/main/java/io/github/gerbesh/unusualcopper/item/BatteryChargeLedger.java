package io.github.gerbesh.unusualcopper.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Durable reservations are already unavailable energy, including after save or transfer. */
public final class BatteryChargeLedger {
    public static final String ENERGY="CuE",RESERVED="UCReservedCuE",BATCH="UCReservationBatch";
    private BatteryChargeLedger(){}
    private static int raw(CompoundTag tag,int capacity){return tag==null?0:Math.max(0,Math.min(capacity,tag.getInt(ENERGY)));}
    public static boolean valid(CompoundTag tag,int capacity){
        if(capacity<0)return false;
        return tag==null||(!tag.contains(RESERVED)?!tag.contains(BATCH):tag.contains(RESERVED,Tag.TAG_INT)
                &&tag.hasUUID(BATCH)&&tag.getInt(RESERVED)>=0&&tag.getInt(RESERVED)<=raw(tag,capacity));
    }
    public static UUID batch(CompoundTag tag){return tag!=null&&tag.hasUUID(BATCH)?tag.getUUID(BATCH):null;}
    public static int available(CompoundTag tag,int capacity){
        return valid(tag,capacity)?raw(tag,capacity)-(tag==null?0:tag.getInt(RESERVED)):0;
    }
    public static boolean reserve(CompoundTag tag,int capacity,int amount){
        if(tag==null||amount<=0||!valid(tag,capacity)||amount>available(tag,capacity))return false;
        if(!tag.contains(RESERVED))tag.putUUID(BATCH,UUID.randomUUID());
        tag.putInt(RESERVED,tag.getInt(RESERVED)+amount);return true;
    }
    public static boolean release(CompoundTag tag,int capacity,int amount,UUID expected){
        if(tag==null||expected==null||!expected.equals(batch(tag))||amount<=0||!valid(tag,capacity)||amount>tag.getInt(RESERVED))return false;
        int remaining=tag.getInt(RESERVED)-amount;
        if(remaining==0){tag.remove(RESERVED);tag.remove(BATCH);}else tag.putInt(RESERVED,remaining);
        return true;
    }
    /** One physical charge-field debit for all successful reservations in the batch. */
    public static boolean settle(CompoundTag tag,int capacity){
        if(tag==null||!tag.contains(RESERVED)||!valid(tag,capacity))return false;
        int remaining=available(tag,capacity);tag.remove(RESERVED);tag.remove(BATCH);
        if(remaining==0)tag.remove(ENERGY);else tag.putInt(ENERGY,remaining);
        return true;
    }
    /** All other battery users assign effective charge, folding any prior reservation first. */
    public static void assign(CompoundTag tag,int capacity,int value){
        int charge=Math.max(0,Math.min(capacity,value));tag.remove(RESERVED);tag.remove(BATCH);
        if(charge==0)tag.remove(ENERGY);else tag.putInt(ENERGY,charge);
    }
}
