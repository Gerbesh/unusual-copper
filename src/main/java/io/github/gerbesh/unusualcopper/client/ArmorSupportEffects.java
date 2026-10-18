package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.armor.ArmorEffectEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Small, depth-tested body pulses, interpolated with the render clock rather than packet ticks. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ArmorSupportEffects {
    private record Pulse(ArmorEffectEvents.Burst burst,double started){}
    private static final List<Pulse> ACTIVE=new ArrayList<>();
    private static ClientLevel world;
    public static void accept(ArmorEffectEvents.Burst burst){
        var mc=Minecraft.getInstance();refresh();
        if(mc.level==null||!mc.level.dimension().location().equals(burst.dimension())||mc.level.getEntity(burst.entity())==null)return;
        double now=AnimationClock.now()/20D;
        // Fire can damage every tick; refresh at most once per 180 ms, without building a queue.
        if(ACTIVE.stream().anyMatch(v->v.burst.entity()==burst.entity()&&v.burst.kind()==burst.kind()&&now-v.started<.18D))return;
        if(ACTIVE.size()>=64)ACTIVE.remove(0);ACTIVE.add(new Pulse(burst,now));
    }
    private static void refresh(){var level=Minecraft.getInstance().level;if(level!=world){world=level;ACTIVE.clear();}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)refresh();}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();refresh();if(world==null)return;
        double now=AnimationClock.now()/20D;ACTIVE.removeIf(p->now-p.started>.85D||world.getEntity(p.burst.entity())==null);
        if(mc.options.particles().get()==net.minecraft.client.ParticleStatus.MINIMAL||ACTIVE.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());var pose=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        try{for(var pulse:ACTIVE){
            var entity=world.getEntity(pulse.burst.entity());
            if(entity==null||entity.distanceToSqr(camera.x,camera.y,camera.z)>48*48||entity==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
            double age=now-pulse.started,t=Math.min(1,age/.85D),ease=t*t*(3-2*t);
            double x=Mth.lerp(event.getPartialTick(),entity.xo,entity.getX());
            double y=Mth.lerp(event.getPartialTick(),entity.yo,entity.getY());
            double z=Mth.lerp(event.getPartialTick(),entity.zo,entity.getZ());
            var kind=pulse.burst.kind();
            float r=kind==ArmorEffectEvents.Kind.THERMAL?1F:kind==ArmorEffectEvents.Kind.HEAL?.35F:.92F;
            float g=kind==ArmorEffectEvents.Kind.THERMAL?.48F:kind==ArmorEffectEvents.Kind.HEAL?1F:.77F;
            float b=kind==ArmorEffectEvents.Kind.THERMAL?.18F:kind==ArmorEffectEvents.Kind.HEAL?.78F:.40F;
            float alpha=(float)(Math.min(1,t*10)*Math.pow(1-t,1.4D));
            int count=mc.options.particles().get()==net.minecraft.client.ParticleStatus.DECREASED?1:pulse.burst.level()+1;
            for(int ring=0;ring<count;ring++){
                double height=kind==ArmorEffectEvents.Kind.HEAL?.28+ease*1.05+ring*.18:.75+ring*.24;
                double radius=kind==ArmorEffectEvents.Kind.STRENGTH?.35+ease*.38:.36+Math.sin(ease*Math.PI)*.15;
                int segments=32;
                for(int i=0;i<segments;i++){
                    // Short breaks make a recognisable copper coil rather than a solid soap bubble.
                    if(i%8==7)continue;
                    double a=2*Math.PI*i/segments+age*1.6,bb=2*Math.PI*(i+1)/segments+age*1.6;
                    line(pose,lines,x+Math.cos(a)*radius,y+height,z+Math.sin(a)*radius,
                            x+Math.cos(bb)*radius,y+height,z+Math.sin(bb)*radius,r,g,b,alpha);
                }
            }
        }}finally{pose.popPose();buffers.endBatch(RenderType.lines());}
    }
    private static void line(PoseStack pose,VertexConsumer out,double x,double y,double z,double xx,double yy,double zz,float r,float g,float b,float alpha){
        var matrix=pose.last();double length=Math.sqrt((xx-x)*(xx-x)+(yy-y)*(yy-y)+(zz-z)*(zz-z));
        float nx=(float)((xx-x)/length),ny=(float)((yy-y)/length),nz=(float)((zz-z)/length);
        out.vertex(matrix.pose(),(float)x,(float)y,(float)z).color(r,g,b,alpha).normal(matrix.normal(),nx,ny,nz).endVertex();
        out.vertex(matrix.pose(),(float)xx,(float)yy,(float)zz).color(r,g,b,alpha).normal(matrix.normal(),nx,ny,nz).endVertex();
    }
    private ArmorSupportEffects(){}
}
