package io.github.gerbesh.unusualcopper.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public final class SolarLensItem extends Item {
    public SolarLensItem() { super(new Properties().durability(256 * 64)); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
        text.add(Component.translatable("tooltip.unusualcopper.lens", stack.getMaxDamage() - stack.getDamageValue()));
    }
}
