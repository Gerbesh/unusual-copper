package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.WorkArea;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.ShepherdLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdPersistenceGameTests {
    private static final BlockPos ANCHOR = new BlockPos(22008, 70, 22008);
    private static final UUID OWNER = UUID.fromString("bef79e06-e562-4bd0-84de-5e1d2d453aa8");
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void savedAnimalAndWaveDeadlineSurviveRealServerProcesses(GameTestHelper h) {
        var level = h.getLevel(); var target = ANCHOR.east(2);
        var checkpoint = level.getDataStorage().computeIfAbsent(Checkpoint::load, Checkpoint::new, "unusualcopper_test_shepherd_restart_checkpoint");
        // Explicitly force only this isolated test fixture while its entity region finishes loading.
        level.setChunkForced(ANCHOR.getX() >> 4, ANCHOR.getZ() >> 4, true); level.getChunkAt(ANCHOR);
        if (checkpoint.round == 0) {
            level.setBlock(ANCHOR, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ANCHOR, ModContent.MACHINES.get(MachineKind.GROWTH_SHEPHERD).get().defaultBlockState(), 3);
            var machine = (CopperMachineBlockEntity)level.getBlockEntity(ANCHOR); var owner = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(OWNER, "SavedShepherd")); owner.setPos(ANCHOR.getX() + .5, ANCHOR.getY() + .5, ANCHOR.getZ() + .5);
            h.assertTrue(machine.setWorkArea(new WorkArea(target, target), owner), "Fixture owner can select the saved area");
            machine.allowOwnTamed = true; machine.energy.add(800, false);
            var calf = EntityType.COW.create(level); calf.setPos(target.getX() + .5, target.getY() + .1, target.getZ() + .5); calf.setNoAi(true); calf.setNoGravity(true); calf.setAge(-24000); level.addFreshEntity(calf); checkpoint.animal = calf.getUUID();
            machine.redstoneMode = 1; machine.nextWaveTick = level.getGameTime() + 10000; machine.setChanged(); checkpoint.deadline = machine.nextWaveTick;
        }
        h.succeedWhen(() -> {
            h.assertTrue(level.getEntity(checkpoint.animal) instanceof Cow, "The original saved animal UUID has loaded from its entity region");
            var calf = (Cow)level.getEntity(checkpoint.animal); var machine = (CopperMachineBlockEntity)level.getBlockEntity(ANCHOR);
            if (checkpoint.round == 0) {
                for (var old : level.getEntitiesOfClass(Cow.class, new net.minecraft.world.phys.AABB(target).inflate(2))) if (old != calf) old.discard();
                int before = calf.getAge(); machine.redstoneMode = 0; machine.nextWaveTick = 0; ShepherdLogic.process(machine, level);
                h.assertTrue(calf.getAge() == Math.min(0, before + 2400) && machine.energy.stored() == 400, "Initial loaded entity wave is paid exactly once: age=" + calf.getAge() + ", before=" + before + ", energy=" + machine.energy.stored() + ", grown=" + machine.grown + ", inspected=" + machine.inspected);
                machine.redstoneMode = 1; machine.nextWaveTick = level.getGameTime() + 10000; checkpoint.deadline = machine.nextWaveTick; machine.setChanged();
            }
            h.assertTrue(machine != null && OWNER.equals(machine.owner()) && machine.allowOwnTamed && machine.nextWaveTick == checkpoint.deadline && machine.redstoneMode == 1 && machine.workArea().equals(new WorkArea(target, target)), "Disk reload preserves owner, area, permission and future wave deadline");
            if (!checkpoint.completed) {
                h.assertTrue(machine.energy.stored() == 400 && calf.getAge() < 0, "Paid energy and a still-growing real calf survived disk reload");
                if (checkpoint.round > 0) {
                    int before = calf.getAge(); machine.redstoneMode = 0;
                    ShepherdLogic.process(machine, level);
                    h.assertTrue(calf.getAge() == before && machine.energy.stored() == 400, "A reloaded future deadline rejects an extra wave");
                    machine.nextWaveTick = 0; ShepherdLogic.process(machine, level);
                    h.assertTrue(calf.getAge() == Math.min(0, before + 2400) && machine.energy.stored() == 0 && machine.grown == 1, "The loaded index can commit one explicitly resumed wave");
                    machine.redstoneMode = 1; checkpoint.deadline = machine.nextWaveTick; checkpoint.completed = true; machine.setChanged();
                }
            } else {
                int before = calf.getAge(); ShepherdLogic.process(machine, level);
                h.assertTrue(machine.energy.stored() == 0 && calf.getAge() == before, "A later paused restart cannot accelerate or charge the animal again");
            }
            checkpoint.round++; checkpoint.setDirty();
            level.setChunkForced(ANCHOR.getX() >> 4, ANCHOR.getZ() >> 4, false);
            UnusualCopper.LOGGER.info("CuE shepherd disk checkpoint: round={}, resumed={}", checkpoint.round, checkpoint.completed);
        });
    }
    private static final class Checkpoint extends SavedData {
        int round; boolean completed; UUID animal; long deadline;
        static Checkpoint load(CompoundTag tag) { var value = new Checkpoint(); value.round = tag.getInt("Round"); value.completed = tag.getBoolean("Completed"); value.animal = tag.getUUID("Animal"); value.deadline = tag.getLong("Deadline"); return value; }
        @Override public CompoundTag save(CompoundTag tag) { tag.putInt("Round", round); tag.putBoolean("Completed", completed); tag.putUUID("Animal", animal); tag.putLong("Deadline", deadline); return tag; }
    }
}
