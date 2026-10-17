package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.material.Fluids;

/** Decorative mechanisms only; server state remains the sole authority for processing. */
final class IndustrialMachineVisuals {
    private static final int FULL=0xf000f0;
    private IndustrialMachineVisuals(){}

    static void render(CopperMachineBlockEntity m,PoseStack p,MultiBufferSource buffers,
                       float phase,float time,float speed,float fill,boolean working,int light){
        boolean industrial=switch(m.kind){
            case RESONANCE_CRUSHER,COPPER_PRESS,VORTEX_PURIFIER,COPPER_FLUID_PUMP,COPPER_FLUID_TANK,
                    RESONANCE_CRYSTALLIZER,CIRCUIT_ETCHER,PRECISION_ASSEMBLER,PRODUCTION_CONTROLLER,ARMOR_WORKBENCH -> true;
            default -> false;
        };
        if(!industrial)return;
        var copper=VisualGeometry.material(buffers,"copper",false);
        var dark=VisualGeometry.material(buffers,"dark",false);
        var aqua=VisualGeometry.material(buffers,"aqua",false);
        switch(m.kind){
            case RESONANCE_CRUSHER -> {
                roller(p,dark,copper,phase*9,6,light);
                roller(p,dark,copper,-phase*9,10,light);
            }
            case COPPER_PRESS -> {
                float stroke=(1-(float)Math.cos(phase*.15F))*1.35F;
                box(p,copper,7,9-stroke,7,9,12,9,light);
                box(p,dark,4.5F,8-stroke,4.5F,11.5F,9-stroke,11.5F,light);
            }
            case VORTEX_PURIFIER, COPPER_FLUID_PUMP -> {
                fluid(m,p,buffers,fill,5,12.4F,light);
                box(p,copper,7.6F,5,7.6F,8.4F,13,8.4F,light);
                rotor(p,copper,phase*(m.kind==MachineKind.COPPER_FLUID_PUMP?9:5),8,3.8F,light);
                rotor(p,dark,-phase*5,6,2.6F,light);
                if(fill>.05F&&speed>.02F)for(int i=0;i<4;i++){
                    float angle=phase*.08F+i*1.5708F,y=5+((time*.09F+i*1.3F)%(Math.max(.2F,fill*7)));
                    float x=8+(float)Math.cos(angle)*2.7F,z=8+(float)Math.sin(angle)*2.7F;
                    box(p,aqua,x,y,z,x+.35F,y+.4F,z+.35F,FULL);
                }
            }
            case COPPER_FLUID_TANK -> fluid(m,p,buffers,fill,4.4F,13.3F,light);
            case RESONANCE_CRYSTALLIZER -> {
                p.pushPose();p.translate(.5,(9.2F+(float)Math.sin(phase*.12F)*.45F)/16,.5);
                p.mulPose(Axis.YP.rotationDegrees(phase*2));
                VisualGeometry.crystal(p,aqua,.13F,working?FULL:light);p.popPose();
                rotor(p,copper,-phase*3,6,3.3F,light);
                if(working)rotor(p,aqua,phase*4,12.2F,3.1F,FULL);
            }
            case CIRCUIT_ETCHER -> {
                fluid(m,p,buffers,fill,4.8F,7.5F,light);
                float z=4.5F+(1-(float)Math.cos(phase*.08F))*3;
                box(p,copper,4,9.2F,z,12,10.2F,z+1.3F,light);
                box(p,dark,7.5F,10,Math.min(z+1,12),8.5F,11.3F,13,light);
                if(working)box(p,aqua,4.4F,7.6F,z+.3F,11.6F,7.75F,z+.7F,FULL);
            }
            case PRECISION_ASSEMBLER -> {
                float x=8+(float)Math.sin(phase*.065F)*3,z=7+(float)Math.sin(phase*.043F)*2.3F;
                box(p,copper,x-.9F,11.7F,z,x+.9F,13.1F,13,light);
                box(p,dark,x-.7F,8.6F,z,x+.7F,11.7F,z+1.4F,light);
                box(p,aqua,x-.35F,8.1F,z+.35F,x+.35F,8.6F,z+1.05F,working?FULL:light);
                if(working)box(p,aqua,x-.08F,5.85F,z+.62F,x+.08F,8.1F,z+.78F,FULL);
            }
            case PRODUCTION_CONTROLLER -> {
                for(int i=0;i<3;i++){
                    float width=working?2.8F+(float)Math.sin(time*.07F+i)*1.8F:1.5F+i;
                    box(p,working?aqua:copper,4.2F,9.7F-i*1.2F,.55F,4.2F+width,10-i*1.2F,.67F,working?FULL:light);
                }
            }
            case ARMOR_WORKBENCH -> {
                float y=7.4F+(float)Math.sin(phase*.1F)*1.4F;
                box(p,copper,3,y,5.3F,4,y+.8F,9.7F,light);
                box(p,copper,12,y,5.3F,13,y+.8F,9.7F,light);
                if(working){box(p,aqua,4,y+.1F,5.3F,4.2F,y+.6F,9.7F,FULL);box(p,aqua,11.8F,y+.1F,5.3F,12,y+.6F,9.7F,FULL);}
            }
            default -> {}
        }
    }

    private static void fluid(CopperMachineBlockEntity m,PoseStack p,MultiBufferSource buffers,float fill,float bottom,float top,int light){
        if(fill<=.001F||m.visualFluid==Fluids.EMPTY)return;
        var extension=net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(m.visualFluid);
        var still=extension.getStillTexture();if(still==null)return;
        var atlas=net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
        var sprite=net.minecraft.client.Minecraft.getInstance().getTextureAtlas(atlas).apply(still);
        int tint=extension.getTintColor();
        if(m.visualFluid==Fluids.WATER&&m.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)
            tint=net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level,m.getBlockPos());
        float r=((tint>>16)&255)/255F,g=((tint>>8)&255)/255F,b=(tint&255)/255F;
        // Sprite-local UVs preserve native animated water/lava and mod-provided fluid art.
        var liquid=sprite.wrap(buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(atlas)));
        VisualGeometry.box(p,liquid,3.5F,bottom,3.5F,12.5F,bottom+(top-bottom)*fill,12.5F,r,g,b,.72F,m.visualFluid==Fluids.LAVA?FULL:light);
    }
    private static void roller(PoseStack p,VertexConsumer dark,VertexConsumer copper,float angle,float z,int light){
        p.pushPose();p.translate(.5,10F/16,z/16);p.mulPose(Axis.XP.rotationDegrees(angle));p.translate(-.5,-10F/16,-z/16);
        box(p,dark,3.7F,9,z-1.3F,12.3F,11,z+1.3F,light);
        for(int i=0;i<4;i++){float x=4.2F+i*2;box(p,copper,x,8.7F,z-.5F,x+.6F,11.3F,z+.5F,light);}
        p.popPose();
    }
    private static void rotor(PoseStack p,VertexConsumer out,float angle,float y,float radius,int light){
        p.pushPose();p.translate(.5,y/16,.5);p.mulPose(Axis.YP.rotationDegrees(angle));p.translate(-.5,-y/16,-.5);
        box(p,out,8-radius,y,7.6F,8+radius,y+.6F,8.4F,light);
        box(p,out,7.6F,y,8-radius,8.4F,y+.6F,7.55F,light);
        box(p,out,7.6F,y,8.45F,8.4F,y+.6F,8+radius,light);p.popPose();
    }
    private static void box(PoseStack p,VertexConsumer out,float x,float y,float z,float xx,float yy,float zz,int light){
        if(xx<=x||yy<=y||zz<=z)return;
        VisualGeometry.box(p,out,x,y,z,xx,yy,zz,1,1,1,1,light);
    }
}
