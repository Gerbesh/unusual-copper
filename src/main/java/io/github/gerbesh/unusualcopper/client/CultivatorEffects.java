package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.*;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.network.MachineEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.*;

/** Disposable visuals with global budgets; every crop flash follows a committed server operation. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class CultivatorEffects {
    private static final int MAX_FRONTS=12,MAX_PLANTS=48,MAX_MESSAGES=16,MAX_LINES=512,MAX_PARTICLES=64;
    private static final LinkedHashMap<BlockPos,Front> FRONTS=new LinkedHashMap<>();
    private static final ArrayDeque<Plant> PLANTS=new ArrayDeque<>();
    private static final LinkedHashMap<BlockPos,Double> PICKUP_ARRIVALS=new LinkedHashMap<>();
    private static final DustParticleOptions AQUA=new DustParticleOptions(new Vector3f(.2F,1F,.85F),.85F);
    private static final DustParticleOptions GOLD=new DustParticleOptions(new Vector3f(1F,.65F,.2F),.8F);
    private static ClientLevel world;
    private static long messageTick=Long.MIN_VALUE;
    private static int messages;
    private record Plant(BlockPos pos,int icon,double start) {}
    private record Front(MachineEffects.Cultivation event,double start,float previous) {
        float radius(double time) {return previous+(event.to()-previous)*(float)Math.max(0,Math.min(1,(time-start)/3));}
    }
    private static ClientLevel world() {
        var level=Minecraft.getInstance().level;
        if(level!=world){world=level;FRONTS.clear();PLANTS.clear();PICKUP_ARRIVALS.clear();messageTick=Long.MIN_VALUE;messages=0;}
        return level;
    }
    public static void accept(MachineEffects.Cultivation event) {
        var level=world();if(level==null||!level.dimension().location().equals(event.dimension()))return;
        long now=level.getGameTime();if(messageTick!=now){messageTick=now;messages=0;}if(messages++>=MAX_MESSAGES)return;
        double visualNow=AnimationClock.now();var previous=FRONTS.remove(event.origin());
        float from=previous==null||event.from()<previous.event.to()-.01F?event.from():previous.radius(visualNow);
        while(FRONTS.size()>=MAX_FRONTS)FRONTS.remove(FRONTS.keySet().iterator().next());
        FRONTS.put(event.origin(),new Front(event,visualNow,from));
        for(var plant:event.plants()) {
            while(PLANTS.size()>=MAX_PLANTS)PLANTS.removeFirst();
            PLANTS.addLast(new Plant(plant.pos(),plant.icon(),visualNow));
            if(plant.icon()>0) {
                PICKUP_ARRIVALS.remove(event.origin());while(PICKUP_ARRIVALS.size()>=MAX_FRONTS)PICKUP_ARRIVALS.remove(PICKUP_ARRIVALS.keySet().iterator().next());
                PICKUP_ARRIVALS.put(event.origin(),visualNow+20);
                ReaperEffects.cultivatorPickup(new MachineEffects.Reap(event.dimension(),event.origin(),UUID.randomUUID(),
                        MachineEffects.TRANSFER,-1,new UUID(0,0),Vec3.atCenterOf(plant.pos()).add(0,.2,0),event.origin(),new int[]{plant.icon()}));
            }
        }
    }
    public static void delivery(MachineEffects.CultivationDelivery event) {
        var level=world();if(level==null||!level.dimension().location().equals(event.dimension()))return;
        int delay=(int)Math.max(0,Math.min(20,PICKUP_ARRIVALS.getOrDefault(event.origin(),0.0)-AnimationClock.now()));
        ReaperEffects.accept(new MachineEffects.Reap(event.dimension(),event.origin(),UUID.randomUUID(),MachineEffects.TRANSFER,
                -1,new UUID(0,0),Vec3.atCenterOf(event.origin()),event.storage(),new int[]{event.icon()}),delay);
    }
    public static float activity(BlockPos origin) {
        var level=world();var front=FRONTS.get(origin);if(level==null||front==null)return 0;
        double age=AnimationClock.now()-front.start;return age<0?0:(float)Math.max(0,1-age/10);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;var level=world();if(level==null)return;
        double now=AnimationClock.now();FRONTS.values().removeIf(f->now<f.start||now-f.start>=10);
        PLANTS.removeIf(p->now<p.start||now-p.start>=16);
        PICKUP_ARRIVALS.values().removeIf(arrival->arrival<now||arrival>now+20);
        int budget=MAX_PARTICLES;
        for(var plant:PLANTS) {
            if(budget<2)break;double age=now-plant.start,t=age/16.0;
            for(int arm=0;arm<2;arm++,budget--) {
                double angle=age*.65+arm*Math.PI,radius=plant.icon==0?.35:.55*(1-t);
                var p=Vec3.atCenterOf(plant.pos).add(Math.cos(angle)*radius,-.35+t*(plant.icon==0?1.3:.35),Math.sin(angle)*radius);
                level.addParticle(plant.icon==0?AQUA:GOLD,p.x,p.y,p.z,0,.015,0);
            }
        }
    }
    private static void line(PoseStack pose,VertexConsumer vertices,Vec3 a,Vec3 b,float red,float green,float blue,float alpha) {
        var n=b.subtract(a).normalize();var entry=pose.last();
        vertices.vertex(entry.pose(),(float)a.x,(float)a.y,(float)a.z).color(red,green,blue,alpha).normal(entry.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
        vertices.vertex(entry.pose(),(float)b.x,(float)b.y,(float)b.z).color(red,green,blue,alpha).normal(entry.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
    private static boolean inside(Vec3 p,MachineEffects.Cultivation wave) {
        return p.x>=wave.min().getX()&&p.x<=wave.max().getX()+1&&p.z>=wave.min().getZ()&&p.z<=wave.max().getZ()+1;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var level=world();if(level==null || FRONTS.isEmpty()&&PLANTS.isEmpty())return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();var pose=event.getPoseStack();
        var buffers=mc.renderBuffers().bufferSource();var vertices=buffers.getBuffer(RenderType.lines());int budget=MAX_LINES;
        double now=AnimationClock.now();
        for(var front:FRONTS.values()) {
            double age=now-front.start;if(age<0||age>=10)continue;var wave=front.event;
            if(camera.distanceToSqr(Vec3.atCenterOf(wave.origin()))>80*80)continue;
            double radius=front.radius(now);float alpha=(float)Math.min(1,(10-age)/5);
            // Two close contours make the moving field readable without flooding it with particles.
            for(int band=0;band<2;band++)for(int i=0;i<96&&budget>0;i++,budget--) {
                double a=Math.PI*2*i/96,b=Math.PI*2*(i+1)/96,r=Math.max(0,radius-band*.18);
                var p=new Vec3(wave.origin().getX()+.5+Math.cos(a)*r,wave.min().getY()+.09+band*.06,wave.origin().getZ()+.5+Math.sin(a)*r);
                var q=new Vec3(wave.origin().getX()+.5+Math.cos(b)*r,p.y,wave.origin().getZ()+.5+Math.sin(b)*r);
                if(inside(p,wave)&&inside(q,wave))line(pose,vertices,p.subtract(camera),q.subtract(camera),.18F,1F,.85F,alpha);
            }
        }
        for(var plant:PLANTS) {
            double age=now-plant.start;if(age<0||age>=16||budget<=0)continue;
            if(camera.distanceToSqr(Vec3.atCenterOf(plant.pos))>80*80)continue;
            boolean growing=plant.icon==0;float alpha=(float)(1-age/16);
            var center=Vec3.atCenterOf(plant.pos).add(0,-.35,0).subtract(camera);
            for(int i=0;i<12&&budget>0;i++,budget--) {
                double a=age*.18+Math.PI*2*i/12,b=age*.18+Math.PI*2*(i+1)/12;
                double radius=growing?.3:.6*(1-age/20);
                double y=growing?age*.055+i*.035:.25;
                var p=center.add(Math.cos(a)*radius,y,Math.sin(a)*radius);
                var q=center.add(Math.cos(b)*radius,y+(growing?.035:0),Math.sin(b)*radius);
                line(pose,vertices,p,q,growing?.2F:1F,growing?1F:.65F,growing?.85F:.2F,alpha);
            }
        }
        buffers.endBatch(RenderType.lines());
    }
    private CultivatorEffects() {}
}
