package io.github.gerbesh.unusualcopper.fluid.tank;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/** Old contents may exceed 16k, but never create additional room for new fluid. */
public final class LocalTank extends FluidTank {
    private final CopperMachineBlockEntity owner;
    public LocalTank(CopperMachineBlockEntity owner){super(TankVolumes.PER_BLOCK);this.owner=owner;}
    @Override public int getCapacity(){return Math.max(TankVolumes.PER_BLOCK,getFluidAmount());}
    @Override public int fill(FluidStack resource,FluidAction action){
        int room=Math.max(0,TankVolumes.PER_BLOCK-getFluidAmount());
        if(room==0||resource.isEmpty())return 0;
        var bounded=resource.copy();bounded.setAmount(Math.min(room,resource.getAmount()));return super.fill(bounded,action);
    }
    @Override public void setFluid(FluidStack stack){super.setFluid(stack);onContentsChanged();}
    @Override public FluidTank readFromNBT(CompoundTag tag){super.readFromNBT(tag);onContentsChanged();return this;}
    @Override protected void onContentsChanged(){if(owner!=null){owner.setChanged();TankNetworks.changed(owner);}}
}
