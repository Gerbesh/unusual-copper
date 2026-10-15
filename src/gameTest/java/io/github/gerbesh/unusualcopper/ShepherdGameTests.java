package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.ShepherdLogic;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdGameTests {
    private static CopperMachineBlockEntity machine(GameTestHelper h, BlockPos pos, WorkArea area) {
        var level = h.getLevel(); level.getChunkAt(pos); level.getChunkAt(area.min()); level.getChunkAt(area.max());
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos, ModContent.MACHINES.get(MachineKind.GROWTH_SHEPHERD).get().defaultBlockState(), 3);
        var m = (CopperMachineBlockEntity)level.getBlockEntity(pos); var actor = player(level, m.owner());
        actor.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        h.assertTrue(m.setWorkArea(area, actor), "Loaded area is accepted by its owner"); return m;
    }
    private static net.minecraft.server.level.ServerPlayer player(ServerLevel level, UUID owner) { return FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(owner == null ? UUID.randomUUID() : owner, "ShepherdTest")); }
    private static <T extends AgeableMob> T animal(ServerLevel level, EntityType<T> type, BlockPos pos, int age) {
        var animal = type.create(level); animal.setPos(pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5); animal.setNoAi(true); animal.setNoGravity(true); animal.setAge(age);
        level.addFreshEntity(animal); return animal;
    }
    @GameTest(template = "empty")
    public static void waveAdvancesOnlyBabiesAndPreservesBreedingCooldownAndLove(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var start = pos.east(2); var m = machine(h, pos, WorkArea.between(start, start.east(3)));
        var baby = animal(h.getLevel(), EntityType.COW, start, -5000); var nearAdult = animal(h.getLevel(), EntityType.SHEEP, start.east(), -1);
        var adult = animal(h.getLevel(), EntityType.PIG, start.east(2), 0); adult.setInLoveTime(400);
        var parent = animal(h.getLevel(), EntityType.CHICKEN, start.east(3), 6000); parent.setInLoveTime(200);
        m.energy.add(800, false); ShepherdLogic.process(m, h.getLevel());
        h.assertTrue(baby.getAge() == -2600 && nearAdult.getAge() == 0 && adult.getAge() == 0 && parent.getAge() == 6000 && m.grown == 2 && m.energy.stored() == 0, "Two real babies receive up to 2400 age ticks for exactly 800 CuE; adults and parent cooldown are unchanged");
        h.assertTrue(adult.getInLoveTime() == 400 && parent.getInLoveTime() == 200, "No love timer or breeding resources are modified"); h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 245)
    public static void actualBlockTickerWaitsTwoHundredTicksBetweenWaves(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var target = pos.east(2); var m = machine(h, pos, new WorkArea(target, target));
        var baby = animal(h.getLevel(), EntityType.COW, target, -24000); m.energy.add(1200, false); m.nextWaveTick = h.getLevel().getGameTime() + 10;
        h.runAfterDelay(5, () -> h.assertTrue(m.energy.stored() == 1200, "No early wave before the saved deadline"));
        h.runAfterDelay(25, () -> h.assertTrue(m.energy.stored() == 800 && baby.getAge() >= -21600, "The actual server block ticker executes the first scheduled wave"));
        h.runAfterDelay(195, () -> h.assertTrue(m.energy.stored() == 800, "A second wave cannot occur before 200 more world ticks"));
        h.runAfterDelay(225, () -> { h.assertTrue(m.energy.stored() == 400 && baby.getAge() >= -19200, "The actual scheduler executes the second wave, not one per tick"); m.redstoneMode = 1; h.succeed(); });
    }
    @GameTest(template = "empty")
    public static void paymentFailureAndNbtReloadDoNotCreateAnExtraWave(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var target = pos.east(2); var m = machine(h, pos, new WorkArea(target, target));
        var baby = animal(h.getLevel(), EntityType.COW, target, -5000); m.energy.add(399, false); ShepherdLogic.process(m, h.getLevel());
        h.assertTrue(baby.getAge() == -5000 && m.energy.stored() == 399 && m.grown == 0, "An incomplete payment cannot change the animal");
        m.nextWaveTick = 0; m.energy.add(1, false); m.allowOwnTamed = true; ShepherdLogic.process(m, h.getLevel());
        var saved = m.saveWithFullMetadata(); var restored = (CopperMachineBlockEntity)BlockEntity.loadStatic(pos, m.getBlockState(), saved);
        h.assertTrue(restored != null && restored.nextWaveTick == m.nextWaveTick && restored.allowOwnTamed && restored.workArea().equals(m.workArea()), "Deadline, pet permission and area survive a block NBT restore");
        restored.setLevel(h.getLevel()); restored.energy.add(400, false); ShepherdLogic.process(restored, h.getLevel());
        h.assertTrue(baby.getAge() == -2600 && restored.energy.stored() == 400, "Reloaded machine cannot grant another wave before the saved deadline"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void villagersUnknownImplementationsAndForeignPetsRemainUntouched(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var start = pos.east(2); var m = machine(h, pos, WorkArea.between(start, start.east(5)));
        var villager = animal(h.getLevel(), EntityType.VILLAGER, start, -5000);
        var unknown = new Cow(EntityType.COW, h.getLevel()) {}; unknown.setPos(start.getX() + 1.5, start.getY() + .1, start.getZ() + .5); unknown.setAge(-5000); unknown.setNoAi(true); unknown.setNoGravity(true); h.getLevel().addFreshEntity(unknown);
        var ownWolf = animal(h.getLevel(), EntityType.WOLF, start.east(2), -5000); ownWolf.setTame(true); ownWolf.setOwnerUUID(m.owner());
        var foreignCat = animal(h.getLevel(), EntityType.CAT, start.east(3), -5000); foreignCat.setTame(true); foreignCat.setOwnerUUID(UUID.randomUUID());
        var ownHorse = animal(h.getLevel(), EntityType.HORSE, start.east(4), -5000); ownHorse.setTamed(true); ownHorse.setOwnerUUID(m.owner());
        var foreignCamel = animal(h.getLevel(), EntityType.CAMEL, start.east(5), -5000); foreignCamel.setOwnerUUID(UUID.randomUUID());
        m.energy.add(2000, false); ShepherdLogic.process(m, h.getLevel());
        h.assertTrue(m.grown == 0 && m.energy.stored() == 2000 && villager.getAge() == -5000 && unknown.getAge() == -5000 && ownWolf.getAge() == -5000 && foreignCat.getAge() == -5000 && ownHorse.getAge() == -5000 && foreignCamel.getAge() == -5000, "Excluded NPCs, unknown classes and all pets are unchanged by default");
        m.nextWaveTick = 0; m.allowOwnTamed = true; ShepherdLogic.process(m, h.getLevel());
        h.assertTrue(ownWolf.getAge() == -2600 && ownHorse.getAge() == -2600 && foreignCat.getAge() == -5000 && foreignCamel.getAge() == -5000 && m.energy.stored() == 1200 && m.grown == 2, "Explicit permission affects only the machine owner's pets, including any stored camel ownership"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void denseMobIndexStaysBoundedAndEventuallyVisitsAllBabies(GameTestHelper h) {
        var level = h.getLevel(); var pos = new BlockPos(15008, 70, 15008); var target = pos.east(2); var m = machine(h, pos, new WorkArea(target, target));
        // Isolated fixture coordinates are reused across disk-backed test launches.
        for (var entity : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(target).inflate(2))) entity.discard();
        var babies = new ArrayList<Cow>(); for (int i = 0; i < 40; i++) babies.add(animal(level, EntityType.COW, target, -24000)); m.energy.add(50000, false);
        int charged = 0;
        for (int wave = 0; wave < 3; wave++) { m.nextWaveTick = 0; ShepherdLogic.process(m, level); h.assertTrue(m.inspected <= 16 && m.grown <= 16, "A wave consumes no more than sixteen candidates"); charged += m.grown * 400; }
        h.assertTrue(babies.stream().allMatch(baby -> baby.getAge() > -24000) && m.energy.stored() == 50000 - charged, "The rotating index reaches all forty babies with exact per-success accounting"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void petToggleRequiresCurrentOwnerMenuAndAllSidesAreEnergyOnly(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); var m = machine(h, pos, new WorkArea(pos, pos)); var owner = player(h.getLevel(), m.owner()); owner.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        var other = player(h.getLevel(), null); other.setPos(owner.getX(), owner.getY(), owner.getZ());
        var menu = new CopperMachineMenu(31, owner.getInventory(), m);
        h.assertTrue(!menu.clickMenuButton(other, 6) && !m.allowOwnTamed && menu.clickMenuButton(owner, 6) && m.allowOwnTamed && m.menuData().get(22) == 1, "Only a current owner-authorized menu may enable own pets");
        for (var side : Direction.values()) { h.assertTrue(m.sideMode(side) == SideMode.ENERGY_INPUT, "All default sides accept CuE"); for (int i = 0; i < 4; i++) { m.cycleSide(side); h.assertTrue(m.sideMode(side) == SideMode.ENERGY_INPUT || m.sideMode(side) == SideMode.DISABLED, "No hidden inventory ports can be enabled"); } }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void supportedVanillaSpeciesReachAdulthoodThroughTheirNormalAgeHooks(GameTestHelper h) {
        List<EntityType<? extends AgeableMob>> types = List.of(EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT,
                EntityType.GOAT, EntityType.BEE, EntityType.FOX, EntityType.PANDA, EntityType.OCELOT, EntityType.TURTLE, EntityType.AXOLOTL, EntityType.POLAR_BEAR,
                EntityType.WOLF, EntityType.CAT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.CAMEL, EntityType.SNIFFER, EntityType.STRIDER, EntityType.HOGLIN);
        var level = h.getLevel(); var base = new BlockPos(17008, 70, 17008); var area = new WorkArea(base.offset(1, 0, 1), base.offset(11, 4, 7));
        var m = machine(h, base, area); m.energy.add(50000, false);
        for (var entity : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(area.min(), area.max().offset(1, 1, 1)))) entity.discard();
        var babies = new ArrayList<AgeableMob>(); var parents = new ArrayList<AgeableMob>();
        for (int i = 0; i < types.size(); i++) {
            var pos = base.offset(1 + (i % 6) * 2, 0, 1 + (i / 6) * 2);
            babies.add(animal(level, types.get(i), pos, -1)); parents.add(animal(level, types.get(i), pos.above(3), 6000));
        }
        for (int wave = 0; wave < 3; wave++) { m.nextWaveTick = 0; ShepherdLogic.process(m, level); UnusualCopper.LOGGER.info("CuE species fixture wave {}: checked={}, grown={}, status={}", wave, m.inspected, m.grown, m.status()); }
        var unexpectedBabies = babies.stream().filter(baby -> baby.getAge() != 0).map(baby -> baby.getType() + ":age=" + baby.getAge() + ":pos=" + baby.blockPosition() + ":adapter=" + (AnimalGrowthAdapters.adapter(baby) != null)).toList();
        var unexpectedParents = parents.stream().filter(parent -> parent.getAge() != 6000).map(parent -> parent.getType() + ":age=" + parent.getAge()).toList();
        h.assertTrue(babies.stream().allMatch(baby -> baby.getAge() == 0) && parents.stream().allMatch(parent -> parent.getAge() == 6000) && m.energy.stored() == 50000 - types.size() * 400,
                "All 24 vanilla species mature exactly once: babies=" + unexpectedBabies + ", parents=" + unexpectedParents + ", energy=" + m.energy.stored()); h.succeed();
    }
}
