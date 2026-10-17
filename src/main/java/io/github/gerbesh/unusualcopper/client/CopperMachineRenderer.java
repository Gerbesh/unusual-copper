package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.gerbesh.unusualcopper.energy.EnergyPipeBlock;
import io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlock;
import io.github.gerbesh.unusualcopper.fluid.FluidPipeBlock;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.PipeBlock;

/** Working mechanisms and physical docking sockets, all driven by server state. */
public final class CopperMachineRenderer implements BlockEntityRenderer<CopperMachineBlockEntity> {
    private final SentinelRenderer sentinel;
    private final java.util.Map<CopperMachineBlockEntity,Motion> animations=new java.util.WeakHashMap<>();
    private static final class Motion {
        double last=-1,phase;float speed;
        float advance(double now,boolean working){
            float dt=last<0?0:(float)Math.max(0,Math.min(1,now-last));last=now;
            speed=Math.max(0,Math.min(1,speed+(working?1:-1)*dt*.14F));phase=(phase+dt*speed)%72000;
            return (float)phase;
        }
    }
    public static long portFrames,workingFrames,idleFrames;
    public CopperMachineRenderer(BlockEntityRendererProvider.Context context){sentinel=new SentinelRenderer(context);}
    @Override public void render(CopperMachineBlockEntity m,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(m.getLevel()==null)return;
        ports(m,pose,buffers,light);
        if(m.mining!=null){MiningEffects.track(m);return;}
        if(m.sentinel!=null){sentinel.render(m,partial,pose,buffers,light,overlay);return;}
        boolean working=m.getBlockState().getValue(CopperMachineBlock.ACTIVE);
        if(working)workingFrames++;else idleFrames++;
        double now=AnimationClock.now();float time=(float)(now%72000);
        float motion=animations.computeIfAbsent(m,key->new Motion()).advance(now,working);
        var copper=VisualGeometry.material(buffers,"copper",false);
        var dark=VisualGeometry.material(buffers,"dark",false);
        var glow=VisualGeometry.material(buffers,"aqua",false);
        var liquid=VisualGeometry.material(buffers,"energy",true);
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-m.getBlockState().getValue(CopperMachineBlock.FACING).toYRot()+180));pose.translate(-.5,0,-.5);
        if(m.kind.isCell()||m.kind==MachineKind.ESSENCE_SPLITTER){
            float charge=m.energy.capacity()==0?0:m.visualEnergy(partial)/m.energy.capacity();
            if(charge>0){float top=3.25F+9.3F*charge;
                VisualGeometry.box(pose,liquid,3.3F,3.25F,3.3F,12.7F,top,12.7F,.6F,1,1,.7F,0xf000f0);
                if(charge>.1F&&(working||Math.abs(m.visualEnergy(partial)-m.energy.stored())>1))for(int i=0;i<5;i++){float y=3.4F+(time*.1F+i*1.8F)%(Math.max(.2F,top-3.5F));float x=5+(i%3)*2.4F,z=5+(i%2)*5;
                    VisualGeometry.box(pose,glow,x,y,z,x+.55F,y+.55F,z+.55F,.8F,1,1,1,0xf000f0);}
            }
            if(m.kind==MachineKind.ESSENCE_SPLITTER){
                pose.pushPose();pose.translate(.5,.54,.5);pose.mulPose(Axis.YP.rotationDegrees(motion*5));pose.translate(-.5,-.54,-.5);
                VisualGeometry.box(pose,copper,4,8.5F,7.5F,12,9.3F,8.5F,1,1,1,1,light);
                VisualGeometry.box(pose,copper,7.5F,8.5F,4,8.5F,9.3F,12,1,1,1,1,light);pose.popPose();
            }
        }
        switch(m.kind){
            case REPAIR_CRYSTAL -> {
                crystal(pose,glow,8,9+(float)Math.sin(time*.08)*.35F,8,time*(working?5:.6F),2.1F,working?0xf000f0:light);
                if(working){rotor(pose,copper,motion*8,5.5F,3.6F,light);rotor(pose,glow,-motion*8,12.5F,3.6F,0xf000f0);}
            }
            case AURA_CULTIVATOR -> {
                float pulse=CultivatorEffects.activity(m.getBlockPos());
                crystal(pose,glow,8,12.6F+(float)Math.sin(motion*.14)*.65F+pulse*.4F,8,motion*2+time*pulse*3,2.2F+pulse*.25F,working||pulse>0?0xf000f0:light);
                if(pulse>0)rotor(pose,glow,time*7,13.4F,3.4F+pulse*.5F,0xf000f0);
            }
            case ENCHANTMENT_RESONATOR -> {
                if(working){crystal(pose,glow,8,15.8F+(float)Math.sin(motion*.12)*.45F,8,motion*3,1.1F,0xf000f0);rotor(pose,copper,motion*3,14.2F,4,light);}
            }
            case AETHERIC_BREWER -> {
                for(int i=0;i<3;i++){float x=i==0?5.2F:i==1?9.2F:7.2F,z=i<2?5.2F:9.2F;
                    VisualGeometry.box(pose,liquid,x,5.1F,z,x+1.6F,working?7.8F+(float)Math.sin(time*.1+i)*.35F:6.5F,z+1.6F,.65F,1,1,.8F,working?0xf000f0:light);}
                rotor(pose,copper,motion*4,11.7F,3,light);
            }
            case RESONANCE_SMELTER -> {
                if(working){var flame=VisualGeometry.material(buffers,"fire",false);float pulse=.75F+.25F*(float)Math.sin(time*.9);
                    VisualGeometry.box(pose,flame,4.15F,4.15F,.85F,11.85F,8.85F,1.6F,1,pulse,1,1,0xf000f0);}
                piston(pose,copper,motion,8,14.4F,7,2.8F,light,true);
                pose.pushPose();pose.translate(13.4F/16,9.55F/16,-.02F/16);
                pose.mulPose(Axis.ZP.rotationDegrees(-110+animations.get(m).speed*(65+(float)Math.sin(motion*.14)*20)));
                VisualGeometry.box(pose,dark,-.12F,0,-.04F,.12F,1.05F,0,1,1,1,1,light);pose.popPose();
            }
            case MAGNETIC_COLLECTOR -> {if(working)rotor(pose,glow,motion*5,13,3,0xf000f0);}
            case GROWTH_SHEPHERD -> {rotor(pose,copper,motion*2.5F,14.6F,4,light);if(working)crystal(pose,glow,8,15.6F,8,-motion*2,1,0xf000f0);}
            case MOB_REAPER -> {rotor(pose,dark,motion*7,15,3.4F,light);}
            case ARBORIST -> {piston(pose,copper,motion,8,15,8,4,light,true);}
            case SOLAR_INFUSER -> {
                if(working){float scan=3+(time*.14F)%9;pose.pushPose();pose.translate(.5,10F/16,.5);pose.mulPose(Axis.XP.rotationDegrees(-22.5F));pose.translate(-.5,-10F/16,-.5);VisualGeometry.box(pose,glow,scan,11.55F,3,scan+.4F,11.6F,12,.7F,1,1,1,0xf000f0);pose.popPose();}
            }
            default -> {}
        }
        pose.popPose();
        if(m.kind==MachineKind.COPPER_FLUID_PUMP && working && m.pumpSource!=null)pumpAura(m,pose,buffers,light,time);
    }
    private static void ports(CopperMachineBlockEntity m,PoseStack pose,MultiBufferSource buffers,int light){
        for(var side:Direction.values()){
            var pos=m.getBlockPos().relative(side);if(!m.getLevel().hasChunkAt(pos))continue;
            var state=m.getLevel().getBlockState(pos);var mode=m.sideMode(side);
            boolean energy=state.getBlock() instanceof EnergyPipeBlock&&(mode==SideMode.ENERGY_INPUT||mode==SideMode.ENERGY_OUTPUT||m.kind==MachineKind.ENERGY_MONITOR);
            boolean item=state.getBlock() instanceof ItemPipeBlock&&(mode==SideMode.ITEM_INPUT||mode==SideMode.ITEM_OUTPUT);
            boolean fluid=state.getBlock() instanceof FluidPipeBlock&&(mode==SideMode.FLUID_INPUT||mode==SideMode.FLUID_OUTPUT);
            if(!(energy||item||fluid)||!state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite())))continue;
            portFrames++;pose.pushPose();VisualGeometry.orientNorth(pose,side);
            var copper=VisualGeometry.material(buffers,"copper",false);var dark=VisualGeometry.material(buffers,"patina",false);
            // All sockets meet the block boundary at (8,8), with a load-bearing backing pedestal.
            VisualGeometry.box(pose,dark,4.6F,2,1.1F,11.4F,11.4F,4,1,1,1,1,light);
            VisualGeometry.box(pose,copper,4.8F,4.8F,.01F,6,11.2F,2,1,1,1,1,light);
            VisualGeometry.box(pose,copper,10,4.8F,.01F,11.2F,11.2F,2,1,1,1,1,light);
            VisualGeometry.box(pose,copper,6,4.8F,.01F,10,6,2,1,1,1,1,light);
            VisualGeometry.box(pose,copper,6,10,.01F,10,11.2F,2,1,1,1,1,light);
            if(energy&&m.energy.stored()>0){var glow=VisualGeometry.material(buffers,"energy",true);
                VisualGeometry.box(pose,glow,6.1F,6.1F,.03F,9.9F,9.9F,1.05F,.65F,1,1,.7F,0xf000f0);}
            pose.popPose();
        }
    }
    /** Pure client-side appearance; no fluid source mutation or particle packets. */
    private static void pumpAura(CopperMachineBlockEntity m,PoseStack pose,MultiBufferSource buffers,int light,float time){
        var source=m.pumpSource;
        if(source==null||!m.getLevel().hasChunkAt(source))return;
        boolean lava=m.getLevel().getFluidState(source).is(net.minecraft.tags.FluidTags.LAVA);
        float red=lava?1F:.27F,green=lava?.54F:.92F,blue=lava?.17F:.96F;
        pose.pushPose();
        pose.translate(source.getX()-m.getBlockPos().getX()+.5,source.getY()-m.getBlockPos().getY()+2.15,
                source.getZ()-m.getBlockPos().getZ()+.5);
        var translucent=VisualGeometry.material(buffers,"energy",true);
        VisualGeometry.sphere(pose,translucent,.64F,red,green,blue,.30F,0xf000f0);
        VisualGeometry.sphere(pose,translucent,.47F,red,green,blue,.12F,0xf000f0);
        for(int index=0;index<24;index++){
            float angle=index*2.399963F;
            float radius=1.8F+(index%7)*1.6F;
            int offX=Math.round((float)Math.cos(angle)*radius);
            int offZ=Math.round((float)Math.sin(angle)*radius);
            var block=source.offset(offX,0,offZ);
            if(!m.getLevel().hasChunkAt(block)||!m.getLevel().getFluidState(block).isSource())continue;
            if(m.getLevel().getFluidState(block).getType()!=m.getLevel().getFluidState(source).getType())continue;
            float t=(time*.012F+index*.173F)%1;
            float x=offX*(1-t),z=offZ*(1-t),y=-1.95F+1.95F*t;
            VisualGeometry.box(pose,translucent,x*16-1.25F,y*16-1.25F,z*16-1.25F,
                    x*16+1.25F,y*16+1.25F,z*16+1.25F,red,green,blue,.72F,0xf000f0);
        }
        pose.popPose();
    }
    private static void rotor(PoseStack p,VertexConsumer out,float angle,float y,float radius,int light){
        p.pushPose();p.translate(.5,y/16,.5);p.mulPose(Axis.YP.rotationDegrees(angle));p.translate(-.5,-y/16,-.5);
        VisualGeometry.box(p,out,8-radius,y,7.6F,8+radius,y+.65F,8.4F,1,1,1,1,light);
        VisualGeometry.box(p,out,7.6F,y,8-radius,8.4F,y+.65F,7.59F,1,1,1,1,light);
        VisualGeometry.box(p,out,7.6F,y,8.41F,8.4F,y+.65F,8+radius,1,1,1,1,light);p.popPose();
    }
    private static void piston(PoseStack p,VertexConsumer out,float time,float x,float y,float z,float width,int light,boolean active){
        float dy=active?(float)Math.sin(time*.35)*.65F:0;
        VisualGeometry.box(p,out,x-width/2,y+dy,z-1,x+width/2,y+dy+1.1F,z+1,1,1,1,1,light);
    }
    private static void crystal(PoseStack p,VertexConsumer out,float x,float y,float z,float angle,float size,int light){
        p.pushPose();p.translate(x/16,y/16,z/16);p.mulPose(Axis.YP.rotationDegrees(angle));
        VisualGeometry.crystal(p,out,size/16,light);p.popPose();
    }
    @Override public boolean shouldRenderOffScreen(CopperMachineBlockEntity m){return m.sentinel!=null||m.mining!=null||m.kind==MachineKind.COPPER_FLUID_PUMP;}
    @Override public boolean shouldRender(CopperMachineBlockEntity m,net.minecraft.world.phys.Vec3 camera){return m.mining!=null?net.minecraft.world.phys.Vec3.atCenterOf(m.getBlockPos()).distanceToSqr(camera)<224*224:BlockEntityRenderer.super.shouldRender(m,camera);}
    @Override public int getViewDistance(){return 64;}
}
