package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Static scene quantities and display screens only. No bucket/pipe/break acceptance run. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class FluidTankVisualClient {
    private static final String[] SHOTS={"front-low","fill-a","fill-b","side","top","mixed","legacy-menu","group-menu","items","night"};
    private static final BlockPos MAIN=new BlockPos(109,150,130),LEGACY=new BlockPos(100,150,134);
    private static final List<BlockPos> MAIN_CUBES=new ArrayList<>();
    private static final AtomicBoolean READY=new AtomicBoolean(),SAVED=new AtomicBoolean();
    private static ServerLevel display;private static boolean started,captured;private static int stage,ticks,total;
    private FluidTankVisualClient(){}
    private static void cube(ServerLevel level,BlockPos pos,int amount,boolean lava,UUID owner){
        level.setBlock(pos,ModContent.MACHINES.get(MachineKind.COPPER_FLUID_TANK).get().defaultBlockState(),3);
        var tank=(CopperMachineBlockEntity)level.getBlockEntity(pos);tank.setOwner(owner);tank.water.setFluid(amount==0?FluidStack.EMPTY:new FluidStack(lava?Fluids.LAVA:Fluids.WATER,amount));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.fluidTankVisualClient"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++total>1800)throw new IllegalStateException("Fluid tank display stalled");
        if(!started){started=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(55);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getSingleplayerServer().execute(()->{
                display=mc.getSingleplayerServer().overworld();display.setDayTime(6000);display.setWeatherParameters(0,0,false,false);
                display.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,display.getServer());
                display.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,display.getServer());
                for(int x=96;x<=126;x++)for(int z=110;z<=140;z++){
                    display.setBlock(new BlockPos(x,149,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
                    for(int y=150;y<=158;y++)display.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                }
                for(var entity:display.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(96,149,110,127,159,141),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
                var player=display.getServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(GameType.SPECTATOR);var owner=player.getUUID();
                cube(display,new BlockPos(100,150,130),8000,false,owner);
                for(int y=0;y<4;y++)cube(display,new BlockPos(104,150+y,130),y<2?16_000:0,false,owner);
                for(int x=0;x<2;x++)for(int z=0;z<2;z++)for(int y=0;y<3;y++){
                    var p=MAIN.offset(x,y,z);MAIN_CUBES.add(p);cube(display,p,y==0?8000:0,false,owner);
                }
                for(int y=0;y<2;y++){cube(display,new BlockPos(116,150+y,130),8000,false,owner);cube(display,new BlockPos(117,150+y,130),8000,false,owner);cube(display,new BlockPos(116,150+y,131),8000,false,owner);}
                cube(display,new BlockPos(121,150,130),8000,false,owner);cube(display,new BlockPos(122,150,130),0,false,owner);cube(display,new BlockPos(123,150,130),8000,true,owner);
                cube(display,LEGACY,64_000,false,owner);front(player);READY.set(true);
            });return;
        }
        if(!READY.get())return;
        if(stage==0&&(!(mc.level.getBlockEntity(MAIN) instanceof CopperMachineBlockEntity tank)||tank.tankView.revision()==0))return;
        int wait=stage==0?120:stage==1?5:stage==2?8:45;if(++ticks<wait)return;
        if(!captured){captured=true;Screenshot.grab(mc.gameDirectory,"fluid-tank-"+SHOTS[stage]+".png",mc.getMainRenderTarget(),message->{
            UnusualCopper.LOGGER.info("FLUID TANK DISPLAY FRAME {}: {}",stage,message.getString());SAVED.set(true);
        });return;}
        if(!SAVED.get())return;
        if(stage==SHOTS.length-1){UnusualCopper.LOGGER.info("FLUID TANK DISPLAY COMPLETE: static scene and client screens; no functional transfer acceptance");mc.options.hideGui=false;mc.stop();return;}
        stage++;ticks=0;captured=false;SAVED.set(false);
        if(stage==6||stage==7){openMenu(stage==6?LEGACY:MAIN);return;}
        if(stage==8){mc.setScreen(new Catalogue());return;}
        mc.setScreen(null);if(stage==2)return;READY.set(false);
        mc.getSingleplayerServer().execute(()->{
            var player=display.getServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(stage==1){for(var p:MAIN_CUBES){int amount=p.getY()<152?16_000:8000;((CopperMachineBlockEntity)display.getBlockEntity(p)).water.setFluid(new FluidStack(Fluids.WATER,amount));}}
            if(stage==3)player.teleportTo(display,121.5,154,119.5,35,13);
            if(stage==4)player.teleportTo(display,109.8,158.5,130.8,180,80);
            if(stage==5)player.teleportTo(display,122,150.8,126.2,0,8);
            if(stage==9){display.setDayTime(18000);front(player);}
            READY.set(true);
        });
    }
    private static void front(net.minecraft.server.level.ServerPlayer p){p.teleportTo(display,111.5,153.5,114,0,10);}
    private static void openMenu(BlockPos pos){
        var mc=Minecraft.getInstance();var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{buffer.writeBlockPos(pos);var menu=new io.github.gerbesh.unusualcopper.menu.CopperMachineMenu(0,mc.player.getInventory(),buffer);
            menu.data.set(8,MachineKind.COPPER_FLUID_TANK.ordinal());
            mc.setScreen(new io.github.gerbesh.unusualcopper.client.CopperMachineScreen(menu,mc.player.getInventory(),Component.translatable("block.unusualcopper.copper_fluid_tank")){
                @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,-1,-1,partial);}
            });
        }finally{buffer.release();}
    }
    private static final class Catalogue extends Screen {
        private final ItemStack[] items=new ItemStack[3];
        Catalogue(){super(Component.literal("Unusual Copper / empty, filled and legacy tank items"));
            var block=ModContent.MACHINES.get(MachineKind.COPPER_FLUID_TANK).get();
            for(int i=0;i<3;i++){items[i]=new ItemStack(block);if(i>0){var tag=new net.minecraft.nbt.CompoundTag();var fluid=new FluidStack(Fluids.WATER,i==1?16000:64000);tag.put("WaterTank",fluid.writeToNBT(new net.minecraft.nbt.CompoundTag()));items[i].getOrCreateTag().put("BlockEntityTag",tag);}}
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xff142624);g.drawCenteredString(font,title,width/2,10,0xffe5cfab);
            int w=(width-24)/3;for(int i=0;i<3;i++){
                int x=12+i*w;float scale=height<260?3:4;g.fill(x,32,x+w-5,height-15,0xff24423c);g.pose().pushPose();g.pose().translate(x+w/2F-8*scale,40,0);g.pose().scale(scale,scale,1);g.renderItem(items[i],0,0);g.pose().popPose();
                var lines=items[i].getTooltipLines(Minecraft.getInstance().player,net.minecraft.world.item.TooltipFlag.Default.NORMAL);int row=Math.min(112,height-140);
                for(var line:lines)for(var wrapped:font.split(line,w-16)){g.drawString(font,wrapped,x+6,row,0xffc8e8da,false);row+=10;}
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
