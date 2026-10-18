package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.menu.MiningMenu;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.network.MiningNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class MiningScreen extends AbstractContainerScreen<MiningMenu> {
    private int page;private EditBox search;private Button pause,output;private List<MiningNetwork.Entry> shown=List.of();
    public MiningScreen(MiningMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=430;imageHeight=252;inventoryLabelY=155;}
    private static Component tr(String key,Object... args){return Component.translatable("mining.unusualcopper."+key,args);}
    private void action(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){super.init();addRenderableWidget(Button.builder(tr("area"),b->action(5)).bounds(leftPos+12,topPos+110,84,20).build());pause=addRenderableWidget(Button.builder(tr("pause"),b->action(0)).bounds(leftPos+100,topPos+110,84,20).build());
        addRenderableWidget(Button.builder(tr("rescan"),b->action(1)).bounds(leftPos+12,topPos+133,56,20).build());addRenderableWidget(Button.builder(tr("preview"),b->action(3)).bounds(leftPos+71,topPos+133,54,20).build());addRenderableWidget(Button.builder(tr("xp"),b->action(4)).bounds(leftPos+128,topPos+133,56,20).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);}).bounds(leftPos+208,topPos+217,24,20).build());addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(Math.max(0,(shown.size()-1)/24),page+1);}).bounds(leftPos+398,topPos+217,24,20).build());
        output=addRenderableWidget(Button.builder(tr("output"),b->action(6)).bounds(leftPos+111,topPos+50,73,20).build());
        search=new EditBox(font,leftPos+208,topPos+28,214,18,tr("search"));search.setMaxLength(96);search.setHint(tr("search"));search.setResponder(v->page=0);addRenderableWidget(search);
    }
    private void text(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),x,y,color,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xff75bdb0);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff203e38);g.fill(leftPos+196,topPos+5,leftPos+198,topPos+245,0xff9a6748);
        for(var slot:menu.slots)g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff112822);
        text(g,tr("tool"),leftPos+18,topPos+38,45,0xffb4e7d3);text(g,tr("buffer"),leftPos+65,topPos+38,110,0xffb4e7d3);
        var s=menu.snapshot;if(s==null)return;
        text(g,tr("energy",s.energy(),s.cost()),leftPos+12,topPos+78,176,0xff8eedd7);
        var status=MachineStatus.values()[Math.floorMod(s.status(),MachineStatus.values().length)];text(g,status==MachineStatus.CHUNK_LOADING?tr("chunks",s.chunksLoaded(),s.chunksTotal()):status==MachineStatus.NO_ENERGY&&s.cost()>s.energy()?tr("energy_wait",s.cost()-s.energy()):Component.translatable(status.translationKey()),leftPos+12,topPos+90,180,0xffffd7a7);
        text(g,tr("counts",s.mined(),s.experience()),leftPos+208,topPos+244,214,0xffadcfbf);
        int percent=s.total()==0?0:(int)Math.min(100,(long)s.scanned()*100/s.total());text(g,tr("scan",percent),leftPos+208,topPos+204,210,0xff9fcebc);
        text(g,s.area().length==4?tr("size",s.area()[2]-s.area()[0]+1,s.area()[3]-s.area()[1]+1):tr("no_area"),leftPos+12,topPos+101,180,0xffb8d9c9);
        String q=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);shown=s.entries().stream().filter(e->q.isEmpty()||e.id().toString().contains(q)||e.icon().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)).toList();page=Math.min(page,Math.max(0,(shown.size()-1)/24));
        for(int i=0;i<24&&page*24+i<shown.size();i++){var e=shown.get(page*24+i);int x=leftPos+208+i%6*36,y=topPos+54+i/6*36;g.fill(x,y,x+32,y+32,e.enabled()?0xff315e50:0xff493d36);g.renderItem(e.icon(),x+7,y+3);text(g,Component.literal(e.found()>999?"999+":""+e.found()),x+2,y+23,29,0xffd1ead9);g.drawString(font,e.enabled()?"+":"×",x+24,y+2,e.enabled()?0xff80f5bf:0xffff9e82,false);}
        if(shown.isEmpty())text(g,tr("finding"),leftPos+211,topPos+90,205,0xffc3d9cc);
        text(g,Component.literal((page+1)+" / "+Math.max(1,(shown.size()+23)/24)),leftPos+281,topPos+223,110,0xffd0ecdf);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){text(g,title,12,9,180,0xffe7f3df);text(g,tr("ores"),208,9,214,0xffe7f3df);g.drawString(font,playerInventoryTitle,16,155,0xffbed7c6,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);if(menu.snapshot!=null){pause.setMessage(tr(menu.snapshot.enabled()?"pause":"resume"));var bound=menu.snapshot.output();output.setTooltip(Tooltip.create(bound==null?tr("no_output"):tr("output_at",bound.getX(),bound.getY(),bound.getZ())));}super.render(g,mx,my,dt);renderTooltip(g,mx,my);
        int x=mx-leftPos-208,y=my-topPos-54;if(x>=0&&x<216&&y>=0&&y<144){int i=page*24+x/36+y/36*6;if(i<shown.size()){var e=shown.get(i);g.renderTooltip(font,List.of(e.icon().getHoverName(),Component.literal(e.id().toString()),tr(e.enabled()?"enabled":"disabled"),tr("found",e.found())),Optional.empty(),mx,my);}}
        if(mx>=leftPos+12&&mx<leftPos+184&&my>=topPos+101&&my<topPos+110){var s=menu.snapshot;if(s!=null)g.renderTooltip(font,List.of(tr("chunks",s.chunksLoaded(),s.chunksTotal()),tr("chunk_state."+io.github.gerbesh.unusualcopper.gameplay.MiningChunkStatus.values()[s.chunkState()].name().toLowerCase(Locale.ROOT)),tr("bind_hint")),Optional.empty(),mx,my);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){int dx=(int)x-leftPos-208,dy=(int)y-topPos-54;if(button==0&&dx>=0&&dx<216&&dy>=0&&dy<144&&menu.snapshot!=null){int i=page*24+dx/36+dy/36*6;if(i<shown.size()){MiningNetwork.toggle(new MiningNetwork.Toggle(menu.containerId,menu.pos,menu.snapshot.runtime(),shown.get(i).id()));return true;}}return super.mouseClicked(x,y,button);}
}
