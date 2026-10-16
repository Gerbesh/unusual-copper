package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.gerbesh.unusualcopper.energy.EnergyPipeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.PipeBlock;

/** Transparent fluid inside the glass, with separate, directed motes for accepted transfers. */
public final class EnergyPipeRenderer implements BlockEntityRenderer<EnergyPipeBlockEntity> {
    public static long emptyFrames, chargedFrames, flowFrames;
    public EnergyPipeRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(EnergyPipeBlockEntity pipe,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(pipe.getLevel()==null)return;
        float fill=pipe.visibleFill(partial); if(fill<.005F){emptyFrames++;return;} chargedFrames++;
        var liquid=VisualGeometry.material(buffers,"energy",true);
        float top=5.8F+4.4F*fill;
        int connected=0;
        for(var side:Direction.values())if(pipe.getBlockState().getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side)))connected|=1<<side.ordinal();
        VisualGeometry.box(pose,liquid,5.8F,5.8F,5.8F,10.2F,top,10.2F,.65F,1,1,.62F,0xf000f0,connected);
        float time=(float)(AnimationClock.now()%72000);
        for(var side:Direction.values()) if(pipe.getBlockState().getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) {
            int ends=(1<<side.ordinal())|(1<<side.getOpposite().ordinal());
            // Horizontal liquid has a flat free surface. Vertical arms visibly rise from below.
            if(side.getAxis()==Direction.Axis.Y) {
                float y0=side==Direction.DOWN?0:10.2F;
                float y1=side==Direction.DOWN?5.8F:16;
                VisualGeometry.box(pose,liquid,5.8F,y0,5.8F,10.2F,y0+(y1-y0)*fill,10.2F,.65F,1,1,.62F,0xf000f0,ends);
            } else {
                float x0=side==Direction.WEST?0:side==Direction.EAST?10.2F:5.8F;
                float x1=side==Direction.WEST?5.8F:side==Direction.EAST?16:10.2F;
                float z0=side==Direction.NORTH?0:side==Direction.SOUTH?10.2F:5.8F;
                float z1=side==Direction.NORTH?5.8F:side==Direction.SOUTH?16:10.2F;
                VisualGeometry.box(pose,liquid,x0,5.8F,z0,x1,top,z1,.65F,1,1,.62F,0xf000f0,ends);
            }
            if(!pipe.incoming(side)&&!pipe.outgoing(side))continue;
            var motes=VisualGeometry.material(buffers,"aqua",false);
            for(int i=0;i<2;i++) {
                float travel=((time*.045F+i*.5F)%1)*8;
                if(pipe.incoming(side))travel=8-travel;
                float x=8+side.getStepX()*travel,y=(side.getAxis()==Direction.Axis.Y?8:7)+side.getStepY()*travel,z=8+side.getStepZ()*travel;
                VisualGeometry.box(pose,motes,x-.42F,y-.42F,z-.42F,x+.42F,y+.42F,z+.42F,.7F,1,1,1,0xf000f0);
            }
        }
        if(pipe.flowing())flowFrames++;
    }
    @Override public int getViewDistance(){return 48;}
}
