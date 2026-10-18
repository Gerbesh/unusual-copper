package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.reactor.*;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;

public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    private Button pause,mode,credit;
    public ReactorScreen(ReactorMenu menu,Inventory player,Component title){
        super(menu,player,title);imageWidth=320;imageHeight=232;inventoryLabelY=139;inventoryLabelX=79;
    }
    @Override protected void init(){
        super.init();
        pause=addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+10,topPos+118,90,18).build());
        mode=addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1)).bounds(leftPos+104,topPos+118,142,18).tooltip(Tooltip.create(Component.translatable("reactor.mode_hint"))).build());
        credit=addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,2)).bounds(leftPos+250,topPos+118,60,18).tooltip(Tooltip.create(Component.translatable("reactor.credit_hint"))).build());
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xffa96f49);g.fill(x+4,y+4,x+imageWidth-4,y+imageHeight-4,0xff203d35);g.fill(x+4,y+4,x+imageWidth-4,y+20,0xffbb8055);
        for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff102721);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff526a61);}
        Item[] hints={Items.COPPER_INGOT,ModContent.REACTOR_LENS.get(),ModContent.REACTOR_LENS.get(),ModContent.REACTOR_LENS.get(),ModContent.REACTOR_LENS.get(),ModContent.REACTOR_FILTER.get(),ModContent.REACTOR_INSULATION.get(),ReactorFluids.BUCKET.get(),Items.BUCKET};
        for(int i=0;i<9;i++){var slot=menu.slots.get(i);if(slot.hasItem())continue;g.renderFakeItem(new ItemStack(hints[i]),x+slot.x,y+slot.y);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xa0203d35);}
        bar(g,x+10,y+80,300,3,menu.energy(),ReactorEngine.CAPACITY,0xff3dd6d3);
        bar(g,x+10,y+99,144,3,menu.data.get(5),16000,0xff6cdae4);bar(g,x+166,y+99,144,3,menu.data.get(6),16000,0xfff39c42);
        bar(g,x+10,y+113,300,2,menu.data.get(4),10000,menu.data.get(4)>=8000?0xffff8360:0xffdca55f);
    }
    private static void bar(GuiGraphics g,int x,int y,int w,int h,int value,int max,int color){g.fill(x,y,x+w,y+h,0xff102822);g.fill(x,y,x+(int)((long)w*Math.max(0,Math.min(max,value))/max),y+h,color);}
    private Component status(){return Component.translatable("reactor.status."+ReactorBlockEntity.Status.values()[Math.floorMod(menu.data.get(8),ReactorBlockEntity.Status.values().length)].name().toLowerCase(java.util.Locale.ROOT));}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,10,7,0xff211c16,false);
        g.drawString(font,font.plainSubstrByWidth(status().getString(),300),10,25,menu.data.get(8)==ReactorBlockEntity.Status.WORKING.ordinal()?0xff91f2d3:0xffffcd91,false);
        g.drawString(font,Component.translatable("reactor.slots"),10,37,0xffb8d6c8,false);
        g.drawString(font,Component.translatable("reactor.energy",CopperBatteryItem.amount(menu.energy()),menu.data.get(9),menu.data.get(10)),10,69,0xffdceadc,false);
        g.drawString(font,Component.translatable("reactor.coolant",menu.data.get(5)),10,88,0xffa9e9ed,false);
        g.drawString(font,Component.translatable("reactor.lava",menu.data.get(6)),166,88,0xffffc28b,false);
        g.drawString(font,Component.translatable("reactor.heat",20+menu.data.get(4)/10),10,104,0xffe1ddc4,false);
        g.drawString(font,Component.translatable("reactor.yield"),170,104,0xffc1dcc7,false);
        g.drawString(font,playerInventoryTitle,79,139,0xffc1dcc7,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        renderBackground(g);var fuel=menu.slots.get(0).getItem();credit.active=menu.data.get(12)>0&&menu.data.get(11)!=0&&menu.data.get(4)<1000&&(fuel.isEmpty()||fuel.is(Items.COPPER_INGOT)&&!fuel.hasTag()&&fuel.getCount()+menu.data.get(12)<=64);
        credit.setMessage(Component.translatable("reactor.credit",menu.data.get(12)));
        pause.setMessage(Component.translatable(menu.data.get(11)==0?"reactor.stop":"reactor.start"));mode.setMessage(Component.translatable("reactor.mode",ReactorEngine.RATES[Math.floorMod(menu.data.get(7),3)]));
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(x>=leftPos+10&&x<leftPos+310&&y>=topPos+24&&y<topPos+34)g.renderTooltip(font,status(),x,y);
        for(int i=0;i<9;i++){var slot=menu.slots.get(i);if(!slot.hasItem()&&x>=leftPos+slot.x&&x<leftPos+slot.x+16&&y>=topPos+slot.y&&y<topPos+slot.y+16)g.renderTooltip(font,Component.translatable("reactor.slot."+i),x,y);}
    }
}
