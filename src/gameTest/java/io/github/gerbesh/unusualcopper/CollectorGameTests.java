package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.CollectorLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectorGameTests {
    public static final class CancelSpawn {
        private final UUID id;
        public CancelSpawn(UUID id) { this.id = id; }
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event) { if (event.getEntity().getUUID().equals(id)) event.setCanceled(true); }
    }
    private static CopperMachineBlockEntity setup(GameTestHelper h, BlockPos pos, WorkArea area) {
        var level = h.getLevel(); level.getChunkAt(pos); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModContent.MACHINES.get(MachineKind.MAGNETIC_COLLECTOR).get().defaultBlockState(), 3);
        var m = (CopperMachineBlockEntity)level.getBlockEntity(pos);
        var player = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CollectorTest")); player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        h.assertTrue(m.setWorkArea(area, player), "Owner selects the collector's loaded work area"); return m;
    }
    private static ItemEntity item(ServerLevel level, BlockPos pos, int count) {
        var stack = new ItemStack(Items.IRON_INGOT, count); stack.setHoverName(Component.literal("Ground batch"));
        var item = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
        item.setNoGravity(true); item.setNoPickUpDelay(); level.addFreshEntity(item); return item;
    }
    @GameTest(template = "empty")
    public static void collectorMovesExistingStackOnceWithExactNbtAndDistancePayment(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var target = pos.east(3);
        var m = setup(h, pos, new WorkArea(target, target)); var entity = item(h.getLevel(), target, 17); m.energy.add(100, false);
        int cost = CollectorLogic.cost(3); CollectorLogic.process(m, h.getLevel());
        h.assertTrue(entity.isRemoved() && m.inventory.getStackInSlot(2).getCount() == 17 && m.inventory.getStackInSlot(2).getHoverName().getString().equals("Ground batch") && m.energy.stored() == 100 - cost, "One existing stack is removed and committed with exact NBT and one distance payment");
        int paid = m.energy.stored(); var saved = m.saveWithFullMetadata(); m.load(saved); CollectorLogic.process(m, h.getLevel());
        h.assertTrue(m.inventory.getStackInSlot(2).getCount() == 17 && m.energy.stored() == paid && m.grown == 0, "NBT reload cannot issue or charge a removed stack again");
        h.assertTrue(CollectorLogic.cost(0) == 30 && CollectorLogic.cost(32) == 100 && CollectorLogic.cost(100) == 100 && !m.inventory.isItemValid(0, new ItemStack(Items.IRON_INGOT)), "Default price is bounded 30..100 and the collector has no accepted item input"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void fullOutputAndInsufficientEnergyLeaveGroundStackUntouched(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var target = pos.east(3);
        var m = setup(h, pos, new WorkArea(target, target)); var entity = item(h.getLevel(), target, 8); m.energy.add(100, false);
        m.inventory.setStackInSlot(2, new ItemStack(Items.STONE, 64)); m.inventory.setStackInSlot(3, new ItemStack(Items.STONE, 64));
        CollectorLogic.process(m, h.getLevel()); h.assertTrue(entity.isAlive() && entity.getItem().getCount() == 8 && m.energy.stored() == 100 && m.status() == MachineStatus.OUTPUT_FULL, "A full output neither removes the entity nor spends CuE");
        m.inventory.setStackInSlot(2, ItemStack.EMPTY); int cost = CollectorLogic.cost(3); m.energy.restore(cost - 1);
        CollectorLogic.process(m, h.getLevel()); h.assertTrue(entity.isAlive() && m.inventory.getStackInSlot(2).isEmpty() && m.energy.stored() == cost - 1, "Incomplete payment leaves the whole stack on the ground");
        m.energy.add(1, false); CollectorLogic.process(m, h.getLevel());
        h.assertTrue(entity.isRemoved() && m.inventory.getStackInSlot(2).getCount() == 8 && m.energy.stored() == 0, "One full payment resumes one full stack"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void pickupDelayForeignOwnershipAndOutsideAreaAreRespected(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var start = pos.east(2);
        var m = setup(h, pos, WorkArea.between(start, start.east(2))); m.energy.add(1000, false);
        var delayed = item(h.getLevel(), start, 1); delayed.setNeverPickUp();
        var targeted = item(h.getLevel(), start.east(), 2); targeted.setTarget(UUID.randomUUID());
        var thrown = item(h.getLevel(), start.east(2), 3); thrown.setThrower(UUID.randomUUID());
        var outside = item(h.getLevel(), start.east(3), 4);
        CollectorLogic.process(m, h.getLevel());
        h.assertTrue(delayed.isAlive() && targeted.isAlive() && thrown.isAlive() && outside.isAlive() && m.energy.stored() == 1000 && m.inventory.getStackInSlot(2).isEmpty(), "Delayed, foreign-owned and outside-area entities are untouched");
        targeted.setTarget(m.owner()); CollectorLogic.process(m, h.getLevel());
        h.assertTrue(targeted.isRemoved() && delayed.isAlive() && thrown.isAlive() && outside.isAlive() && m.inventory.getStackInSlot(2).getCount() == 2 && m.grown == 1, "The owner can collect their own target while other stacks remain"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void indexedCandidatesAndDimensionQueueStayBounded(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(9008, 70, 9008); var target = base.offset(10, 0, 2);
        level.getChunkAt(base); level.getChunkAt(base.east(20));
        for (int i = 0; i < 40; i++) item(level, target, 1).setNeverPickUp();
        var machines = new ArrayList<CopperMachineBlockEntity>();
        for (int i = 0; i < 20; i++) { var m = setup(h, base.east(i), new WorkArea(target, target)); machines.add(m); AreaWorkScheduler.request(level, m); }
        AreaWorkScheduler.tick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, net.minecraftforge.event.TickEvent.Phase.END, level, () -> true));
        int inspected = machines.stream().mapToInt(m -> m.inspected).sum();
        h.assertTrue(inspected > 0 && inspected <= 256 && machines.stream().allMatch(m -> m.inspected <= 16), "Section buckets take at most sixteen candidates and the dimension spends at most 256 candidate reservations per tick");
        AreaWorkScheduler.tick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, net.minecraftforge.event.TickEvent.Phase.END, level, () -> true));
        h.assertTrue(machines.stream().allMatch(m -> m.inspected == 16 && m.grown == 0), "Deferred collectors receive a bounded wave without taking delayed items"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void sectionIndexRotatesPastDenseDeniedItemsAndTracksMovement(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(11008, 70, 11008); var target = base.east(2);
        var m = setup(h, base, new WorkArea(target, target)); m.energy.add(1000, false);
        for (int i = 0; i < 40; i++) item(level, target, 1).setNeverPickUp();
        var allowed = item(level, target, 7);
        for (int wave = 0; wave < 4; wave++) { CollectorLogic.process(m, level); h.assertTrue(m.inspected <= 16, "Every dense wave stays bounded"); }
        h.assertTrue(allowed.isRemoved() && m.inventory.getStackInSlot(2).getCount() == 7, "Forty delayed entities cannot permanently hide a later eligible stack");
        var outside = target.east(20); level.getChunkAt(outside); var moving = item(level, outside, 3);
        moving.setPos(target.getX() + .5, target.getY() + .5, target.getZ() + .5);
        for (int wave = 0; wave < 4; wave++) CollectorLogic.process(m, level);
        h.assertTrue(moving.isRemoved() && m.inventory.getStackInSlot(2).getCount() == 10, "A real section-change event moves an item into the bounded index"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void cancelledSpawnCannotBecomeARealCollectedStack(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(13008, 70, 13008); var target = base.east(2);
        var m = setup(h, base, new WorkArea(target, target)); m.energy.add(100, false);
        var phantom = new ItemEntity(level, target.getX() + .5, target.getY() + .5, target.getZ() + .5, new ItemStack(Items.DIAMOND, 64)); phantom.setNoPickUpDelay();
        // Registered after the index, at the same last priority: its earlier event is not proof of acceptance.
        var canceller = new CancelSpawn(phantom.getUUID()); net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(canceller);
        try {
            h.assertTrue(!level.addFreshEntity(phantom), "A later real Forge listener cancels this item spawn");
            CollectorLogic.process(m, level);
            h.assertTrue(level.getEntity(phantom.getUUID()) == null && m.inventory.getStackInSlot(2).isEmpty() && m.inventory.getStackInSlot(3).isEmpty() && m.energy.stored() == 100, "An indexed but rejected spawn cannot mint a stack or spend energy");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(canceller); }
        h.succeed();
    }
}
