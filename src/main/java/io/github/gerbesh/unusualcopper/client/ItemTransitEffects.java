package io.github.gerbesh.unusualcopper.client;

import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlock;
import io.github.gerbesh.unusualcopper.network.ItemTransitNetwork.Trail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ItemTransitEffects {
    private record Flight(Trail trail,ItemStack icon,double start){}
    private static final ArrayDeque<Flight> flights=new ArrayDeque<>();
    private static ClientLevel world;private static long messageTick=Long.MIN_VALUE;private static int messages;
    private static ClientLevel world(){var current=Minecraft.getInstance().level;if(world!=current){flights.clear();world=current;messageTick=Long.MIN_VALUE;messages=0;}return current;}
    public static void accept(Trail trail){var level=world();if(level==null||!trail.dimension().equals(level.dimension().location()))return;long now=level.getGameTime();if(messageTick!=now){messageTick=now;messages=0;}if(messages++>=8)return;var icon=new ItemStack(BuiltInRegistries.ITEM.byId(trail.item()));if(icon.isEmpty())return;while(flights.size()>=64)flights.removeFirst();flights.addLast(new Flight(trail,icon,AnimationClock.now()));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var level=world();if(level!=null)flights.removeIf(f->AnimationClock.now()-f.start>=f.trail.duration());}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        // Draw before translucent glass, so its depth does not hide the icons inside the tube.
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)return;var level=world();if(level==null||flights.isEmpty())return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();int drawn=0;
        for(var f:flights){if(drawn>=32)break;double age=AnimationClock.now()-f.start;if(age<0||age>=f.trail.duration())continue;var path=f.trail.path();double step=age/f.trail.duration()*(path.size()-1);int i=Math.min(path.size()-2,(int)step);var a=path.get(i);var b=path.get(i+1);
            if(!level.hasChunkAt(a)||!level.hasChunkAt(b)||i>0&&!(level.getBlockState(a).getBlock() instanceof ItemPipeBlock)||i+1<path.size()-1&&!(level.getBlockState(b).getBlock() instanceof ItemPipeBlock))continue;
            var point=Vec3.atCenterOf(a).lerp(Vec3.atCenterOf(b),step-i);if(point.distanceToSqr(camera)>64*64)continue;
            pose.pushPose();pose.translate(point.x-camera.x,point.y-camera.y,point.z-camera.z);pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.32F,.32F,.32F);
            mc.getItemRenderer().renderStatic(f.icon,ItemDisplayContext.FIXED,0xf000f0,OverlayTexture.NO_OVERLAY,pose,buffers,level,0);pose.popPose();drawn++;
        }if(drawn>0)buffers.endBatch();
    }
    private ItemTransitEffects(){}
}
