package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.gameplay.PotionPlans;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.menu.BrewerMenu;
import io.github.gerbesh.unusualcopper.network.BrewerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.util.*;

public final class BrewerScreen extends AbstractContainerScreen<BrewerMenu> {
    private EditBox search,count,priority;private Button mode,apply,cancel;private boolean initialized,maintain;
    private BrewerNetwork.Option selected;private int chainOffset,needOffset,seenSerial;
    public BrewerScreen(BrewerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=360;imageHeight=236;}
    private Button button(Component label,int x,int y,int width,Runnable click){return addRenderableWidget(Button.builder(label,b->click.run()).bounds(leftPos+x,topPos+y,width,18).build());}
    @Override protected void init(){
        super.init();initialized=false;
        button(Component.literal("<"),12,27,22,()->choose(-1));button(Component.literal(">"),326,27,22,()->choose(1));
        search=new EditBox(font,leftPos+12,topPos+61,336,18,Component.translatable("brewer.unusualcopper.search"));search.setMaxLength(128);search.setHint(Component.translatable("brewer.unusualcopper.search"));search.setResponder(text->{var list=filtered();if(!list.contains(selected))selected=list.isEmpty()?null:list.get(0);chainOffset=0;});addRenderableWidget(search);
        count=new EditBox(font,leftPos+69,topPos+84,51,18,Component.translatable("brewer.unusualcopper.count"));count.setMaxLength(4);addRenderableWidget(count);
        priority=new EditBox(font,leftPos+323,topPos+84,25,18,Component.translatable("brewer.unusualcopper.priority"));priority.setMaxLength(1);addRenderableWidget(priority);
        mode=button(Component.empty(),128,83,117,()->maintain=!maintain);
        apply=button(Component.translatable("brewer.unusualcopper.apply"),12,106,108,()->{
            if(selected==null||menu.snapshot==null)return;
            try{int amount=Integer.parseInt(count.getValue()),rank=Integer.parseInt(priority.getValue());if(amount<1||amount>4096||rank<0||rank>9)return;BrewerNetwork.configure(menu.containerId,menu.snapshot.runtime(),new BrewerNetwork.Settings(selected.plan().potion(),selected.plan().vessel(),amount,maintain,rank));}catch(NumberFormatException ignored){}
        });
        button(Component.translatable("brewer.unusualcopper.back"),128,106,108,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
        cancel=button(Component.translatable("brewer.unusualcopper.cancel"),244,106,104,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1));
        button(Component.literal("^"),208,165,14,()->chainOffset=Math.max(0,chainOffset-1));button(Component.literal("v"),208,187,14,()->{if(selected!=null)chainOffset=Math.min(Math.max(0,shown().plan().steps().size()-2),chainOffset+1);});
        button(Component.literal("^"),336,165,12,()->needOffset=Math.max(0,needOffset-1));button(Component.literal("v"),336,187,12,()->{if(menu.snapshot!=null)needOffset=Math.min(Math.max(0,menu.snapshot.needs().size()-3),needOffset+1);});
    }
    private List<BrewerNetwork.Option> filtered(){String text=search==null?"":search.getValue().toLowerCase(Locale.ROOT).trim();return menu.options.stream().filter(o->text.isEmpty()||(o.plan().potion()+" "+o.plan().output().getHoverName().getString()).toLowerCase(Locale.ROOT).contains(text)).toList();}
    private BrewerNetwork.Option shown(){var active=menu.snapshot==null?null:menu.snapshot.active();return active!=null&&selected!=null&&active.plan().potion().equals(selected.plan().potion())&&active.plan().vessel()==selected.plan().vessel()?active:selected;}
    private void choose(int direction){var list=filtered();if(list.isEmpty()){selected=null;return;}int index=list.indexOf(selected);selected=list.get(Math.floorMod(index+direction,list.size()));chainOffset=0;}
    private void receive(){if(menu.snapshot==null||menu.options.isEmpty())return;boolean acknowledged=seenSerial!=menu.serial&&menu.snapshot.result()==1;seenSerial=menu.serial;if(initialized&&!acknowledged)return;var s=menu.snapshot.settings();selected=menu.options.stream().filter(o->o.plan().potion().equals(s.potion())&&o.plan().vessel()==s.vessel()).findFirst().orElse(menu.options.get(0));count.setValue(Integer.toString(Math.max(1,s.count())));priority.setValue(Integer.toString(s.priority()));maintain=s.maintain();initialized=true;}
    private void text(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff243a36);g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xffaa724d);g.fill(leftPos+7,topPos+21,leftPos+imageWidth-7,topPos+imageHeight-7,0xff243a36);}
    private ItemStack chain(int index){if(selected==null)return ItemStack.EMPTY;return index==0?new ItemStack(Items.GLASS_BOTTLE):shown().plan().steps().get(index-1).ingredient();}
    private ItemStack product(int index){return index==0?PotionPlans.water():shown().plan().steps().get(index-1).output();}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        text(g,title,9,7,340,0xff201b15);
        if(selected!=null){g.renderItem(selected.plan().output(),43,28);text(g,selected.plan().output().getHoverName(),64,32,254,0xffe6f5ee);text(g,Component.literal(selected.plan().potion().toString()),43,49,300,0xff9ad8cf);}
        text(g,Component.translatable("brewer.unusualcopper.count"),12,89,54,0xffe6f5ee);text(g,Component.translatable("brewer.unusualcopper.priority"),252,89,70,0xffe6f5ee);
        if(selected!=null)text(g,Component.translatable("brewer.unusualcopper.price",shown().costEach(),menu.snapshot==null?0:menu.snapshot.energy()),12,130,336,0xffe6f5ee);
        if(menu.snapshot!=null){var s=menu.snapshot;text(g,Component.translatable(MachineStatus.values()[Math.floorMod(s.status(),MachineStatus.values().length)].translationKey()),12,141,226,0xffb8efdf);if(s.result()>0)text(g,Component.translatable("brewer.unusualcopper.result."+s.result()),242,141,106,s.result()==1?0xff9cf0c6:0xffffbc74);}
        text(g,Component.translatable("brewer.unusualcopper.chain"),12,155,194,0xffe6f5ee);text(g,Component.translatable("brewer.unusualcopper.reagents"),230,155,106,0xffe6f5ee);
        if(selected!=null)for(int row=0;row<3;row++){int index=chainOffset+row;if(index>shown().plan().steps().size())break;int y=168+row*17;g.renderItem(chain(index),12,y);text(g,Component.literal("→ ").append(product(index).getHoverName()),32,y+4,173,0xffe6f5ee);}
        if(menu.snapshot!=null){var s=menu.snapshot;needOffset=Math.min(needOffset,Math.max(0,s.needs().size()-3));for(int row=0;row<3&&needOffset+row<s.needs().size();row++){var n=s.needs().get(needOffset+row);int y=168+row*17;g.renderItem(n.stack(),230,y);text(g,n.stack().getHoverName(),250,y,84,0xffe6f5ee);text(g,Component.literal(n.available()+" / "+n.required()+(s.sourceComplete()?"":" ?")),250,y+9,84,n.available()>=n.required()?0xff9cf0c6:0xffffbc74);}text(g,Component.translatable("brewer.unusualcopper.progress",s.settings().count(),s.stage(),s.stages(),s.batch()),12,221,336,0xffc4e2d9);}
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(x>=leftPos+12&&x<leftPos+222&&y>=topPos+153&&y<topPos+219&&selected!=null){chainOffset=Math.max(0,Math.min(Math.max(0,shown().plan().steps().size()-2),chainOffset-(int)Math.signum(delta)));return true;}if(x>=leftPos+230&&x<leftPos+348&&y>=topPos+153&&y<topPos+219&&menu.snapshot!=null){needOffset=Math.max(0,Math.min(Math.max(0,menu.snapshot.needs().size()-3),needOffset-(int)Math.signum(delta)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        receive();mode.setMessage(Component.translatable("brewer.unusualcopper.mode."+(maintain?1:0)));apply.active=selected!=null&&menu.snapshot!=null&&!menu.snapshot.job();cancel.active=menu.snapshot!=null&&(menu.snapshot.job()||menu.snapshot.settings().count()>0);
        renderBackground(g);super.render(g,mx,my,partial);int x=mx-leftPos,y=my-topPos;
        if(selected!=null&&x>=42&&x<324&&y>=27&&y<59)g.renderTooltip(font,selected.plan().output(),mx,my);
        if(selected!=null&&x>=12&&x<208&&y>=168&&y<219){int index=chainOffset+(y-168)/17;if(index<=shown().plan().steps().size())g.renderTooltip(font,x<32?chain(index):product(index),mx,my);}
        if(menu.snapshot!=null&&x>=230&&x<336&&y>=168&&y<219){int index=needOffset+(y-168)/17;if(index<menu.snapshot.needs().size())g.renderTooltip(font,menu.snapshot.needs().get(index).stack(),mx,my);}
    }
}
