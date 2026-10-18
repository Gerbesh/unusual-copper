package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.reactor.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.energy.CopperCapabilities;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluids;

/** Gameplay acceptance scenarios: compile in CI, run only at the user's request. */
@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactorGameTests {
    private static ReactorBlockEntity build(GameTestHelper h,BlockPos origin){
        for(var pos:ReactorGeometry.positions(origin))h.setBlock(pos,ModContent.REACTOR_BLOCK.get());
        var r=(ReactorBlockEntity)h.getBlockEntity(origin);r.tryForm();var c=r.core();h.assertTrue(c!=null,"36 blocks did not form");c.paused=true;return c;
    }
    private static void fuel(ReactorBlockEntity c){
        c.inventory.setStackInSlot(0,new ItemStack(Items.COPPER_INGOT,8));
        for(int i=1;i<=4;i++)c.inventory.setStackInSlot(i,new ItemStack(ModContent.REACTOR_LENS.get()));
        c.inventory.setStackInSlot(5,new ItemStack(ModContent.REACTOR_FILTER.get()));c.inventory.setStackInSlot(6,new ItemStack(ModContent.REACTOR_INSULATION.get()));
        c.coolant.fill(new FluidStack(ReactorFluids.STILL.get(),16000),IFluidHandler.FluidAction.EXECUTE);c.lava.fill(new FluidStack(Fluids.LAVA,1000),IFluidHandler.FluidAction.EXECUTE);c.engine.mode=2;
    }
    @GameTest(template="empty",timeoutTicks=10) public static void thirtyFiveDoesNotFormAndThirtySevenKeepsExtraSeparate(GameTestHelper h){
        var origin=new BlockPos(0,1,0);var list=ReactorGeometry.positions(origin);
        for(int i=0;i<35;i++)h.setBlock(list.get(i),ModContent.REACTOR_BLOCK.get());
        var first=(ReactorBlockEntity)h.getBlockEntity(origin);first.tryForm();h.assertTrue(first.core()==null,"Incomplete structure formed");
        h.setBlock(list.get(35),ModContent.REACTOR_BLOCK.get());first.tryForm();var c=first.core();h.assertTrue(c!=null,"Complete structure did not form");c.paused=true;
        var extra=origin.offset(3,0,0);h.setBlock(extra,ModContent.REACTOR_BLOCK.get());((ReactorBlockEntity)h.getBlockEntity(extra)).tryForm();h.assertTrue(((ReactorBlockEntity)h.getBlockEntity(extra)).core()==null,"Extra block expanded a reactor");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30) public static void oneBatchPaysOnceAndProducesExactly512000(GameTestHelper h){
        var c=build(h,new BlockPos(0,1,0));fuel(c);c.paused=false;
        h.runAtTickTime(18,()->{
            h.assertTrue(c.engine.produced==512000&&c.engine.remaining==0,"Batch energy was duplicated or lost");
            h.assertTrue(c.inventory.getStackInSlot(0).isEmpty()&&c.lava.getFluidAmount()==750&&c.coolant.getFluidAmount()==16000,"Fuel/lava accounting or coolant conservation failed");
            h.assertTrue(c.inventory.getStackInSlot(1).getDamageValue()==8&&c.inventory.getStackInSlot(5).getDamageValue()==8,"Wear was not charged once per batch");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10) public static void eightSocketsAndOldCapabilitiesShareAndRespectBudgets(GameTestHelper h){
        var c=build(h,new BlockPos(0,1,0));c.engine.stored=100000;int total=0;io.github.gerbesh.unusualcopper.energy.CopperEnergyHandler old=null;
        for(var pos:ReactorGeometry.positions(c.anchor))for(var side:Direction.values())if(ReactorGeometry.outlet(c.anchor,pos,side)>=0){
            var member=ReactorBlockEntity.at(c.getLevel(),pos);var cap=member.getCapability(CopperCapabilities.ENERGY,side).orElseThrow(IllegalStateException::new);if(old==null)old=cap;
            int taken=cap.extract(100000,false);h.assertTrue(taken<=4096,"A socket exceeded its budget");total+=taken;
        }
        h.assertTrue(total==32000&&c.engine.stored==68000,"Eight sockets multiplied the controller budget");
        c.unform();h.assertTrue(old.extract(1,false)==0,"Stale port exported after disassembly");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10) public static void breakingMemberKeepsOneResourceOwnerAndNeverResurrectsBlock(GameTestHelper h){
        var c=build(h,new BlockPos(0,1,0));fuel(c);c.engine.begin();c.engine.heat=4800;var broken=new BlockPos(2,4,2);h.setBlock(broken,Blocks.AIR);
        h.assertTrue(h.getBlockState(broken).isAir(),"Disassembly resurrected the removed block");
        h.assertTrue(c.controller==null&&c.engine.remaining==512000&&c.inventory.getStackInSlot(0).getCount()==8,"Member removal lost or copied core resources");
        int owners=0;for(var pos:ReactorGeometry.positions(new BlockPos(0,1,0))){var m=ReactorBlockEntity.at(h.getLevel(),h.absolutePos(pos));if(m!=null&&m.payload())owners++;}
        h.assertTrue(owners==1,"Disassembly copied the resource owner");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10) public static void memoryPreservesThermalStateAndPaidRemainderWithoutMembership(GameTestHelper h){
        var c=build(h,new BlockPos(0,1,0));fuel(c);c.engine.restore(123456,400000,8500,17,2,234567);c.engine.cooling=true;
        var tag=c.memory();h.assertTrue(!tag.contains("Controller")&&!tag.contains("Group"),"Dropped memory retained a live group binding");
        var restored=new ReactorBlockEntity(BlockPos.ZERO,ModContent.REACTOR_BLOCK.get().defaultBlockState());restored.load(tag);
        h.assertTrue(restored.engine.stored==123456&&restored.engine.remaining==400000&&restored.engine.heat==8500&&restored.engine.cooling&&restored.coolant.getFluidAmount()==16000,"Core memory lost thermal/fuel accounting");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=65) public static void purifierProducesFluidAndEmptyBucketDoesNotDuplicateContainer(GameTestHelper h){
        var pos=new BlockPos(5,2,5);h.setBlock(pos,ModContent.MACHINES.get(MachineKind.VORTEX_PURIFIER).get());var m=(CopperMachineBlockEntity)h.getBlockEntity(pos);
        m.inventory.setStackInSlot(0,new ItemStack(ModContent.COOLANT_CONCENTRATE.get()));m.inventory.setStackInSlot(1,new ItemStack(Items.BUCKET));m.water.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE);m.energy.add(1000,false);
        h.runAtTickTime(45,()->{
            int buckets=0;for(int i=2;i<4;i++)if(m.inventory.getStackInSlot(i).is(ReactorFluids.BUCKET.get()))buckets+=m.inventory.getStackInSlot(i).getCount();
            h.assertTrue(buckets==1&&m.inventory.getStackInSlot(1).isEmpty()&&m.water.isEmpty()&&m.coolantOutput.isEmpty()&&m.energy.stored()==800,"Coolant mixing duplicated fluid, bucket or CuE");h.succeed();
        });
    }
}
