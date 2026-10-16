package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Standard Forge durability with an exact preservation check on the serialized item and capabilities. */
public final class RepairPolicy {
    public static boolean target(ItemStack stack){try{return !stack.isEmpty()&&stack.getCount()==1&&stack.isDamageableItem()&&stack.getMaxDamage()>0&&stack.getDamageValue()>0&&stack.getDamageValue()<=stack.getMaxDamage()&&!(stack.hasTag()&&stack.getTag().getBoolean("Unbreakable"));}catch(RuntimeException unsupported){return false;}}
    public static RepairPrice quote(ItemStack stack,int requested){if(!target(stack))return null;try{var price=RepairPrice.of(stack.getDamageValue(),requested,ServerConfig.repairCue(),ServerConfig.repairDurabilityPerXp());apply(stack,price);return price;}catch(RuntimeException unsupported){return null;}}
    private static CompoundTag withoutDamage(ItemStack stack){var data=stack.save(new CompoundTag());var tag=data.getCompound("tag");tag.remove("Damage");if(tag.isEmpty())data.remove("tag");return data;}
    public static ItemStack apply(ItemStack source,RepairPrice price){
        if(!target(source)||price.points()<1||price.points()>source.getDamageValue())throw new IllegalArgumentException("Invalid repair target");
        var before=source.save(new CompoundTag());if(before.getCompound("tag").getInt("Damage")!=source.getDamageValue())throw new IllegalArgumentException("Nonstandard durability storage");
        var result=source.copy();result.setDamageValue(source.getDamageValue()-price.points());
        if(result.getCount()!=1||result.getItem()!=source.getItem()||result.getMaxDamage()!=source.getMaxDamage()||result.getDamageValue()!=source.getDamageValue()-price.points()||!withoutDamage(source).equals(withoutDamage(result)))throw new IllegalArgumentException("Durability hook changed protected item data");
        // The saved representation must also survive loading without changing any non-durability data.
        var loaded=ItemStack.of(result.save(new CompoundTag()));if(!ItemStack.matches(loaded,result)||loaded.getDamageValue()!=result.getDamageValue())throw new IllegalArgumentException("Unstable repaired item");
        return result;
    }
    private RepairPolicy(){}
}
