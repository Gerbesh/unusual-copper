package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID)
public final class PopulationGameTests {
    private static final Map<ServerLevel, Set<Long>> FORCED = new IdentityHashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        FORCED.forEach((level, chunks) -> chunks.forEach(key -> { var pos = new net.minecraft.world.level.ChunkPos(key); level.setChunkForced(pos.x, pos.z, false); })); FORCED.clear();
    }
    private static final ReaperRule ALL = new ReaperRule(true, true, Set.of(), Set.of(), EnumSet.allOf(MobCategory.class));
    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos) {
        level.getChunkAt(pos); var mob = type.create(level); mob.setPos(pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5); mob.setNoAi(true); mob.setNoGravity(true); mob.setInvulnerable(true); level.addFreshEntity(mob); return mob;
    }
    private static void clear(ServerLevel level, WorkArea area) {
        for (var mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(area.min(), area.max().offset(1, 1, 1)).inflate(2))) mob.discard();
    }
    public static void loaded(GameTestHelper h, WorkArea area, Runnable test) {
        loadedSequence(h, area, sequence -> sequence.thenExecute(test));
    }
    public static void loadedSequence(GameTestHelper h, WorkArea area, java.util.function.Consumer<GameTestSequence> steps) {
        var level = h.getLevel();
        for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4; x++) for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) { level.setChunkForced(x, z, true); FORCED.computeIfAbsent(level, ignored -> new HashSet<>()).add(net.minecraft.world.level.ChunkPos.asLong(x,z)); }
        level.getChunkAt(area.min()); level.getChunkAt(area.max());
        var sequence = h.startSequence().thenWaitUntil(() -> {
            for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4; x++) for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) h.assertTrue(level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z)), "Fixture entity regions have finished loading");
            h.assertTrue(level.isPositionEntityTicking(area.min()) && level.isPositionEntityTicking(area.max()), "Fixture chunks are ready to track and tick entities");
        }).thenExecute(() -> clear(level, area));
        steps.accept(sequence);
        sequence.thenExecute(() -> clear(level, area)).thenSucceed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void creatureCatalogueTracksAgesProtectedNamesAndMovementWithoutChangingReaperLedger(GameTestHelper h) {
        var level=h.getLevel();var base=new BlockPos(37008,70,39008);var target=base.east(2);var area=WorkArea.between(target,target.east());
        loaded(h,area,()->{
            var adult=spawn(level,EntityType.COW,target);var baby=spawn(level,EntityType.COW,target);baby.setAge(-1000);var sheep=spawn(level,EntityType.SHEEP,target);
            var machineId=UUID.randomUUID();var working=MobPopulations.ledger(level,machineId,base,area,ReaperRule.defaults());working.step(64);
            var browser=MobPopulations.ledger(level,UUID.randomUUID(),base,area,new ReaperRule(true,false,Set.of(),Set.of(),EnumSet.allOf(MobCategory.class)));browser.enableCatalogue();
            for(int i=0;i<8&&!browser.ready();i++)h.assertTrue(browser.step(1)<=1,"Browser cold start remains bounded");
            var cows=browser.catalogue().stream().filter(s->s.type()==EntityType.COW).findFirst().orElseThrow();
            h.assertTrue(cows.total()==2&&cows.adults()==1&&working.ready()&&working.count()==0,"Species catalogue includes babies without replacing or broadening the production ledger");
            baby.setAge(0);cows=browser.catalogue().stream().filter(s->s.type()==EntityType.COW).findFirst().orElseThrow();h.assertTrue(cows.adults()==2,"Growing up updates the species count without a rescan");
            adult.setCustomName(Component.literal("Protected"));cows=browser.catalogue().stream().filter(s->s.type()==EntityType.COW).findFirst().orElseThrow();h.assertTrue(cows.total()==1&&cows.adults()==1,"Protected mobs disappear from selectable target counts");
            sheep.setPos(target.getX()+.5,target.getY()+.1,target.getZ()+2.5);h.assertTrue(browser.catalogue().size()==1,"Moving outside the exact area removes a species");
            baby.discard();h.assertTrue(browser.catalogue().isEmpty(),"Removed final member leaves no stale species");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coldLedgerCountsThreeHundredWithBoundedStepsAndConcurrentChanges(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(33008, 70, 33008); var target = base.east(2); var area = new WorkArea(target, target);
        loaded(h, area, () -> {
            var cows = new ArrayList<Cow>(); for (int i = 0; i < 300; i++) cows.add(spawn(level, EntityType.COW, target));
            var ledger = MobPopulations.ledger(level, UUID.randomUUID(), base, area, ALL);
            h.assertTrue(ledger.step(16) <= 16 && !ledger.ready(), "A dense cold population uses multiple bounded steps: tracked=" + MobPopulations.tracked(level) + ", added=" + cows.get(0).isAddedToWorld() + ", lookup=" + (level.getEntity(cows.get(0).getUUID()) == cows.get(0)));
            cows.get(0).setPos(target.getX() + 1.5, target.getY() + .1, target.getZ() + .5);
            cows.get(1).setCustomName(Component.literal("protected")); cows.get(2).discard(); spawn(level, EntityType.COW, target);
            for (int i = 0; i < 25 && !ledger.ready(); i++) h.assertTrue(ledger.step(16) <= 16, "Every cold-start step respects its candidate limit");
            h.assertTrue(ledger.ready() && ledger.count() == 298, "Live movement, naming, removal and new spawn reconcile with a 300-entity cold scan: count=" + ledger.count());
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void warmCountsTrackSameSectionMovementNameAgeHealthOwnersAndLeash(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(35008, 70, 35008); var start = base.east(2); var area = WorkArea.between(start, start.east(4));
        loaded(h, area, () -> {
            var cow = spawn(level, EntityType.COW, start); var wolf = spawn(level, EntityType.WOLF, start.east()); var horse = spawn(level, EntityType.HORSE, start.east(2));
            var zombie = spawn(level, EntityType.ZOMBIE, start.east(3)); var villager = spawn(level, EntityType.VILLAGER, start.east(4));
            var id = UUID.randomUUID(); var ledger = MobPopulations.ledger(level, id, base, area, ALL); ledger.step(64);
            h.assertTrue(ledger.ready() && ledger.count() == 4, "NPC is excluded from four matching live targets: tracked=" + MobPopulations.tracked(level) + ", count=" + ledger.count() + ", ready=" + ledger.ready());
            cow.setCustomName(Component.literal("pet")); h.assertTrue(ledger.count() == 3, "Name changes update an already-warm count synchronously");
            cow.setCustomName(null); h.assertTrue(ledger.count() == 4, "Removing the name restores target eligibility");
            cow.setCustomName(Component.literal("Bulk protected")); var snapshot = cow.getEntityData().getNonDefaultValues(); cow.setCustomName(null);
            cow.getEntityData().assignValues(snapshot); h.assertTrue(ledger.count() == 3, "Bulk metadata assignment also updates an exact population"); cow.setCustomName(null);
            wolf.setOwnerUUID(UUID.randomUUID()); h.assertTrue(ledger.count() == 3, "Owner UUID protects a wolf even before its tame bit changes");
            wolf.setOwnerUUID(null); h.assertTrue(ledger.count() == 4, "Removing untamed wolf ownership updates the ledger");
            horse.setOwnerUUID(UUID.randomUUID()); h.assertTrue(ledger.count() == 3, "Plain horse owner field is observed");
            horse.setOwnerUUID(null); h.assertTrue(ledger.count() == 4, "Horse ownership removal is observed");
            cow.setLeashedTo(villager, false); h.assertTrue(ledger.count() == 3, "A leashed animal is protected");
            cow.dropLeash(false, false); h.assertTrue(ledger.count() == 4, "Dropping the leash updates the count");
            cow.setAge(-5000); h.assertTrue(ledger.count() == 3, "Baby transition updates the adult-only filter");
            cow.setAge(0); h.assertTrue(ledger.count() == 4, "Normal adulthood updates the filter immediately");
            zombie.setHealth(0); h.assertTrue(ledger.count() == 3, "Death removes a match before its delayed entity removal");
            horse.setPos(start.getX() + .5, start.getY() + .1, start.getZ() + 1.5); h.assertTrue(ledger.count() == 2, "Movement within the same section changes exact area membership");
            horse.setPos(start.getX() + 2.5, start.getY() + .1, start.getZ() + .5); h.assertTrue(ledger.count() == 3, "Moving back restores membership without a rescan");
            var replacement = MobPopulations.ledger(level, id, base, area, ReaperRule.defaults()); replacement.step(64);
            h.assertTrue(!ledger.ready() && ledger.target() == null && replacement.ready() && replacement.count() == 0, "A changed rule retires the previous ledger");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancelledMobSpawnDoesNotInflateAReadyPopulation(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(37008, 70, 37008); var target = base.east(2); var area = new WorkArea(target, target);
        loaded(h, area, () -> {
            spawn(level, EntityType.COW, target);
            var ledger = MobPopulations.ledger(level, UUID.randomUUID(), base, area, ALL); ledger.step(16);
            h.assertTrue(ledger.ready() && ledger.count() == 1, "One actual accepted cow is present: tracked=" + MobPopulations.tracked(level) + ", count=" + ledger.count() + ", ready=" + ledger.ready());
            var phantom = EntityType.COW.create(level); phantom.setPos(target.getX() + .5, target.getY() + .1, target.getZ() + .5);
            var cancel = new CollectorGameTests.CancelSpawn(phantom.getUUID()); net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);
            try { h.assertTrue(!level.addFreshEntity(phantom) && ledger.count() == 1, "An event-cancelled spawn cannot inflate the threshold count"); }
            finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel); }
        });
    }
    @GameTest(template = "empty")
    public static void ruleFiltersUseActualTypesTagsCategoriesAndSafeNbtDefaults(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(1, 2, 1)); var cow = spawn(level, EntityType.COW, pos); var zombie = spawn(level, EntityType.ZOMBIE, pos.east());
        var tagged = new ReaperRule(true, true, Set.of(), Set.of(new ResourceLocation(UnusualCopper.MOD_ID, "reaper_fixture_animals")), Set.of());
        h.assertTrue(tagged.matches(cow) && !tagged.matches(zombie) && ReaperRule.defaults().matches(zombie) && !ReaperRule.defaults().matches(cow), "Type tags and default hostile category use real registry data");
        var typed = new ReaperRule(true, false, Set.of(new ResourceLocation("minecraft", "cow")), Set.of(), Set.of()); cow.setAge(-5000);
        h.assertTrue(typed.matches(cow) && !new ReaperRule(false, false, typed.types(), Set.of(), Set.of()).matches(cow), "Explicit passive mode is required even for an exact type filter");
        h.assertTrue(ReaperRule.load(tagged.save()).equals(tagged), "Rule data survives NBT round trip");
        var bad = tagged.save(); var ids = new net.minecraft.nbt.ListTag(); ids.add(net.minecraft.nbt.StringTag.valueOf("Bad Id!")); bad.put("Types", ids);
        h.assertTrue(ReaperRule.load(bad).equals(ReaperRule.defaults()), "Malformed filter data cannot enable broader passive targeting"); h.succeed();
    }
}
