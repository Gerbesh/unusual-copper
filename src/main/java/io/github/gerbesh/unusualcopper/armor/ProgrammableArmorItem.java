package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import java.util.List;

/** Additive armor IDs: does not replace vanilla armor or persisted CuE batteries. */
public final class ProgrammableArmorItem extends ArmorItem {
    private static ArmorMaterial material(int tier){return new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) {
            return (switch(type){case HELMET->165;case CHESTPLATE->240;case LEGGINGS->225;case BOOTS->195;}) * tier;
        }
        @Override public int getDefenseForType(Type type) {
            return (switch(type){case HELMET->3;case CHESTPLATE->7;case LEGGINGS->6;case BOOTS->3;}) + tier - 1;
        }
        @Override public int getEnchantmentValue(){return 12;}
        @Override public SoundEvent getEquipSound(){return SoundEvents.ARMOR_EQUIP_IRON;}
        @Override public Ingredient getRepairIngredient(){return Ingredient.of(Items.COPPER_INGOT);}
        @Override public String getName(){return "unusualcopper:copper_laminate";}
        @Override public float getToughness(){return tier;}
        @Override public float getKnockbackResistance(){return 0;}
    };}
    public final int tier;
    public ProgrammableArmorItem(Type type,int tier){
        super(material(tier),type,new Properties());
        if(tier<1||tier>3)throw new IllegalArgumentException("Armor tier");
        this.tier=tier;
    }
    @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type) {
        return "unusualcopper:textures/models/armor/copper_laminate_tier_"+tier+"_layer_"+(slot==EquipmentSlot.LEGS?"2":"1")+".png";
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("armor.unusualcopper.sockets",ArmorData.modules(stack).size(),ArmorData.slots(this)));
        tooltip.add(Component.translatable("armorconsole.open_hint"));
        if(getType()==Type.CHESTPLATE) {
            var battery=ArmorData.battery(stack);
            tooltip.add(Component.translatable("armor.unusualcopper.battery",
                    io.github.gerbesh.unusualcopper.item.CopperBatteryItem.amount(
                        io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(battery))));
            tooltip.add(Component.translatable("armor.unusualcopper.profile."+ArmorData.profile(stack)));
        }
    }
}
