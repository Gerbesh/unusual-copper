package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.armor.*;
import io.github.gerbesh.unusualcopper.config.ArmorClientConfig;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.network.ArmorSenseSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Ephemeral paid server lease, additionally gated by the wearer's present equipment. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ArmorSenseClient {
    private static ArmorSenseSnapshot state;
    private static ClientLevel world;
    private static int clock,received;
    private static int eligibilityTick=-1,eligibility;
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final ItemStack[] CHECKED_PIECES=new ItemStack[4];
    private static final Map<BlockPos,Integer> APPEARED=new HashMap<>();
    private ArmorSenseClient(){}
    public static void receive(ArmorSenseSnapshot value){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.level.dimension().location().toString().equals(value.dimension()))return;
        if(world!=mc.level)APPEARED.clear();world=mc.level;state=value;received=clock;eligibilityTick=-1;
        APPEARED.keySet().retainAll(value.ores());for(var pos:value.ores())APPEARED.putIfAbsent(pos,clock);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;clock++;
        var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null||clock-received>ArmorSenseSnapshot.LEASE_TICKS){
            state=null;APPEARED.clear();world=mc.level;Arrays.fill(CHECKED_PIECES,null);eligibilityTick=-1;eligibility=0;
        }
    }
    private static boolean current(){var mc=Minecraft.getInstance();return state!=null&&mc.player!=null&&mc.player.isAlive()&&mc.level==world
            &&clock-received<=ArmorSenseSnapshot.LEASE_TICKS&&mc.level.dimension().location().toString().equals(state.dimension());}
    private static boolean active(ArmorModuleType module){
        if(!current())return false;var player=Minecraft.getInstance().player;
        boolean changed=eligibilityTick!=clock;
        for(int i=0;i<4;i++)if(CHECKED_PIECES[i]!=player.getItemBySlot(SLOTS[i])){CHECKED_PIECES[i]=player.getItemBySlot(SLOTS[i]);changed=true;}
        if(changed){
            eligibilityTick=clock;eligibility=0;
            if(equipped(ArmorModuleType.OPTIC_VISOR))eligibility|=1;
            if(equipped(ArmorModuleType.FIELD_SCANNER))eligibility|=2;
        }
        return (eligibility&(module==ArmorModuleType.OPTIC_VISOR?1:2))!=0;
    }
    /** NBT validation runs once per client tick/snapshot/equipment replacement, not per rendered line. */
    private static boolean equipped(ArmorModuleType module){
        if(!current())return false;var p=Minecraft.getInstance().player;
        int expected=module==ArmorModuleType.OPTIC_VISOR?state.visor():state.scanner();if(expected==0)return false;
        int tier=0;
        for(var slot:SLOTS){
            var item=p.getItemBySlot(slot);
            if(!(item.getItem() instanceof ProgrammableArmorItem armor)||armor.getEquipmentSlot()!=slot||!ArmorData.upgradeSafe(item))return false;
            if(tier!=0&&tier!=armor.tier)return false;tier=armor.tier;
        }
        var helmet=p.getItemBySlot(EquipmentSlot.HEAD);var chest=p.getItemBySlot(EquipmentSlot.CHEST);
        return ArmorData.profile(chest)==state.profile()&&ArmorData.modules(helmet).contains(module)
                &&ArmorData.moduleLevel(helmet,module)==expected&&CopperBatteryItem.stored(ArmorData.battery(chest))>0
                &&ArmorData.settings(helmet,module,state.profile()).permits(ArmorData.percentage(chest),state.conditions());
    }
    public static boolean nightVision(){return active(ArmorModuleType.OPTIC_VISOR);}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!current())return;
        var mc=Minecraft.getInstance();boolean ores=state.visor()==2&&active(ArmorModuleType.OPTIC_VISOR),scanner=active(ArmorModuleType.FIELD_SCANNER);
        if(!ores&&!scanner)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        double now=clock+event.getPartialTick();float pulse=(float)(.78D+.08D*Math.sin(now*.10D));
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        try{
            if(ores){
                var lines=buffers.getBuffer(ArmorSenseRenderTypes.ORE_LINES);
                for(var pos:state.ores())if(mc.player.blockPosition().distSqr(pos)<=64){
                    float fade=(float)Math.min(1D,Math.max(.12D,(now-APPEARED.getOrDefault(pos,clock))/3D));
                    LevelRenderer.renderLineBox(pose,lines,new AABB(pos).inflate(.003D),.22F,1F,.85F,pulse*fade);
                }
                buffers.endBatch(ArmorSenseRenderTypes.ORE_LINES);
            }
            var target=state.target();
            if(scanner&&target!=null&&mc.player.blockPosition().distSqr(target.pos())<=100){
                var lines=buffers.getBuffer(RenderType.lines());
                LevelRenderer.renderLineBox(pose,lines,new AABB(target.pos()).inflate(.006D),.95F,.70F,.38F,.9F);
                if(state.scanner()==2&&target.status()>=0)for(var side:Direction.values()){
                    int mode=target.ports()>>>(side.ordinal()*3)&7;if(mode==0)continue;
                    double x=target.pos().getX()+.5D+side.getStepX()*.505D,y=target.pos().getY()+.5D+side.getStepY()*.505D,z=target.pos().getZ()+.5D+side.getStepZ()*.505D;
                    double dx=side.getAxis()==Direction.Axis.X?.015D:.12D,dy=side.getAxis()==Direction.Axis.Y?.015D:.12D,dz=side.getAxis()==Direction.Axis.Z?.015D:.12D;
                    float[] color=portColor(mode);
                    LevelRenderer.renderLineBox(pose,lines,new AABB(x-dx,y-dy,z-dz,x+dx,y+dy,z+dz),color[0],color[1],color[2],pulse);
                }
                buffers.endBatch(RenderType.lines());
            }
        }finally{pose.popPose();}
    }
    private static float[] portColor(int mode){return switch(mode){case 1->new float[]{1F,.85F,.25F};case 2->new float[]{1F,.45F,.12F};case 3->new float[]{.2F,1F,.9F};case 4->new float[]{.2F,.65F,.5F};case 5->new float[]{.3F,.7F,1F};default->new float[]{.4F,.3F,.95F};};}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(!current()||mc.options.hideGui||mc.screen!=null||!ArmorClientConfig.HUD.get())return;
        boolean visor=state.visor()==2&&active(ArmorModuleType.OPTIC_VISOR),scanner=active(ArmorModuleType.FIELD_SCANNER);
        if(!visor&&!scanner)return;
        var g=event.getGuiGraphics();var font=mc.font;int w=Math.min(220,Math.max(120,mc.getWindow().getGuiScaledWidth()/2-16));
        int x=mc.getWindow().getGuiScaledWidth()-w-8,y=8;var target=scanner?state.target():null;
        int h=(target!=null?(target.status()>=0?63:29):scanner?29:6)+(visor?14:0);
        g.fill(x,y,x+w,y+h,0xc0152b29);g.fill(x+w-2,y,x+w,y+h,0xffc08b56);int row=y+6;
        if(scanner){
            if(target==null){g.drawString(font,font.plainSubstrByWidth(Component.translatable("armor.sense.diagnostic."+state.diagnostic().name().toLowerCase(Locale.ROOT)).getString(),w-12),x+6,row,0xffc4d3c3,false);row+=14;}
            else{
                var block=BuiltInRegistries.BLOCK.get(new ResourceLocation(target.block()));
                g.renderItem(new ItemStack(block),x+5,row-2);
                g.drawString(font,font.plainSubstrByWidth(block.getName().getString(),w-32),x+25,row,0xffe4d3b4,false);row+=15;
                if(target.status()>=0){
                    var text=Component.translatable("armor.sense.energy",CopperBatteryItem.amount(target.energy()),CopperBatteryItem.amount(target.capacity()));
                    g.drawString(font,font.plainSubstrByWidth(text.getString(),w-12),x+6,row,0xff7be5d2,false);row+=12;
                    g.fill(x+6,row,x+w-8,row+3,0xff12211e);
                    if(target.capacity()>0)g.fill(x+6,row,x+6+(int)((long)(w-14)*target.energy()/target.capacity()),row+3,0xff43ccbd);row+=7;
                    g.drawString(font,font.plainSubstrByWidth(Component.translatable(MachineStatus.values()[target.status()].translationKey()).getString(),w-12),x+6,row,0xffc4d3c3,false);row+=14;
                }
            }
        }
        if(visor){
            int percent=state.total()==0?0:100*state.checked()/state.total();
            var line=state.scan()==ArmorSenseSnapshot.ScanState.DISABLED?Component.translatable("armor.sense.disabled"):state.scan()==ArmorSenseSnapshot.ScanState.PARTIAL?Component.translatable("armor.sense.partial",state.ores().size()):
                    Component.translatable(state.scan()==ArmorSenseSnapshot.ScanState.READY?"armor.sense.ready":"armor.sense.progress",state.ores().size(),percent);
            g.drawString(font,font.plainSubstrByWidth(line.getString(),w-12),x+6,row,0xff81d8c8,false);
        }
    }
}
