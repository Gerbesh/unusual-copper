package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.ReaperState;
import io.github.gerbesh.unusualcopper.network.MachineEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Bounded, disposable visuals. No entity spawn, inventory mutation or client operation request. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ReaperEffects {
    public static final int MAX_EVENTS=16, MAX_MESSAGES_PER_TICK=8, MAX_ICONS_PER_FRAME=24;
    public static final int MAX_TRAILS_PER_TICK=16;
    private static final LinkedHashMap<UUID,Active> ACTIVE=new LinkedHashMap<>();
    private static ClientLevel world;
    private static long messageTick=Long.MIN_VALUE;
    private static int messages;
    private static long windups, strikes, transfers, runeFrames, iconFrames, compressedModels;
    private static int peakIcons;
    public record Statistics(long windups,long strikes,long transfers,long runeFrames,long iconFrames,long compressedModels,int active,int peakIcons) {}
    public static Statistics statistics() { return new Statistics(windups,strikes,transfers,runeFrames,iconFrames,compressedModels,ACTIVE.size(),peakIcons); }
    public static int youngestAge(int phase) {
        var level=world(); if(level==null) return -1;
        return ACTIVE.values().stream().filter(active -> active.event.phase()==phase).mapToInt(active -> (int)(level.getGameTime()-active.start)).min().orElse(-1);
    }
    private static final class Active {
        final MachineEffects.Reap event; final long start; final List<ItemStack> icons;
        Active(MachineEffects.Reap event,long start) {
            this.event=event; this.start=start;
            icons=Arrays.stream(event.icons()).mapToObj(id -> new ItemStack(BuiltInRegistries.ITEM.byId(id))).filter(stack -> !stack.isEmpty()).toList();
        }
    }
    private static ClientLevel world() {
        var current=Minecraft.getInstance().level;
        if(current!=world) { ACTIVE.clear(); world=current; messageTick=Long.MIN_VALUE; messages=0; peakIcons=0; windups=strikes=transfers=runeFrames=iconFrames=compressedModels=0; }
        return current;
    }
    public static void accept(MachineEffects.Reap event) {
        var level=world(); if(level==null || !level.dimension().location().equals(event.dimension())) return;
        if(event.phase()==MachineEffects.CANCEL) { ACTIVE.remove(event.action()); return; }
        long now=level.getGameTime(); if(now!=messageTick) { messageTick=now; messages=0; }
        if(messages>=MAX_MESSAGES_PER_TICK) return; messages++;
        while(ACTIVE.size()>=MAX_EVENTS && !ACTIVE.containsKey(event.action())) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        var previous=ACTIVE.get(event.action());
        if(previous!=null && previous.event.phase()!=MachineEffects.WINDUP && event.phase()==MachineEffects.WINDUP) return;
        ACTIVE.put(event.action(),new Active(event,now));
        if(event.phase()==MachineEffects.WINDUP) windups++; else if(event.phase()==MachineEffects.STRIKE) strikes++; else transfers++;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var level=world(); if(level==null) return;
        ACTIVE.values().removeIf(active -> { long age=level.getGameTime()-active.start; return age<0 || age>=(active.event.phase()==MachineEffects.WINDUP?ReaperState.WINDUP_TICKS:20); });
        int trails=0;
        for(var active:ACTIVE.values()) if(active.event.phase()!=MachineEffects.WINDUP) for(int i=0;i<active.icons.size() && trails<MAX_TRAILS_PER_TICK;i++) {
            double t=(level.getGameTime()-active.start-i*1.2)/16.0; if(t<0 || t>=1) continue;
            var point=active.event.from().lerp(Vec3.atCenterOf(active.event.to()),t).add(0,Math.sin(t*Math.PI)*(1.1+i*.12),0);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,point.x,point.y,point.z,0,.005,0); trails++;
        }
    }
    @SubscribeEvent public static void compress(net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> event) {
        var level=world(); if(level==null) return;
        for(var active:ACTIVE.values()) {
            boolean preparing=active.event.phase()==MachineEffects.WINDUP;
            if(!preparing && active.event.phase()!=MachineEffects.STRIKE || !active.event.target().equals(event.getEntity().getUUID())) continue;
            double age=level.getGameTime()-active.start+event.getPartialTick(); if(age<0 || age>=(preparing?ReaperState.WINDUP_TICKS:20)) return;
            // Keep the real corpse visually inside the flash instead of popping back to full size.
            float scale=preparing?(float)(1-.9*age/ReaperState.WINDUP_TICKS):(float)Math.max(.001,.1*(1-Math.min(1,age/4)));
            // EntityRenderDispatcher owns the surrounding push/pop, including cancelled renders.
            // This changes only the current entity's drawing; server position and hitbox stay real.
            var pose=event.getPoseStack(); pose.translate(0,event.getEntity().getBbHeight()*.55*(1-scale),0); pose.scale(scale,scale,scale); compressedModels++; return;
        }
    }
    private static void line(PoseStack pose,VertexConsumer vertices,Vec3 a,Vec3 b,float red,float green,float blue,float alpha) {
        var normal=b.subtract(a).normalize(); var entry=pose.last();
        vertices.vertex(entry.pose(),(float)a.x,(float)a.y,(float)a.z).color(red,green,blue,alpha).normal(entry.normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
        vertices.vertex(entry.pose(),(float)b.x,(float)b.y,(float)b.z).color(red,green,blue,alpha).normal(entry.normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
    private static Vec3 point(double angle,double radius) { return new Vec3(Math.cos(angle)*radius,0,Math.sin(angle)*radius); }
    private static void rune(PoseStack pose,VertexConsumer vertices,double radius,double rotation,float alpha) {
        for(int i=0;i<16;i++) line(pose,vertices,point(rotation+Math.PI*2*i/16,radius),point(rotation+Math.PI*2*(i+1)/16,radius),.25F,1F,.9F,alpha);
        for(int i=0;i<8;i++) line(pose,vertices,point(rotation+Math.PI*2*i/8,radius*.78),point(rotation+Math.PI*2*(i+3)/8,radius*.78),1F,.65F,.32F,alpha);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var level=world(); if(level==null || ACTIVE.isEmpty()) return;
        var mc=Minecraft.getInstance(); var camera=event.getCamera().getPosition(); var pose=event.getPoseStack(); var buffers=mc.renderBuffers().bufferSource();
        int iconsRendered=0;
        for(var active:ACTIVE.values()) {
            var effect=active.event; float age=(float)(level.getGameTime()-active.start)+event.getPartialTick();
            boolean charging=effect.phase()==MachineEffects.WINDUP;
            if(age<0 || age>=(charging?ReaperState.WINDUP_TICKS:20)) continue;
            Vec3 source=effect.from(); double radius=.7;
            if(charging) {
                var entity=level.getEntity(effect.entity());
                if(entity!=null && entity.getUUID().equals(effect.target()) && entity.position().distanceToSqr(effect.from())<40*40) { source=entity.getPosition(event.getPartialTick()).add(0,entity.getBbHeight()*.55,0); radius=Math.max(.5,entity.getBbWidth()*.8); }
            }
            if(charging || effect.phase()==MachineEffects.STRIKE && age<4) {
                double progress=charging?age/ReaperState.WINDUP_TICKS:age/4;
                pose.pushPose(); pose.translate(source.x-camera.x,source.y-camera.y,source.z-camera.z);
                rune(pose,buffers.getBuffer(RenderType.lines()),charging?radius*(1-progress)+.12:.12+progress*.8,age*.12,(float)(charging?.9:1-progress));
                pose.mulPose(Axis.XP.rotationDegrees(90)); rune(pose,buffers.getBuffer(RenderType.lines()),charging?radius*(1-progress)+.12:.12+progress*.8,-age*.12,(float)(charging?.9:1-progress));
                pose.popPose(); runeFrames++;
            }
            if(!charging) for(int i=0;i<active.icons.size() && iconsRendered<MAX_ICONS_PER_FRAME;i++) {
                double t=Math.max(0,Math.min(1,(age-i*1.2)/16.0)); if(t>=1) continue;
                var destination=Vec3.atCenterOf(effect.to()); var position=source.lerp(destination,t).add(0,Math.sin(t*Math.PI)*(1.1+i*.12),0);
                pose.pushPose(); pose.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation()); pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.ZP.rotationDegrees((float)(Math.sin(age*.2+i)*12))); pose.scale(.8F,.8F,.8F);
                mc.getItemRenderer().renderStatic(active.icons.get(i),ItemDisplayContext.GROUND,0xf000f0,OverlayTexture.NO_OVERLAY,pose,buffers,level,i);
                pose.popPose(); iconsRendered++; iconFrames++;
            }
        }
        buffers.endBatch(); peakIcons=Math.max(peakIcons,iconsRendered);
    }
    private ReaperEffects() {}
}
