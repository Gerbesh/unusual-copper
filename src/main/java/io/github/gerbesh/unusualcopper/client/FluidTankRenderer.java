package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.fluid.tank.*;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Shared frame and real fluid for placed/held tanks. Late cube packets cannot rewind the plane. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class FluidTankRenderer {
    private static final class Motion {
        final TankSurfaceAnimation<Fluid> plane,gauge;TankView summary;
        Motion(TankView view,double now){plane=new TankSurfaceAnimation<>(view.surface(),now);gauge=new TankSurfaceAnimation<>(ratio(view),now);summary=view;}
    }
    private record Key(UUID identity,long shape){}
    private static final LinkedHashMap<Key,Motion> MOTION=new LinkedHashMap<>(64,.75F,true);
    private static ClientLevel world;private static double cleaned;
    private FluidTankRenderer(){}
    private static void refresh(){var level=Minecraft.getInstance().level;if(level!=world){world=level;MOTION.clear();cleaned=0;}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)refresh();}
    private static boolean linked(TankView view,Direction side){return (view.connections()&1<<side.ordinal())!=0;}
    private static double ratio(TankView view){return Math.min(1,view.amount()/(double)view.nominalCapacity());}
    private static Motion motion(TankView view,double now){
        var key=new Key(view.identity(),view.shape());var current=MOTION.get(key);if(current!=null)return current;
        if(MOTION.size()>=2048)MOTION.remove(MOTION.keySet().iterator().next());
        var created=new Motion(view,now);MOTION.put(key,created);return created;
    }
    private static void accept(Motion motion,TankView view,Fluid fluid,double now){
        motion.plane.advance(now);motion.gauge.advance(now);Fluid incoming=fluid==Fluids.EMPTY?null:fluid;
        motion.plane.accept(view.revision(),view.surface(),incoming);motion.gauge.accept(view.revision(),ratio(view),incoming);
        if(view.revision()>motion.summary.revision())motion.summary=view;
    }
    public static void receive(TankNetworks.Visual message){
        refresh();if(world==null||world!=message.level())return;
        double now=AnimationClock.now();if(MOTION.size()>2048)MOTION.remove(MOTION.keySet().iterator().next());
        accept(motion(message.view(),now),message.view(),message.fluid(),now);
    }
    static TankView summary(TankView local){
        refresh();var state=MOTION.get(new Key(local.identity(),local.shape()));
        return state!=null&&state.summary.revision()>local.revision()?state.summary:local;
    }
    public static void render(CopperMachineBlockEntity m,PoseStack pose,MultiBufferSource buffers,int light){
        if(m.tankUnloaded)return;
        refresh();var view=m.tankView;double now=AnimationClock.now();
        if(now-cleaned>200){MOTION.values().removeIf(v->now-v.plane.touched()>1200);cleaned=now;}
        if(MOTION.size()>2048)MOTION.remove(MOTION.keySet().iterator().next());
        var motion=motion(view,now);accept(motion,view,m.visualFluid,now);
        var facing=m.getBlockState().getValue(io.github.gerbesh.unusualcopper.machine.CopperMachineBlock.FACING);
        var gaugeFace=gaugeFace(view,facing);frame(view,pose,buffers,light,gaugeFace);var fluid=motion.plane.material();
        if(fluid!=null){int color=net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor();
            if(fluid==Fluids.WATER&&world!=null)color=net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(world,m.getBlockPos());
            liquid(fluid,color,(float)((motion.plane.value()-m.getBlockPos().getY())*16)+.03F,(float)motion.gauge.value(),view,pose,buffers,light,gaugeFace);
        }
    }
    public static void item(ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light){
        frame(TankView.EMPTY,pose,buffers,light,Direction.NORTH);var fluid=TankItemStorage.contents(stack);if(fluid.isEmpty())return;
        float fill=Math.min(1,fluid.getAmount()/(float)TankVolumes.PER_BLOCK);
        int color=net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
        liquid(fluid.getFluid(),color,fill*16+.03F,fill,TankView.EMPTY,pose,buffers,light,Direction.NORTH);
    }
    private static Direction gaugeFace(TankView view,Direction preferred){return !linked(view,preferred)?preferred:!linked(view,Direction.NORTH)?Direction.NORTH:Direction.WEST;}
    private static void frame(TankView view,PoseStack pose,MultiBufferSource buffers,int light,Direction gaugeFace){
        var copper=VisualGeometry.material(buffers,"copper",false);var dark=VisualGeometry.material(buffers,"dark",false);
        var patina=VisualGeometry.material(buffers,"patina",false);var glass=VisualGeometry.material(buffers,"glass",true);
        boolean down=linked(view,Direction.DOWN),up=linked(view,Direction.UP);float bottom=down?0:1.2F,top=up?16:14;
        int vertical=(down?1<<Direction.DOWN.ordinal():0)|(up?1<<Direction.UP.ordinal():0);
        if(!down)VisualGeometry.box(pose,dark,0,0,0,16,1.2F,16,1,1,1,1,light,view.connections());
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)if((view.corners()&1<<(x*2+z))!=0){
            float xx=x*14,zz=z*14;
            VisualGeometry.box(pose,copper,xx,bottom,zz,xx+2,top,zz+2,1,1,1,1,light,(1<<Direction.DOWN.ordinal())|(1<<Direction.UP.ordinal()));
        }
        for(var side:Direction.Plane.HORIZONTAL)if(!linked(view,side)){
            boolean left=linked(view,side.getCounterClockWise()),right=linked(view,side.getClockWise());
            int horizontal=(left?1<<Direction.WEST.ordinal():0)|(right?1<<Direction.EAST.ordinal():0);
            pose.pushPose();orient(pose,side);
            if(!down)VisualGeometry.box(pose,patina,left?0:2,1.2F,.1F,right?16:14,1.9F,1.8F,1,1,1,1,light,horizontal|(1<<Direction.DOWN.ordinal()));
            float start=side.getAxis()==Direction.Axis.X&&!left?2:0,end=side.getAxis()==Direction.Axis.X&&!right?14:16;
            int caps=horizontal|(side.getAxis()==Direction.Axis.X?(1<<Direction.WEST.ordinal())|(1<<Direction.EAST.ordinal()):0);
            if(!up){VisualGeometry.box(pose,copper,start,14,0,end,16,2,1,1,1,1,light,caps);
                for(float x:new float[]{3,12.2F})VisualGeometry.box(pose,dark,x,14.6F,-.18F,x+.8F,15.4F,-.05F,1,1,1,1,light);
            }
            VisualGeometry.box(pose,glass,left?0:1.4F,down?0:1.22F,.48F,right?16:14.6F,top,.52F,1,1,1,.38F,light,vertical|horizontal);
            pose.popPose();
        }
        if(!up)VisualGeometry.box(pose,glass,linked(view,Direction.WEST)?0:1.4F,14.45F,linked(view,Direction.NORTH)?0:1.4F,
                linked(view,Direction.EAST)?16:14.6F,14.5F,linked(view,Direction.SOUTH)?16:14.6F,1,1,1,.32F,light,view.connections()&~(1<<Direction.DOWN.ordinal()));
        if(view.controller()){
            pose.pushPose();orient(pose,gaugeFace);
            VisualGeometry.box(pose,copper,12.9F,2.7F,-.4F,15.5F,12.3F,.02F,1,1,1,1,light);
            VisualGeometry.box(pose,dark,13.4F,3.2F,-.56F,15,11.8F,-.42F,1,1,1,1,light);
            pose.popPose();
        }
    }
    private static void orient(PoseStack pose,Direction side){pose.translate(.5,0,.5);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-side.toYRot()));pose.translate(-.5,0,-.5);}
    private static void liquid(Fluid fluid,int color,float surface,float ratio,TankView view,PoseStack pose,MultiBufferSource buffers,int light,Direction gaugeFace){
        var extension=net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);var texture=extension.getStillTexture();if(texture==null)return;
        var atlas=net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;var sprite=Minecraft.getInstance().getTextureAtlas(atlas).apply(texture);
        var liquid=sprite.wrap(buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(atlas)));
        float r=((color>>16)&255)/255F,g=((color>>8)&255)/255F,b=(color&255)/255F;int packed=fluid==Fluids.LAVA?0xf000f0:light;
        float x0=linked(view,Direction.WEST)?0:1.4F,x1=linked(view,Direction.EAST)?16:14.6F;
        float z0=linked(view,Direction.NORTH)?0:1.4F,z1=linked(view,Direction.SOUTH)?16:14.6F;
        float y0=linked(view,Direction.DOWN)?0:.04F,y1=Math.min(linked(view,Direction.UP)?16:14.4F,surface);int hidden=view.connections();
        if(surface<16)hidden&=~(1<<Direction.UP.ordinal());
        if(y1>y0)VisualGeometry.box(pose,liquid,x0,y0,z0,x1,y1,z1,r,g,b,.76F,packed,hidden);
        if(view.controller()&&ratio>.001F){
            pose.pushPose();orient(pose,gaugeFace);
            VisualGeometry.box(pose,liquid,13.7F,3.4F,-.64F,14.7F,3.4F+8.1F*ratio,-.58F,r,g,b,.95F,packed);pose.popPose();
        }
    }
}
