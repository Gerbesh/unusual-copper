package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreGameTests {
    @GameTest(template = "empty")
    public static void menuSurvivesSignedShortWireValues(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.COPPER_CELL);
        m.energy.add(123_456, false); m.progress = 160_000; m.duration = 384_000; m.operationCost = 24_000;
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            buffer.writeBlockPos(m.getBlockPos());
            var menu = new io.github.gerbesh.unusualcopper.menu.CopperMachineMenu(7, h.makeMockPlayer().getInventory(), buffer);
            var data = m.menuData();
            for (int i = 0; i < data.getCount(); i++) {
                buffer.clear();
                new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(7, i, data.get(i)).write(buffer);
                var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(buffer);
                menu.data.set(packet.getId(), packet.getValue());
            }
            h.assertTrue(menu.energy() == 123_456 && menu.capacity() == 250_000, "Energy remains exact over the real menu packet encoding");
            h.assertTrue(menu.progress() == 160_000 && menu.duration() == 384_000 && menu.operationCost() == 24_000, "Long configured jobs retain progress, duration and quoted cost");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static CopperMachineBlockEntity place(GameTestHelper helper, BlockPos pos, MachineKind kind) {
        helper.setBlock(pos, ModContent.MACHINES.get(kind).get());
        var machine = (CopperMachineBlockEntity)helper.getBlockEntity(pos);
        if (kind == MachineKind.SOLAR_INFUSER) {
            // GameTest arenas may be enclosed by framework barrier blocks.
            for (int y = machine.getBlockPos().getY() + 1; y < helper.getLevel().getMaxBuildHeight(); y++) {
                helper.getLevel().setBlock(new BlockPos(machine.getBlockPos().getX(), y, machine.getBlockPos().getZ()), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return machine;
    }
    private static int essence(CopperMachineBlockEntity machine) { return machine.inventory.getStackInSlot(2).getCount() + machine.inventory.getStackInSlot(3).getCount(); }
    private static void solarInput(CopperMachineBlockEntity m, int count, boolean ingots) {
        m.inventory.setStackInSlot(0, new ItemStack(ingots ? Items.COPPER_INGOT : Items.RAW_COPPER, count));
        m.inventory.setStackInSlot(1, new ItemStack(ModContent.SOLAR_LENS.get()));
    }

    @GameTest(template = "empty")
    public static void fullSolarBatchAndPartialLensWear(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.SOLAR_INFUSER);
        h.getLevel().setDayTime(6000); h.getLevel().setWeatherParameters(0, 0, false, false);
        solarInput(m, 64, true); var logic = new SolarLogic();
        for (int i = 0; i < 799; i++) logic.tick(m, h.getLevel());
        h.assertTrue(m.progress == 3196, "Clear-sky progress=" + m.progress + ", status=" + m.status() + ", sky=" + h.getLevel().canSeeSky(m.getBlockPos().above()) + ", time=" + h.getLevel().getDayTime());
        h.assertTrue(essence(m) == 0, "No premature output");
        logic.tick(m, h.getLevel());
        h.assertTrue(essence(m) == 128 && m.inventory.getStackInSlot(0).isEmpty(), "Exactly two stacks of essence");
        h.assertTrue(m.inventory.getStackInSlot(1).getDamageValue() == 64, "Full batch wears 64 input units");
        m.inventory.setStackInSlot(2, ItemStack.EMPTY); m.inventory.setStackInSlot(3, ItemStack.EMPTY);
        m.inventory.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 1));
        for (int i = 0; i < 13; i++) logic.tick(m, h.getLevel());
        h.assertTrue(essence(m) == 2 && m.inventory.getStackInSlot(1).getDamageValue() == 65, "Partial batch is proportional and wears lens");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rawCopperHasNoIngotBonus(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.SOLAR_INFUSER);
        h.getLevel().setDayTime(6000); solarInput(m, 64, false);
        for (int i = 0; i < 800; i++) new SolarLogic().tick(m, h.getLevel());
        h.assertTrue(essence(m) == 64, "Raw copper produces exactly 64 essence"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void solar128ResultSurvivesSerialization(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.SOLAR_INFUSER);
        h.getLevel().setDayTime(6000); solarInput(m, 64, true); var logic = new SolarLogic();
        for (int i = 0; i < 300; i++) logic.tick(m, h.getLevel());
        for (int restart = 0; restart < 10; restart++) {
            var saved = m.saveWithFullMetadata();
            m = (CopperMachineBlockEntity)BlockEntity.loadStatic(m.getBlockPos(), m.getBlockState(), saved);
            h.assertTrue(m != null && m.result.getCount() == 128 && m.progress == 1200, "128 result and progress survive reload " + restart);
            m.setLevel(h.getLevel());
        }
        for (int i = 0; i < 500; i++) logic.tick(m, h.getLevel());
        h.assertTrue(essence(m) == 128 && m.inventory.getStackInSlot(0).isEmpty(), "Restored batch commits once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void solarNightRoofAndFullOutputPause(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.SOLAR_INFUSER); solarInput(m, 64, true);
        var logic = new SolarLogic(); h.getLevel().setDayTime(18000); logic.tick(m, h.getLevel());
        h.assertTrue(m.status() == MachineStatus.NO_SUN && m.progress == 0, "Night does not behave as sunlight");
        h.getLevel().setDayTime(6000); h.setBlock(new BlockPos(1, 2, 1), Blocks.STONE); logic.tick(m, h.getLevel());
        h.assertTrue(m.progress == 0, "Covered sky pauses infusion");
        h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        m.inventory.setStackInSlot(2, new ItemStack(ModContent.ESSENCE.get(), 1)); logic.tick(m, h.getLevel());
        h.assertTrue(m.status() == MachineStatus.OUTPUT_FULL && m.progress == 0, "127 free slots cannot accept 128");
        h.assertTrue(m.inventory.getStackInSlot(0).getCount() == 64 && m.inventory.getStackInSlot(1).getDamageValue() == 0, "Paused inputs intact"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void splitterStopsBeforeConsumingLastEssence(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.ESSENCE_SPLITTER);
        m.energy.add(49_750, false); m.inventory.setStackInSlot(0, new ItemStack(ModContent.ESSENCE.get(), 1));
        new SplitterLogic().tick(m, h.getLevel());
        h.assertTrue(m.energy.stored() == 49_750 && m.inventory.getStackInSlot(0).getCount() == 1, "Incomplete 500 CuE room cannot consume");
        h.assertTrue(!m.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(), "No Forge Energy input/output masquerading as CuE"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void smelterFullStackExactCostAndTime(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER);
        m.inventory.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 64)); m.energy.add(15_360, false); var logic = new SmelterLogic();
        for (int i = 0; i < 1279; i++) logic.tick(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).getCount() == 63, "64th result is not early"); logic.tick(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).is(Items.IRON_INGOT) && m.inventory.getStackInSlot(2).getCount() == 64, "Exact vanilla recipe output");
        h.assertTrue(m.energy.stored() == 0 && m.inventory.getStackInSlot(0).isEmpty(), "15,360 CuE consumed in 1,280 operation ticks"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void replacingInputNeverCarriesPaidProgress(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER); var logic = new SmelterLogic();
        m.inventory.setStackInSlot(0, new ItemStack(Items.RAW_IRON)); m.energy.add(240, false);
        for (int i = 0; i < 10; i++) logic.tick(m, h.getLevel());
        m.inventory.setStackInSlot(0, new ItemStack(Items.RAW_COPPER)); logic.tick(m, h.getLevel());
        h.assertTrue(m.progress == 1 && m.paid == 12 && m.source.is(Items.RAW_COPPER), "Replacement begins a fresh quoted job");
        h.assertTrue(m.inventory.getStackInSlot(2).isEmpty(), "Old iron result is never awarded");
        m.energy.add(120, false); for (int i = 0; i < 19; i++) logic.tick(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).is(Items.COPPER_INGOT) && m.energy.stored() == 0, "Only new copper output; cancelled CuE not refunded"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void smelterPauseRestoreAndOutputProtection(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER);
        m.inventory.setStackInSlot(0, new ItemStack(Items.RAW_IRON)); m.energy.add(120, false); var logic = new SmelterLogic();
        for (int i = 0; i < 20; i++) logic.tick(m, h.getLevel());
        h.assertTrue(m.progress == 10 && m.paid == 120 && m.inventory.getStackInSlot(2).isEmpty(), "Half-paid task pauses");
        var saved = m.saveWithFullMetadata(); m = (CopperMachineBlockEntity)BlockEntity.loadStatic(m.getBlockPos(), m.getBlockState(), saved); m.setLevel(h.getLevel());
        m.inventory.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 64)); m.inventory.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64)); m.energy.add(120, false);
        logic.tick(m, h.getLevel()); h.assertTrue(m.progress == 10 && m.energy.stored() == 120, "Full output charges nothing");
        m.inventory.setStackInSlot(2, ItemStack.EMPTY);
        for (int i = 0; i < 10; i++) logic.tick(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).is(Items.IRON_INGOT) && m.energy.stored() == 0, "Resume pays only remaining 120"); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void realTickNetworkMovesOneIngotStack(GameTestHelper h) {
        var splitter = place(h, new BlockPos(1, 1, 1), MachineKind.ESSENCE_SPLITTER);
        h.setBlock(new BlockPos(2, 1, 1), ModContent.ENERGY_PIPE.get()); h.setBlock(new BlockPos(3, 1, 1), ModContent.ENERGY_PIPE.get());
        var cell = place(h, new BlockPos(4, 1, 1), MachineKind.COPPER_CELL);
        splitter.inventory.setStackInSlot(0, new ItemStack(ModContent.ESSENCE.get(), 64));
        h.runAtTickTime(20, () -> splitter.inventory.setStackInSlot(0, new ItemStack(ModContent.ESSENCE.get(), 64)));
        h.succeedWhen(() -> {
            h.assertTrue(cell.energy.stored() == 64_000, "Cell receives exactly 64,000 through actual world ticks; actual=" + cell.energy.stored() + ", source=" + splitter.energy.stored() + ", input=" + splitter.inventory.getStackInSlot(0).getCount());
            h.assertTrue(splitter.energy.stored() == 0 && splitter.inventory.getStackInSlot(0).isEmpty(), "Splitter empty; nothing copied");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void flowingWaterPreservesEnergyPipe(GameTestHelper h) {
        var pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModContent.ENERGY_PIPE.get());
        h.setBlock(pos.above(), Blocks.WATER);
        h.runAtTickTime(40, () -> {
            h.assertTrue(h.getBlockState(pos).is(ModContent.ENERGY_PIPE.get()) && h.getBlockEntity(pos) instanceof EnergyPipeBlockEntity, "Flowing water cannot replace the pipe or its network node");
            var pipe = (net.minecraft.world.level.block.SimpleWaterloggedBlock)h.getBlockState(pos).getBlock();
            h.assertTrue(pipe.placeLiquid(h.getLevel(), h.absolutePos(pos), h.getBlockState(pos), net.minecraft.world.level.material.Fluids.WATER.getSource(false)), "Standard bucket interface accepts a source water block");
            h.assertTrue(h.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED), "Pipe retains source water through the standard waterlogging property");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sharedPipeBottleneckAndFairness(GameTestHelper h) {
        var splitter = place(h, new BlockPos(1, 1, 1), MachineKind.ESSENCE_SPLITTER); splitter.energy.add(10_000, false);
        h.setBlock(new BlockPos(2, 1, 1), ModContent.ENERGY_PIPE.get());
        var first = place(h, new BlockPos(3, 1, 1), MachineKind.COPPER_CELL);
        var second = place(h, new BlockPos(2, 1, 2), MachineKind.COPPER_CELL);
        int[] sample = new int[1];
        h.runAtTickTime(3, () -> sample[0] = first.energy.stored() + second.energy.stored());
        h.runAtTickTime(4, () -> h.assertTrue(first.energy.stored() + second.energy.stored() - sample[0] <= 256, "One pipe cannot transmit 512 to two consumers in one tick"));
        h.runAtTickTime(45, () -> {
            h.assertTrue(first.energy.stored() > 0 && second.energy.stored() > 0, "Equal-priority consumers do not starve");
            h.assertTrue(first.energy.stored() + second.energy.stored() + splitter.energy.stored() == 10_000, "Graph conserves energy"); h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void rainStormAndGlassRoofUseExplicitSkyPolicy(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.SOLAR_INFUSER); solarInput(m, 8, true);
        h.getLevel().setDayTime(6000); h.getLevel().setWeatherParameters(0, 0, false, false); var logic = new SolarLogic();
        logic.tick(m, h.getLevel()); h.assertTrue(m.progress == 4, "Clear sun rate 4");
        h.getLevel().setWeatherParameters(0, 6000, true, false); logic.tick(m, h.getLevel()); h.assertTrue(m.progress == 6, "Rain halves the rate");
        h.getLevel().setWeatherParameters(0, 6000, true, true); logic.tick(m, h.getLevel()); h.assertTrue(m.progress == 7, "Storm quarters the rate");
        h.setBlock(new BlockPos(1, 2, 1), Blocks.GLASS); logic.tick(m, h.getLevel()); h.assertTrue(m.progress == 7, "Transparent roof still obstructs open sky");
        h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR); h.getLevel().setWeatherParameters(0, 0, false, false);
        logic.tick(m, h.getLevel()); h.assertTrue(m.progress == 11, "Clear sky resumes preserved progress"); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void cableCycleBreakAndRejoinConserveEnergy(GameTestHelper h) {
        var source = place(h, new BlockPos(0, 1, 2), MachineKind.ESSENCE_SPLITTER); source.energy.add(10_000, false);
        for (var pos : new BlockPos[]{new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(2, 1, 1), new BlockPos(1, 1, 1)}) h.setBlock(pos, ModContent.ENERGY_PIPE.get());
        var cell = place(h, new BlockPos(3, 1, 2), MachineKind.COPPER_CELL); int[] sample = new int[1];
        h.runAtTickTime(10, () -> { h.setBlock(new BlockPos(2, 1, 2), Blocks.AIR); sample[0] = cell.energy.stored(); });
        h.runAtTickTime(20, () -> {
            h.assertTrue(cell.energy.stored() == sample[0], "Broken boundary pauses transport");
            h.assertTrue(cell.energy.stored() + source.energy.stored() == 10_000, "Break loses no CuE");
            h.setBlock(new BlockPos(2, 1, 2), ModContent.ENERGY_PIPE.get());
        });
        h.succeedWhen(() -> h.assertTrue(cell.energy.stored() == 10_000 && source.energy.stored() == 0, "Rejoined cycle transfers exactly once"));
    }

    @GameTest(template = "empty")
    public static void retiredCapabilityCannotDuplicateDroppedItems(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER);
        m.inventory.setStackInSlot(2, new ItemStack(Items.IRON_INGOT, 8));
        var oldOutput = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(() -> new IllegalStateException("No output port"));
        m.cycleSide(Direction.DOWN);
        h.assertTrue(oldOutput.extractItem(2, 8, false).isEmpty(), "Reconfigured port tombstones cached handler");
        while (m.sideMode(Direction.DOWN) != SideMode.ITEM_OUTPUT) m.cycleSide(Direction.DOWN);
        var currentOutput = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(() -> new IllegalStateException("No rebuilt output"));
        m.setRemoved();
        h.assertTrue(currentOutput.extractItem(2, 8, false).isEmpty(), "Removed machine cannot be drained through a cached interface");
        h.assertTrue(m.inventory.getStackInSlot(2).getCount() == 8, "Rejected access leaves stacks unchanged"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sidedItemsAndCancelledInput(GameTestHelper h) {
        var m = place(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER);
        var top = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(() -> new IllegalStateException("Missing top handler"));
        var bottom = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(() -> new IllegalStateException("Missing bottom handler"));
        h.assertTrue(top.insertItem(0, new ItemStack(Items.RAW_IRON), false).isEmpty(), "Top accepts ore");
        h.assertTrue(top.insertItem(2, new ItemStack(Items.IRON_INGOT), false).getCount() == 1, "Cannot insert in output");
        h.assertTrue(bottom.extractItem(0, 1, false).isEmpty(), "Output side cannot steal input");
        m.energy.add(120, false); new SmelterLogic().tick(m, h.getLevel()); m.cancelOperation();
        h.assertTrue(m.inventory.getStackInSlot(0).getCount() == 1 && m.inventory.getStackInSlot(2).isEmpty(), "Cancellation does not invent a result or remove ore"); h.succeed();
    }
}
