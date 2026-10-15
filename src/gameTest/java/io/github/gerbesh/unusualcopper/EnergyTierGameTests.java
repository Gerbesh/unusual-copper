package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.energy.CopperCapabilities;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnergyTierGameTests {
    private static CopperMachineBlockEntity place(GameTestHelper h, BlockPos pos, MachineKind kind) {
        h.setBlock(pos, ModContent.MACHINES.get(kind).get());
        return (CopperMachineBlockEntity)h.getBlockEntity(pos);
    }
    private static void rate(GameTestHelper h, Block pipe, int expected) {
        var source = place(h, new BlockPos(0, 1, 2), MachineKind.ESSENCE_SPLITTER);
        source.energy.add(50_000, false);
        for (int x = 1; x < 4; x++) h.setBlock(new BlockPos(x, 1, 2), pipe);
        var sink = place(h, new BlockPos(4, 1, 2), MachineKind.COPPER_CELL_III);
        int[] sample = new int[1];
        h.runAtTickTime(3, () -> sample[0] = sink.energy.stored());
        h.runAtTickTime(4, () -> {
            h.assertTrue(sink.energy.stored() - sample[0] == expected, "Wrong actual pipe throughput: " + (sink.energy.stored() - sample[0]));
            h.assertTrue(sink.energy.stored() + source.energy.stored() == 50_000, "Tier network created or lost CuE");
            h.succeed();
        });
    }
    @GameTest(template="empty", timeoutTicks=30)
    public static void basicPipeStillLimitsAdvancedMachinePorts(GameTestHelper h) { rate(h, ModContent.ENERGY_PIPE.get(), 256); }
    @GameTest(template="empty", timeoutTicks=30)
    public static void reinforcedPipeTransfersExactly1024(GameTestHelper h) { rate(h, ModContent.REINFORCED_ENERGY_PIPE.get(), 1024); }
    @GameTest(template="empty", timeoutTicks=30)
    public static void crystallinePipeTransfersExactly4096(GameTestHelper h) { rate(h, ModContent.CRYSTALLINE_ENERGY_PIPE.get(), 4096); }

    @GameTest(template="empty", timeoutTicks=30)
    public static void mixedTierUpgradeAndDowngradeRebuildPathCapacity(GameTestHelper h) {
        var source = place(h, new BlockPos(0, 1, 2), MachineKind.ESSENCE_SPLITTER);
        source.energy.add(50_000, false);
        for (int x = 1; x < 4; x++) h.setBlock(new BlockPos(x, 1, 2), ModContent.CRYSTALLINE_ENERGY_PIPE.get());
        var weak = new BlockPos(2, 1, 2); h.setBlock(weak, ModContent.ENERGY_PIPE.get());
        var sink = place(h, new BlockPos(4, 1, 2), MachineKind.COPPER_CELL_III);
        int[] sample = new int[1];
        h.runAtTickTime(3, () -> sample[0] = sink.energy.stored());
        h.runAtTickTime(4, () -> {
            h.assertTrue(sink.energy.stored() - sample[0] == 256, "Mixed path bypassed its basic bottleneck");
            h.setBlock(weak, ModContent.CRYSTALLINE_ENERGY_PIPE.get());
        });
        h.runAtTickTime(6, () -> sample[0] = sink.energy.stored());
        h.runAtTickTime(7, () -> {
            h.assertTrue(sink.energy.stored() - sample[0] == 4096, "Upgraded path retained a stale capacity");
            h.setBlock(weak, ModContent.REINFORCED_ENERGY_PIPE.get());
        });
        h.runAtTickTime(9, () -> sample[0] = sink.energy.stored());
        h.runAtTickTime(10, () -> {
            h.assertTrue(sink.energy.stored() - sample[0] == 1024, "Downgraded path retained a stale capacity");
            h.assertTrue(source.energy.stored() + sink.energy.stored() == 50_000, "Replacing pipe tiers lost or duplicated energy");
            h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=30)
    public static void sharedTrunkAndCellPortsShareOneTickBudget(GameTestHelper h) {
        var source = place(h, new BlockPos(0, 2, 2), MachineKind.ESSENCE_SPLITTER); source.energy.add(50_000, false);
        h.setBlock(new BlockPos(1, 2, 2), ModContent.CRYSTALLINE_ENERGY_PIPE.get());
        h.setBlock(new BlockPos(2, 2, 2), ModContent.CRYSTALLINE_ENERGY_PIPE.get());
        var a = place(h, new BlockPos(3, 2, 2), MachineKind.COPPER_CELL_III);
        var b = place(h, new BlockPos(2, 2, 3), MachineKind.COPPER_CELL_III);
        var c = place(h, new BlockPos(2, 3, 2), MachineKind.COPPER_CELL_II);
        var d = place(h, new BlockPos(2, 1, 2), MachineKind.COPPER_CELL);
        int[] sample = new int[5];
        h.runAtTickTime(3, () -> { sample[0] = a.energy.stored() + b.energy.stored() + c.energy.stored() + d.energy.stored(); sample[1] = c.energy.stored(); sample[2] = d.energy.stored(); });
        h.runAtTickTime(4, () -> {
            h.assertTrue(a.energy.stored() + b.energy.stored() + c.energy.stored() + d.energy.stored() - sample[0] == 4096, "Shared trunk budget was spent once per consumer");
            h.assertTrue(c.energy.stored() - sample[1] <= 1024 && d.energy.stored() - sample[2] <= 256, "Cell tier limits were bypassed");
        });
        h.runAtTickTime(8, () -> {
            h.assertTrue(a.energy.stored() > 0 && b.energy.stored() > 0 && c.energy.stored() > 0 && d.energy.stored() > 0, "Same-priority cell tiers starved");
            h.assertTrue(source.energy.stored() + a.energy.stored() + b.energy.stored() + c.energy.stored() + d.energy.stored() == 50_000, "Branched tier network changed total CuE");
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void largeCellsPreserveExactCapacityChargeOwnerAndWireValues(GameTestHelper h) {
        int x = 1;
        for (var kind : new MachineKind[]{MachineKind.COPPER_CELL_II, MachineKind.COPPER_CELL_III}) {
            var cell = place(h, new BlockPos(x++, 1, 1), kind); var owner = UUID.randomUUID(); cell.setOwner(owner);
            int charge = kind.capacity - 1; cell.energy.add(charge, false); cell.redstoneMode = 2;
            var restored = (CopperMachineBlockEntity)BlockEntity.loadStatic(cell.getBlockPos(), cell.getBlockState(), cell.saveWithFullMetadata());
            h.assertTrue(restored != null && restored.kind == kind && restored.energy.stored() == charge && restored.energy.capacity() == kind.capacity && owner.equals(restored.owner()) && restored.redstoneMode == 2, "Large cell NBT truncated charge, capacity or private settings");
            h.assertTrue(!cell.getCapability(ForgeCapabilities.ENERGY).isPresent() && !cell.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Large cell exposed FE or a hidden inventory");
            var port = cell.getCapability(CopperCapabilities.ENERGY, Direction.WEST).orElseThrow(() -> new IllegalStateException("Missing CuE input"));
            h.assertTrue(port.receive(10_000, true) == 1 && cell.energy.stored() == charge, "Nearly full cell simulation changed charge or exceeded capacity");
            var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                buffer.writeBlockPos(cell.getBlockPos()); var menu = new CopperMachineMenu(7, h.makeMockPlayer().getInventory(), buffer);
                var data = cell.menuData();
                for (int i = 0; i < data.getCount(); i++) {
                    buffer.clear(); new ClientboundContainerSetDataPacket(7, i, data.get(i)).write(buffer);
                    var packet = new ClientboundContainerSetDataPacket(buffer); menu.data.set(packet.getId(), packet.getValue());
                }
                h.assertTrue(menu.energy() == charge && menu.capacity() == kind.capacity && menu.kind() == kind, "Actual signed-short menu packets truncated a large cell");
            } finally { buffer.release(); }
        }
        h.succeed();
    }
}
