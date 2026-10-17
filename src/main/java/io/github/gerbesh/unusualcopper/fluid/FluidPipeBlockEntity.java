package io.github.gerbesh.unusualcopper.fluid;
import io.github.gerbesh.unusualcopper.itempipe.PipeMode;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.SideMode;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import java.util.*;

public final class FluidPipeBlockEntity extends BlockEntity {
    private UUID owner;
    public BlockPos origin;
    private final PipeMode[] modes=new PipeMode[6];
    public final FluidTank buffer=new FluidTank(1000){@Override protected void onContentsChanged(){setChanged();}};
    public FluidPipeBlockEntity(BlockPos pos,BlockState state){
        super(ModContent.FLUID_PIPE_ENTITY.get(),pos,state);
        Arrays.fill(modes,PipeMode.AUTO);
    }
    public UUID owner(){return owner;}
    public void owner(UUID value){owner=value;changed();}
    public boolean canAccess(Player player){return owner==null||owner.equals(player.getUUID())||player.hasPermissions(2);}
    public PipeMode mode(Direction face){return modes[face.ordinal()];}
    public void cycle(Direction side){modes[side.ordinal()]=mode(side).next();changed();}
    public PipeMode effective(Direction side){
        PipeMode own=mode(side);
        if(own!=PipeMode.AUTO||level==null||!level.hasChunkAt(worldPosition.relative(side)))return own;
        var other=level.getBlockEntity(worldPosition.relative(side));
        if(other instanceof FluidPipeBlockEntity)return PipeMode.AUTO;
        if(other instanceof CopperMachineBlockEntity machine)
            return machine.sideMode(side.getOpposite())==SideMode.FLUID_OUTPUT?PipeMode.EXTRACT
                    :machine.sideMode(side.getOpposite())==SideMode.FLUID_INPUT?PipeMode.INSERT
                    :((machine.kind==io.github.gerbesh.unusualcopper.machine.MachineKind.VORTEX_PURIFIER
                    || machine.kind==io.github.gerbesh.unusualcopper.machine.MachineKind.CIRCUIT_ETCHER)
                    && machine.sideMode(side.getOpposite())!=SideMode.DISABLED)?PipeMode.INSERT:PipeMode.DISABLED;
        return PipeMode.INSERT;
    }
    public void changed(){
        setChanged();
        if(level!=null){
            FluidNetworks.dirty(level);
            if(getBlockState().getBlock() instanceof FluidPipeBlock block)block.refreshConnections(level,worldPosition);
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
            level.updateNeighborsAt(worldPosition,getBlockState().getBlock());
        }
    }
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);if(owner!=null)tag.putUUID("Owner",owner);
        tag.putIntArray("Modes",Arrays.stream(modes).mapToInt(PipeMode::ordinal).toArray());
        tag.put("BufferedFluid",buffer.writeToNBT(new CompoundTag()));
        if(origin!=null)tag.putLong("Origin",origin.asLong());
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        int[] values=tag.getIntArray("Modes");
        for(int i=0;i<6;i++)modes[i]=i<values.length?PipeMode.values()[Math.floorMod(values[i],PipeMode.values().length)]:PipeMode.AUTO;
        buffer.readFromNBT(tag.getCompound("BufferedFluid"));
        origin=tag.contains("Origin")?BlockPos.of(tag.getLong("Origin")):null;
    }
    @Override public CompoundTag getUpdateTag(){
        var tag=new CompoundTag();tag.putIntArray("Modes",Arrays.stream(modes).mapToInt(PipeMode::ordinal).toArray());
        tag.putInt("Fluid",buffer.getFluidAmount());return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag){
        int[] values=tag.getIntArray("Modes");
        for(int i=0;i<6&&i<values.length;i++)modes[i]=PipeMode.values()[Math.floorMod(values[i],PipeMode.values().length)];
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(Connection connection,ClientboundBlockEntityDataPacket packet){
        if(packet.getTag()!=null)handleUpdateTag(packet.getTag());
    }
    @Override public void onLoad(){super.onLoad();FluidNetworks.add(level,worldPosition);}
    @Override public void onChunkUnloaded(){FluidNetworks.remove(level,worldPosition);super.onChunkUnloaded();}
    @Override public void setRemoved(){FluidNetworks.remove(level,worldPosition);super.setRemoved();}
}
