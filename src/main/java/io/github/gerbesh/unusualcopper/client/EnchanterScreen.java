package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy.Offer;
import io.github.gerbesh.unusualcopper.menu.EnchanterMenu;
import io.github.gerbesh.unusualcopper.network.EnchanterNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public class EnchanterScreen extends AbstractContainerScreen<EnchanterMenu> {
    private EditBox search;private final Button[] choices=new Button[3];private Button confirm,redstone;private int offset;
    public EnchanterScreen(EnchanterMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=340;imageHeight=234;}
    private Component name(Offer offer){var enchantment=ForgeRegistries.ENCHANTMENTS.getValue(offer.enchantment());return enchantment==null?Component.literal(offer.enchantment().toString()):enchantment.getFullname(offer.level());}
    private List<Offer> filtered(){if(menu.snapshot==null)return List.of();String term=search==null?"":search.getValue().toLowerCase(Locale.ROOT).trim();return menu.snapshot.offers().stream().filter(o->term.isEmpty()||(o.enchantment()+" "+name(o).getString()).toLowerCase(Locale.ROOT).contains(term)).toList();}
    @Override protected void init(){
        super.init();search=new EditBox(font,leftPos+66,topPos+26,260,18,Component.translatable("enchanter.unusualcopper.search"));search.setMaxLength(128);search.setHint(Component.translatable("enchanter.unusualcopper.search"));search.setResponder(value->offset=0);addRenderableWidget(search);
        for(int row=0;row<3;row++){final int index=row;choices[row]=addRenderableWidget(Button.builder(Component.empty(),b->{var list=filtered();if(menu.snapshot!=null&&offset+index<list.size())EnchanterNetwork.select(menu.containerId,menu.snapshot.runtime(),list.get(offset+index).enchantment());}).bounds(leftPos+66,topPos+47+17*row,244,16).build());}
        addRenderableWidget(Button.builder(Component.literal("^"),b->offset=Math.max(0,offset-1)).bounds(leftPos+314,topPos+47,12,16).build());addRenderableWidget(Button.builder(Component.literal("v"),b->offset=Math.min(Math.max(0,filtered().size()-3),offset+1)).bounds(leftPos+314,topPos+81,12,16).build());
        addRenderableWidget(Button.builder(Component.literal("↻"),b->{if(menu.snapshot!=null)EnchanterNetwork.refresh(menu.containerId,menu.snapshot.runtime());}).bounds(leftPos+314,topPos+64,12,16).tooltip(Tooltip.create(Component.translatable("enchanter.unusualcopper.refresh"))).build());
        confirm=addRenderableWidget(Button.builder(Component.translatable("enchanter.unusualcopper.confirm"),b->{var state=menu.snapshot;if(state!=null&&state.quote()!=null)EnchanterNetwork.confirm(menu.containerId,state.runtime(),state.quote().nonce());}).bounds(leftPos+188,topPos+210,138,18).build());
        redstone=addRenderableWidget(Button.builder(Component.literal("R"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+10,topPos+210,20,18).tooltip(Tooltip.create(Component.translatable("enchanter.unusualcopper.redstone"))).build());
    }
    private void text(GuiGraphics g,Component value,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(value.getString(),width),x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff243a36);g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xffaa724d);g.fill(leftPos+7,topPos+19,leftPos+imageWidth-7,topPos+123,0xff243a36);for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff1d2825);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff52625c);}}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        text(g,title,9,7,244,0xff201b15);text(g,Component.translatable("enchanter.unusualcopper.item"),10,20,54,0xffe6f5ee);text(g,Component.translatable("enchanter.unusualcopper.lapis"),10,50,54,0xffe6f5ee);text(g,Component.translatable("enchanter.unusualcopper.preview"),10,81,54,0xffe6f5ee);
        var state=menu.snapshot;if(state==null)return;var quote=state.quote();
        String count=state.offers().size()+"/"+state.total();g.drawString(font,count,326-font.width(count),7,0xff243a36,false);
        if(quote!=null){g.renderItem(quote.preview(),18,92);g.drawString(font,"→",42,97,0xff82f1e7,false);text(g,Component.translatable("enchanter.unusualcopper.price",quote.offer().xp(),quote.offer().cue()),66,102,260,0xffe6f5ee);}else text(g,Component.translatable(state.offers().size()<state.total()?"enchanter.unusualcopper.loading":state.offers().isEmpty()?"enchanter.unusualcopper.no_offers":"enchanter.unusualcopper.choose"),66,102,260,0xffb8efdf);
        text(g,Component.translatable("enchanter.unusualcopper.wallet",state.xp(),state.energy(),state.lapis()),10,114,318,0xffe6f5ee);
        text(g,Component.translatable("enchanter.unusualcopper.result."+state.result().name().toLowerCase(Locale.ROOT)),36,215,148,state.result()==EnchanterNetwork.Result.APPLIED?0xff9cf0c6:0xffe6f5ee);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(x>=leftPos+66&&x<leftPos+326&&y>=topPos+45&&y<topPos+98){offset=Math.max(0,Math.min(Math.max(0,filtered().size()-3),offset-(int)Math.signum(delta)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        var list=filtered();offset=Math.min(offset,Math.max(0,list.size()-3));for(int i=0;i<3;i++){var b=choices[i];b.visible=offset+i<list.size();if(b.visible){var offer=list.get(offset+i);b.setMessage(name(offer));b.setTooltip(Tooltip.create(name(offer).copy().append("\n").append(offer.enchantment().toString()).append("\n").append(Component.translatable("enchanter.unusualcopper.price",offer.xp(),offer.cue()))));}}
        var state=menu.snapshot;var quote=state==null?null:state.quote();confirm.active=quote!=null&&quote.remaining()>0&&state.enabled()&&state.xp()>=quote.offer().xp()&&state.energy()>=quote.offer().cue()&&state.lapis()>=64;if(state!=null)redstone.setTooltip(Tooltip.create(Component.translatable("gui.unusualcopper.redstone_mode."+state.redstone())));if(quote!=null)confirm.setTooltip(Tooltip.create(Component.translatable("enchanter.unusualcopper.confirm_hint",(quote.remaining()+19)/20).append("\n").append(Component.translatable("gui.unusualcopper.redstone_mode."+state.redstone()))));
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);if(quote!=null&&mx>=leftPos+16&&mx<leftPos+36&&my>=topPos+90&&my<topPos+110)g.renderTooltip(font,quote.preview(),mx,my);
    }
}
