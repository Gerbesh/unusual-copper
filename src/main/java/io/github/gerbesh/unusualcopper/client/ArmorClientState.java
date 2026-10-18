package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.armor.*;
import io.github.gerbesh.unusualcopper.config.ArmorClientConfig;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.network.ArmorNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client holds an owner snapshot only; controls send intentions and wait for acknowledgements. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ArmorClientState {
    private static ArmorNetwork.Snapshot snapshot;
    private static ClientLevel world;
    private static int clock,received,sent,warningBand;
    private static boolean opening,pending,profileQueued,scanQueued;
    public static ArmorNetwork.Snapshot view(){return snapshot;}
    public static boolean pending(){return pending;}
    public static void receive(ArmorNetwork.Snapshot value){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        boolean acknowledged=snapshot==null||!value.session().equals(snapshot.session())||value.revision()!=snapshot.revision()||value.result()!=ArmorNetwork.Result.OK;
        boolean wasWaiting=pending;snapshot=value;received=clock;if(acknowledged)pending=false;
        if(wasWaiting&&value.result()!=ArmorNetwork.Result.OK&&!(mc.screen instanceof ArmorScreen))
            mc.player.displayClientMessage(Component.translatable("armorconsole.result."+value.result().ordinal()),true);
        int percent=value.capacity()==0?100:(int)(100L*value.charge()/value.capacity());
        int band=!value.complete()?0:percent<=5?3:percent<=10?2:percent<=25?1:0;
        if(band>warningBand)mc.player.displayClientMessage(Component.translatable("armorconsole.warning",percent),true);
        warningBand=band;
        if(opening){opening=false;if(mc.screen==null)mc.setScreen(new ArmorScreen());}
        if(profileQueued){profileQueued=false;if(value.pieces().get(1).tier()>0&&(mc.screen==null||mc.screen instanceof ArmorScreen))send(1,null,ArmorControlPolicy.Operation.PROFILE,(value.profile()+1)%4);}
        if(scanQueued){scanQueued=false;if(mc.screen==null||mc.screen instanceof ArmorScreen)toggleScanner();}
    }
    public static void open(){opening=true;ArmorNetwork.request();}
    private static void toggleScanner(){
        if(snapshot==null||pending)return;
        var row=snapshot.pieces().get(0).modules().stream().filter(module->module.module()==ArmorModuleType.FIELD_SCANNER).findFirst();
        if(row.isPresent())send(0,ArmorModuleType.FIELD_SCANNER,ArmorControlPolicy.Operation.ENABLE,row.get().settings().enabled()?0:1);
        else{var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.translatable("armor.sense.no_scanner"),true);}
    }
    public static void send(int piece,ArmorModuleType module,ArmorControlPolicy.Operation operation,int value){
        if(snapshot==null||pending)return;
        pending=true;sent=clock;
        ArmorNetwork.action(new ArmorNetwork.Action(snapshot.session(),snapshot.revision(),piece,module==null?-1:module.ordinal(),operation,value));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();clock++;
        if(mc.level!=world||mc.player==null){world=mc.level;snapshot=null;opening=pending=profileQueued=scanQueued=false;warningBand=0;}
        if(mc.player==null||mc.level==null)return;
        if(pending&&clock-sent>40){pending=false;ArmorNetwork.request();}
        if(clock%20==0&&snapshot==null&&mc.player.getArmorSlots().iterator().hasNext()){
            for(var item:mc.player.getArmorSlots())if(item.getItem() instanceof ProgrammableArmorItem){ArmorNetwork.request();break;}
        }
        while(ArmorKeys.OPEN.consumeClick()){
            if(mc.screen instanceof ArmorScreen)mc.setScreen(null);else if(mc.screen==null)open();
        }
        while(ArmorKeys.PROFILE.consumeClick())if(mc.screen==null||mc.screen instanceof ArmorScreen){if(snapshot!=null)send(1,null,ArmorControlPolicy.Operation.PROFILE,(snapshot.profile()+1)%4);else{profileQueued=true;ArmorNetwork.request();}}
        while(ArmorKeys.HUD.consumeClick())if(mc.screen==null||mc.screen instanceof ArmorScreen){ArmorClientConfig.HUD.set(!ArmorClientConfig.HUD.get());ArmorClientConfig.SPEC.save();}
        while(ArmorKeys.SCAN.consumeClick())if(mc.screen==null||mc.screen instanceof ArmorScreen){if(snapshot==null){scanQueued=true;ArmorNetwork.request();}else toggleScanner();}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();var v=snapshot;
        if(v==null||mc.player==null||mc.options.hideGui||!ArmorClientConfig.HUD.get()||clock-received>60||v.pieces().stream().noneMatch(p->p.tier()>0))return;
        var g=event.getGuiGraphics();int x=8,y=8,w=160;
        int h=v.flight().session()?79:64;
        g.fill(x,y,x+w,y+h,0xb0112422);g.fill(x,y,x+2,y+h,0xffb66b42);
        int percent=v.capacity()==0?0:(int)(100L*v.charge()/v.capacity());
        int color=percent<=5?0xffffa385:percent<=10?0xffffc174:percent<=25?0xffe6d389:0xff83e7d0;
        String charge=v.capacity()==0?Component.translatable("armorconsole.no_battery").getString():percent+"% · "+compact(v.charge())+" CuE";
        g.drawString(mc.font,charge,x+7,y+5,color,false);
        g.fill(x+7,y+17,x+w-7,y+20,0xff122d2a);if(v.capacity()>0)g.fill(x+7,y+17,x+7+(w-14)*percent/100,y+20,color);
        g.drawString(mc.font,Component.translatable("armorconsole.hud_cost",compact(v.perSecond())),x+7,y+24,0xffc3e1d6,false);
        if(!v.complete())g.drawString(mc.font,Component.translatable("armorconsole.hud_incomplete"),x+7,y+35,0xffffc174,false);
        else{
            int index=0;for(var module:ArmorModuleType.values())if((v.recent()&1L<<module.ordinal())!=0&&index<9){
                int level=v.pieces().stream().flatMap(piece->piece.modules().stream()).filter(row->row.module()==module).mapToInt(ArmorNetwork.Row::level).findFirst().orElse(1);
                var item=io.github.gerbesh.unusualcopper.registry.ModContent.armorModuleItem(module,level);
                g.renderItem(new ItemStack(item),x+6+index++*17,y+43);
            }
        }
        if(v.flight().session()) {
            var flight=v.flight();
            var label=flight.ticksLeft()==0
                    ?Component.translatable("armorconsole.hud_f3_exhausted")
                    :Component.translatable("armorconsole.hud_f3",
                            (flight.ticksLeft()+19)/20,flight.heightLeft());
            g.drawString(mc.font,mc.font.plainSubstrByWidth(label.getString(),w-14),x+7,y+65,
                    flight.ticksLeft()==0?0xffffc174:0xff83e7d0,false);
        }
    }
    private static String compact(int value){return value>=1_000_000?String.format(java.util.Locale.ROOT,"%.1fM",value/1_000_000D):value>=10_000?String.format(java.util.Locale.ROOT,"%.1fk",value/1000D):Integer.toString(value);}
    private ArmorClientState(){}
}
