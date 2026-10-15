package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArboristPersistenceGameTests {
    private static final BlockPos ROOT=new BlockPos(86008,70,86008);
    private static final class Checkpoint extends SavedData {
        int round,logs; UUID owner;
        static Checkpoint load(CompoundTag tag) { var value=new Checkpoint(); value.round=tag.getInt("Round"); value.logs=tag.getInt("Logs"); value.owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null; return value; }
        @Override public CompoundTag save(CompoundTag tag) { tag.putInt("Round",round); tag.putInt("Logs",logs); if(owner!=null) tag.putUUID("Owner",owner); return tag; }
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void actualDiskRestartResumesTheOriginalPartialTreeWithoutRepeatLoot(GameTestHelper h) {
        var level=h.getLevel();
        PopulationGameTests.loaded(h,new WorkArea(ROOT.offset(-7,0,-7),ROOT.offset(8,15,8)),()->{
            var data=level.getDataStorage().computeIfAbsent(Checkpoint::load,Checkpoint::new,"unusualcopper_arborist_disk_fixture");
            if(data.round>0) {
                var m=(CopperMachineBlockEntity)level.getBlockEntity(ROOT.west(5));
                h.assertTrue(m!=null && m.arborist.job!=null && m.arborist.job.cut==4 && m.owner().equals(data.owner) && m.arborist.job.owner.equals(data.owner),"Original owner and four committed removals loaded from actual region files");
                h.assertTrue(m.arborist.job.tool.getHoverName().getString().equals("Restart axe") && m.arborist.job.tool.getDamageValue()==4 && m.arborist.job.saplings.getCount()==1 && m.energy.stored()==(data.logs-4)*250+120 && ArboristGameTests.owned(m,Items.SPRUCE_LOG)==4,"Actual escrow, tool NBT, energy and first four outputs loaded once");
                m.redstoneMode=0; for(int i=0;i<100 && m.arborist.job!=null;i++) ArboristLogic.process(m,level);
                h.assertTrue(m.grown==1 && m.energy.stored()==0 && ArboristGameTests.owned(m,Items.SPRUCE_LOG)==data.logs && level.getBlockState(ROOT).is(net.minecraft.world.level.block.Blocks.SPRUCE_SAPLING),"Disk-restored partial tree finishes with exact total output and one sapling");
                UnusualCopper.LOGGER.info("CuE ARBORIST DISK RESTORE PASS: round={}, original owner/tool/escrow, total logs={}, exact fee and one replant",data.round,data.logs);
            }
            ArboristGameTests.clear(level,ROOT); ArboristGameTests.grow(level,ROOT,TreeSpecies.SPRUCE,false,7);
            var scan=ArboristGameTests.inspect(level,ROOT,TreeSpecies.SPRUCE); h.assertTrue(scan.result()==TreeScan.Result.READY && scan.logs().size()>4,"Disk fixture has a real multi-step spruce");
            var m=ArboristGameTests.machine(h,ROOT,TreeSpecies.SPRUCE); m.inventory.getStackInSlot(1).setHoverName(net.minecraft.network.chat.Component.literal("Restart axe")); m.energy.add(scan.logs().size()*250+120,false); ArboristGameTests.untilAdmitted(m,level); m.redstoneMode=1;
            h.assertTrue(m.arborist.job!=null && m.arborist.job.cut==4 && m.energy.stored()==(scan.logs().size()-4)*250+120,"Fresh checkpoint pauses after exactly four actual cuts");
            data.round++; data.logs=scan.logs().size(); data.owner=m.owner(); data.setDirty(); m.setChanged();
            UnusualCopper.LOGGER.info("CuE ARBORIST DISK CHECKPOINT: round={}, four cuts, remaining={}, real tool/sapling escrow",data.round,data.logs-4);
        });
    }
}
