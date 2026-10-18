package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.*;
import io.github.gerbesh.unusualcopper.reactor.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Display-only reactor layouts and client-side readings. Server has no fuel, coolant or stored CuE. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ReactorVisualClient {
    private static final BlockPos POS=new BlockPos(180,150,180);
    private static final String[] SHOTS={"active-front","active-corner","active-night","idle","console","empty-slots","filter-tooltip","coolant-purifier","incomplete"};
    private static final AtomicBoolean READY=new AtomicBoolean(),SAVED=new AtomicBoolean();
    private static CompletableFuture<Void> reload;
    private static boolean started,captured;private static int stage,ticks,total;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.reactorVisualClient"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++total>2400)throw new IllegalStateException("Reactor display stalled at "+stage);
        if(!started){started=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            String language=System.getProperty("unusualcopper.reactorVisualLanguage","ru_ru");mc.options.languageCode=language;mc.getLanguageManager().setSelected(language);reload=mc.reloadResourcePacks();
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);level.setWeatherParameters(0,0,false,false);
                for(int x=-3;x<=6;x++)for(int z=-4;z<=6;z++)level.setBlock(POS.offset(x,-1,z),Blocks.POLISHED_DEEPSLATE.defaultBlockState(),3);
                for(int x:new int[]{-1,3})for(int z:new int[]{-1,3})level.setBlock(POS.offset(x,2,z),Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL,12),3);
                for(var pos:ReactorGeometry.positions(POS))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                UUID group=UUID.randomUUID();
                for(var pos:ReactorGeometry.positions(POS)){level.setBlock(pos,ModContent.REACTOR_BLOCK.get().defaultBlockState().setValue(ReactorBlock.FORMED,true),3);var part=ReactorBlockEntity.at(level,pos);part.anchor=POS;part.controller=POS;part.group=group;part.paused=true;part.sync();}
                for(int x=0;x<2;x++)for(int z=1;z<=3;z++)level.setBlock(POS.offset(x,0,-z),ModContent.CRYSTALLINE_ENERGY_PIPE.get().defaultBlockState(),3);
                var player=level.getServer().getPlayerList().getPlayer(mc.player.getUUID());player.getInventory().clearContent();for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);READY.set(true);
            });return;
        }
        if(!READY.get()||!reload.isDone())return;
        if(ticks==0)display(stage);
        var reactor=ReactorBlockEntity.at(mc.level,POS);
        if(reactor!=null&&stage<8){reactor.generated=stage==3||stage==5?0:32000;reactor.previousPower=reactor.generated;reactor.engine.heat=stage==3||stage==5?0:4800;reactor.previousHeat=reactor.engine.heat;reactor.visualLenses=stage==5?0:15;reactor.coolant.setFluid(new net.minecraftforge.fluids.FluidStack(ReactorFluids.STILL.get(),16000));}
        if(++ticks<(stage==0?100:45))return;
        if(!captured){captured=true;Screenshot.grab(mc.gameDirectory,"reactor-"+SHOTS[stage]+".png",mc.getMainRenderTarget(),message->SAVED.set(true));return;}
        if(!SAVED.get())return;
        if(stage==SHOTS.length-1){UnusualCopper.LOGGER.info("REACTOR DISPLAY COMPLETE: nine fixed display frames; no paid processing or functional acceptance");mc.setScreen(null);mc.player.containerMenu=mc.player.inventoryMenu;mc.options.hideGui=false;mc.stop();return;}
        stage++;ticks=0;captured=false;SAVED.set(false);
    }
    private static void display(int shot){
        var mc=Minecraft.getInstance();mc.setScreen(null);mc.player.containerMenu=mc.player.inventoryMenu;
        mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();level.setDayTime(shot==2?18000:6000);var p=level.getServer().getPlayerList().getPlayer(mc.player.getUUID());
            double x=shot==1||shot==8?187.5:181.5,z=shot==1||shot==8?174.8:172.4,y=151.5;
            double dx=181.5-x,dz=181.5-z,dy=152.1-(y+1.62);
            p.teleportTo(level,x,y,z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
            if(shot==8){var c=ReactorBlockEntity.at(level,POS);if(c!=null)c.unform();level.setBlock(POS.offset(2,3,2),Blocks.AIR.defaultBlockState(),3);}
        });
        if(shot>=4&&shot<=6){
            final ReactorMenu menu=new ReactorMenu(0,mc.player.getInventory(),(ReactorBlockEntity)null);
            menu.data.set(0,8_000_000&65535);menu.data.set(1,8_000_000>>>16);menu.data.set(4,4800);menu.data.set(5,16000);menu.data.set(6,8250);menu.data.set(7,2);menu.data.set(8,(shot==5?ReactorBlockEntity.Status.NO_LENSES:ReactorBlockEntity.Status.WORKING).ordinal());menu.data.set(9,shot==5?0:32000);menu.data.set(10,shot==5?0:28672);menu.data.set(13,1);
            if(shot!=5){menu.slots.get(0).set(new ItemStack(Items.COPPER_BLOCK,24));for(int i=1;i<=4;i++)menu.slots.get(i).set(new ItemStack(ModContent.REACTOR_LENS.get()));var filter=new ItemStack(ModContent.REACTOR_FILTER.get());filter.setDamageValue(256);menu.slots.get(5).set(filter);menu.slots.get(6).set(new ItemStack(ModContent.REACTOR_INSULATION.get()));menu.slots.get(8).set(new ItemStack(Items.BUCKET,16));}
            var screen=new ReactorScreen(menu,mc.player.getInventory(),Component.translatable("reactor.title")){
                @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,shot==6?(width-320)/2+180:shot==5?(width-320)/2+68:-1,shot==5||shot==6?(height-232)/2+54:-1,partial);}
            };mc.setScreen(screen);mc.player.containerMenu=menu;
        }
        if(shot==7){
            var b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());final CopperMachineMenu menu;
            try{b.writeBlockPos(POS);menu=new CopperMachineMenu(0,mc.player.getInventory(),b);}finally{b.release();}
            menu.data.set(8,MachineKind.VORTEX_PURIFIER.ordinal());menu.data.set(47,3000);menu.data.set(54,2500);menu.data.set(6,MachineStatus.WORKING.ordinal());menu.slots.get(0).set(new ItemStack(ModContent.COOLANT_CONCENTRATE.get(),16));menu.slots.get(1).set(new ItemStack(Items.BUCKET,8));menu.slots.get(2).set(new ItemStack(ReactorFluids.BUCKET.get()));
            mc.setScreen(new CopperMachineScreen(menu,mc.player.getInventory(),Component.translatable("block.unusualcopper.vortex_purifier")));mc.player.containerMenu=menu;
        }
    }
}
