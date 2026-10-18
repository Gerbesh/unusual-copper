package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.fluid.tank.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

/** Compiled scenarios. Execute only when the user explicitly requests functional GameTests. */
@GameTestHolder(UnusualCopper.MOD_ID)
public final class FluidTankGameTests {
    private static CopperMachineBlockEntity cube(GameTestHelper h,BlockPos relative,UUID owner,int amount){
        var pos=h.absolutePos(relative);h.getLevel().setBlock(pos,ModContent.MACHINES.get(MachineKind.COPPER_FLUID_TANK).get().defaultBlockState(),3);
        var tank=(CopperMachineBlockEntity)h.getLevel().getBlockEntity(pos);tank.setOwner(owner);
        tank.water.setFluid(amount==0?FluidStack.EMPTY:new FluidStack(Fluids.WATER,amount));return tank;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void wholeBucketFailureIsReadOnlyAndSimulationReturnsCopies(GameTestHelper h){
        var tank=cube(h,new BlockPos(1,1,1),UUID.randomUUID(),15_999);var storage=(TankNetworks.Storage)tank.fluidStorage();
        h.assertTrue(!storage.fillWhole(new FluidStack(Fluids.WATER,1000))&&tank.water.getFluidAmount()==15_999,"Partial bucket changed the reservoir");
        h.assertTrue(!storage.drainWhole(new FluidStack(Fluids.WATER,16_000))&&tank.water.getFluidAmount()==15_999,"Partial bucket drain changed the reservoir");
        h.assertTrue(storage.fillWhole(new FluidStack(Fluids.WATER,1)),"Exact room was rejected");
        h.assertTrue(storage.drainWhole(new FluidStack(Fluids.WATER,1000))&&tank.water.getFluidAmount()==15_000,"Whole drain mismatch");
        var copy=storage.getFluidInTank(0);copy.setAmount(1);
        storage.fill(new FluidStack(Fluids.WATER,500),IFluidHandler.FluidAction.SIMULATE);
        h.assertTrue(tank.water.getFluidAmount()==15_000,"A simulation or returned reference mutated storage");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void legacyMergeSplitAndItemRoundTripPreserveEveryMb(GameTestHelper h){
        var owner=UUID.randomUUID();var old=cube(h,new BlockPos(1,1,1),owner,64_000);
        var b=cube(h,new BlockPos(1,2,1),owner,0);var c=cube(h,new BlockPos(1,3,1),owner,0);var d=cube(h,new BlockPos(1,4,1),owner,0);
        var storage=(TankNetworks.Storage)old.fluidStorage();h.assertTrue(storage.getFluidInTank(0).getAmount()==64_000&&!storage.fillWhole(new FluidStack(Fluids.WATER,1000)),"Legacy merge created room or lost fluid");
        storage.drainWhole(new FluidStack(Fluids.WATER,16_000));storage.fillWhole(new FluidStack(Fluids.WATER,16_000));
        var block=ModContent.MACHINES.get(MachineKind.COPPER_FLUID_TANK).get();
        var original=new ItemStack(block,2);var copiedData=new net.minecraft.nbt.CompoundTag();copiedData.put("WaterTank",old.water.writeToNBT(new net.minecraft.nbt.CompoundTag()));original.getOrCreateTag().put("BlockEntityTag",copiedData);
        var drops=TankItemStorage.drops(List.of(original,new ItemStack(block)),block.asItem(),old);
        int held=drops.stream().mapToInt(s->TankItemStorage.contents(s).getAmount()*s.getCount()).sum();
        h.assertTrue(held==old.water.getFluidAmount()&&drops.get(0).getCount()==1&&TankItemStorage.contents(original).getAmount()==old.water.getFluidAmount(),"Loot stacks duplicated fluid or the source stack was mutated");
        var saved=drops.get(0).getTag().getCompound("BlockEntityTag").copy();var pos=old.getBlockPos();h.getLevel().removeBlock(pos,false);
        int remaining=b.water.getFluidAmount()+c.water.getFluidAmount()+d.water.getFluidAmount();
        h.assertTrue(held+remaining==64_000,"Split lost or duplicated fluid");
        var restored=cube(h,new BlockPos(1,1,1),owner,0);restored.load(saved);restored.setOwner(owner);
        h.assertTrue(restored.fluidStorage().getFluidInTank(0).getAmount()==64_000,"Item restore changed group fluid");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void fluidTagsAndOwnersRemainSeparateAndRemovedHandlersStayDead(GameTestHelper h){
        var owner=UUID.randomUUID();var a=cube(h,new BlockPos(1,1,1),owner,4000);var b=cube(h,new BlockPos(2,1,1),UUID.randomUUID(),4000);
        h.assertTrue(TankNetworks.view(a).blocks()==1,"Different owners merged");b.setOwner(owner);
        h.assertTrue(TankNetworks.view(a).blocks()==2,"Same-owner merge was not invalidated");
        var tagged=new FluidStack(Fluids.WATER,4000);tagged.getOrCreateTag().putString("Grade","other");b.water.setFluid(tagged);
        h.assertTrue(TankNetworks.view(a).blocks()==1&&TankNetworks.view(b).blocks()==1,"Fluid tags were mixed");
        h.assertTrue(a.fluidStorage().fill(tagged,IFluidHandler.FluidAction.EXECUTE)==0,"Different fluid tags entered storage");
        var stale=(TankNetworks.Storage)a.fluidStorage();a.setRemoved();
        h.assertTrue(stale.getFluidInTank(0).isEmpty()&&!stale.fillWhole(new FluidStack(Fluids.WATER,1000)),"Removed backend was revived");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void chunkLifecycleLeavesOldCapabilitiesInvalidAfterReload(GameTestHelper h){
        var tank=cube(h,new BlockPos(1,1,1),UUID.randomUUID(),4000);
        var old=tank.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,net.minecraft.core.Direction.UP).orElseThrow(IllegalStateException::new);
        tank.onChunkUnloaded();h.assertTrue(old.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE)==0&&tank.fluidStorage().getFluidInTank(0).isEmpty(),"Unloaded storage remained active");
        tank.onLoad();var fresh=tank.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,net.minecraft.core.Direction.UP).orElseThrow(IllegalStateException::new);
        h.assertTrue(old.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE)==0&&fresh.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE)==1000,"Old capability revived, or new storage stayed detached");
        h.succeed();
    }
}
