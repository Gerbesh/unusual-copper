package io.github.gerbesh.unusualcopper.probe;

import com.mojang.logging.LogUtils;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** Only used by the separate ordinary-server probe JAR. Never shipped in the mod. */
final class EnergyTierProbe {
    private static final UUID OWNER = UUID.fromString("f56111b4-8ac6-4cf5-8879-141b58bc3cc9");
    private static ServerLevel level;
    private static CopperMachineBlockEntity source, sink;
    private static long started;
    private static int sampled;
    private static boolean ready;
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static final class Checkpoint extends SavedData {
        boolean written;
        static Checkpoint load(CompoundTag tag) { var data = new Checkpoint(); data.written = tag.getBoolean("Written"); return data; }
        @Override public CompoundTag save(CompoundTag tag) { tag.putBoolean("Written", written); return tag; }
    }
    private static CopperMachineBlockEntity place(BlockPos pos, MachineKind kind) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModContent.MACHINES.get(kind).get().defaultBlockState(), 3);
        return (CopperMachineBlockEntity)level.getBlockEntity(pos);
    }
    static void start(ServerLevel world, BlockPos anchor) {
        level = world;
        var checkpoint = level.getDataStorage().computeIfAbsent(Checkpoint::load, Checkpoint::new, "unusualcopper_energy_tiers_probe");
        int i = 0;
        for (var kind : new MachineKind[]{MachineKind.COPPER_CELL_II, MachineKind.COPPER_CELL_III}) {
            var pos = anchor.offset(i++ * 2, 0, 4);
            if (checkpoint.written) {
                var entity = level.getBlockEntity(pos);
                check(entity instanceof CopperMachineBlockEntity, "Large cell region checkpoint disappeared");
                var cell = (CopperMachineBlockEntity)entity;
                check(cell.kind == kind && cell.energy.stored() == kind.capacity - 1 && OWNER.equals(cell.owner()) && cell.redstoneMode == 1,
                        "Large cell region restore changed exact charge, tier, owner or pause");
            } else {
                var cell = place(pos, kind); cell.setOwner(OWNER); cell.energy.add(kind.capacity - 1, false); cell.redstoneMode = 1; ((net.minecraft.world.level.block.entity.BlockEntity)cell).setChanged();
            }
        }
        if (checkpoint.written) LogUtils.getLogger().info("CuE PRODUCTION ENERGY TIERS DISK RESTORE PASS: actual region-file cell II/III, exact 1999999/15999999 CuE, owner and redstone pause");
        checkpoint.written = true; checkpoint.setDirty();
        LogUtils.getLogger().info("CuE PRODUCTION ENERGY TIERS DISK CHECKPOINT: two physical large cells, exact charge, owner and pause");
        for (int x = 0; x < 5; x++) level.setBlock(anchor.east(x), Blocks.AIR.defaultBlockState(), 3);
        source = place(anchor, MachineKind.ESSENCE_SPLITTER); source.energy.add(50_000, false);
        for (int x = 1; x < 4; x++) level.setBlock(anchor.east(x), ModContent.CRYSTALLINE_ENERGY_PIPE.get().defaultBlockState(), 3);
        sink = place(anchor.east(4), MachineKind.COPPER_CELL_III);
        started = level.getGameTime(); ready = false;
    }
    static void tick() {
        if (level == null || ready) return;
        long elapsed = level.getGameTime() - started;
        if (elapsed == 2) sampled = sink.energy.stored();
        if (elapsed == 3) {
            check(sink.energy.stored() - sampled == 4096, "Ordinary crystalline path did not transfer exactly 4096 CuE in one tick");
            check(source.energy.stored() + sink.energy.stored() == 50_000, "Ordinary tier network changed total energy");
            source.redstoneMode = 1; ((net.minecraft.world.level.block.entity.BlockEntity)source).setChanged(); ready = true;
            LogUtils.getLogger().info("CuE PRODUCTION ENERGY TIERS PASS: three real crystalline pipes and cell III transfer exactly 4096 CuE/tick without creating or losing charge");
        }
        check(elapsed < 20 || ready, "Energy tier production sample timed out");
    }
    static boolean ready() { return ready; }
}
