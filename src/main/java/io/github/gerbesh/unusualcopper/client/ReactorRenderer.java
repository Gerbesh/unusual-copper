package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.reactor.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One bounded renderer per formed reactor; visual time never depends on packet arrival intervals. */
public final class ReactorRenderer implements BlockEntityRenderer<ReactorBlockEntity> {
    private static final int FULL=0xf000f0;
    private static final class Motion{double time;float angle;}
    private final java.util.Map<ReactorBlockEntity,Motion> motions=new java.util.WeakHashMap<>();
    public ReactorRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(ReactorBlockEntity r,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!r.isController()||r.anchor==null||r.getLevel()==null)return;
        double time=r.getLevel().getGameTime()+(double)partial;
        float heat=Math.min(1,r.visibleHeat(partial)/9000F),power=Math.min(1,r.visiblePower(partial)/32000F);
        var motion=motions.computeIfAbsent(r,key->{var m=new Motion();m.time=time;return m;});
        motion.angle=(motion.angle+(float)Math.max(0,Math.min(5,time-motion.time))*(heat*8+power*3))%360;motion.time=time;
        pose.pushPose();pose.translate(r.anchor.getX()-r.getBlockPos().getX(),r.anchor.getY()-r.getBlockPos().getY(),r.anchor.getZ()-r.getBlockPos().getZ());
        pose.pushPose();pose.scale(3,4,3);
        var state=r.getBlockState().setValue(ReactorBlock.FORMED,true);var dispatcher=Minecraft.getInstance().getBlockRenderer();
        var type=RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        dispatcher.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(type),state,dispatcher.getBlockModel(state),1,1,1,light,overlay,net.minecraftforge.client.model.data.ModelData.EMPTY,type);
        pose.popPose();
        var copper=VisualGeometry.material(buffers,"copper",false);var dark=VisualGeometry.material(buffers,"dark",false);
        var glow=VisualGeometry.material(buffers,"energy",true);
        // Fins are static authored geometry; four rotors sit within the lower body.
        for(int side=0;side<4;side++){
            pose.pushPose();pose.translate(1.5,.85,1.5);pose.mulPose(Axis.YP.rotationDegrees(side*90));pose.translate(.75,0,-1.47);
            pose.mulPose(Axis.ZP.rotationDegrees(motion.angle));VisualGeometry.box(pose,dark,-5,-.7F,-.8F,5,.7F,.8F,1,1,1,1,light);VisualGeometry.box(pose,copper,-.7F,-5,-.81F,.7F,5,.81F,1,1,1,1,light);pose.popPose();
        }
        // Four visible focusing sockets. Their beam brightness follows confirmed production.
        var centre=new Vec3(1.5,2.25,1.5);
        for(int side=0;side<4;side++){
            if((r.visualLenses&(1<<side))==0)continue;
            double angle=side*Math.PI/2;var lens=new Vec3(1.5+Math.sin(angle)*1.12,2.25,1.5+Math.cos(angle)*1.12);
            pose.pushPose();pose.translate(lens.x,lens.y,lens.z);VisualGeometry.sphere(pose,glow,.16F,.3F,.95F,1F,.2F+.65F*power,FULL);pose.popPose();
            if(power>.01F)beam(pose,buffers,lens,centre,.025F+.025F*power,.3F,.95F,1F,.65F*power);
        }
        pose.pushPose();pose.translate(centre.x,centre.y,centre.z);pose.mulPose(Axis.YP.rotationDegrees((float)((time*1.1)%360)));
        float intensity=Math.max(power,heat*.2F),radius=.28F+.24F*power+(float)Math.sin(time*.08F)*.015F*power;
        if(intensity>.005F)VisualGeometry.sphere(pose,VisualGeometry.material(buffers,"fire",true),radius,1F,1F,1F,.85F*intensity,FULL);
        if(power>.01F)for(int i=0;i<3;i++){
            pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(35+i*50));pose.mulPose(Axis.YP.rotationDegrees((float)((time*(1+i*.3))%360)));
            for(int segment=0;segment<24;segment++){
                double a=segment*Math.PI*2/24,b=(segment+1)*Math.PI*2/24;
                beam(pose,buffers,new Vec3(Math.cos(a)*.7,0,Math.sin(a)*.7),new Vec3(Math.cos(b)*.7,0,Math.sin(b)*.7),.012F,.2F,.9F,1F,.5F*power);
            }pose.popPose();
        }pose.popPose();
        // Flow is an internal visual loop, not loose item/particle entities.
        if(r.coolant.getFluidAmount()>0&&(power>.01F||heat>.01F))for(int i=0;i<8;i++){
            double a=(time*.025+i*Math.PI/4);float x=(float)(1.5+Math.cos(a)*1.08),z=(float)(1.5+Math.sin(a)*1.08);
            VisualGeometry.box(pose,glow,x*16-1,6,z*16-1,x*16+1,7.5F,z*16+1,.25F+heat*.7F,.85F-heat*.35F,1F-heat*.7F,.7F,FULL);
        }
        // Any enabled service face that meets a pipe receives a short, flush collar.
        for(var pos:ReactorGeometry.positions(r.anchor))for(var side:Direction.values()){
            if(!ReactorGeometry.outside(r.anchor,pos,side)||!r.getLevel().hasChunkAt(pos.relative(side)))continue;
            if(!(r.getLevel().getBlockState(pos.relative(side)).getBlock() instanceof io.github.gerbesh.unusualcopper.block.AbstractPipeBlock))continue;
            var point=Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(r.anchor)).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.43));
            pose.pushPose();pose.translate(point.x,point.y,point.z);pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f(side.getStepX(),side.getStepY(),side.getStepZ())));
            VisualGeometry.box(pose,copper,-3.6F,-3.6F,-2.2F,3.6F,3.6F,1.12F,1,1,1,1,light);VisualGeometry.box(pose,dark,-2.5F,-2.5F,1.13F,2.5F,2.5F,1.17F,1,1,1,1,light);pose.popPose();
        }
        pose.popPose();
    }
    private static void beam(PoseStack pose,MultiBufferSource buffers,Vec3 from,Vec3 to,float radius,float r,float g,float b,float alpha){
        var delta=to.subtract(from);if(delta.lengthSqr()<1e-8)return;pose.pushPose();pose.translate(from.x,from.y,from.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)delta.x,(float)delta.y,(float)delta.z).normalize()));
        VisualGeometry.box(pose,VisualGeometry.material(buffers,"energy",true),-radius*16,0,-radius*16,radius*16,(float)delta.length()*16,radius*16,r,g,b,alpha,FULL);pose.popPose();
    }
    @Override public int getViewDistance(){return 128;}
}
