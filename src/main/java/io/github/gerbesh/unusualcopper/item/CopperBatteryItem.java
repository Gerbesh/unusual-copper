package io.github.gerbesh.unusualcopper.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Portable CuE belongs to the individual, unstackable item; it is never Forge Energy. */
public final class CopperBatteryItem extends Item {
    public static final String ENERGY_TAG="CuE";
    public final int tier, capacity;
    public CopperBatteryItem(int tier) {
        super(new Properties().stacksTo(1));
        if(tier<0 || tier>3)throw new IllegalArgumentException("Battery tier");
        this.tier=tier;this.capacity=10_000*(int)Math.pow(10,tier);
    }
    public static int stored(ItemStack stack) {
        return stack.getItem() instanceof CopperBatteryItem battery && stack.hasTag()
                ?Math.max(0,Math.min(battery.capacity,stack.getTag().getInt(ENERGY_TAG))):0;
    }
    public static void stored(ItemStack stack,int value) {
        if(!(stack.getItem() instanceof CopperBatteryItem battery))return;
        int charge=Math.max(0,Math.min(battery.capacity,value));
        if(charge>0)stack.getOrCreateTag().putInt(ENERGY_TAG,charge);
        else if(stack.hasTag()) {
            // Keep empty crafted/upgraded batteries identical to their recipe-book result.
            // SSN verifies exact NBT; CuE:0 would make its planned result differ from assemble().
            var tag=stack.getTag();tag.remove(ENERGY_TAG);
            if(tag.isEmpty())stack.setTag(null);
        }
    }
    public static int capacity(ItemStack stack) { return stack.getItem() instanceof CopperBatteryItem battery?battery.capacity:0; }
    public static String amount(int value) { return String.format(java.util.Locale.ROOT,"%,d",value).replace(',',' '); }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return (int)(13L*stored(stack)/capacity); }
    @Override public int getBarColor(ItemStack stack) { return 0x3dd6d3; }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("battery.unusualcopper.charge",amount(stored(stack)),amount(capacity)));
        tooltip.add(Component.translatable("battery.unusualcopper.hint"));
    }
}
