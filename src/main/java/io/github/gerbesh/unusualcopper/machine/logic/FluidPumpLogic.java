package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;

/** Powered extraction from a validated *surface*, without changing world fluid blocks. */
public final class FluidPumpLogic implements MachineLogic {
    private static final int POND_MIN=15, RADIUS=15, OUTPUT_MB=250;
    private long nextScan;
    private Fluid sourceFluid=Fluids.EMPTY;

    @Override public void tick(CopperMachineBlockEntity machine,ServerLevel level) {
        if(level.getGameTime()>=nextScan || machine.pumpSource!=null && !surface(level,machine.pumpSource,sourceFluid)){
            nextScan=level.getGameTime()+100;
            BlockPos found=find(level,machine);
            if(!Objects.equals(machine.pumpSource,found)){
                machine.pumpSource=found;machine.setChanged();
            }
            sourceFluid=found==null?Fluids.EMPTY:level.getFluidState(found).getType();
        }
        if(machine.pumpSource==null || sourceFluid==Fluids.EMPTY) {
            machine.status(MachineStatus.NO_TARGETS);return;
        }
        if(level.getGameTime()%5!=0)return;
        if(!machine.water.getFluid().isEmpty() && machine.water.getFluid().getFluid()!=sourceFluid){
            machine.status(MachineStatus.BUFFER_FULL);return;
        }
        int room=machine.water.fill(new FluidStack(sourceFluid,OUTPUT_MB),IFluidHandler.FluidAction.SIMULATE);
        if(room<OUTPUT_MB){machine.status(MachineStatus.BUFFER_FULL);return;}
        int cost=sourceFluid==Fluids.WATER?40:120;
        if(!machine.energy.spend(cost)){machine.status(MachineStatus.NO_ENERGY);return;}
        machine.water.fill(new FluidStack(sourceFluid,OUTPUT_MB),IFluidHandler.FluidAction.EXECUTE);
        machine.status(MachineStatus.WORKING);machine.setChanged();
    }

    private static boolean surface(ServerLevel level,BlockPos pos,Fluid expected) {
        if(expected==Fluids.EMPTY||!level.hasChunkAt(pos)||!level.hasChunkAt(pos.above()))return false;
        var state=level.getFluidState(pos);
        return state.isSource() && state.getType()==expected && level.getFluidState(pos.above()).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty();
    }

    /** Small capped breadth-first search: only loaded chunks, never forces a chunk load. */
    public static boolean lake(ServerLevel level,BlockPos start) {
        if(!level.hasChunkAt(start))return false;
        Fluid fluid=level.getFluidState(start).getType();
        if(!surface(level,start,fluid))return false;
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();
        queue.add(start);seen.add(start);
        int sources=0;
        while(!queue.isEmpty() && seen.size()<=300){
            BlockPos pos=queue.poll();
            if(!surface(level,pos,fluid))continue;
            if(++sources>=POND_MIN)return true;
            for(Direction side:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                BlockPos next=pos.relative(side);
                if(Math.abs(next.getX()-start.getX())<=RADIUS && Math.abs(next.getZ()-start.getZ())<=RADIUS && seen.add(next))
                    queue.add(next);
            }
        }
        return false;
    }
    private static BlockPos find(ServerLevel level,CopperMachineBlockEntity m){
        BlockPos origin=m.getBlockPos();
        // Prioritize the nearest liquid to avoid picking distant ocean over a local pond.
        for(int distance=0;distance<=6;distance++){
            for(int dx=-distance;dx<=distance;dx++)for(int dz=-distance;dz<=distance;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=distance)continue;
                for(int dy=-4;dy<=4;dy++){
                    BlockPos candidate=origin.offset(dx,dy,dz);
                    if(!level.hasChunkAt(candidate)||!level.hasChunkAt(candidate.above()))continue;
                    Fluid fluid=level.getFluidState(candidate).getType();
                    if(fluid!=Fluids.EMPTY && surface(level,candidate,fluid) && lake(level,candidate)
                            && (m.owner()==null||ProtectionPolicy.allowed(level,candidate,m.owner())))
                        return candidate.immutable();
                }
            }
        }
        return null;
    }
}
