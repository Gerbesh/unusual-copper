package io.github.gerbesh.unusualcopper.fluid.tank;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class FluidTankBlockItem extends BlockItem {
    public FluidTankBlockItem(Block block){super(block,new Item.Properties());}
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null)renderer=new io.github.gerbesh.unusualcopper.client.FluidTankItemRenderer();return renderer;
            }
        });
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,level,tooltip,flag);tooltip.add(Component.translatable("tank.unusualcopper.item_hint",TankTopology.MAX_BLOCKS));
        var fluid=TankItemStorage.contents(stack);
        if(!fluid.isEmpty())tooltip.add(Component.translatable("tank.unusualcopper.item_contents",fluid.getDisplayName(),String.format(java.util.Locale.ROOT,"%,d",fluid.getAmount()).replace(',',' ')));
        if(fluid.getAmount()>TankVolumes.PER_BLOCK)tooltip.add(Component.translatable("tank.unusualcopper.legacy"));
    }
}
