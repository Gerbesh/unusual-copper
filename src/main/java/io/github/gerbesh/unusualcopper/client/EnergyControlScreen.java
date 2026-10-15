package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.EnergyControlMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

public final class EnergyControlScreen extends AbstractContainerScreen<EnergyControlMenu> {
    private Button priority,redstone;
    private final Button[][] branches=new Button[6][3];
    public EnergyControlScreen(EnergyControlMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=226;}
    private Button button(int id,int x,int y,int width){return addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,width,17).build());}
    @Override protected void init(){super.init();priority=button(0,196,22,96);priority.setTooltip(Tooltip.create(Component.translatable("energy.unusualcopper.priority_hint")));redstone=button(1,8,22,180);for(int i=0;i<6;i++){branches[i][0]=button(10+i,8,112+i*18,98);branches[i][1]=button(20+i,110,112+i*18,78);branches[i][2]=button(30+i,196,112+i*18,96);branches[i][2].setTooltip(Tooltip.create(Component.translatable("energy.unusualcopper.branch_priority_hint")));}update();}
    private void update(){
        var v=menu.view;var kind=v==null?null:MachineKind.values()[Math.floorMod(v.kind(),MachineKind.values().length)];
        priority.visible=kind!=null&&kind.consumes();redstone.visible=kind!=null&&kind.capacity>0;
        if(v!=null){priority.setMessage(Component.translatable("energy.unusualcopper.priority."+v.priority()));redstone.setMessage(Component.translatable("gui.unusualcopper.redstone_mode."+v.redstone()));}
        for(int i=0;i<6;i++)for(int j=0;j<3;j++)branches[i][j].visible=kind==MachineKind.ENERGY_DISTRIBUTOR;
        if(kind==MachineKind.ENERGY_DISTRIBUTOR)for(int i=0;i<6;i++){
            var side=net.minecraft.core.Direction.values()[i];var mode=SideMode.values()[Math.floorMod(v.modes()[i],SideMode.values().length)];
            branches[i][0].setMessage(Component.translatable("direction.unusualcopper."+side.getName()).append(": ").append(Component.translatable("energy.unusualcopper.mode."+mode.name().toLowerCase(Locale.ROOT))));
            branches[i][1].setMessage(Component.literal(Integer.toString(v.limits()[i])));branches[i][1].setTooltip(Tooltip.create(Component.translatable("energy.unusualcopper.limit_hint")));
            branches[i][2].setMessage(Component.translatable("energy.unusualcopper.priority."+v.priorities()[i]));
        }
    }
    private static String compact(long value){if(value>=1_000_000_000)return String.format(Locale.ROOT,"%.2fG",value/1e9);if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fM",value/1e6);if(value>=10_000)return String.format(Locale.ROOT,"%.1fk",value/1e3);return Long.toString(value);}
    private String rate(long amount,int ticks){return ticks==0?"—":String.format(Locale.ROOT,"%.1f",amount/(double)ticks);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffaa724d);g.fill(leftPos+3,topPos+19,leftPos+imageWidth-3,topPos+imageHeight-3,0xff243a36);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,8,7,0xff201b15,false);var v=menu.view;if(v==null)return;
        g.drawString(font,Component.translatable("energy.unusualcopper.buffer",compact(v.stored()),compact(v.capacity())),8,46,0xffe6f5ee,false);
        g.drawString(font,Component.translatable("energy.unusualcopper.nodes",v.nodes(),v.devices()),8,58,0xffb8efdf,false);
        g.drawString(font,Component.translatable("energy.unusualcopper.produced",rate(v.produced(),v.ticks())),8,72,0xff82f1e7,false);
        g.drawString(font,Component.translatable("energy.unusualcopper.consumed",rate(v.consumed(),v.ticks())),154,72,0xffe6f5ee,false);
        g.drawString(font,Component.translatable("energy.unusualcopper.transferred",rate(v.transferred(),v.ticks())),8,85,0xff82f1e7,false);
        g.drawString(font,Component.translatable("energy.unusualcopper.deficit",rate(v.deficit(),v.ticks())),154,85,0xffffd591,false);
        g.drawString(font,Component.translatable("energy.unusualcopper.units"),8,99,0xffb8efdf,false);
        if(MachineKind.values()[Math.floorMod(v.kind(),MachineKind.values().length)]!=MachineKind.ENERGY_DISTRIBUTOR){
            g.drawWordWrap(font,Component.translatable("energy.unusualcopper.deficit_hint"),8,119,284,0xffe6f5ee);
            g.drawWordWrap(font,Component.translatable("energy.unusualcopper.open_hint"),8,171,284,0xffb8efdf);
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){update();renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);if(menu.view!=null&&mx>=leftPos+8&&mx<=leftPos+290&&my>=topPos+44&&my<=topPos+56)g.renderTooltip(font,Component.literal(menu.view.stored()+" / "+menu.view.capacity()+" CuE"),mx,my);}
}
