package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.fluid.tank.TankView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import java.util.*;

/** Reads full-width public tank metadata, never the vanilla signed-short data slot. */
final class FluidTankPanel {
    private FluidTankPanel(){}
    private static Component text(String key,Object...args){return Component.translatable("tank.unusualcopper."+key,args);}
    private static String amount(int value){return String.format(Locale.ROOT,"%,d",value).replace(',',' ');}
    private static FluidStack fluid(TankView view){var fluid=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new ResourceLocation(view.fluid()));return fluid==null?FluidStack.EMPTY:new FluidStack(fluid,Math.max(1,view.amount()));}
    private static Component status(TankView view){return text(view.state()==TankView.State.PROTECTED?"protected":view.state()==TankView.State.LIMIT?"limit":view.legacyOverflow()?"legacy":"ports_hint");}
    static void labels(GuiGraphics g,Font font,TankView view){
        view=FluidTankRenderer.summary(view);
        var fluid=fluid(view);var bucketFluid=fluid.copy();if(!bucketFluid.isEmpty())bucketFluid.setAmount(1000);
        var icon=fluid.isEmpty()?new ItemStack(Items.BUCKET):FluidUtil.getFilledBucket(bucketFluid);
        if(icon.isEmpty())icon=new ItemStack(Items.GLASS_BOTTLE);g.renderItem(icon,13,25);
        var name=fluid.isEmpty()?text("empty"):fluid.getDisplayName();g.drawString(font,font.plainSubstrByWidth(name.getString(),174),35,29,0xffc9eee1,false);
        g.drawString(font,text("amount",amount(view.amount()),amount(view.nominalCapacity())),13,46,0xffd8e9dc,false);
        g.fill(13,59,211,67,0xff142d2b);int width=(int)Math.min(198,198L*view.amount()/view.nominalCapacity());
        g.fill(13,59,13+width,67,view.legacyOverflow()?0xffe6aa65:0xff55d8c4);
        g.drawString(font,text("blocks",view.blocks()),13,73,0xffa6cfbf,false);
        var lines=font.split(status(view),198);for(int row=0;row<Math.min(2,lines.size());row++)g.drawString(font,lines.get(row),13,87+row*10,view.legacyOverflow()||view.state()!=TankView.State.READY?0xffffc174:0xffc7d9cd,false);
    }
    static void tooltip(GuiGraphics g,Font font,TankView view,int x,int y){
        view=FluidTankRenderer.summary(view);
        var fluid=fluid(view);var entries=new ArrayList<Component>();entries.add(fluid.isEmpty()?text("empty"):fluid.getDisplayName());
        entries.add(text("amount",amount(view.amount()),amount(view.nominalCapacity())));entries.add(status(view));entries.add(text("item_hint",256));
        g.renderTooltip(font,entries,Optional.empty(),x,y);
    }
}
