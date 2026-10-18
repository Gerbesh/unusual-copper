package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public final class ArmorModuleItem extends Item {
    public final ArmorModuleType module;
    public final int moduleLevel;
    public ArmorModuleItem(ArmorModuleType module) {this(module,1);}
    public ArmorModuleItem(ArmorModuleType module,int moduleLevel) {
        super(new Properties().stacksTo(16));
        this.module=module;
        if(moduleLevel<1||moduleLevel>module.maxLevel())throw new IllegalArgumentException("Module level");this.moduleLevel=moduleLevel;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        if(module.maxLevel()>1)tooltip.add(Component.translatable("armor.unusualcopper.module_level",moduleLevel));
        tooltip.add(Component.translatable("armor.unusualcopper.module_chassis",module.requiredTier(moduleLevel)));
        tooltip.add(Component.translatable("armor.unusualcopper.module_cost",module.cue(moduleLevel)));
        if(module==ArmorModuleType.OPTIC_VISOR)tooltip.add(Component.translatable("armor.unusualcopper.visor_hint"));
        if(module==ArmorModuleType.FIELD_SCANNER)tooltip.add(Component.translatable("armor.unusualcopper.scanner_hint"));
        if(module==ArmorModuleType.FALL_DAMPER)tooltip.add(Component.translatable("armor.unusualcopper.event_cost",1000));
        if(module==ArmorModuleType.JUMP_DRIVE)tooltip.add(Component.translatable("armor.unusualcopper.event_cost",moduleLevel==1?2000:5000));
        if(module==ArmorModuleType.HOVER_DRIVE)tooltip.add(Component.translatable("armor.unusualcopper.hover_hint"));
        if(module==ArmorModuleType.FLIGHT_CORE_II)tooltip.add(Component.translatable("armor.unusualcopper.flight_ii_hint"));
        if(module==ArmorModuleType.SHIELD_FIELD)tooltip.add(Component.translatable("armor.unusualcopper.shield_event",moduleLevel==1?300:moduleLevel==2?600:1200));
    }
}
