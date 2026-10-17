package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public final class ArmorModuleItem extends Item {
    public final ArmorModuleType module;
    public ArmorModuleItem(ArmorModuleType module) {
        super(new Properties().stacksTo(16));
        this.module=module;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("armor.unusualcopper.module_cost",module.cue));
        if(module==ArmorModuleType.FALL_DAMPER)tooltip.add(Component.translatable("armor.unusualcopper.event_cost",1000));
        if(module==ArmorModuleType.JUMP_DRIVE)tooltip.add(Component.translatable("armor.unusualcopper.event_cost",2000));
        if(module==ArmorModuleType.HOVER_DRIVE)tooltip.add(Component.translatable("armor.unusualcopper.hover_hint"));
        if(module==ArmorModuleType.FLIGHT_CORE_II)tooltip.add(Component.translatable("armor.unusualcopper.flight_ii_hint"));
        if(module==ArmorModuleType.SHIELD_FIELD)tooltip.add(Component.translatable("armor.unusualcopper.shield_event",300));
    }
}
