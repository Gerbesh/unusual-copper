package io.github.gerbesh.unusualcopper.fluid;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.itempipe.PipeMode;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;

/** 250mB per step; the withdrawing pipe persists fluid until the destination accepts it. */
public final class FluidNetworks {
    private static final Map<ServerLevel,World> WORLDS=new IdentityHashMap<>();
    private static final int RATE=FluidFlowPolicy.BATCH_MB,MAX_NODES=128,WORK=32,BUDGET=64;
    private FluidNetworks(){}
    public static void add(LevelAccessor level,BlockPos pos){
        if(level instanceof ServerLevel server)WORLDS.computeIfAbsent(server,ignore->new World()).pipes.add(pos.immutable());
    }
    public static void remove(LevelAccessor level,BlockPos pos){
        if(level instanceof ServerLevel server&&WORLDS.containsKey(server))WORLDS.get(server).pipes.remove(pos);
    }
    public static void dirty(LevelAccessor level){}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel server)WORLDS.remove(server);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(e.phase==TickEvent.Phase.END&&e.level instanceof ServerLevel server&&server.getGameTime()%FluidFlowPolicy.TICKS_PER_BATCH==0){
            World world=WORLDS.get(server);if(world!=null)world.tick(server);
        }
    }
    private static FluidPipeBlockEntity pipe(ServerLevel level,BlockPos pos){
        return level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof FluidPipeBlockEntity p&&!p.isRemoved()?p:null;
    }
    private record Sink(BlockPos pos,IFluidHandler handler){}
    private static final class World {
        final LinkedHashSet<BlockPos> pipes=new LinkedHashSet<>();
        int cursor;
        void tick(ServerLevel level){
            if(pipes.isEmpty())return;
            List<BlockPos> positions=new ArrayList<>(pipes);
            int attempts=0;
            for(int i=0;i<Math.min(WORK,positions.size())&&attempts<BUDGET;i++){
                FluidPipeBlockEntity source=pipe(level,positions.get(Math.floorMod(cursor+i,positions.size())));
                if(source==null)continue;
                if(source.owner()!=null&&!ProtectionPolicy.allowed(level,source.getBlockPos(),source.owner()))continue;
                var buffered=source.buffer.getFluid();
                if(!buffered.isEmpty()){
                    Sink sink=findSink(level,source,buffered,source.origin);
                    if(sink!=null){attempts++;deliver(source,sink);}
                    continue;
                }
                for(Direction side:Direction.values()){
                    if(attempts>=BUDGET)break;
                    if(source.effective(side)!=PipeMode.EXTRACT)continue;
                    BlockPos providerPos=source.getBlockPos().relative(side);
                    if(!level.hasChunkAt(providerPos))continue;
                    if(source.owner()!=null&&!ProtectionPolicy.allowed(level,providerPos,source.owner()))continue;
                    var provider=level.getBlockEntity(providerPos);
                    if(provider==null||provider instanceof FluidPipeBlockEntity)continue;
                    if(provider instanceof CopperMachineBlockEntity machine && machine.owner()!=null
                            && !machine.owner().equals(source.owner()))continue;
                    var optional=provider.getCapability(ForgeCapabilities.FLUID_HANDLER,side.getOpposite());
                    if(!optional.isPresent())continue;
                    IFluidHandler handler=optional.orElseThrow(IllegalStateException::new);
                    FluidStack simulated=handler.drain(RATE,IFluidHandler.FluidAction.SIMULATE);
                    if(simulated.isEmpty())continue;
                    var offer=simulated.copy();offer.setAmount(Math.min(RATE,offer.getAmount()));
                    Sink sink=findSink(level,source,offer,providerPos);
                    if(sink==null)continue;
                    int accepted=sink.handler.fill(offer,IFluidHandler.FluidAction.SIMULATE);
                    if(accepted<=0)continue;
                    FluidStack extracted=handler.drain(Math.min(accepted,offer.getAmount()),IFluidHandler.FluidAction.EXECUTE);
                    if(extracted.isEmpty())continue;
                    // Persist physical transit fluid before attempting output.
                    int stored=source.buffer.fill(extracted,IFluidHandler.FluidAction.EXECUTE);
                    if(stored!=extracted.getAmount())throw new IllegalStateException("Pipe buffer overflow after fluid withdrawal");
                    source.origin=providerPos.immutable();source.setChanged();
                    attempts++;deliver(source,sink);
                    break;
                }
            }
            cursor=Math.floorMod(cursor+WORK,Math.max(1,positions.size()));
        }
        void deliver(FluidPipeBlockEntity source,Sink target){
            FluidStack fluid=source.buffer.getFluid();
            if(fluid.isEmpty())return;
            var offer=fluid.copy();offer.setAmount(Math.min(RATE,offer.getAmount()));
            int accepted=Math.max(0,Math.min(offer.getAmount(),target.handler.fill(offer,IFluidHandler.FluidAction.EXECUTE)));
            if(accepted>0)source.buffer.drain(accepted,IFluidHandler.FluidAction.EXECUTE);
            if(source.buffer.getFluidAmount()==0){source.origin=null;source.setChanged();}
        }
        Sink findSink(ServerLevel level,FluidPipeBlockEntity source,FluidStack offer,BlockPos origin){
            Set<BlockPos> visited=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();
            queue.add(source.getBlockPos());visited.add(source.getBlockPos());
            while(!queue.isEmpty()&&visited.size()<=MAX_NODES){
                BlockPos pos=queue.poll();FluidPipeBlockEntity current=pipe(level,pos);
                if(current==null||!Objects.equals(current.owner(),source.owner()))continue;
                if(current.owner()!=null&&!ProtectionPolicy.allowed(level,pos,current.owner()))continue;
                for(Direction side:Direction.values()){
                    BlockPos next=pos.relative(side);
                    if(!level.hasChunkAt(next))continue;
                    if(current.mode(side)==PipeMode.AUTO){
                        FluidPipeBlockEntity adjacent=pipe(level,next);
                        if(adjacent!=null&&adjacent.mode(side.getOpposite())==PipeMode.AUTO&&visited.size()<MAX_NODES&&visited.add(next))queue.add(next);
                    }
                    if(current.effective(side)!=PipeMode.INSERT||(origin!=null&&origin.equals(next)))continue;
                    if(source.owner()!=null&&!ProtectionPolicy.allowed(level,next,source.owner()))continue;
                    var target=level.getBlockEntity(next);
                    if(target==null||target instanceof FluidPipeBlockEntity)continue;
                    if(target instanceof CopperMachineBlockEntity machine && machine.owner()!=null
                            && !machine.owner().equals(source.owner()))continue;
                    var cap=target.getCapability(ForgeCapabilities.FLUID_HANDLER,side.getOpposite());
                    if(!cap.isPresent())continue;
                    IFluidHandler handler=cap.orElseThrow(IllegalStateException::new);
                    if(handler.fill(offer,IFluidHandler.FluidAction.SIMULATE)>0)return new Sink(next,handler);
                }
            }
            return null;
        }
    }
}
