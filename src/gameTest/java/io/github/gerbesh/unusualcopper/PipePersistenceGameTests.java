package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.itempipe.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipePersistenceGameTests {
    private static final BlockPos ANCHOR = new BlockPos(180, 70, 100);
    private static final UUID OWNER = UUID.fromString("d6e66130-f8e8-4fb1-9ac7-355492d08fe3");
    private static ItemStack cargo(int count) { var stack = new ItemStack(Items.COPPER_INGOT, count); stack.setHoverName(Component.literal("persisted cargo")); return stack; }
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void pausedCargoAndRoutingSurviveTenDiskRestarts(GameTestHelper h) {
        var level = h.getLevel(); var checkpoint = level.getDataStorage().computeIfAbsent(Checkpoint::load, Checkpoint::new, "unusualcopper_test_pipe_restart_checkpoint");
        level.getChunkAt(ANCHOR); level.getChunkAt(ANCHOR.east());
        if (checkpoint.round == 0) {
            level.setBlock(ANCHOR, ModContent.ITEM_PIPE.get().defaultBlockState(), 3); level.setBlock(ANCHOR.east(), Blocks.BARREL.defaultBlockState(), 3);
            var pipe = (ItemPipeBlockEntity)level.getBlockEntity(ANCHOR); pipe.owner(OWNER); pipe.redstoneMode = 1;
            pipe.hold(List.of(cargo(96)), ANCHOR.west(), Direction.WEST);
            var rule = pipe.rule(Direction.EAST); rule.mode = PipeMode.INSERT; rule.exactNbt = true; rule.filter(0, cargo(1)); rule.settings(20, 8, 96, 0); pipe.changed();
        }
        var pipe = (ItemPipeBlockEntity)level.getBlockEntity(ANCHOR); var target = (BarrelBlockEntity)level.getBlockEntity(ANCHOR.east()); var rule = pipe.rule(Direction.EAST);
        h.assertTrue(OWNER.equals(pipe.owner()) && rule.mode == PipeMode.INSERT && rule.exactNbt && rule.maximum == 96 && rule.priority == 20, "Disk reload retains owner, face mode, filter data and stock settings");
        if (!checkpoint.completed) {
            h.assertTrue(pipe.cargoCount() == 96 && pipe.cargo().get(0).getHoverName().getString().equals("persisted cargo") && target.isEmpty(), "Paused cargo stays exact across server processes");
            if (checkpoint.round >= 10) {
                pipe.redstoneMode = 0; pipe.changed();
                h.succeedWhen(() -> {
                    int total = 0; for (int slot = 0; slot < target.getContainerSize(); slot++) total += target.getItem(slot).getCount();
                    h.assertTrue(pipe.cargoCount() == 0 && total == 96, "After ten disk restarts the exact cargo is delivered once");
                    checkpoint.completed = true; checkpoint.round++; checkpoint.setDirty();
                    UnusualCopper.LOGGER.info("CuE pipe disk checkpoint: round={}, committed=true", checkpoint.round);
                });
                return;
            }
        } else {
            int total = 0; for (int slot = 0; slot < target.getContainerSize(); slot++) total += target.getItem(slot).getCount();
            h.assertTrue(pipe.cargoCount() == 0 && total == 96, "Later restart does not deliver the cargo again");
        }
        checkpoint.round++; checkpoint.setDirty(); UnusualCopper.LOGGER.info("CuE pipe disk checkpoint: round={}, committed={}", checkpoint.round, checkpoint.completed); h.succeed();
    }
    private static final class Checkpoint extends SavedData {
        int round; boolean completed;
        static Checkpoint load(CompoundTag tag) { var value = new Checkpoint(); value.round = tag.getInt("Round"); value.completed = tag.getBoolean("Completed"); return value; }
        @Override public CompoundTag save(CompoundTag tag) { tag.putInt("Round", round); tag.putBoolean("Completed", completed); return tag; }
    }
}
