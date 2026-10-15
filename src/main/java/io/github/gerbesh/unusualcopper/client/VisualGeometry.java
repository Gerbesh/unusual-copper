package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Small world-space parts, sharing the same pixel textures as the baked models. */
final class VisualGeometry {
    private static final java.util.Map<String,ResourceLocation> TEXTURES=new java.util.HashMap<>();
    private VisualGeometry() {}
    static VertexConsumer material(MultiBufferSource buffers, String name, boolean translucent) {
        var texture = TEXTURES.computeIfAbsent(name,n->new ResourceLocation("unusualcopper", "textures/block/" + n + ".png"));
        return buffers.getBuffer(translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutout(texture));
    }
    static void box(PoseStack pose, VertexConsumer out, float x0,float y0,float z0,float x1,float y1,float z1,
                    float r,float g,float b,float alpha,int light) {
        box(pose,out,x0,y0,z0,x1,y1,z1,r,g,b,alpha,light,0);
    }
    static void box(PoseStack pose, VertexConsumer out, float x0,float y0,float z0,float x1,float y1,float z1,
                    float r,float g,float b,float alpha,int light,int hidden) {
        x0/=16; y0/=16; z0/=16; x1/=16; y1/=16; z1/=16;
        if((hidden&1<<Direction.NORTH.ordinal())==0)face(pose,out,new float[][]{{x0,y0,z0},{x0,y1,z0},{x1,y1,z0},{x1,y0,z0}},Direction.NORTH,r,g,b,alpha,light);
        if((hidden&1<<Direction.SOUTH.ordinal())==0)face(pose,out,new float[][]{{x1,y0,z1},{x1,y1,z1},{x0,y1,z1},{x0,y0,z1}},Direction.SOUTH,r,g,b,alpha,light);
        if((hidden&1<<Direction.WEST.ordinal())==0)face(pose,out,new float[][]{{x0,y0,z1},{x0,y1,z1},{x0,y1,z0},{x0,y0,z0}},Direction.WEST,r,g,b,alpha,light);
        if((hidden&1<<Direction.EAST.ordinal())==0)face(pose,out,new float[][]{{x1,y0,z0},{x1,y1,z0},{x1,y1,z1},{x1,y0,z1}},Direction.EAST,r,g,b,alpha,light);
        if((hidden&1<<Direction.UP.ordinal())==0)face(pose,out,new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}},Direction.UP,r,g,b,alpha,light);
        if((hidden&1<<Direction.DOWN.ordinal())==0)face(pose,out,new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}},Direction.DOWN,r,g,b,alpha,light);
    }
    private static void face(PoseStack pose,VertexConsumer out,float[][] p,Direction n,float r,float g,float b,float alpha,int light) {
        float shade=n==Direction.UP?1:n==Direction.DOWN?.6F:n.getAxis()==Direction.Axis.Z?.85F:.72F;
        float[][] uv={{0,1},{0,0},{1,0},{1,1}};
        for(int i=0;i<4;i++) out.vertex(pose.last().pose(),p[i][0],p[i][1],p[i][2]).color(r*shade,g*shade,b*shade,alpha)
                .uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.last().normal(),n.getStepX(),n.getStepY(),n.getStepZ()).endVertex();
    }
    static void orientNorth(PoseStack pose, Direction side) {
        pose.translate(.5,.5,.5);
        // Keep pedestals upright on all four walls; getRotation() includes an unwanted roll.
        switch(side){
            case EAST -> pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            case WEST -> pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            case UP -> pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            case DOWN -> pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
            default -> {}
        }
        pose.translate(-.5,-.5,-.5);
    }
    static void crystal(PoseStack pose,VertexConsumer out,float size,int light){
        var ring=new net.minecraft.world.phys.Vec3[]{new net.minecraft.world.phys.Vec3(size,0,0),new net.minecraft.world.phys.Vec3(0,0,size),new net.minecraft.world.phys.Vec3(-size,0,0),new net.minecraft.world.phys.Vec3(0,0,-size)};
        for(int half=0;half<2;half++)for(int i=0;i<4;i++){
            var a=new net.minecraft.world.phys.Vec3(0,(half==0?1.5:-1.5)*size,0);
            var b=ring[(i+(half==0?1:0))%4];var c=ring[(i+(half==0?0:1))%4];var n=b.subtract(a).cross(c.subtract(a)).normalize();
            float shade=.68F+(i%3)*.13F;
            var points=new net.minecraft.world.phys.Vec3[]{a,b,c,c};float[][] uv={{.5F,0},{0,1},{1,1},{1,1}};
            for(int k=0;k<4;k++)out.vertex(pose.last().pose(),(float)points[k].x,(float)points[k].y,(float)points[k].z).color(.6F*shade,shade,shade,1).uv(uv[k][0],uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
        }
    }
}
