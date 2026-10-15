package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.EnergyControlMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnergyControlGameTests {
    private static CopperMachineBlockEntity place(GameTestHelper h,BlockPos pos,MachineKind kind){h.setBlock(pos,ModContent.MACHINES.get(kind).get());return (CopperMachineBlockEntity)h.getBlockEntity(pos);}
    private static void wire(GameTestHelper h,BlockPos... positions){for(var p:positions)h.setBlock(p,ModContent.CRYSTALLINE_ENERGY_PIPE.get());}
    @GameTest(template="empty",timeoutTicks=40)
    public static void highConsumerWinsScarcePowerAndFullBufferYields(GameTestHelper h){
        var source=place(h,new BlockPos(0,1,2),MachineKind.ESSENCE_SPLITTER);source.energy.add(50_000,false);
        h.setBlock(new BlockPos(1,1,2),ModContent.ENERGY_PIPE.get());h.setBlock(new BlockPos(2,1,2),ModContent.ENERGY_PIPE.get());
        var high=place(h,new BlockPos(3,1,2),MachineKind.COPPER_CELL_III);high.energyPriority(2);
        var low=place(h,new BlockPos(2,1,3),MachineKind.COPPER_CELL_III);low.energyPriority(0);
        h.runAtTickTime(8,()->{h.assertTrue(high.energy.stored()>0&&low.energy.stored()==0,"Low priority stole the shared 256 CuE while high priority requested power");high.energy.restore(high.energy.capacity());});
        h.runAtTickTime(12,()->{h.assertTrue(low.energy.stored()>0,"A full high-priority buffer blocked a valid lower-priority consumer");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void equalPriorityDevicesRotateDespiteExtraInputFaces(GameTestHelper h){
        var source=place(h,new BlockPos(0,1,2),MachineKind.ESSENCE_SPLITTER);source.energy.add(50_000,false);
        for(var p:new BlockPos[]{new BlockPos(1,1,2),new BlockPos(2,1,2),new BlockPos(2,2,2),new BlockPos(3,2,2)})h.setBlock(p,ModContent.ENERGY_PIPE.get());
        var a=place(h,new BlockPos(3,1,2),MachineKind.COPPER_CELL_III);a.energyPriority(2);
        var b=place(h,new BlockPos(2,1,3),MachineKind.COPPER_CELL_III);b.energyPriority(2);
        var low=place(h,new BlockPos(1,2,2),MachineKind.COPPER_CELL_III);low.energyPriority(0);
        h.runAtTickTime(22,()->{
            h.assertTrue(a.energy.stored()>0&&b.energy.stored()>0&&Math.abs(a.energy.stored()-b.energy.stored())<=256,"Extra input faces or other priority groups biased equal-device rotation");
            h.assertTrue(low.energy.stored()==0,"Low-priority consumer bypassed high-priority demand");
            h.assertTrue(source.energy.stored()+a.energy.stored()+b.energy.stored()==50_000,"Priority scheduling changed total CuE");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=35)
    public static void distributorCapsHighBranchAndFeedsRemainderThenClosesIt(GameTestHelper h){
        var source=place(h,new BlockPos(0,2,2),MachineKind.ESSENCE_SPLITTER);source.energy.add(50_000,false);
        var router=place(h,new BlockPos(2,2,2),MachineKind.ENERGY_DISTRIBUTOR);router.energy.add(4096,false);
        var east=place(h,new BlockPos(4,2,2),MachineKind.COPPER_CELL_III);var south=place(h,new BlockPos(2,2,4),MachineKind.COPPER_CELL_III);
        wire(h,new BlockPos(1,2,2),new BlockPos(3,2,2),new BlockPos(2,2,3));
        router.branchPriority(Direction.EAST,2);router.branchLimit(Direction.EAST,256);router.branchPriority(Direction.SOUTH,0);
        int[] sample=new int[2];
        h.runAtTickTime(3,()->{sample[0]=east.energy.stored();sample[1]=south.energy.stored();});
        h.runAtTickTime(4,()->{
            h.assertTrue(east.energy.stored()-sample[0]==256&&south.energy.stored()-sample[1]==3840,"Branch limit or independent relay intake/output budget was bypassed");
            router.branchLimit(Direction.EAST,0);
        });
        h.runAtTickTime(6,()->{sample[0]=east.energy.stored();sample[1]=south.energy.stored();});
        h.runAtTickTime(7,()->{
            h.assertTrue(east.energy.stored()==sample[0]&&south.energy.stored()-sample[1]==4096,"Zero branch leaked power or stopped the lower-priority branch");
            h.assertTrue(source.energy.stored()+router.energy.stored()+east.energy.stored()+south.energy.stored()==54_096,"Buffered relay lost or created energy");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void oneOutputFaceSharesItsLimitAcrossConsumers(GameTestHelper h){
        var router=place(h,new BlockPos(2,2,2),MachineKind.ENERGY_DISTRIBUTOR);router.energy.add(4096,false);router.branchLimit(Direction.EAST,256);
        wire(h,new BlockPos(3,2,2));var a=place(h,new BlockPos(4,2,2),MachineKind.COPPER_CELL_III);var b=place(h,new BlockPos(3,2,3),MachineKind.COPPER_CELL_III);
        int[] previous=new int[1];h.runAtTickTime(3,()->previous[0]=a.energy.stored()+b.energy.stored());
        h.runAtTickTime(4,()->h.assertTrue(a.energy.stored()+b.energy.stored()-previous[0]==256,"Face budget was charged once per target instead of once per tick"));
        h.runAtTickTime(10,()->{h.assertTrue(a.energy.stored()>0&&b.energy.stored()>0,"Shared branch starved an equal consumer");h.assertTrue(router.energy.stored()+a.energy.stored()+b.energy.stored()==4096,"Face accounting changed total CuE");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void monitorSeparatesFlowConsumptionAndUnmetDemandWithoutFees(GameTestHelper h){
        var source=place(h,new BlockPos(0,2,2),MachineKind.ESSENCE_SPLITTER);source.energy.add(50_000,false);
        var sink=place(h,new BlockPos(3,2,2),MachineKind.COPPER_CELL_III);
        var monitor=place(h,new BlockPos(2,2,1),MachineKind.ENERGY_MONITOR);
        wire(h,new BlockPos(1,2,2),new BlockPos(2,2,2),new BlockPos(2,3,2),new BlockPos(3,3,2));
        EnergyNetworks.Sample[] previous=new EnergyNetworks.Sample[1];
        h.runAtTickTime(3,()->{previous[0]=EnergyNetworks.inspect(h.getLevel(),monitor);source.energy.add(500,false);sink.energy.spend(125);});
        h.runAtTickTime(4,()->{
            var now=EnergyNetworks.inspect(h.getLevel(),monitor);
            h.assertTrue(now.produced()-previous[0].produced()==500&&now.consumed()-previous[0].consumed()==125&&now.transferred()-previous[0].transferred()==4096,"Monitor confused physical transfers with generation or operation fees");
            h.assertTrue(now.stored()==50_375&&now.nodes()==4&&now.devices()==3,"Monitor counted a multiply-connected machine more than once");
            previous[0]=now;source.energy.restore(0);
        });
        h.runAtTickTime(5,()->{
            var now=EnergyNetworks.inspect(h.getLevel(),monitor);
            h.assertTrue(now.epoch()==previous[0].epoch()&&now.demand()-previous[0].demand()==4096&&now.delivered()==previous[0].delivered(),"Unpowered receiver deficit changed after an unrelated rebuild or counted per input face: before="+previous[0]+", after="+now);
            h.assertTrue(monitor.energy.stored()==0&&monitor.energy.consumed()==0&&!monitor.getCapability(CopperCapabilities.ENERGY,Direction.NORTH).isPresent()&&!monitor.getCapability(ForgeCapabilities.ENERGY).isPresent(),"Passive monitor charged idle energy or exposed an energy port");h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void settingsPersistAndForeignOrStaleMenuCannotChangeThem(GameTestHelper h){
        var p=new BlockPos(2,2,2);var router=place(h,p,MachineKind.ENERGY_DISTRIBUTOR);var owner=UUID.randomUUID();router.setOwner(owner);router.energy.add(3000,false);router.energyPriority(2);router.branchLimit(Direction.NORTH,65536);router.branchPriority(Direction.NORTH,0);
        var restored=(CopperMachineBlockEntity)BlockEntity.loadStatic(router.getBlockPos(),router.getBlockState(),router.saveWithFullMetadata());
        h.assertTrue(restored!=null&&restored.energy.stored()==3000&&restored.energyPriority()==2&&restored.branchLimit(Direction.NORTH)==65536&&restored.branchPriority(Direction.NORTH)==0&&owner.equals(restored.owner()),"Router NBT dropped its buffer, owner or branch policy");
        var actor=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(owner,"EnergyOwner"));actor.setPos(router.getBlockPos().getX()+.5,router.getBlockPos().getY()+.5,router.getBlockPos().getZ()+.5);
        var foreign=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"EnergyGuest"));foreign.setPos(actor.position());
        var menu=new EnergyControlMenu(7,actor.getInventory(),router);int north=Direction.NORTH.ordinal();
        h.assertTrue(!menu.clickMenuButton(foreign,20+north)&&router.branchLimit(Direction.NORTH)==65536,"Foreign container button changed branch settings");
        h.assertTrue(menu.clickMenuButton(actor,20+north)&&router.branchLimit(Direction.NORTH)==0,"Owner could not close a branch through the menu");
        h.assertTrue(!menu.clickMenuButton(actor,99999),"Unknown container button accepted");
        h.setBlock(p,net.minecraft.world.level.block.Blocks.AIR);h.assertTrue(!menu.clickMenuButton(actor,0),"Stale menu mutated a removed machine");h.succeed();
    }
}
