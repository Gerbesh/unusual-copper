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
    /** BufferSource may reuse one builder across textures: bind at emission, not at lookup. */
    private static final class Material implements VertexConsumer {
        final java.util.function.Supplier<VertexConsumer> factory;VertexConsumer current;
        Material(java.util.function.Supplier<VertexConsumer> factory){this.factory=factory;}
        VertexConsumer bind(){return factory.get();}
        private VertexConsumer writer(){if(current==null)current=bind();return current;}
        @Override public VertexConsumer vertex(double x,double y,double z){current=bind();current.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){writer().color(r,g,b,a);return this;}
        @Override public VertexConsumer uv(float u,float v){writer().uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){writer().overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){writer().uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){writer().normal(x,y,z);return this;}
        @Override public void endVertex(){writer().endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){current=bind();current.defaultColor(r,g,b,a);}
        @Override public void unsetDefaultColor(){writer().unsetDefaultColor();}
    }
    private static VertexConsumer bind(VertexConsumer out){return out instanceof Material m?m.bind():out;}
    static VertexConsumer deferred(java.util.function.Supplier<VertexConsumer> factory){return new Material(factory);}
    private VisualGeometry() {}
    static VertexConsumer material(MultiBufferSource buffers, String name, boolean translucent) {
        var texture = TEXTURES.computeIfAbsent(name,n->new ResourceLocation("unusualcopper", "textures/block/" + n + ".png"));
        var type=translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutout(texture);
        return deferred(()->buffers.getBuffer(type));
    }
    static void box(PoseStack pose, VertexConsumer out, float x0,float y0,float z0,float x1,float y1,float z1,
                    float r,float g,float b,float alpha,int light) {
        box(pose,out,x0,y0,z0,x1,y1,z1,r,g,b,alpha,light,0);
    }
    static void box(PoseStack pose, VertexConsumer out, float x0,float y0,float z0,float x1,float y1,float z1,
                    float r,float g,float b,float alpha,int light,int hidden) {
        out=bind(out);
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
    /** Lightweight translucent orb made from quads, for the fluid harvester. */
    static void sphere(PoseStack pose,VertexConsumer out,float radius,float r,float g,float b,float alpha,int light){
        out=bind(out);
        int bands=8,sectors=16;
        for(int band=0;band<bands;band++)for(int step=0;step<sectors;step++){
            float a0=(float)(Math.PI*((double)band/bands-.5));
            float a1=(float)(Math.PI*((double)(band+1)/bands-.5));
            float t0=(float)(Math.PI*2*step/sectors);
            float t1=(float)(Math.PI*2*(step+1)/sectors);
            float[] lat={a0,a1,a1,a0},lon={t0,t0,t1,t1};
            for(int k=0;k<4;k++){
                float nx=(float)(Math.cos(lat[k])*Math.cos(lon[k]));
                float ny=(float)Math.sin(lat[k]);
                float nz=(float)(Math.cos(lat[k])*Math.sin(lon[k]));
                out.vertex(pose.last().pose(),radius*nx,radius*ny,radius*nz)
                        .color(r,g,b,alpha).uv(k==0||k==3?0:1,k<2?0:1)
                        .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .uv2(light).normal(pose.last().normal(),nx,ny,nz).endVertex();
            }
        }
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
        out=bind(out);
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
