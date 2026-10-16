package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.menu.RepairMenu;
import io.github.gerbesh.unusualcopper.network.RepairNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.util.Locale;

public final class RepairScreen extends AbstractContainerScreen<RepairMenu> {
    private Button confirm,small,full;
    public RepairScreen(RepairMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=330;imageHeight=234;}
    private Component tr(String key,Object...args){return Component.translatable("repair.unusualcopper."+key,args);}
    private Button button(Component text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(text,b->action.run()).bounds(leftPos+x,topPos+y,w,18).build());}
    @Override protected void init(){super.init();small=button(tr("small"),74,27,112,()->select(128));full=button(tr("full"),194,27,122,()->select(4096));full.setTooltip(Tooltip.create(tr("full_hint")));confirm=button(tr("confirm"),174,210,142,()->{var s=menu.snapshot;if(s!=null&&s.quote()!=null)RepairNetwork.confirm(menu.containerId,s.runtime(),s.quote().nonce());});button(Component.literal("R"),12,210,22,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).setTooltip(Tooltip.create(tr("redstone")));button(Component.literal("CuE"),38,210,36,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1));}
    private void select(int n){if(menu.snapshot!=null)RepairNetwork.select(menu.containerId,menu.snapshot.runtime(),n);}
    private void text(GuiGraphics g,Component s,int x,int y,int w,int color){g.drawString(font,font.plainSubstrByWidth(s.getString(),w),x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float p,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffaa724d);g.fill(leftPos+5,topPos+20,leftPos+imageWidth-5,topPos+121,0xff243a36);for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff1d2825);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff52625c);}}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){text(g,title,9,7,312,0xff201b15);text(g,tr("item"),12,27,57,0xffe6f5ee);text(g,tr("preview"),12,69,57,0xffb8efdf);var s=menu.snapshot;if(s==null)return;var q=s.quote();
        if(q!=null){g.renderItem(q.preview(),26,83);text(g,tr("restored",q.price().points()),74,52,242,0xff9cf0c6);g.renderItem(new ItemStack(Items.EXPERIENCE_BOTTLE),74,67);text(g,tr("xp_price",q.price().xp()),94,71,214,0xffe6f5ee);text(g,Component.literal(q.price().cue()+" CuE"),74,89,242,0xffe6f5ee);}else{var item=menu.slots.get(0).getItem();text(g,tr(item.isEmpty()?"empty":item.getDamageValue()==0?"whole":"unsupported"),74,61,242,0xffb8efdf);}
        text(g,tr("wallet",s.xp(),s.energy()),12,109,304,0xffe6f5ee);text(g,tr("result."+s.result().name().toLowerCase(Locale.ROOT)),82,215,88,0xff201b15);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float p){var s=menu.snapshot;var q=s==null?null:s.quote();small.active=full.active=s!=null&&!menu.slots.get(0).getItem().isEmpty();confirm.active=q!=null&&q.remaining()>0&&s.enabled()&&s.energy()>=q.price().cue()&&s.xp()>=q.price().xp();if(q!=null)confirm.setTooltip(Tooltip.create(tr("confirm_hint",q.price().points(),q.price().cue(),q.price().xp())));renderBackground(g);super.render(g,mx,my,p);renderTooltip(g,mx,my);if(q!=null&&mx>=leftPos+24&&mx<leftPos+44&&my>=topPos+81&&my<topPos+101)g.renderTooltip(font,q.preview(),mx,my);}
}
