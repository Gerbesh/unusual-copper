package io.github.gerbesh.unusualcopper.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Portable CuE belongs to the individual, unstackable item; it is never Forge Energy. */
public final class CopperBatteryItem extends Item {
    public static final String ENERGY_TAG=BatteryChargeLedger.ENERGY;
    public final int tier, capacity;
    private final boolean infinite;
    public CopperBatteryItem(int tier) { this(tier,false); }
    public CopperBatteryItem(int tier,boolean infinite) {
        super(new Properties().stacksTo(1).rarity(infinite?Rarity.EPIC:Rarity.COMMON));
        this.tier=tier;this.capacity=capacityForTier(tier);this.infinite=infinite;
    }
    public static boolean infinite(ItemStack stack) { return stack.getItem() instanceof CopperBatteryItem battery&&battery.infinite; }
    public static int capacityForTier(int tier) { return BatteryTierPolicy.capacityForTier(tier); }
    public static int stored(ItemStack stack) {
        if(!(stack.getItem() instanceof CopperBatteryItem battery))return 0;
        return battery.infinite?CreativeBatteryCharge.available(stack.getTag(),battery.capacity)
                :BatteryChargeLedger.available(stack.getTag(),battery.capacity);
    }
    public static void stored(ItemStack stack,int value) {
        if(!(stack.getItem() instanceof CopperBatteryItem battery)||battery.infinite)return;
        int charge=Math.max(0,Math.min(battery.capacity,value));
        if(charge>0)BatteryChargeLedger.assign(stack.getOrCreateTag(),battery.capacity,charge);
        else if(stack.hasTag()) {
            // Keep empty crafted/upgraded batteries identical to their recipe-book result.
            // SSN verifies exact NBT; CuE:0 would make its planned result differ from assemble().
            var tag=stack.getTag();BatteryChargeLedger.assign(tag,battery.capacity,0);
            if(tag.isEmpty())stack.setTag(null);
        }
    }
    public static boolean reserve(ItemStack stack,int amount){
        if(stack.getCount()!=1||!(stack.getItem() instanceof CopperBatteryItem battery))return false;
        return battery.infinite?CreativeBatteryCharge.reserve(stack.getOrCreateTag(),battery.capacity,amount)
                :BatteryChargeLedger.reserve(stack.getTag(),battery.capacity,amount);
    }
    public static boolean validReservations(ItemStack stack){
        return stack.getCount()==1&&stack.getItem() instanceof CopperBatteryItem battery
                &&BatteryChargeLedger.valid(stack.getTag(),battery.capacity);
    }
    public static java.util.UUID reservationBatch(ItemStack stack){return BatteryChargeLedger.batch(stack.getTag());}
    public static boolean release(ItemStack stack,int amount,java.util.UUID expected){
        return stack.getCount()==1&&stack.getItem() instanceof CopperBatteryItem battery
                &&BatteryChargeLedger.release(stack.getTag(),battery.capacity,amount,expected);
    }
    public static boolean settle(ItemStack stack){
        if(!(stack.getItem() instanceof CopperBatteryItem battery)||stack.getCount()!=1)return false;
        boolean changed=battery.infinite?CreativeBatteryCharge.settle(stack.getTag(),battery.capacity)
                :BatteryChargeLedger.settle(stack.getTag(),battery.capacity);
        if(stack.hasTag()&&stack.getTag().isEmpty())stack.setTag(null);
        return changed;
    }
    public static int capacity(ItemStack stack) { return stack.getItem() instanceof CopperBatteryItem battery?battery.capacity:0; }
    public static String amount(int value) { return String.format(java.util.Locale.ROOT,"%,d",value).replace(',',' '); }
    public static Component chargeText(ItemStack stack) {
        return infinite(stack)?Component.translatable("battery.unusualcopper.infinite")
                :Component.translatable("battery.unusualcopper.charge",amount(stored(stack)),amount(capacity(stack)));
    }
    @Override public boolean isFoil(ItemStack stack) { return infinite||super.isFoil(stack); }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return infinite?13:(int)(13L*stored(stack)/capacity); }
    @Override public int getBarColor(ItemStack stack) { return 0x3dd6d3; }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(chargeText(stack));
        if(infinite)tooltip.add(Component.translatable("battery.unusualcopper.creative_hint"));
        else tooltip.add(Component.translatable("battery.unusualcopper.hint"));
    }
}
