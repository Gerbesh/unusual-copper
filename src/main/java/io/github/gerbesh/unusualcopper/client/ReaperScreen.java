package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.gameplay.ReaperSettings;
import io.github.gerbesh.unusualcopper.menu.ReaperMenu;
import io.github.gerbesh.unusualcopper.network.ReaperNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class ReaperScreen extends AbstractContainerScreen<ReaperMenu> {
    private EditBox trigger,keep,search;
    private Button adultsButton,apply;
    private boolean adults=true,initialized,legacy;
    private int observed=-1,offset;
    private final Set<ResourceLocation> selected=new LinkedHashSet<>();
    private String message="loading";
    public ReaperScreen(ReaperMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=384;imageHeight=236;}
    private Component tr(String key,Object... args){return Component.translatable("reaper.unusualcopper."+key,args);}
    private EditBox box(int x,int y,int w,int max,String key){var b=new EditBox(font,leftPos+x,topPos+y,w,18,tr(key));b.setMaxLength(max);addRenderableWidget(b);return b;}
    private Button button(int x,int y,int w,String key,Runnable action){return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(leftPos+x,topPos+y,w,18).build());}
    @Override protected void init(){
        super.init();initialized=false;observed=-1;
        button(12,27,142,"select_area",()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1));
        button(160,27,52,"show_area",()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,2));
        button(226,27,146,"back",()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
        search=box(12,65,200,80,"search");search.setHint(tr("search"));search.setResponder(s->offset=0);
        trigger=box(226,86,146,7,"trigger");keep=box(226,122,146,7,"keep");
        trigger.setFilter(s->s.matches("[0-9]{0,7}"));keep.setFilter(s->s.matches("[0-9]{0,7}"));
        adultsButton=button(226,147,146,"adults.1",()->adults=!adults);
        button(226,171,146,"clear",()->selected.clear());
        apply=button(226,195,146,"apply",this::apply);
        button(12,201,32,"previous",()->offset=Math.max(0,offset-6));
        button(180,201,32,"next",()->offset=Math.min(Math.max(0,rows().size()-1)/6*6,offset+6));
    }
    private Component name(ResourceLocation id){var type=ForgeRegistries.ENTITY_TYPES.getValue(id);return type==null?Component.literal(id.getPath()):type.getDescription();}
    private List<ReaperNetwork.Species> rows(){if(menu.view==null)return List.of();String text=search.getValue().trim().toLowerCase(Locale.ROOT);return menu.view.species().stream().filter(s->name(s.type()).getString().toLowerCase(Locale.ROOT).contains(text)).sorted(Comparator.comparing(s->name(s.type()).getString())).toList();}
    private void apply(){if(menu.view==null||menu.view.area()==null)return;try{
        var values=new ReaperSettings(Integer.parseInt(trigger.getValue()),Integer.parseInt(keep.getValue()),true,adults,selected.stream().sorted().map(Object::toString).collect(java.util.stream.Collectors.joining(",")),"","");values.validate();
        ReaperNetwork.choose(new ReaperNetwork.Choose(menu.containerId,menu.view.runtime(),menu.view.area(),values));message="pending";
    }catch(IllegalArgumentException invalid){message="invalid";}}
    private void receive(){if(menu.view==null||observed==menu.snapshotSerial)return;observed=menu.snapshotSerial;
        if(!initialized||menu.result==1){var v=menu.snapshot();trigger.setValue(""+v.trigger());keep.setValue(""+v.keep());selected.clear();for(String id:v.types().split(","))if(!id.isBlank())selected.add(new ResourceLocation(id));adults=v.adults();legacy=!v.tags().isBlank()||!v.categories().isBlank();initialized=true;}
        if(menu.result>0)message=switch(menu.result){case 1->"applied";case 2->"invalid";case 3->"throttled";case 4->"need_wand";default->"ready";};
    }
    private void text(GuiGraphics g,Component s,int x,int y,int w,int color){g.drawString(font,font.plainSubstrByWidth(s.getString(),w),x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float p,int x,int y){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffaa724d);g.fill(leftPos+5,topPos+20,leftPos+imageWidth-5,topPos+imageHeight-5,0xff243a36);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        text(g,title,8,6,368,0xff201b15);boolean area=menu.view!=null&&menu.view.area()!=null;
        text(g,tr(area?"choose_species":"area_first"),12,50,360,0xffb8efdf);
        text(g,tr("trigger"),226,74,146,0xffe6f5ee);text(g,tr("keep"),226,110,146,0xffe6f5ee);
        var rows=rows();offset=Math.min(offset,Math.max(0,rows.size()-1)/6*6);
        for(int i=0;i<6&&offset+i<rows.size();i++){var s=rows.get(offset+i);int y=88+i*18;boolean on=selected.contains(s.type());g.fill(12,y,212,y+17,on?0xff416e60:0xff182c29);
            g.drawString(font,on?"✓":"+",16,y+4,on?0xff9cf0c6:0xffe6f5ee,false);
            var type=ForgeRegistries.ENTITY_TYPES.getValue(s.type());var egg=type==null?null:SpawnEggItem.byId(type);if(egg!=null)g.renderItem(new ItemStack(egg),28,y);
            text(g,name(s.type()),47,y+4,128,0xffe6f5ee);text(g,Component.literal(""+(adults?s.adults():s.total())),178,y+4,31,0xff9cf0c6);
        }
        if(area&&rows.isEmpty())text(g,tr(menu.view.complete()?"empty":"scanning"),12,105,200,0xffb8efdf);
        text(g,tr("selected",selected.size()),51,206,126,0xffb8efdf);
        text(g,tr(menu.view!=null&&!menu.view.complete()&&area?"scanning":message.equals("loading")?legacy?"legacy":"protected":message),12,224,360,0xffb8efdf);
    }
    @Override public boolean mouseClicked(double x,double y,int button){int rx=(int)x-leftPos,ry=(int)y-topPos;if(button==0&&rx>=12&&rx<212&&ry>=88&&ry<196){var rows=rows();int index=offset+(ry-88)/18;if(index<rows.size()){var id=rows.get(index).type();if(!selected.remove(id)&&selected.size()<8)selected.add(id);return true;}}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double d){if(x>=leftPos+12&&x<leftPos+212){offset=Math.max(0,Math.min(Math.max(0,rows().size()-1)/6*6,offset-(int)Math.signum(d)*6));return true;}return super.mouseScrolled(x,y,d);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(getFocused() instanceof EditBox box&&box.isFocused()&&key!=256)return box.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);}
    @Override public void render(GuiGraphics g,int mx,int my,float p){receive();apply.active=menu.view!=null&&menu.view.area()!=null&&menu.view.complete();adultsButton.setMessage(tr("adults."+(adults?1:0)));renderBackground(g);super.render(g,mx,my,p);
        int x=mx-leftPos,y=my-topPos;var rows=rows();int index=offset+(y-88)/18;if(x>=12&&x<212&&y>=88&&y<196&&index<rows.size()){var s=rows.get(index);g.renderTooltip(font,List.of(name(s.type()),tr("counts",s.total(),s.adults())),Optional.empty(),mx,my);}
    }
}
