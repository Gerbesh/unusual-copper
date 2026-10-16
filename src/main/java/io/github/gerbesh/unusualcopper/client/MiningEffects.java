package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.MiningTiming;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.MiningNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.ref.WeakReference;
import java.util.*;

/** Persistent fields have their own world render pass and buffers, independent of BE visibility and power. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class MiningEffects {
    private static final int FULL_BRIGHT=0xf000f0;
    private static final RenderType CORE=RenderType.entityTranslucentEmissive(new ResourceLocation("unusualcopper","textures/block/energy.png"));
    private static final RenderType HALO=RenderType.eyes(new ResourceLocation("unusualcopper","textures/block/aqua.png"));
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));
    private static final class Active {
        final MiningNetwork.Extraction packet;final double started,approach;final Vec3 from;boolean sparked;
        Active(MiningNetwork.Extraction packet,double started,Vec3 from,double approach){this.packet=packet;this.started=started;this.from=from;this.approach=approach;}
    }
    private record Flight(UUID runtime,long id,BlockPos pos){}
    private static final class Motion {
        double last=Double.NaN;float activity;final Map<Flight,InterpolatedProgress> progress=new HashMap<>();
        void update(double now,boolean working){double dt=Double.isNaN(last)?0:Math.max(0,Math.min(1,now-last));last=now;activity+=(working?1-activity:-activity)*(float)(1-Math.exp(-dt*.2));}
    }
    private static final ArrayDeque<Active> active=new ArrayDeque<>();
    private static final LinkedHashMap<BlockPos,WeakReference<CopperMachineBlockEntity>> fields=new LinkedHashMap<>();
    private static final Map<CopperMachineBlockEntity,Motion> motion=new WeakHashMap<>();
    private static ClientLevel world;private static long messageTick;private static int messages;
    private static ClientLevel world(){var current=Minecraft.getInstance().level;if(world!=current){world=current;active.clear();fields.clear();motion.clear();messageTick=Long.MIN_VALUE;messages=0;}return current;}
    public static void track(CopperMachineBlockEntity m){var level=world();if(level==null||m.getLevel()!=level||m.mining==null)return;var pos=m.getBlockPos();if(m.isRemoved()||m.mining.area==null){fields.remove(pos);return;}var old=fields.get(pos);if(old!=null&&old.get()==m)return;if(fields.size()>=128)fields.remove(fields.keySet().iterator().next());fields.put(pos.immutable(),new WeakReference<>(m));}
    private static boolean live(ClientLevel level,CopperMachineBlockEntity m){return m!=null&&!m.isRemoved()&&m.getLevel()==level&&m.mining!=null&&m.mining.area!=null&&level.hasChunkAt(m.getBlockPos())&&level.getBlockEntity(m.getBlockPos())==m;}
    public static void accept(MiningNetwork.Extraction r){
        var level=world();if(level==null||!level.dimension().location().equals(r.dimension())||!Double.isFinite(r.sphereX())||!Double.isFinite(r.sphereY())||!Double.isFinite(r.sphereZ())||Math.abs(r.sphereY()-r.origin().getY()-15)>.01||Math.abs(r.sphereX()-r.origin().getX())>129||Math.abs(r.sphereZ()-r.origin().getZ())>129||Math.abs((long)r.target().getX()-r.origin().getX())>128||Math.abs((long)r.target().getZ()-r.origin().getZ())>128||!level.hasChunkAt(r.origin()))return;
        if(!(level.getBlockEntity(r.origin()) instanceof CopperMachineBlockEntity m)||m.mining==null||!r.runtime().equals(m.mining.visualIdentity))return;
        track(m);long now=level.getGameTime();if(messageTick!=now){messageTick=now;messages=0;}if(messages++>=8)return;
        double time=AnimationClock.now();var center=new Vec3(r.sphereX(),r.sphereY(),r.sphereZ());Vec3 from=center;
        var phase=motion.get(m);
        if(phase!=null)for(var job:m.mining.jobs)if(job.pos.equals(r.target())){
            var progress=phase.progress.get(new Flight(m.mining.visualIdentity,job.visualId,job.pos));
            if(progress!=null)from=Vec3.atCenterOf(job.pos).lerp(center,progress.sample(time,job.progress,m.mining.visualRevision,m.status()==MachineStatus.WORKING,MiningTiming.FLIGHT_TICKS)/MiningTiming.FLIGHT_TICKS);
        }
        m.mining.jobs.removeIf(job->job.pos.equals(r.target()));
        while(active.size()>=32)active.removeFirst();active.addLast(new Active(r,time,from,from.distanceToSqr(center)>.01?4:0));
    }
    private static Vec3 sphere(CopperMachineBlockEntity m){var a=m.mining.area;return new Vec3((a.minX()+a.maxX()+1)/2.0, m.getBlockPos().getY()+15,(a.minZ()+a.maxZ()+1)/2.0);}
    private static void quad(PoseStack p,VertexConsumer out,Vec3 a,Vec3 b,Vec3 side,float alpha){for(var v:new Vec3[]{a.add(side),b.add(side),b.subtract(side),a.subtract(side)})out.vertex(p.last().pose(),(float)v.x,(float)v.y,(float)v.z).color(.2F,.95F,1F,alpha).endVertex();}
    private static void beam(PoseStack p,Vec3 a,Vec3 b,float width,float alpha){var out=BUFFERS.getBuffer(RenderType.lightning());var d=b.subtract(a).normalize();var side=d.cross(new Vec3(0,1,0));if(side.lengthSqr()<.001)side=new Vec3(1,0,0);side=side.normalize().scale(width);quad(p,out,a,b,side,alpha);quad(p,out,a,b,d.cross(side).normalize().scale(width),alpha);}
    private static void rings(CopperMachineBlockEntity m,float time,PoseStack pose,boolean luminous){
        var center=sphere(m);var out=luminous?BUFFERS.getBuffer(HALO):VisualGeometry.material(BUFFERS,"copper",false);
        for(int ring=0;ring<3;ring++){
            pose.pushPose();pose.translate(center.x,center.y,center.z);pose.mulPose(Axis.XP.rotationDegrees(ring*60));pose.mulPose(Axis.YP.rotationDegrees(time*(ring%2==0?.7F:-.55F)));
            for(int i=0;i<16;i++){pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(i*22.5F));
                float z=MiningSphereMesh.RADIUS*16+2;
                if(luminous)VisualGeometry.box(pose,out,-4,-.28F,z-.3F,4,.28F,z-.1F,.45F,1,1,.8F,FULL_BRIGHT);
                else VisualGeometry.box(pose,out,-8.2F,-.8F,z,8.2F,.8F,z+1.6F,1,1,1,1,FULL_BRIGHT);
                pose.popPose();
            }pose.popPose();
        }
    }
    private static void shell(PoseStack pose,Vec3 center,float radius,float alpha){
        var out=BUFFERS.getBuffer(CORE);
        for(var v:MiningSphereMesh.VERTICES)out.vertex(pose.last().pose(),(float)center.x+v.x()*radius,(float)center.y+v.y()*radius,(float)center.z+v.z()*radius)
            .color(.35F,.95F,1F,alpha).uv(v.u(),v.v()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(pose.last().normal(),v.x(),v.y(),v.z()).endVertex();
    }
    private static void travellingBlocks(CopperMachineBlockEntity m,double now,PoseStack pose){
        var phase=motion.get(m);var present=new HashSet<Flight>();var center=sphere(m);boolean working=m.status()==MachineStatus.WORKING;
        for(var job:m.mining.jobs){
            var key=new Flight(m.mining.visualIdentity,job.visualId,job.pos);present.add(key);
            double ticks=phase.progress.computeIfAbsent(key,k->new InterpolatedProgress()).sample(now,job.progress,m.mining.visualRevision,working,MiningTiming.FLIGHT_TICKS);
            float progress=MiningTiming.fraction((float)ticks);
            var pos=Vec3.atCenterOf(job.pos).lerp(center,progress);
            pose.pushPose();pose.translate(pos.x,pos.y,pos.z);pose.mulPose(Axis.YP.rotationDegrees((float)(now%7200)*3));pose.scale(.38F,.38F,.38F);pose.translate(-.5,-.5,-.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(job.state,pose,BUFFERS,FULL_BRIGHT,OverlayTexture.NO_OVERLAY);pose.popPose();
        }phase.progress.keySet().retainAll(present);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var level=world();if(level==null)return;
        fields.entrySet().removeIf(e2->!live(level,e2.getValue().get()));
        double now=AnimationClock.now();active.removeIf(a->now-a.started>44+a.approach);
        for(var a:active)if(!a.sparked&&now-a.started>=12+a.approach){a.sparked=true;for(int i=0;i<6;i++)level.addParticle(ParticleTypes.ELECTRIC_SPARK,a.packet.sphereX(),a.packet.sphereY(),a.packet.sphereZ(),(level.random.nextDouble()-.5)*.2,(level.random.nextDouble()-.5)*.2,(level.random.nextDouble()-.5)*.2);}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;var level=world();if(level==null||fields.isEmpty()&&active.isEmpty())return;
        var pose=e.getPoseStack();var camera=e.getCamera().getPosition();double now=AnimationClock.now();float time=(float)(now%72000);
        var visible=fields.values().stream().map(WeakReference::get).filter(m->live(level,m)).filter(m->sphere(m).distanceToSqr(camera)<224*224).sorted(Comparator.comparingDouble(m->sphere(m).distanceToSqr(camera))).limit(16).toList();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        try{
            for(var m:visible){motion.computeIfAbsent(m,key->new Motion()).update(now,m.status()==MachineStatus.WORKING);rings(m,time,pose,false);travellingBlocks(m,now,pose);}BUFFERS.endBatch();
            for(int index=visible.size()-1;index>=0;index--){var m=visible.get(index);
                var center=sphere(m);var phase=motion.get(m);rings(m,time,pose,true);
                // Size, minimum opacity and rotation never depend on power, status or the number of beams.
                shell(pose,center,MiningSphereMesh.RADIUS+(float)Math.sin(time*.06)*.025F,.42F+phase.activity*.1F);
                shell(pose,center,.8F+(float)Math.sin(time*.09)*.04F,.75F);
                beam(pose,Vec3.atCenterOf(m.getBlockPos()).add(0,.5,0),center,.045F,.45F+phase.activity*.2F);
                for(var job:m.mining.jobs)beam(pose,center,Vec3.atCenterOf(job.pos),.025F,.7F);
            }BUFFERS.endBatch();
            int rendered=0;
            for(var a:active){
                if(rendered++>=16)break;var r=a.packet;if(!level.hasChunkAt(r.origin())||!(level.getBlockEntity(r.origin()) instanceof CopperMachineBlockEntity m)||!live(level,m)||!r.runtime().equals(m.mining.visualIdentity))continue;
                float age=(float)(now-a.started-a.approach);var center=new Vec3(r.sphereX(),r.sphereY(),r.sphereZ());pose.pushPose();
                if(age<0){var pos=a.from.lerp(center,Math.max(0,Math.min(1,(age+a.approach)/a.approach)));pose.translate(pos.x,pos.y,pos.z);pose.mulPose(Axis.YP.rotationDegrees((float)(now%7200)*3));pose.scale(.38F,.38F,.38F);pose.translate(-.5,-.5,-.5);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Block.stateById(r.block()),pose,BUFFERS,FULL_BRIGHT,OverlayTexture.NO_OVERLAY);}
                else if(age<12){float swirl=.35F*Math.min(1,age/2);pose.translate(center.x+Math.sin(age*.6)*swirl,center.y,center.z+Math.cos(age*.6)*swirl);pose.mulPose(Axis.YP.rotationDegrees(age*25));float scale=.38F*(1-age/14);pose.scale(scale,scale,scale);pose.translate(-.5,-.5,-.5);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Block.stateById(r.block()),pose,BUFFERS,FULL_BRIGHT,OverlayTexture.NO_OVERLAY);}
                else if(!r.loot().isEmpty()){float t=Math.min(1,(age-12)/30);var destination=Vec3.atCenterOf(r.output());var pos=center.lerp(destination,t).add(0,Math.sin(t*Math.PI)*1.5,0);beam(pose,center,destination,.015F,(1-t)*.45F);pose.translate(pos.x,pos.y,pos.z);pose.scale(.5F,.5F,.5F);Minecraft.getInstance().getItemRenderer().renderStatic(r.loot(),ItemDisplayContext.GROUND,FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,BUFFERS,level,0);}
                pose.popPose();
            }
        }finally{BUFFERS.endBatch();pose.popPose();}
    }
    private MiningEffects(){}
}
