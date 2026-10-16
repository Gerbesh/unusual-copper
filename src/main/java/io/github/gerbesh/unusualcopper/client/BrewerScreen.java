package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.menu.BrewerMenu;
import io.github.gerbesh.unusualcopper.network.BrewerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** A read-only recipe browser until the player explicitly submits an order. */
public final class BrewerScreen extends AbstractContainerScreen<BrewerMenu> {
    private EditBox search,count,priority;private Button mode,apply,cancel;
    private boolean initialized,maintain;private int page,vessel=-1,seenSerial,ticks,lastInspect=-10;
    private BrewerNetwork.Option selected;
    private BrewerNetwork.Inspect sent;
    public BrewerScreen(BrewerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=410;imageHeight=236;}
    private Component tr(String key,Object...args){return Component.translatable("brewer.unusualcopper."+key,args);}
    private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(label,b->action.run()).bounds(leftPos+x,topPos+y,w,18).build());}
    private EditBox box(int x,int y,int w,int max,String key){var b=new EditBox(font,leftPos+x,topPos+y,w,18,tr(key));b.setMaxLength(max);addRenderableWidget(b);return b;}
    @Override protected void init(){
        super.init();initialized=false;sent=null;
        search=box(12,27,184,80,"search");search.setHint(tr("search"));search.setResponder(s->page=0);
        for(int i=0;i<4;i++){int filter=i-1;var b=button(tr("filter."+i),12+i*47,49,43,()->{vessel=filter;page=0;});b.setTooltip(Tooltip.create(tr("vessel."+i)));}
        button(Component.literal("<"),12,187,28,()->page=Math.max(0,page-1));button(Component.literal(">"),168,187,28,()->page=Math.min(pages()-1,page+1));
        button(tr("back"),12,214,88,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
        cancel=button(tr("cancel"),106,214,90,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1));
        button(Component.literal("−"),208,75,20,()->amount(amount()-1));count=box(232,75,42,4,"count");count.setFilter(s->s.matches("[0-9]{0,4}"));
        button(Component.literal("+"),278,75,20,()->amount(amount()+1));
        priority=box(363,75,35,1,"priority");priority.setFilter(s->s.matches("[0-9]?"));priority.setValue("0");
        int[] quick={1,16,64};for(int i=0;i<3;i++){int n=quick[i];button(Component.literal(""+n),208+i*31,97,28,()->amount(n));}
        mode=button(Component.empty(),208,119,92,()->maintain=!maintain);mode.setTooltip(Tooltip.create(tr("mode_hint")));
        apply=button(tr("apply"),304,119,94,this::order);
    }
    private int amount(){try{return Math.max(1,Math.min(4096,Integer.parseInt(count.getValue())));}catch(NumberFormatException bad){return 1;}}
    private void amount(int n){count.setValue(""+Math.max(1,Math.min(4096,n)));}
    private BrewerNetwork.Key key(BrewerNetwork.Option o){return new BrewerNetwork.Key(o.plan().potion(),o.plan().vessel());}
    private List<BrewerNetwork.Option> filtered(){String text=search.getValue().trim().toLowerCase(Locale.ROOT);return menu.options.stream().filter(o->(vessel<0||o.plan().vessel()==vessel)&&o.plan().output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(text)).toList();}
    private int pages(){return Math.max(1,(filtered().size()+11)/12);}
    private List<BrewerNetwork.Option> visible(){var list=filtered();page=Math.min(page,pages()-1);return list.subList(Math.min(page*12,list.size()),Math.min(page*12+12,list.size()));}
    private BrewerNetwork.Stock stock(BrewerNetwork.Option o){return menu.snapshot==null?null:menu.snapshot.stocks().stream().filter(s->s.key().equals(key(o))).findFirst().orElse(null);}
    private String badge(BrewerNetwork.Stock s){if(s==null||s.amount()<0)return "…";return (s.complete()?"":"≈")+(s.amount()>=1_000_000?s.amount()/1_000_000+"M":s.amount()>=10_000?s.amount()/1000+"k":""+s.amount());}
    private boolean previewReady(){return menu.snapshot!=null&&selected!=null&&menu.snapshot.preview().potion().equals(selected.plan().potion())&&menu.snapshot.preview().vessel()==selected.plan().vessel()&&menu.snapshot.preview().count()==amount();}
    private BrewerNetwork.Settings settings(){return new BrewerNetwork.Settings(selected.plan().potion(),selected.plan().vessel(),amount(),maintain,priority.getValue().isEmpty()?0:Integer.parseInt(priority.getValue()));}
    private void order(){if(selected!=null&&menu.snapshot!=null)BrewerNetwork.configure(menu.containerId,menu.snapshot.runtime(),settings());}
    private void receive(){if(menu.snapshot==null||menu.options.isEmpty())return;boolean ack=seenSerial!=menu.serial&&menu.snapshot.result()==1;seenSerial=menu.serial;if(initialized&&!ack)return;
        var s=menu.snapshot.settings();selected=menu.options.stream().filter(o->o.plan().potion().equals(s.potion())&&o.plan().vessel()==s.vessel()).findFirst().orElse(menu.options.get(0));amount(Math.max(1,s.count()));priority.setValue(""+s.priority());maintain=s.maintain();initialized=true;
    }
    @Override protected void containerTick(){super.containerTick();ticks++;receive();if(selected!=null&&menu.snapshot!=null&&ticks-lastInspect>=6){var request=new BrewerNetwork.Inspect(menu.containerId,menu.snapshot.runtime(),settings(),visible().stream().map(this::key).toList());if(!request.equals(sent)){BrewerNetwork.inspect(request);sent=request;lastInspect=ticks;}}}
    private void text(GuiGraphics g,Component s,int x,int y,int w,int color){g.drawString(font,font.plainSubstrByWidth(s.getString(),w),x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffaa724d);g.fill(leftPos+5,topPos+21,leftPos+imageWidth-5,topPos+imageHeight-5,0xff243a36);g.fill(leftPos+201,topPos+24,leftPos+203,topPos+231,0xffaa724d);int x=leftPos+12+(vessel+1)*47;g.fill(x,topPos+68,x+43,topPos+70,0xff9cf0c6);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        text(g,title,9,7,392,0xff201b15);var list=visible();
        for(int i=0;i<list.size();i++){var o=list.get(i);int x=12+(i%4)*47,y=72+(i/4)*37;boolean on=selected!=null&&key(o).equals(key(selected));g.fill(x,y,x+43,y+34,on?0xff4a7d6c:0xff182c29);g.renderItem(o.plan().output(),x+13,y+2);String badge=badge(stock(o));g.drawString(font,badge,x+(43-font.width(badge))/2,y+22,0xffb8efdf,false);}
        text(g,Component.literal((page+1)+" / "+pages()),71,192,90,0xffe6f5ee);
        if(menu.snapshot!=null)text(g,tr(menu.snapshot.linked()?"stock_linked":"stock_local"),12,205,184,0xffb8efdf);
        if(selected==null)return;
        g.renderItem(selected.plan().output(),208,27);text(g,selected.plan().output().getHoverName(),230,30,168,0xffe6f5ee);
        var stock=stock(selected);text(g,tr("stock",stock==null||stock.amount()<0?"…":(stock.complete()?"":"≈")+stock.amount()),208,49,190,0xff9cf0c6);
        text(g,tr("count"),208,64,95,0xffe6f5ee);text(g,tr("priority"),310,64,88,0xffe6f5ee);
        text(g,Component.literal(selected.costEach()+" CuE × "+amount()),306,102,92,0xffb8efdf);
        text(g,tr("batch_reagents"),208,143,190,0xffe6f5ee);
        if(previewReady())for(int i=0;i<menu.snapshot.needs().size();i++){var n=menu.snapshot.needs().get(i);int x=208+i%5*38,y=156+i/5*26;g.renderItem(n.stack(),x,y);String value=(n.available()<0?"…":n.available()>999?"999+":""+n.available())+"/"+n.required();text(g,Component.literal(value),x,y+16,37,n.available()>=n.required()?0xff9cf0c6:0xffffbc74);}
        else text(g,tr("reading"),208,161,190,0xffb8efdf);
        if(menu.snapshot!=null){var s=menu.snapshot;text(g,Component.translatable(MachineStatus.values()[Math.floorMod(s.status(),MachineStatus.values().length)].translationKey()),208,211,190,0xffb8efdf);
            text(g,tr("job",s.settings().count(),s.stage(),s.stages(),s.batch()),208,223,190,0xffe6f5ee);
            if(s.result()>0)text(g,tr("result."+s.result()),260,7,138,s.result()==1?0xff153e2f:0xff522710);
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){int rx=(int)x-leftPos,ry=(int)y-topPos;if(button==0&&rx>=12&&rx<200&&ry>=72&&ry<183){int col=(rx-12)/47,row=(ry-72)/37,index=row*4+col;var list=visible();if((rx-12)%47<43&&(ry-72)%37<34&&index<list.size()){selected=list.get(index);return true;}}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(x>=leftPos+12&&x<leftPos+200){page=Math.max(0,Math.min(pages()-1,page-(int)Math.signum(delta)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(getFocused() instanceof EditBox box&&box.isFocused()&&key!=256)return box.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){receive();mode.setMessage(tr("mode."+(maintain?1:0)));apply.active=selected!=null&&menu.snapshot!=null&&!menu.snapshot.job()&&!count.getValue().isEmpty()&&Integer.parseInt(count.getValue())>=1&&Integer.parseInt(count.getValue())<=4096;cancel.active=menu.snapshot!=null&&(menu.snapshot.job()||menu.snapshot.settings().count()>0);
        renderBackground(g);super.render(g,mx,my,partial);int x=mx-leftPos,y=my-topPos;
        if(x>=12&&x<200&&y>=72&&y<183){int index=(y-72)/37*4+(x-12)/47;var list=visible();if(index<list.size())g.renderTooltip(font,list.get(index).plan().output(),mx,my);}
        if(selected!=null&&x>=208&&x<398&&y>=27&&y<44)g.renderTooltip(font,selected.plan().output(),mx,my);
        if(previewReady()&&x>=208&&x<398&&y>=156&&y<208){int index=(y-156)/26*5+(x-208)/38;if(index<menu.snapshot.needs().size()){var n=menu.snapshot.needs().get(index);g.renderTooltip(font,List.of(n.stack().getHoverName(),tr("need",n.available()<0?"…":n.available(),n.required()),tr(menu.snapshot.sourceComplete()?"source_complete":"source_scanning")),Optional.empty(),mx,my);}}
        if(selected!=null&&x>=306&&x<398&&y>=97&&y<117)g.renderTooltip(font,tr("energy_total",(long)selected.costEach()*amount(),menu.snapshot==null?0:menu.snapshot.energy()),mx,my);
    }
}
