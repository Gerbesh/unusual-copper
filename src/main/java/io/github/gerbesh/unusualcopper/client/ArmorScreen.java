package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.armor.*;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.network.ArmorNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Worn equipment console: no physical install/removal, stacks or local NBT writes. */
public class ArmorScreen extends Screen {
    private int selected,page,left,top,panelWidth,panelHeight,rows;
    private ArmorNetwork.Snapshot shown;
    private boolean wasPending;
    private final List<Button> mutations=new ArrayList<>();
    public ArmorScreen(){this(0);}
    public ArmorScreen(int piece){super(Component.translatable("armorconsole.title"));selected=Math.max(0,Math.min(3,piece));}
    private Component text(String key,Object...args){return Component.translatable("armorconsole."+key,args);}
    @Override protected void init(){
        panelWidth=Math.min(460,width-12);panelHeight=Math.min(280,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;rows=Math.max(1,(panelHeight-155)/56);
        rebuild();
    }
    private void rebuild(){
        clearWidgets();mutations.clear();shown=ArmorClientState.view();wasPending=ArmorClientState.pending();
        if(shown==null)return;
        int card=(panelWidth-24)/4;
        for(int i=0;i<4;i++){final int piece=i;
            var part=Button.builder(Component.empty(),b->{selected=piece;page=0;rebuild();}).bounds(left+12+i*card,top+27,card-3,32).build();
            part.setTooltip(Tooltip.create(text("piece."+i)));addRenderableWidget(part);
            var profile=Button.builder(text("profile."+i),b->ArmorClientState.send(1,null,ArmorControlPolicy.Operation.PROFILE,piece)).bounds(left+12+i*card,top+64,card-3,18).build();
            profile.setTooltip(Tooltip.create(Component.translatable("armor.unusualcopper.profile."+i)));
            profile.active=shown.pieces().get(1).tier()>0;mutations.add(profile);addRenderableWidget(profile);
        }
        var modules=shown.pieces().get(selected).modules();int maxPage=Math.max(0,(modules.size()-1)/rows);page=Math.min(page,maxPage);
        for(int i=0;i<rows&&page*rows+i<modules.size();i++){
            var row=modules.get(page*rows+i);var s=row.settings();int y=top+142+i*56;
            int start=left+12,triggerWidth=panelWidth-219;
            var enabled=control(s.enabled()?"on":"off",start,y,50,()->ArmorClientState.send(selected,row.module(),ArmorControlPolicy.Operation.ENABLE,s.enabled()?0:1));
            enabled.setTooltip(Tooltip.create(text("enable_hint")));
            var trigger=Button.builder(text("trigger."+s.trigger().ordinal()),b->ArmorClientState.send(selected,row.module(),ArmorControlPolicy.Operation.TRIGGER,Math.floorMod(s.trigger().ordinal()+(hasShiftDown()?-1:1),ArmorModuleSettings.Trigger.values().length))).bounds(start+53,y,triggerWidth,18).build();
            trigger.setTooltip(Tooltip.create(text("trigger_hint")));mutations.add(trigger);addRenderableWidget(trigger);
            var minimum=Button.builder(text("minimum",s.minimumPercent()),b->ArmorClientState.send(selected,row.module(),ArmorControlPolicy.Operation.MINIMUM,Math.floorMod(s.minimumPercent()+(hasShiftDown()?-5:5),100))).bounds(start+56+triggerWidth,y,74,18).build();
            minimum.setTooltip(Tooltip.create(text("minimum_hint")));mutations.add(minimum);addRenderableWidget(minimum);
            var manual=control(s.armed()?"stop":"start",start+133+triggerWidth,y,62,()->ArmorClientState.send(selected,row.module(),ArmorControlPolicy.Operation.ARM,s.armed()?0:1));
            manual.visible=s.trigger()==ArmorModuleSettings.Trigger.MANUAL;
            var priority=control("priority",start,y+19,102,()->ArmorClientState.send(selected,row.module(),ArmorControlPolicy.Operation.PRIORITY,2+Math.floorMod(s.priority()-2+(hasShiftDown()?-1:1),5)));
            priority.setMessage(text("priority",s.priority()));
            priority.setTooltip(Tooltip.create(text("priority_hint")));
        }
        if(maxPage>0){addRenderableWidget(Button.builder(Component.literal("‹"),b->{page=Math.max(0,page-1);rebuild();}).bounds(left+panelWidth-54,top+panelHeight-23,20,18).build());
            addRenderableWidget(Button.builder(Component.literal("›"),b->{page=Math.min(maxPage,page+1);rebuild();}).bounds(left+panelWidth-31,top+panelHeight-23,20,18).build());}
        for(var button:mutations)button.active&=!wasPending;
    }
    private Button control(String label,int x,int y,int width,Runnable action){var button=Button.builder(text(label),b->action.run()).bounds(x,y,width,18).build();mutations.add(button);addRenderableWidget(button);return button;}
    @Override public void tick(){if(shown!=ArmorClientState.view()||wasPending!=ArmorClientState.pending())rebuild();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);g.fill(left,top,left+panelWidth,top+panelHeight,0xf0112824);g.fill(left,top,left+panelWidth,top+2,0xffb66b42);
        g.drawString(font,title,left+12,top+10,0xffc8e8da,false);
        var v=ArmorClientState.view();if(v==null){g.drawString(font,text("sync"),left+12,top+95,0xffe4cb96,false);super.render(g,mouseX,mouseY,partial);return;}
        var modules=v.pieces().get(selected).modules();
        for(int i=0;i<rows&&page*rows+i<modules.size();i++){
            int y=top+125+i*56;g.fill(left+8,y-2,left+panelWidth-8,y+54,0xff1b3832);
        }
        super.render(g,mouseX,mouseY,partial);
        var selectedPiece=v.pieces().get(selected);
        int budget=ArmorProgression.pieceBudget(selected==1,selected==3,selected==0);
        var slots=text("sockets",selectedPiece.modules().size(),selectedPiece.sockets(),budget);
        g.drawString(font,slots,left+panelWidth-12-font.width(slots),top+10,0xff9fc8b7,false);
        int card=(panelWidth-24)/4;
        for(int i=0;i<4;i++){var piece=v.pieces().get(i);var id=ResourceLocation.tryParse(piece.item());if(id!=null)g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)),left+17+i*card,top+34);
            if(i==selected)g.fill(left+12+i*card,top+59,left+9+(i+1)*card,top+61,0xff3dd6d3);
            g.drawString(font,font.plainSubstrByWidth(text("piece."+i).getString(),card-39),left+37+i*card,top+35,0xffe1e8dc,false);
            g.drawString(font,piece.tier()==0?"—":"I".repeat(piece.tier()),left+37+i*card,top+47,0xffa3d2be,false);
        }
        g.fill(left+12+v.profile()*card,top+82,left+9+(v.profile()+1)*card,top+84,0xff3dd6d3);
        g.drawString(font,text("charge",CopperBatteryItem.amount(v.charge()),CopperBatteryItem.amount(v.capacity())),left+12,top+89,0xffb9e8da,false);
        String usage=text("consumption",CopperBatteryItem.amount(v.perSecond())).getString();
        if(v.perSecond()>0){long seconds=(long)v.charge()/v.perSecond();
            usage+=" · "+(seconds>=3600?text("hours",seconds/3600,seconds/60%60):seconds>=60?text("minutes",seconds/60,seconds%60):text("seconds",seconds)).getString();}
        g.drawString(font,font.plainSubstrByWidth(usage,panelWidth-24),left+12,top+101,0xffb9cfc3,false);
        int percent=v.capacity()==0?0:(int)(100L*v.charge()/v.capacity());g.fill(left+12,top+114,left+panelWidth-12,top+119,0xff173b34);
        g.fill(left+12,top+114,left+12+(panelWidth-24)*percent/100,top+119,percent<=10?0xffe6a969:0xff3dd6d3);
        int labelEnd=(panelWidth-48)/2;
        for(int i=0;i<rows&&page*rows+i<modules.size();i++){
            var row=modules.get(page*rows+i);int y=top+125+i*56;
            g.renderItem(new ItemStack(ModContent.armorModuleItem(row.module(),row.level())),left+12,y);
            String name=Component.translatable("item.unusualcopper."+row.module().itemId(row.level())).getString();
            g.drawString(font,font.plainSubstrByWidth(name,labelEnd),left+32,y,0xffe1dfc7,false);
            String status=!row.settings().enabled()?"off":row.module()==ArmorModuleType.OPTIC_VISOR?"unavailable":!v.complete()?"incomplete":v.capacity()==0?"no_battery":!row.settings().permits(percent,v.conditions())?"waiting":(v.limited()&1L<<row.module().ordinal())!=0?"limited":(v.recent()&1L<<row.module().ordinal())!=0?"recent":"ready";
            String state=font.plainSubstrByWidth(text(status).getString(),panelWidth-labelEnd-55);
            g.drawString(font,state,left+panelWidth-12-font.width(state),y+2,0xff8fc3b0,false);
            if(mouseX>=left+10&&mouseX<left+panelWidth-10&&mouseY>=y&&mouseY<y+16)g.renderTooltip(font,List.of(Component.literal(name),text(status),text("nominal",row.module().cue(row.level()))),java.util.Optional.empty(),mouseX,mouseY);
        }
        if(modules.isEmpty())g.drawString(font,text("empty"),left+12,top+140,0xffc6d6c7,false);
        Component footer=ArmorClientState.pending()?text("sync"):v.result()!=ArmorNetwork.Result.OK?text("result."+v.result().ordinal()):!v.complete()?text("incomplete"):text("footer");
        boolean paged=modules.size()>rows;
        g.drawString(font,font.plainSubstrByWidth(footer.getString(),panelWidth-(paged?130:24)),left+12,top+panelHeight-18,0xffdfc89b,false);
        if(paged)g.drawString(font,(page+1)+"/"+((modules.size()+rows-1)/rows),left+panelWidth-92,top+panelHeight-18,0xffa3d2be,false);
    }
}
