package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeAppearanceGameTests {
    private static CopperMachineBlockEntity machine(GameTestHelper h,BlockPos p,MachineKind kind){h.setBlock(p,ModContent.MACHINES.get(kind).get());return (CopperMachineBlockEntity)h.getBlockEntity(p);}
    @GameTest(template="empty",timeoutTicks=65)
    public static void acceptedFlowHasDirectionDrainsAndNeverPersistsAsEnergy(GameTestHelper h){
        var source=machine(h,new BlockPos(1,2,2),MachineKind.ESSENCE_SPLITTER);source.energy.add(50000,false);
        h.setBlock(new BlockPos(2,2,2),ModContent.ENERGY_PIPE.get());var pipe=(EnergyPipeBlockEntity)h.getBlockEntity(new BlockPos(2,2,2));
        var sink=machine(h,new BlockPos(3,2,2),MachineKind.COPPER_CELL_III);
        h.runAtTickTime(16,()->{
            h.assertTrue(pipe.visualCharge()==15&&pipe.flowing()&&pipe.incoming(Direction.WEST)&&pipe.outgoing(Direction.EAST)&&!pipe.outgoing(Direction.WEST),"Accepted transfer did not publish its actual entry/exit faces");
            var disk=pipe.saveWithoutMetadata();h.assertTrue(!disk.contains("Fill")&&!disk.contains("In")&&!disk.contains("Out"),"Transient visual state leaked into persistent CuE");
            h.assertTrue(source.energy.stored()+sink.energy.stored()==50000,"Appearance sampling created or destroyed CuE");
            source.energy.spend(source.energy.stored());
        });
        h.runAtTickTime(42,()->{
            h.assertTrue(pipe.visualCharge()==0&&!pipe.flowing(),"Disconnected charge or old flow left an eternal glowing core");
            h.assertTrue(sink.energy.stored()+source.energy.consumed()==50000,"Draining animation affected physical accounting");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=55)
    public static void chargedIdleNetworkDoesNotInventFlowAndPausedSourceEmptiesIt(GameTestHelper h){
        var source=machine(h,new BlockPos(1,2,2),MachineKind.ESSENCE_SPLITTER);source.energy.add(25000,false);
        h.setBlock(new BlockPos(2,2,2),ModContent.ENERGY_PIPE.get());var pipe=(EnergyPipeBlockEntity)h.getBlockEntity(new BlockPos(2,2,2));
        h.runAtTickTime(16,()->{
            h.assertTrue(pipe.visualCharge()==10&&!pipe.flowing(),"Charged idle wire invented a transfer or ignored available energy");
            h.assertTrue(source.energy.stored()==25000,"Idle appearance charged a hidden fee");source.redstoneMode=1;
        });
        h.runAtTickTime(32,()->{h.assertTrue(pipe.visualCharge()==0&&!pipe.flowing()&&source.energy.stored()==25000,"Disabled output remained energized or lost CuE");h.succeed();});
    }
}
