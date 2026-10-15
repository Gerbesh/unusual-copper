package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.*;
import io.github.gerbesh.unusualcopper.energy.EnergyPipeBlockEntity;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable visual fixture in the repository-owned cue-smoke world; absent from releases. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class VisualClientSmoke {
    private static final BlockPos ORIGIN=new BlockPos(104,100,108);
    private static final AtomicBoolean ready=new AtomicBoolean(),saved=new AtomicBoolean();
    private static boolean started,captured;
    private static int stage,ticks;
    private static int totalTicks;
    private static long activeBaseline;
    private static CopperMachineBlockEntity machine(ServerLevel level,BlockPos pos,MachineKind kind){
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        level.setBlock(pos,ModContent.MACHINES.get(kind).get().defaultBlockState(),3);
        return (CopperMachineBlockEntity)level.getBlockEntity(pos);
    }
    private static void side(CopperMachineBlockEntity m,Direction side,SideMode mode){
        for(int i=0;i<5&&m.sideMode(side)!=mode;i++)m.cycleSide(side);
        if(m.sideMode(side)!=mode)throw new IllegalStateException("Unsupported fixture port "+m.kind+" "+mode);
    }
    private static void line(ServerLevel l,boolean reverse,boolean powered){
        var source=machine(l,ORIGIN.east(reverse?6:0),MachineKind.ESSENCE_SPLITTER);
        source.energy.restore(powered?50000:0);
        if(powered)source.inventory.setStackInSlot(0,new ItemStack(ModContent.ESSENCE.get(),64));
        var sink=machine(l,ORIGIN.east(reverse?0:6),MachineKind.RESONANCE_SMELTER);
        sink.inventory.setStackInSlot(0,new ItemStack(Items.RAW_IRON,64));
        for(int i=1;i<6;i++)l.setBlock(ORIGIN.east(i),switch(i%3){case 0->ModContent.CRYSTALLINE_ENERGY_PIPE.get().defaultBlockState();case 1->ModContent.ENERGY_PIPE.get().defaultBlockState();default->ModContent.REINFORCED_ENERGY_PIPE.get().defaultBlockState();},3);
        for(var drop:l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(ORIGIN.offset(-2,-1,-2),ORIGIN.offset(9,3,3))))drop.discard();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.visualClient"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(++totalTicks>2000)throw new IllegalStateException("Visual fixture initialization or phase stalled");
        if(!started){started=true;mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.hideGui=true;
            mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();var p=l.getServer().getPlayerList().getPlayer(mc.player.getUUID());
                l.setDayTime(6000);l.setWeatherParameters(0,0,false,false);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                for(int x=99;x<127;x++)for(int z=103;z<134;z++){
                    l.setBlock(new BlockPos(x,99,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
                    for(int y=100;y<109;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                line(l,false,false);
                int i=0;for(var kind:MachineKind.values()){
                    var pos=new BlockPos(102+(i%4)*5,100,118+(i/4)*4);i++;
                    var m=machine(l,pos,kind);m.redstoneMode=1;m.energy.restore(kind.capacity/2);
                    boolean energy=kind.produces()||kind.consumes()||kind==MachineKind.ENERGY_MONITOR;
                    if(kind!=MachineKind.ENERGY_MONITOR)side(m,Direction.WEST,energy?(kind.produces()?SideMode.ENERGY_OUTPUT:SideMode.ENERGY_INPUT):SideMode.ITEM_INPUT);
                    l.setBlock(pos.west(),energy?ModContent.ENERGY_PIPE.get().defaultBlockState():ModContent.ITEM_PIPE.get().defaultBlockState(),3);
                }
                var junction=ORIGIN.offset(14,3,0);
                for(var dir:Direction.values()){
                    for(int n=0;n<3;n++)l.setBlock(junction.relative(dir,n),ModContent.ENERGY_PIPE.get().defaultBlockState(),3);
                    var m=machine(l,junction.relative(dir,3),dir==Direction.EAST?MachineKind.ESSENCE_SPLITTER:MachineKind.RESONANCE_SMELTER);
                    if(dir==Direction.EAST){m.energy.restore(50000);m.inventory.setStackInSlot(0,new ItemStack(ModContent.ESSENCE.get(),64));}
                    else m.inventory.setStackInSlot(0,new ItemStack(Items.RAW_GOLD,64));
                    side(m,dir.getOpposite(),dir==Direction.EAST?SideMode.ENERGY_OUTPUT:SideMode.ENERGY_INPUT);
                }
                for(var drop:l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(96,96,99,132,112,138)))drop.discard();
                p.teleportTo(l,107.5,102.1,103,0,14);ready.set(true);
            });return;
        }
        if(!ready.get())return;ticks++;
        if(ticks>400){throw new IllegalStateException("Visual fixture timed out at stage "+stage);}
        if(!captured&&ticks>35){
            boolean valid=stage!=0&&stage!=4||mc.level.getBlockEntity(ORIGIN.east(3)) instanceof EnergyPipeBlockEntity pipe&&pipe.visualCharge()==0;
            if(stage==1||stage==2||stage==3){
                if(!(mc.level.getBlockEntity(ORIGIN.east(3)) instanceof EnergyPipeBlockEntity pipe)||!pipe.flowing()||!pipe.incoming(stage==3?Direction.EAST:Direction.WEST)||!pipe.outgoing(stage==3?Direction.WEST:Direction.EAST)||EnergyPipeRenderer.flowFrames<=activeBaseline)valid=false;
            }
            if(stage==5&&(!(mc.level.getBlockEntity(ORIGIN.east(3)) instanceof EnergyPipeBlockEntity pipe)||pipe.visualCharge()==0||pipe.flowing()))valid=false;
            if(valid){captured=true;String[] names={"empty","flow-a","flow-b","reverse","drained","charged-idle","low-ports","machines","six-ports","items"};
                Screenshot.grab(mc.gameDirectory,"cue-visual-"+names[stage]+".png",mc.getMainRenderTarget(),message->{UnusualCopper.LOGGER.info("CuE VISUAL FRAME {}: {}",stage,message.getString());saved.set(true);});}
        }
        if(!saved.get())return;
        if(stage==9){
            if(EnergyPipeRenderer.emptyFrames==0||EnergyPipeRenderer.chargedFrames==0||EnergyPipeRenderer.flowFrames==0||CopperMachineRenderer.portFrames==0||CopperMachineRenderer.workingFrames==0)throw new IllegalStateException("Missing native visual renderer branch");
            UnusualCopper.LOGGER.info("CuE VISUAL CLIENT PASS: empty/charged/directed CuE flow, reverse/drain, all 16 machine bodies, six port directions and nine item sprites; pipe frames empty={} charged={} flow={}, machine ports={} working={}",EnergyPipeRenderer.emptyFrames,EnergyPipeRenderer.chargedFrames,EnergyPipeRenderer.flowFrames,CopperMachineRenderer.portFrames,CopperMachineRenderer.workingFrames);
            mc.options.hideGui=false;mc.stop();return;
        }
        stage++;ticks=0;captured=false;saved.set(false);activeBaseline=EnergyPipeRenderer.flowFrames;
        if(stage==1||stage==3||stage==4||stage==5){ready.set(false);mc.getSingleplayerServer().execute(()->{
            var l=mc.getSingleplayerServer().overworld();line(l,stage==3,stage!=4);
            if(stage==5){var sink=(CopperMachineBlockEntity)l.getBlockEntity(ORIGIN.east(6));sink.inventory.setStackInSlot(0,ItemStack.EMPTY);sink.energy.restore(sink.energy.capacity());}
            ready.set(true);
        });}
        if(stage==6||stage==7||stage==8){ready.set(false);mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();var p=l.getServer().getPlayerList().getPlayer(mc.player.getUUID());if(stage==6)p.teleportTo(l,99,102.4,114,-35,14);else if(stage==7)p.teleportTo(l,109,108,110,0,28);else p.teleportTo(l,113,105.8,101,-32,16);ready.set(true);});}
        if(stage==9)mc.setScreen(new SpriteScreen());
    }
    private static final class SpriteScreen extends Screen {
        private final Item[] items={ModContent.ESSENCE.get(),ModContent.ENCHANTMENT_CORE.get(),ModContent.SENTINEL_FOCUS.get(),ModContent.SOLAR_LENS.get(),ModContent.FRAME.get(),ModContent.CORE.get(),ModContent.FACE_SELECTOR.get(),ModContent.CARGO_BUNDLE.get(),ModContent.AREA_WAND.get()};
        SpriteScreen(){super(Component.literal("Unusual Copper — item sprites"));}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xff161e20);g.drawCenteredString(font,title,width/2,12,0xffbbdbc9);
            int left=(width-270)/2,top=(height-230)/2;
            for(int i=0;i<items.length;i++){
                int x=left+(i%3)*90,y=top+(i/3)*78;
                g.fill(x,y,x+82,y+72,0xff243431);g.pose().pushPose();g.pose().translate(x+9,y+3,0);g.pose().scale(4,4,1);g.renderItem(new ItemStack(items[i]),0,0);g.pose().popPose();
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
