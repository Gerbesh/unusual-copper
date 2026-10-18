package io.github.gerbesh.unusualcopper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;

/** Only the isolated enchanter fixture profile registers these. Never packaged in the mod. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class EnchanterCompatFixtures {
    public static ResourceLocation id(String name){return new ResourceLocation("uc_enchanter_fixture",name);}
    @SubscribeEvent public static void register(RegisterEvent event){
        if(!Boolean.getBoolean("unusualcopper.enchanterCompatFixture"))return;
        event.register(ForgeRegistries.Keys.ITEMS,h->{h.register(id("gloves"),new Gloves(false));h.register(id("sealed_gloves"),new Gloves(true));h.register(id("opaque_gloves"),new Gloves(false){
            @Override public int getEnchantmentLevel(ItemStack stack,Enchantment enchantment){return 0;}
            @Override public Map<Enchantment,Integer> getAllEnchantments(ItemStack stack){return Map.of();}
        });});
        event.register(ForgeRegistries.Keys.ENCHANTMENTS,h->{
            h.register(id("reach"),new Reach());
            for(int i=0;i<300;i++)h.register(id("catalog_"+String.format(Locale.ROOT,"%03d",i)),new CatalogEntry());
        });
    }
    private static class Gloves extends Item {
        private final boolean bound;
        Gloves(boolean bound){super(new Properties().stacksTo(1));this.bound=bound;}
        @Override public boolean canApplyAtEnchantingTable(ItemStack stack,Enchantment enchantment){return enchantment instanceof CatalogEntry;}
        @Override public Map<Enchantment,Integer> getAllEnchantments(ItemStack stack){
            var result=new LinkedHashMap<>(super.getAllEnchantments(stack));if(bound)result.put(Enchantments.BINDING_CURSE,1);return result;
        }
    }
    private static class CatalogEntry extends Enchantment {
        CatalogEntry(){super(Rarity.COMMON,EnchantmentCategory.ARMOR,new EquipmentSlot[]{EquipmentSlot.CHEST});}
        @Override public int getMaxLevel(){return 1;}
    }
    private static final class Reach extends CatalogEntry {
        @Override public int getMaxLevel(){return 3;}
        @Override public boolean isTreasureOnly(){return true;}
        @Override protected boolean checkCompatibility(Enchantment other){return other!=Enchantments.BINDING_CURSE&&super.checkCompatibility(other);}
    }
    private EnchanterCompatFixtures(){}
}
