package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Eight facets above the base; no server entity, physics, AI or inventory. */
public final class SentinelRenderer implements BlockEntityRenderer<CopperMachineBlockEntity> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("unusualcopper","textures/block/aqua.png");
    public static long activeFrames,inactiveFrames;
    public SentinelRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CopperMachineBlockEntity m,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(m.sentinel==null||m.getLevel()==null) return;
        boolean active=m.sentinel.active; if(active) activeFrames++; else inactiveFrames++;
        float time=m.getLevel().getGameTime()+partial; pose.pushPose();
        pose.translate(.5,active?1.65+Math.sin(time*.07)*.07:.88,.5); pose.mulPose(Axis.YP.rotationDegrees(time*(active?2:.2F)));
        var vertices=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Vec3[] ring={new Vec3(.28,0,0),new Vec3(0,0,.28),new Vec3(-.28,0,0),new Vec3(0,0,-.28)};
        for(int i=0;i<4;i++) {
            facet(pose,vertices,new Vec3(0,.45,0),ring[(i+1)%4],ring[i],active,light,i);
            facet(pose,vertices,new Vec3(0,-.45,0),ring[i],ring[(i+1)%4],active,light,i+1);
        }
        pose.popPose();
    }
    private static void facet(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,boolean active,int light,int face) {
        var normal=b.subtract(a).cross(c.subtract(a)).normalize(); float color=active?.75F+(face%3)*.08F:.25F;
        Vec3[] points={a,b,c,c}; float[][] uv={{.5F,0},{0,1},{1,1},{1,1}};
        for(int i=0;i<4;i++) out.vertex(pose.last().pose(),(float)points[i].x,(float)points[i].y,(float)points[i].z).color(active?.35F:.18F,color,color,active?.9F:.8F).uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(active?0xf000f0:light).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
    @Override public boolean shouldRenderOffScreen(CopperMachineBlockEntity m) { return m.sentinel!=null; }
    @Override public int getViewDistance() { return 96; }
}
