package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Explicit standard-item/book adapters plus configured, tested mod-ID opt-ins. */
public final class EnchantmentPolicy {
    public record Offer(ResourceLocation enchantment,int level,int xp,int cue){}
    public static boolean book(ItemStack stack){return stack.is(Items.BOOK)||stack.is(Items.ENCHANTED_BOOK);}
    private static boolean nonempty(CompoundTag tag,String key){var raw=tag.get(key);return raw!=null&&(!(raw instanceof ListTag list)||!list.isEmpty());}
    public static boolean target(ItemStack stack){var id=ForgeRegistries.ITEMS.getKey(stack.getItem());return !stack.isEmpty()&&id!=null&&(id.getNamespace().equals("minecraft")||ServerConfig.enchanterItems().contains(id.toString()))&&(book(stack)||stack.isEnchantable()||stack.isEnchanted());}
    public static Map<Enchantment,Integer> existing(ItemStack stack){
        var result=new LinkedHashMap<Enchantment,Integer>();var tag=stack.getTag();String key=stack.is(Items.ENCHANTED_BOOK)?"StoredEnchantments":"Enchantments";
        if(tag!=null&&stack.is(Items.BOOK)&&(nonempty(tag,"Enchantments")||nonempty(tag,"StoredEnchantments")))throw new IllegalArgumentException("Nonstandard pre-enchanted plain book");
        if(tag==null||!tag.contains(key))return result;if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Malformed enchantment list");var raw=(ListTag)tag.get(key);if(!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed enchantment entries");
        for(var row:raw){var entry=(CompoundTag)row;var id=ResourceLocation.tryParse(entry.getString("id"));var enchantment=id==null?null:ForgeRegistries.ENCHANTMENTS.getValue(id);int level=entry.getInt("lvl");if(enchantment==null||level<1||level>32767||result.putIfAbsent(enchantment,level)!=null)throw new IllegalArgumentException("Unknown or duplicate enchantment");}return result;
    }
    public static Offer offer(ItemStack stack,ResourceLocation id){
        try{
            if(stack.getCount()!=1||!target(stack))return null;var enchantment=ForgeRegistries.ENCHANTMENTS.getValue(id);if(enchantment==null||!id.getNamespace().equals("minecraft")&&!ServerConfig.enchanterEnchantments().contains(id.toString()))return null;
            if(enchantment.isTreasureOnly()&&!ServerConfig.enchanterTreasure()||enchantment.isCurse()&&!ServerConfig.enchanterCurses())return null;
            int level=Math.min(enchantment.getMaxLevel(),ServerConfig.enchanterMaxLevel());if(level<1)return null;
            if(book(stack)?!enchantment.isAllowedOnBooks():!enchantment.canEnchant(stack.copy()))return null;
            var existing=existing(stack);if(existing.getOrDefault(enchantment,0)>=level)return null;
            for(var a:existing.keySet())for(var b:existing.keySet())if(a!=b&&(!a.isCompatibleWith(b)||!b.isCompatibleWith(a)))return null;
            for(var old:existing.keySet())if(old!=enchantment&&(!old.isCompatibleWith(enchantment)||!enchantment.isCompatibleWith(old)))return null;
            double rarity=ServerConfig.enchanterRarity(enchantment.getRarity().ordinal()),scale=1+existing.size()*ServerConfig.enchanterAdditional();
            double xp=Math.ceil(ServerConfig.enchanterXpBase()*level*rarity*ServerConfig.enchanterXpMultiplier()*scale);
            double cue=Math.ceil(Math.max(ServerConfig.enchanterCueMinimum(),ServerConfig.enchanterCueBase()*level*rarity)*scale);
            if(!Double.isFinite(xp)||!Double.isFinite(cue)||xp<1||xp>Integer.MAX_VALUE||cue<1||cue>MachineKind.ENCHANTMENT_RESONATOR.capacity)return null;
            return new Offer(id,level,(int)xp,(int)cue);
        }catch(RuntimeException unsupported){return null;}
    }
    public static List<Offer> offers(ItemStack stack){
        var choices=new ArrayList<Offer>();for(var e:ForgeRegistries.ENCHANTMENTS.getEntries()){var option=offer(stack.copy(),e.getKey().location());if(option!=null)choices.add(option);}choices.sort(Comparator.comparing(o->o.enchantment().toString()));if(choices.size()>256)return List.of();return List.copyOf(choices);
    }
    public static ItemStack apply(ItemStack source,Offer offer){
        var result=source.copyWithCount(1);if(result.is(Items.BOOK)){var saved=result.save(new CompoundTag());saved.putString("id",ForgeRegistries.ITEMS.getKey(Items.ENCHANTED_BOOK).toString());saved.putByte("Count",(byte)1);result=ItemStack.of(saved);}
        String key=result.is(Items.ENCHANTED_BOOK)?"StoredEnchantments":"Enchantments";var entries=result.getOrCreateTag().getList(key,Tag.TAG_COMPOUND).copy();boolean updated=false;
        for(var raw:entries){var entry=(CompoundTag)raw;if(entry.getString("id").equals(offer.enchantment.toString())){entry.putShort("lvl",(short)offer.level);updated=true;}}
        if(!updated){var entry=new CompoundTag();entry.putString("id",offer.enchantment.toString());entry.putShort("lvl",(short)offer.level);entries.add(entry);}result.getOrCreateTag().put(key,entries);return result;
    }
    private EnchantmentPolicy(){}
}
