package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.*;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.network.MachineEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class SentinelEffects {
    private record Active(MachineEffects.Beam beam,long start) {}
    private static final ArrayDeque<Active> ACTIVE=new ArrayDeque<>();
    private static ClientLevel world;
    private static long messageTick=Long.MIN_VALUE;
    private static int messages;
    public static long accepted,rendered;
    private static ClientLevel world() { var current=Minecraft.getInstance().level; if(current!=world) { world=current; ACTIVE.clear(); messageTick=Long.MIN_VALUE; messages=0; accepted=rendered=0; } return current; }
    public static int youngest() { var level=world(); return level==null||ACTIVE.isEmpty()?-1:(int)(level.getGameTime()-ACTIVE.getLast().start); }
    public static int activeCount() { world(); return ACTIVE.size(); }
    public static void accept(MachineEffects.Beam beam) {
        var level=world(); if(level==null||!level.dimension().location().equals(beam.dimension())) return;
        long now=level.getGameTime(); if(messageTick!=now) {messageTick=now;messages=0;} if(messages++>=8) return;
        while(ACTIVE.size()>=16) ACTIVE.removeFirst(); ACTIVE.addLast(new Active(beam,now)); accepted++;
        var from=Vec3.atCenterOf(beam.origin()).add(0,1.15,0);
        level.playLocalSound(from.x,from.y,from.z,SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,.45F,1.5F,false);
        for(int i=0;i<4;i++) { var p=from.lerp(beam.target(),(i+1)/5.0); level.addParticle(ParticleTypes.END_ROD,p.x,p.y,p.z,0,.01,0); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) { if(e.phase!=TickEvent.Phase.END) return; var level=world(); if(level!=null) ACTIVE.removeIf(a->level.getGameTime()<a.start||level.getGameTime()-a.start>=8); }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return; var level=world(); if(level==null||ACTIVE.isEmpty()) return;
        var pose=e.getPoseStack(); var camera=e.getCamera().getPosition(); var buffers=Minecraft.getInstance().renderBuffers().bufferSource(); var vertices=buffers.getBuffer(RenderType.lightning());
        pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
        for(var a:ACTIVE) {
            float age=level.getGameTime()-a.start+e.getPartialTick(); if(age<0||age>=8) continue;
            var from=Vec3.atCenterOf(a.beam.origin()).add(0,1.15,0); var to=a.beam.target(); float alpha=(1-age/8)*.85F;
            var direction=to.subtract(from).normalize(); var side=direction.cross(new Vec3(0,1,0)); if(side.lengthSqr()<.01) side=new Vec3(1,0,0); side=side.normalize().scale(.025);
            quad(pose,vertices,from,to,side,alpha); quad(pose,vertices,from,to,direction.cross(side).normalize().scale(.025),alpha); rendered++;
        }
        pose.popPose(); buffers.endBatch(RenderType.lightning());
    }
    private static void quad(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to,Vec3 side,float alpha) {
        for(var p:new Vec3[]{from.add(side),to.add(side),to.subtract(side),from.subtract(side)}) out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(.25F,1F,.9F,alpha).endVertex();
    }
    private SentinelEffects() {}
}
