package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceGameTests {
    @GameTest(template = "empty")
    public static void jobsAndEnergySurviveActualServerRestarts(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Checkpoint checkpoint = level.getDataStorage().computeIfAbsent(Checkpoint::load, Checkpoint::new, "unusualcopper_test_restart_checkpoint");
        if (checkpoint.round == 0) {
            level.getChunkAt(new BlockPos(100, 80, 100));
            int y = Math.max(80, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 100, 100) + 1);
            checkpoint.anchor = new BlockPos(100, y, 100);
            var solar = seed(level, checkpoint.anchor, MachineKind.SOLAR_INFUSER);
            solar.inventory.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 64));
            solar.inventory.setStackInSlot(1, new ItemStack(ModContent.SOLAR_LENS.get()));
            solar.begin("unusualcopper:infuse_copper_ingot", new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(ModContent.ESSENCE.get(), 128), 3200);
            solar.progress = 1200; solar.redstoneMode = 1; solar.setChanged();
            var cell = seed(level, checkpoint.anchor.offset(2, 0, 0), MachineKind.COPPER_CELL);
            cell.energy.add(123_456, false); cell.cycleSide(Direction.NORTH);
            var smelter = seed(level, checkpoint.anchor.offset(4, 0, 0), MachineKind.RESONANCE_SMELTER);
            smelter.inventory.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
            smelter.begin("minecraft:iron_ingot_from_smelting_raw_iron", new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT), 20, 240);
            smelter.progress = 10; smelter.paid = 120; smelter.redstoneMode = 1; smelter.setChanged();
        }
        var solar = existing(level, checkpoint.anchor);
        var cell = existing(level, checkpoint.anchor.offset(2, 0, 0));
        var smelter = existing(level, checkpoint.anchor.offset(4, 0, 0));
        h.assertTrue(cell.energy.stored() == 123_456 && cell.sideMode(Direction.NORTH) == SideMode.ENERGY_OUTPUT, "Disk reload preserves CuE and side mode");
        if (!checkpoint.completed) {
            h.assertTrue(solar.progress == 1200 && solar.result.getCount() == 128 && solar.inventory.getStackInSlot(0).getCount() == 64, "Disk reload preserves full solar task");
            h.assertTrue(smelter.progress == 10 && smelter.paid == 120 && smelter.operationCost == 240 && smelter.energy.stored() == 0, "Disk reload preserves half-paid smelting task");
            if (checkpoint.round >= 10) {
                level.setDayTime(6000); level.setWeatherParameters(0, 0, false, false);
                for (int tick = 0; tick < 500; tick++) new SolarLogic().tick(solar, level);
                smelter.energy.add(120, false); for (int tick = 0; tick < 10; tick++) new SmelterLogic().tick(smelter, level);
                checkpoint.completed = true;
            }
        }
        if (checkpoint.completed) {
            h.assertTrue(solar.inventory.getStackInSlot(2).getCount() + solar.inventory.getStackInSlot(3).getCount() == 128 && solar.inventory.getStackInSlot(0).isEmpty(), "Solar commits exactly once after disk restarts");
            h.assertTrue(smelter.inventory.getStackInSlot(2).is(Items.IRON_INGOT) && smelter.inventory.getStackInSlot(2).getCount() == 1 && smelter.energy.stored() == 0, "Smelter pays only remaining 120 after disk restarts");
        }
        checkpoint.round++; checkpoint.setDirty();
        UnusualCopper.LOGGER.info("CuE disk restart checkpoint: round={}, committed={}", checkpoint.round, checkpoint.completed);
        h.succeed();
    }
    private static CopperMachineBlockEntity seed(ServerLevel level, BlockPos pos, MachineKind kind) {
        level.setBlock(pos, ModContent.MACHINES.get(kind).get().defaultBlockState(), 3);
        return (CopperMachineBlockEntity)level.getBlockEntity(pos);
    }
    private static CopperMachineBlockEntity existing(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        if (!(level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine)) throw new IllegalStateException("Missing persisted machine at " + pos);
        return machine;
    }
    private static final class Checkpoint extends SavedData {
        int round;
        BlockPos anchor = BlockPos.ZERO;
        boolean completed;
        static Checkpoint load(CompoundTag tag) { var value = new Checkpoint(); value.round = tag.getInt("Round"); value.anchor = BlockPos.of(tag.getLong("Anchor")); value.completed = tag.getBoolean("Completed"); return value; }
        @Override public CompoundTag save(CompoundTag tag) { tag.putInt("Round", round); tag.putLong("Anchor", anchor.asLong()); tag.putBoolean("Completed", completed); return tag; }
    }
}
