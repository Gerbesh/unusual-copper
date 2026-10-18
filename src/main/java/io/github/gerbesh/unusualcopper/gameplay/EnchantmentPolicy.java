package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Registry-wide applicability through Forge item hooks; no ArmorItem/class whitelist. */
public final class EnchantmentPolicy {
    public record Offer(ResourceLocation enchantment,int level,int xp,int cue){
        public Offer{if(enchantment==null||enchantment.toString().length()>32767||level<1||level>32767||xp<1||cue<1||cue>MachineKind.ENCHANTMENT_RESONATOR.capacity)throw new IllegalArgumentException("Enchantment offer");}
        public int wireBudget(){return enchantment.toString().length()+20;}
    }
    public static boolean book(ItemStack stack){return stack.is(Items.BOOK)||stack.is(Items.ENCHANTED_BOOK);}
    private static boolean nonempty(CompoundTag tag,String key){var raw=tag.get(key);return raw!=null&&(!(raw instanceof ListTag list)||!list.isEmpty());}
    // Slot admission is deliberately broader than vanilla enchantability. Curios/clothing can
    // opt in solely through canEnchant/canApplyAtEnchantingTable, even with enchantability zero.
    public static boolean target(ItemStack stack){var id=ForgeRegistries.ITEMS.getKey(stack.getItem());return !stack.isEmpty()&&id!=null&&(ServerConfig.enchanterAutomatic()||id.getNamespace().equals("minecraft")||ServerConfig.enchanterItems().contains(id.toString()));}
    public static Map<Enchantment,Integer> existing(ItemStack stack){
        var result=new LinkedHashMap<Enchantment,Integer>();var tag=stack.getTag();String key=stack.is(Items.ENCHANTED_BOOK)?"StoredEnchantments":"Enchantments";
        if(tag!=null&&stack.is(Items.BOOK)&&(nonempty(tag,"Enchantments")||nonempty(tag,"StoredEnchantments")))throw new IllegalArgumentException("Nonstandard pre-enchanted plain book");
        if(tag!=null&&tag.contains(key)){
            if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Malformed enchantment list");var raw=(ListTag)tag.get(key);if(!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed enchantment entries");
            for(var row:raw){var entry=(CompoundTag)row;var id=ResourceLocation.tryParse(entry.getString("id"));var enchantment=id==null?null:ForgeRegistries.ENCHANTMENTS.getValue(id);int level=entry.getInt("lvl");if(enchantment==null||level<1||level>32767||result.putIfAbsent(enchantment,level)!=null)throw new IllegalArgumentException("Unknown or duplicate enchantment");}
        }
        // Include virtual/implicit item enchantments exposed by Forge, so they cannot be
        // overwritten, sold repeatedly, or bypassed when checking conflicts.
        for(var row:stack.copy().getAllEnchantments().entrySet()){
            var enchantment=row.getKey();var level=row.getValue();
            if(enchantment==null||ForgeRegistries.ENCHANTMENTS.getKey(enchantment)==null||level==null||level<0||level>32767)throw new IllegalArgumentException("Invalid item enchantment hook");
            if(level==0)continue;
            result.merge(enchantment,level,Math::max);
        }
        return result;
    }
    public static Offer offer(ItemStack stack,ResourceLocation id){
        try{
            if(stack.getCount()!=1||!target(stack))return null;var enchantment=ForgeRegistries.ENCHANTMENTS.getValue(id);if(enchantment==null||!ServerConfig.enchanterAutomatic()&&!id.getNamespace().equals("minecraft")&&!ServerConfig.enchanterEnchantments().contains(id.toString()))return null;
            if(enchantment.isTreasureOnly()&&!ServerConfig.enchanterTreasure()||enchantment.isCurse()&&!ServerConfig.enchanterCurses())return null;
            int level=Math.min(enchantment.getMaxLevel(),ServerConfig.enchanterMaxLevel());if(level<Math.max(1,enchantment.getMinLevel()))return null;
            if(book(stack)?!enchantment.isAllowedOnBooks():!enchantment.canEnchant(stack.copy()))return null;
            var existing=existing(stack);if(existing.getOrDefault(enchantment,0)>=level)return null;
            for(var a:existing.keySet())for(var b:existing.keySet())if(a!=b&&(!a.isCompatibleWith(b)||!b.isCompatibleWith(a)))return null;
            for(var old:existing.keySet())if(old!=enchantment&&(!old.isCompatibleWith(enchantment)||!enchantment.isCompatibleWith(old)))return null;
            double rarity=ServerConfig.enchanterRarity(enchantment.getRarity().ordinal()),scale=1+existing.size()*ServerConfig.enchanterAdditional();
            double xp=Math.ceil(ServerConfig.enchanterXpBase()*level*rarity*ServerConfig.enchanterXpMultiplier()*scale);
            double cue=Math.ceil(Math.max(ServerConfig.enchanterCueMinimum(),ServerConfig.enchanterCueBase()*level*rarity)*scale);
            if(!Double.isFinite(xp)||!Double.isFinite(cue)||xp<1||xp>Integer.MAX_VALUE||cue<1||cue>MachineKind.ENCHANTMENT_RESONATOR.capacity)return null;
            var offer=new Offer(id,level,(int)xp,(int)cue);apply(stack,offer);return offer;
        }catch(RuntimeException unsupported){return null;}
    }
    public static List<Offer> offers(ItemStack stack){
        var choices=new ArrayList<Offer>();if(stack.isEmpty())return List.of();for(var e:ForgeRegistries.ENCHANTMENTS.getEntries()){var option=offer(stack.copy(),e.getKey().location());if(option!=null)choices.add(option);}choices.sort(Comparator.comparing(o->o.enchantment().toString()));return List.copyOf(choices);
    }
    /** Registry IDs are ASCII. Budget bytes too, so unusual long IDs still fit a page. */
    public static List<Offer> page(List<Offer> all,int start){
        if(start<0||start>all.size())throw new IllegalArgumentException("Catalog offset");
        int end=start,bytes=0;
        while(end<all.size()&&end-start<EnchantmentCatalog.PAGE_SIZE&&bytes+all.get(end).wireBudget()<=EnchantmentCatalog.PAGE_BYTES){bytes+=all.get(end).wireBudget();end++;}
        return List.copyOf(all.subList(start,end));
    }
    /** Exact configuration equality invalidates a menu cache on a server config reload. */
    public static List<?> configuration(){return List.of(ServerConfig.enchanterAutomatic(),ServerConfig.enchanterTreasure(),ServerConfig.enchanterCurses(),
            ServerConfig.enchanterMaxLevel(),ServerConfig.enchanterXpBase(),ServerConfig.enchanterXpMultiplier(),ServerConfig.enchanterCueBase(),ServerConfig.enchanterCueMinimum(),
            ServerConfig.enchanterAdditional(),ServerConfig.enchanterRarity(0),ServerConfig.enchanterRarity(1),ServerConfig.enchanterRarity(2),ServerConfig.enchanterRarity(3),
            List.copyOf(ServerConfig.enchanterItems()),List.copyOf(ServerConfig.enchanterEnchantments()));}
    public static ItemStack apply(ItemStack source,Offer offer){
        var result=source.copyWithCount(1);if(result.is(Items.BOOK)){var saved=result.save(new CompoundTag());saved.putString("id",ForgeRegistries.ITEMS.getKey(Items.ENCHANTED_BOOK).toString());saved.putByte("Count",(byte)1);result=ItemStack.of(saved);}
        String key=result.is(Items.ENCHANTED_BOOK)?"StoredEnchantments":"Enchantments";var entries=result.getOrCreateTag().getList(key,Tag.TAG_COMPOUND).copy();boolean updated=false;
        for(var raw:entries){var entry=(CompoundTag)raw;if(entry.getString("id").equals(offer.enchantment.toString())){entry.putShort("lvl",(short)offer.level);updated=true;}}
        if(!updated){var entry=new CompoundTag();entry.putString("id",offer.enchantment.toString());entry.putShort("lvl",(short)offer.level);entries.add(entry);}result.getOrCreateTag().put(key,entries);
        if(!book(result)){
            var enchantment=ForgeRegistries.ENCHANTMENTS.getValue(offer.enchantment());
            if(enchantment==null||result.getEnchantmentLevel(enchantment)<offer.level()||result.getAllEnchantments().getOrDefault(enchantment,0)<offer.level())
                throw new IllegalArgumentException("Item does not expose the written enchantment through Forge");
        }
        return result;
    }
    private EnchantmentPolicy(){}
}
