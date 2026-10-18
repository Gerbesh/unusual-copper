package io.github.gerbesh.unusualcopper.reactor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class ReactorComponentItem extends Item {
    public ReactorComponentItem(int life){super(new Properties().durability(life));}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("reactor.life",Math.max(0,stack.getMaxDamage()-stack.getDamageValue())));}
}
