package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CultivatorGameTests {
    private static CopperMachineBlockEntity machine(GameTestHelper h, BlockPos pos, MachineKind kind) {
        h.setBlock(pos, ModContent.MACHINES.get(kind).get()); var m = (CopperMachineBlockEntity)h.getBlockEntity(pos);
        h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, h.getLevel().getServer()); return m;
    }
    private static Player player(GameTestHelper h, CopperMachineBlockEntity machine) {
        var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AreaTest"));
        player.setPos(machine.getBlockPos().getX() + .5, machine.getBlockPos().getY() + .5, machine.getBlockPos().getZ() + .5); return player;
    }
    private static void wheat(GameTestHelper h, BlockPos pos, int age, int moisture) {
        h.setBlock(pos.above(), Blocks.GLOWSTONE);
        h.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
        h.setBlock(pos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age));
    }
    @GameTest(template = "empty")
    public static void waveChecksAtMostEightAndChargesOnlySuccessfulGrowth(GameTestHelper h) {
        var m = machine(h, new BlockPos(0, 1, 0), MachineKind.AURA_CULTIVATOR);
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) wheat(h, new BlockPos(x, 1, z), 0, 7);
        var actor = player(h, m); h.assertTrue(m.setWorkArea(WorkArea.between(h.absolutePos(new BlockPos(1, 1, 1)), h.absolutePos(new BlockPos(4, 1, 4))), actor), "Valid 4x4 work area selected");
        m.redstoneMode = 1;
        h.runAfterDelay(10, () -> {
            m.redstoneMode = 0;
            for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) wheat(h, new BlockPos(x, 1, z), 0, 7);
        m.energy.add(1120, false); CultivatorLogic.process(m, h.getLevel());
        int changed = 0; for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) if (h.getBlockState(new BlockPos(x, 1, z)).getValue(CropBlock.AGE) > 0) changed++;
        h.assertTrue(m.inspected == 8 && m.grown == 8 && changed == 8 && m.energy.stored() == 0, "One wave grows exactly eight of sixteen plants at 140 CuE each");
        h.assertTrue(m.inventory.getStackInSlot(0).isEmpty() && m.inventory.getStackInSlot(2).isEmpty(), "No fertilizer is consumed and no hidden harvest is issued"); h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void outwardSweepContinuesAcrossTicksWithoutGrowingTheFirstBatchTwice(GameTestHelper h) {
        var m=machine(h,new BlockPos(0,1,0),MachineKind.AURA_CULTIVATOR);m.redstoneMode=1;
        for(int x=1;x<=4;x++)for(int z=1;z<=4;z++)wheat(h,new BlockPos(x,1,z),0,7);
        h.assertTrue(m.setWorkArea(WorkArea.between(h.absolutePos(new BlockPos(1,1,1)),h.absolutePos(new BlockPos(4,1,4))),player(h,m)),"Area setup failed");
        h.runAfterDelay(10,()->{
            m.redstoneMode=0;m.energy.restore(2240);CultivatorLogic.process(m,h.getLevel());
            var first=new HashMap<BlockPos,Integer>();
            for(int x=1;x<=4;x++)for(int z=1;z<=4;z++) {
                var pos=new BlockPos(x,1,z);int age=h.getBlockState(pos).getValue(CropBlock.AGE);if(age>0)first.put(pos,age);
            }
            h.assertTrue(first.size()==8 && m.cultivation.active && m.nextWaveTick==h.getLevel().getGameTime()+1,"First bounded batch did not schedule its continuation for next tick");
            CultivatorLogic.process(m,h.getLevel());
            for(int x=1;x<=4;x++)for(int z=1;z<=4;z++)h.assertTrue(h.getBlockState(new BlockPos(x,1,z)).getValue(CropBlock.AGE)>0,"Wave left a crop untouched");
            first.forEach((pos,age)->h.assertTrue(h.getBlockState(pos).getValue(CropBlock.AGE)==age,"First batch was revisited in the same wave"));
            h.assertTrue(m.grown==16 && m.inspected==16 && m.energy.stored()==0 && !m.cultivation.active && m.scanCursor==0 && m.nextWaveTick==h.getLevel().getGameTime()+20,"Complete wave did not preserve accounting/cursor/cooldown");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void matureDryUnsupportedAndEnergyFailureDoNotCharge(GameTestHelper h) {
        var m = machine(h, new BlockPos(0, 1, 0), MachineKind.AURA_CULTIVATOR); var actor = player(h, m); var pos = new BlockPos(2, 1, 2);
        wheat(h, pos, 7, 7); m.setWorkArea(new WorkArea(h.absolutePos(pos), h.absolutePos(pos)), actor); m.energy.add(140, false);
        m.redstoneMode = 1;
        h.runAfterDelay(10, () -> {
            m.redstoneMode = 0; wheat(h, pos, 7, 7);
        CultivatorLogic.process(m, h.getLevel()); h.assertTrue(m.grown == 0 && m.energy.stored() == 140 && h.getBlockState(pos).getValue(CropBlock.AGE) == 7, "Mature plants are not harvested or charged");
        wheat(h, pos, 0, 0); CultivatorLogic.process(m, h.getLevel()); h.assertTrue(m.grown == 0 && m.energy.stored() == 140, "Dry soil does not receive forced growth");
        h.setBlock(pos.below(), Blocks.SAND); h.setBlock(pos, Blocks.CACTUS); CultivatorLogic.process(m, h.getLevel());
        h.assertTrue(m.grown == 0 && m.energy.stored() == 140 && m.status() == MachineStatus.UNSUPPORTED, "Cactus needs a species adapter");
        wheat(h, pos, 0, 7); m.energy.spend(1); CultivatorLogic.process(m, h.getLevel());
        h.assertTrue(m.grown == 0 && m.energy.stored() == 139 && h.getBlockState(pos).getValue(CropBlock.AGE) == 0 && m.status() == MachineStatus.NO_ENERGY, "An incomplete 140-CuE payment cannot grow a plant"); h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void maturityModeSweepsBothPlantsAndSavedSettingsRemainExact(GameTestHelper h) {
        var m = machine(h, new BlockPos(0, 1, 0), MachineKind.AURA_CULTIVATOR); var actor = player(h, m);
        wheat(h, new BlockPos(1, 1, 1), 0, 7); wheat(h, new BlockPos(2, 1, 1), 0, 7);
        m.setWorkArea(WorkArea.between(h.absolutePos(new BlockPos(1, 1, 1)), h.absolutePos(new BlockPos(2, 1, 1))), actor); m.growthMode = 1; m.energy.add(1120, false);
        CultivatorLogic.process(m, h.getLevel());
        h.assertTrue(h.getBlockState(new BlockPos(1,1,1)).getValue(CropBlock.AGE)>0 && h.getBlockState(new BlockPos(2,1,1)).getValue(CropBlock.AGE)>0 && m.grown==2 && m.energy.stored()==840,"Maturity mode focused on one plant instead of sweeping both once");
        for(int i=0;i<3;i++)CultivatorLogic.process(m,h.getLevel());
        h.assertTrue(h.getBlockState(new BlockPos(1,1,1)).getValue(CropBlock.AGE)==7 && h.getBlockState(new BlockPos(2,1,1)).getValue(CropBlock.AGE)==7 && m.energy.stored()+m.energy.consumed()==1120,"Repeated field sweeps did not mature both plants or lost energy accounting");
        var restored = (CopperMachineBlockEntity)BlockEntity.loadStatic(m.getBlockPos(), m.getBlockState(), m.saveWithFullMetadata());
        h.assertTrue(restored != null && restored.workArea().equals(m.workArea()) && restored.growthMode == 1 && restored.scanCursor == m.scanCursor && restored.owner().equals(actor.getUUID()), "Area, mode, cursor and owner survive NBT reload"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void areaSelectionRejectsForeignOwnersFarBoundsAndInvalidEnergySides(GameTestHelper h) {
        var m = machine(h, new BlockPos(1, 1, 1), MachineKind.AURA_CULTIVATOR); var owner = player(h, m); m.setOwner(owner.getUUID());
        var other = player(h, m); var area = new WorkArea(m.getBlockPos(), m.getBlockPos());
        h.assertTrue(!m.setWorkArea(area, other) && m.workArea() == null, "Foreign actor cannot set the owner's work area");
        var far = m.getBlockPos().east(100); h.assertTrue(!m.setWorkArea(new WorkArea(far, far), owner), "Valid-size remote area cannot exceed the machine radius");
        var diagonal = new WorkArea(m.getBlockPos().offset(10, -28, 0), m.getBlockPos().offset(25, -13, 0));
        h.assertTrue(!AreaAccess.geometryValid(h.getLevel(), m.getBlockPos(), diagonal), "Every corner must fit the radius, including the farthest mixed-coordinate corner");
        h.assertTrue(m.setWorkArea(area, owner), "Owner can set a local area");
        for (int i = 0; i < 12; i++) { m.cycleSide(Direction.NORTH); h.assertTrue(m.sideMode(Direction.NORTH) == SideMode.DISABLED || m.sideMode(Direction.NORTH) == SideMode.ENERGY_INPUT || m.sideMode(Direction.NORTH)==SideMode.ITEM_OUTPUT, "Cultivator only enables energy inputs or harvest outputs"); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void globalQueueBoundsWorkAndIgnoresRemovedMachineRequests(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(5008, 70, 5008); level.getChunkAt(base); level.getChunkAt(base.east(20));
        var machines = new ArrayList<CopperMachineBlockEntity>(); var crop = base.offset(10, 0, 2);
        level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3); level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), 3); level.setBlock(crop.above(), Blocks.GLOWSTONE.defaultBlockState(), 3);
        for (int i = 0; i < 20; i++) {
            var pos = base.east(i); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos, ModContent.MACHINES.get(MachineKind.AURA_CULTIVATOR).get().defaultBlockState(), 3); var m = (CopperMachineBlockEntity)level.getBlockEntity(pos);
            var actor = player(h, m); h.assertTrue(m.setWorkArea(new WorkArea(crop, crop), actor), "Fixture machine has a valid shared work coordinate"); m.energy.add(1120, false); machines.add(m); AreaWorkScheduler.request(level, m);
        }
        AreaWorkScheduler.tick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, net.minecraftforge.event.TickEvent.Phase.END, level, () -> true));
        long worked = machines.stream().filter(m -> m.inspected > 0).count(); h.assertTrue(worked > 0 && worked <= 16, "One dimension tick executes at most 128 checks / eight per machine");
        for (int i = 0; i < 3; i++) AreaWorkScheduler.tick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, net.minecraftforge.event.TickEvent.Phase.END, level, () -> true));
        h.assertTrue(machines.stream().allMatch(m -> m.inspected == 8), "Queued machines eventually receive their bounded wave");
        var removed = machines.get(0); int before = removed.energy.stored(); AreaWorkScheduler.request(level, removed); removed.setRemoved();
        AreaWorkScheduler.tick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, net.minecraftforge.event.TickEvent.Phase.END, level, () -> true));
        h.assertTrue(removed.energy.stored() == before, "Retired request does not execute a removed machine"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void boundStoragePullsInputsPushesResultsAndSavesEscrow(GameTestHelper h) {
        var m = machine(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER); var actor = player(h, m);
        h.setBlock(new BlockPos(3, 1, 1), Blocks.BARREL); var barrel = (BarrelBlockEntity)h.getBlockEntity(new BlockPos(3, 1, 1)); barrel.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        h.assertTrue(m.bind(barrel.getBlockPos(), Direction.UP, actor), "Owner binds a physical loaded inventory"); BoundStorageLogic.tick(m, h.getLevel());
        h.assertTrue(barrel.getItem(0).isEmpty() && m.inventory.getStackInSlot(0).getCount() == 8, "Binding physically withdraws matching input without copying");
        m.inventory.setStackInSlot(2, new ItemStack(Items.IRON_INGOT, 8)); BoundStorageLogic.tick(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).isEmpty() && barrel.getItem(0).is(Items.IRON_INGOT) && barrel.getItem(0).getCount() == 8, "Binding returns real output to storage");
        m.boundCargo(List.of(new ItemStack(Items.RAW_IRON, 12))); m.boundCargoSlot = 0;
        var restored = (CopperMachineBlockEntity)BlockEntity.loadStatic(m.getBlockPos(), m.getBlockState(), m.saveWithFullMetadata());
        h.assertTrue(restored != null && restored.boundInventory().equals(barrel.getBlockPos()) && restored.boundCargoCount() == 12 && restored.boundCargoSlot == 0 && !m.getUpdateTag().contains("BoundCargo"), "Binding and escrow persist without broadcasting private contents"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void boundStorageKeepsRejectedOutputAndResumesOnceAfterNbtLoad(GameTestHelper h) {
        var m = machine(h, new BlockPos(1, 1, 1), MachineKind.RESONANCE_SMELTER); var actor = player(h, m);
        var fixturePos = new BlockPos(3, 1, 1);
        h.setBlock(fixturePos, net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(UnusualCopper.MOD_ID, "fixture_storage")));
        var receiver = (StorageGameTests.Fixture)h.getBlockEntity(fixturePos); receiver.rejectActual = true;
        h.assertTrue(m.bind(receiver.getBlockPos(), Direction.UP, actor), "Owner can bind a receiver which simulates acceptance");
        var output = new ItemStack(Items.IRON_INGOT, 8); output.setHoverName(net.minecraft.network.chat.Component.literal("Bound batch"));
        m.inventory.setStackInSlot(2, output); BoundStorageLogic.tick(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).isEmpty() && m.boundCargoCount() == 8 && receiver.inventory.getStackInSlot(0).isEmpty(), "Actual rejection retains the entire physical output in machine escrow");
        var saved = m.saveWithFullMetadata(); m.load(saved); receiver.rejectActual = false;
        BoundStorageLogic.tick(m, h.getLevel()); BoundStorageLogic.tick(m, h.getLevel());
        var accepted = receiver.inventory.getStackInSlot(0);
        h.assertTrue(m.boundCargoCount() == 0 && m.inventory.getStackInSlot(2).isEmpty() && accepted.getCount() == 8 && accepted.getHoverName().getString().equals("Bound batch"), "NBT-restored output is committed once with its exact name"); h.succeed();
    }
}
