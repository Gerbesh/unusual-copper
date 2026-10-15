package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReaperGameTests {
    private static ReaperRule cows() { return new ReaperRule(true, true, Set.of(new ResourceLocation("minecraft", "cow")), Set.of(), Set.of()); }
    private static CopperMachineBlockEntity machine(GameTestHelper h, BlockPos pos, WorkArea area) {
        var level = h.getLevel(); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        // Replacing the previous disk fixture honestly drops its old inventory/buffer.
        // Remove those prior-run drops before measuring the new action's conservation.
        for (var item : level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(8))) item.discard();
        level.setBlock(pos, ModContent.MACHINES.get(MachineKind.MOB_REAPER).get().defaultBlockState(), 3);
        var machine = (CopperMachineBlockEntity)level.getBlockEntity(pos); var owner = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ReaperOwner")); owner.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        h.assertTrue(machine.setWorkArea(area, owner), "Owner selects the reaper area"); machine.reaper.settings(cows(), 0, 0); return machine;
    }
    private static Cow cow(ServerLevel level, BlockPos pos) { var cow = EntityType.COW.create(level); cow.setPos(pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5); cow.setNoAi(true); cow.setNoGravity(true); level.addFreshEntity(cow); return cow; }
    // Transaction-focused cases advance only this intent's deadline. Real ticker/latency tests below use world time.
    private static void process(CopperMachineBlockEntity m, ServerLevel level) { m.nextWaveTick = 0; for (int i = 0; i < 30; i++) {
        ReaperLogic.process(m, level);
        if(m.reaper.windup!=null) { var pending=m.reaper.windup; m.reaper.windup=new ReaperState.Windup(pending.action(),pending.target(),level.getGameTime(),pending.plan()); continue; }
        if (m.status() != MachineStatus.SCANNING) break;
    } }
    private static void loaded(GameTestHelper h, WorkArea area, Runnable test) {
        PopulationGameTests.loaded(h, area, () -> { var box = new AABB(area.min(), area.max().offset(1,1,1)).inflate(4); for (var item : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) item.discard(); try { test.run(); } finally { for (var item : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) item.discard(); } });
    }
    private static int owned(CopperMachineBlockEntity m, Item item) { int count = 0; for (int slot = 2; slot <= 3; slot++) if (m.inventory.getStackInSlot(slot).is(item)) count += m.inventory.getStackInSlot(slot).getCount(); for (var stack : m.reaper.loot()) if (stack.is(item)) count += stack.getCount(); return count; }
    public static final class Events {
        final UUID target; public boolean cancelDeath, cancelAttack, cancelDropSpawn, cancelDrops, bonus, throwAfterDeath;
        public Events(UUID target) { this.target = target; }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void death(LivingDeathEvent event) { if (cancelDeath && event.getEntity().getUUID().equals(target)) event.setCanceled(true); }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void attack(LivingAttackEvent event) { if (cancelAttack && event.getEntity().getUUID().equals(target)) event.setCanceled(true); }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void drops(LivingDropsEvent event) {
            if (!event.getEntity().getUUID().equals(target)) return;
            if (throwAfterDeath) throw new IllegalStateException("Intentional downstream loot fixture failure");
            if (bonus) { var stack = new ItemStack(Items.DIAMOND, 7); stack.setHoverName(Component.literal("Late listener bonus")); event.getDrops().add(new ItemEntity(event.getEntity().level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack)); }
            if (cancelDrops) event.setCanceled(true);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void join(EntityJoinLevelEvent event) { if (cancelDropSpawn && event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)) event.setCanceled(true); }
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void actualDeathsRespectTwentyTwelveHysteresisAndExactPayment(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(43008,70,43008); var target = base.east(2); var area = new WorkArea(target,target);
        loaded(h, area, () -> {
            var m = machine(h,base,area); m.reaper.settings(cows(),20,12); m.energy.add(13500,false); var cows = new ArrayList<Cow>(); for(int i=0;i<21;i++) cows.add(cow(level,target));
            for(int i=0;i<9;i++) process(m,level);
            h.assertTrue(cows.stream().filter(Entity::isAlive).count()==12 && m.energy.stored()==0 && !m.reaper.active && m.reaper.population==12, "Nine real deaths stop at twelve for exactly 13500 CuE");
            m.energy.add(1500,false); for(int i=0;i<8;i++) cows.add(cow(level,target)); process(m,level);
            h.assertTrue(cows.stream().filter(Entity::isAlive).count()==20 && m.energy.stored()==1500, "Twenty targets do not re-arm the stopped reaper");
            cows.add(cow(level,target)); process(m,level);
            h.assertTrue(cows.stream().filter(Entity::isAlive).count()==20 && m.energy.stored()==0 && m.reaper.active, "The twenty-first target re-arms one paid real kill");
            var restored=(CopperMachineBlockEntity)BlockEntity.loadStatic(base,m.getBlockState(),m.saveWithFullMetadata());
            h.assertTrue(restored!=null && restored.reaper.active && restored.reaper.trigger==20 && restored.reaper.keep==12 && restored.reaper.rule.equals(cows()) && !m.getUpdateTag().contains("Reaper"), "Thresholds and latch persist without broadcasting private loot/settings");
        });
    }
    // Cold CI region I/O may outlast 200 ticks; gameplay assertions run only after entity readiness.
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void realGlmAndLateListenerLootHaveNoPlayerCreditLootingOrXp(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(45008,70,45008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var cow=cow(level,target); var probe=new ReaperLootFixture.Probe(); ReaperLootFixture.PROBES.put(cow.getUUID(),probe);
            var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PreviousAttacker")); var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(Enchantments.MOB_LOOTING,3); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword); cow.setLastHurtByPlayer(player);
            var events=new Events(cow.getUUID()); events.bonus=true; MinecraftForge.EVENT_BUS.register(events);
            try { m.energy.add(1500,false); process(m,level);
                h.assertTrue(((ReaperVictim)cow).unusualcopper$deathCommitted() && m.energy.stored()==0 && probe.seen && !probe.player && probe.looting==0, "A real GLM sees the machine death without previous player credit or Looting");
                h.assertTrue(owned(m,Items.EMERALD)==5 && owned(m,Items.DIAMOND)==7 && owned(m,Items.BEEF)>0, "Actual GLM bonus, later listener bonus and normal cow loot are transferred once");
                h.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class,new AABB(target).inflate(5)).isEmpty(), "Machine kill produces no XP even after a Looting player attacked it");
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(target).inflate(5)).isEmpty(), "Accepted fitting loot is not also left as a world entity");
            } finally { MinecraftForge.EVENT_BUS.unregister(events); ReaperLootFixture.PROBES.remove(cow.getUUID()); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cancelledDeathAndAttackRestoreHealthCreditAndEnergy(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(47008,70,47008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var cow=cow(level,target); float health=cow.getHealth(); var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PreviousAttacker")); cow.setLastHurtByPlayer(player); m.energy.add(1500,false);
            var events=new Events(cow.getUUID()); events.cancelDeath=true; MinecraftForge.EVENT_BUS.register(events);
            try { process(m,level); h.assertTrue(!((ReaperVictim)cow).unusualcopper$deathCommitted() && cow.getHealth()==health && m.energy.stored()==1500 && cow.getKillCredit()==player && owned(m,Items.BEEF)==0, "Death cancellation restores the transaction, including previous player credit");
                events.cancelDeath=false; events.cancelAttack=true; process(m,level); h.assertTrue(cow.isAlive() && cow.getHealth()==health && m.energy.stored()==1500 && cow.getKillCredit()==player, "Attack cancellation also restores the transaction");
            } finally { MinecraftForge.EVENT_BUS.unregister(events); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullBoundStorageAndOutputsStopBeforeDamage(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(49008,70,49008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var cow=cow(level,target); m.energy.add(1500,false); level.setBlock(base.west(),Blocks.BARREL.defaultBlockState(),3); var barrel=(BarrelBlockEntity)level.getBlockEntity(base.west()); for(int i=0;i<27;i++) barrel.setItem(i,new ItemStack(Items.STONE,64));
            var owner=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(m.owner(),"ReaperOwner")); owner.setPos(base.getX()+.5,base.getY()+.5,base.getZ()+.5); h.assertTrue(m.bind(base.west(),Direction.UP,owner),"Owner binds the full barrel"); process(m,level);
            h.assertTrue(cow.isAlive() && m.energy.stored()==1500 && m.status()==MachineStatus.OUTPUT_FULL,"A full external inventory stops the first damage attempt"); m.clearBinding();
            m.inventory.setStackInSlot(2,new ItemStack(Items.STONE,64)); m.inventory.setStackInSlot(3,new ItemStack(Items.STONE,64)); process(m,level);
            h.assertTrue(cow.isAlive() && m.energy.stored()==1500,"Full internal outputs also stop before damage");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void overflowKeepsTenThousandItemsAcrossOwnedBufferAndWorldParcel(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(51008,70,51008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var first=cow(level,target); var second=cow(level,target); var probe=new ReaperLootFixture.Probe(); probe.stone=10000; ReaperLootFixture.PROBES.put(first.getUUID(),probe); m.energy.add(3000,false);
            try { process(m,level); h.assertTrue(probe.seen && !first.isAlive() && second.isAlive() && m.energy.stored()==1500,"Exactly one kill creates the actual oversized GLM result");
                int world=0; for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(target).inflate(5))) { var stack=entity.getItem(); if(stack.is(Items.STONE)) world+=stack.getCount(); else if(stack.is(ModContent.CARGO_BUNDLE.get())) for(var raw:stack.getOrCreateTag().getList("Cargo",Tag.TAG_COMPOUND)) {var item=(CompoundTag)raw; if(ItemStack.of(item.getCompound("Stack")).is(Items.STONE)) world+=item.getInt("Count");} }
                h.assertTrue(owned(m,Items.STONE)+world==10000 && world>0 && m.reaper.lootCount()>0,"All ten thousand actual items survive in owned storage and honest world overflow");
                var restored=(CopperMachineBlockEntity)BlockEntity.loadStatic(base,m.getBlockState(),m.saveWithFullMetadata()); h.assertTrue(restored!=null && restored.reaper.lootCount()==m.reaper.lootCount(),"Emergency loot survives NBT restore"); process(m,level); h.assertTrue(second.isAlive() && m.energy.stored()==1500,"An undrained emergency buffer stops the next kill");
            } finally { ReaperLootFixture.PROBES.remove(first.getUUID()); }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cancelledItemSpawnCannotMintCapturedLoot(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(53008,70,53008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var cow=cow(level,target); var events=new Events(cow.getUUID()); events.bonus=true; events.cancelDropSpawn=true; MinecraftForge.EVENT_BUS.register(events);
            try {m.energy.add(1500,false); process(m,level); h.assertTrue(!cow.isAlive() && m.energy.stored()==0 && owned(m,Items.DIAMOND)==0 && owned(m,Items.BEEF)>0,"Only accepted drop entities are captured; a cancelled bonus spawn is never minted");}
            finally {MinecraftForge.EVENT_BUS.unregister(events);}
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void failedListenerAfterDeathDoesNotRefundOrLeakAction(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(55008,70,55008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var victim=cow(level,target); m.energy.add(3000,false);
            var events=new Events(victim.getUUID()); events.throwAfterDeath=true; MinecraftForge.EVENT_BUS.register(events); boolean threw=false;
            try { ReaperLoot.kill(m,victim,level,1500); } catch (RuntimeException expected) { threw=true; }
            finally { MinecraftForge.EVENT_BUS.unregister(events); }
            h.assertTrue(threw && ((ReaperVictim)victim).unusualcopper$deathCommitted() && m.energy.stored()==1500,"A committed death remains paid when a downstream mod throws");
            var next=cow(level,target); process(m,level);
            h.assertTrue(!next.isAlive() && m.energy.stored()==0 && owned(m,Items.BEEF)>0,"Exception cleanup leaves the next independent kill working once");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void settingsAreAtomicBoundedAndRequireNearbyOwner(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(57008,70,57008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var owner=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(m.owner(),"ReaperOwner")); owner.setPos(base.getX()+.5,base.getY()+.5,base.getZ()+.5);
            var valid=new ReaperSettings(20,12,true,true,"minecraft:cow","forge:animals","creature");
            var menu=new io.github.gerbesh.unusualcopper.menu.ReaperMenu(7,owner.getInventory(),m);
            h.assertTrue(menu.configure(valid,owner) && m.reaper.trigger==20 && m.reaper.keep==12 && m.reaper.rule.equals(valid.validate()),"Nearby owner applies explicit passive mode, thresholds and all filter kinds");
            h.assertTrue(!menu.configure(new ReaperSettings(0,0,false,true,"","","monster"),owner) && m.reaper.trigger==20,"Same-tick duplicate is rate limited");
            var before=m.reaper.save();
            var invalid=List.of(new ReaperSettings(10,11,true,true,"minecraft:cow","",""), new ReaperSettings(10,0,true,true,"minecraft:does_not_exist","",""), new ReaperSettings(10,0,true,true,"minecraft:cow,","",""), new ReaperSettings(10,0,true,true,"minecraft:cow","","not_a_category"), new ReaperSettings(10,0,true,true,"minecraft:cow,minecraft:cow","",""));
            for (var values:invalid) { var attempt=new io.github.gerbesh.unusualcopper.menu.ReaperMenu(8,owner.getInventory(),m); h.assertTrue(!attempt.configure(values,owner) && before.equals(m.reaper.save()),"Malformed settings preserve every existing targeting field"); }
            var foreign=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ForeignEditor")); foreign.setPos(base.getX()+.5,base.getY()+.5,base.getZ()+.5);
            h.assertTrue(!new io.github.gerbesh.unusualcopper.menu.ReaperMenu(9,foreign.getInventory(),m).configure(valid,foreign),"Another player cannot alter the targeting rule");
            owner.setPos(base.getX()+20,base.getY(),base.getZ()); h.assertTrue(!new io.github.gerbesh.unusualcopper.menu.ReaperMenu(10,owner.getInventory(),m).configure(valid,owner),"Distant owner cannot alter the targeting rule");
            h.assertTrue(before.equals(m.reaper.save()),"Rejected edits never reset the latch or settings");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void actualTickerHonorsTwentyTicksEnergyAndRedstone(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(59008,70,59008); var target=base.east(2); var area=new WorkArea(target,target);
        var machine=new CopperMachineBlockEntity[1]; var victims=new ArrayList<Cow>(); var firstKillTick=new long[1]; var firstDeadline=new long[1];
        PopulationGameTests.loadedSequence(h,area,sequence -> sequence
            .thenExecute(() -> {
                var m=machine(h,base,area); machine[0]=m; m.redstoneMode=1; m.energy.add(1499,false);
                for(int i=0;i<3;i++) victims.add(cow(level,target));
            })
            .thenExecuteAfter(25,() -> {
                h.assertTrue(victims.stream().allMatch(Entity::isAlive) && machine[0].energy.stored()==1499,"Redstone-paused real ticker never damages targets"); machine[0].redstoneMode=0;
            })
            .thenExecuteAfter(25,() -> {
                h.assertTrue(victims.stream().allMatch(Entity::isAlive) && machine[0].energy.stored()==1499 && machine[0].status()==MachineStatus.NO_ENERGY,"1499 CuE never buys a partial damage attempt"); machine[0].energy.add(3001,false);
            })
            .thenWaitUntil(() -> { h.assertTrue(machine[0].reaper.windup!=null,"Real ticker begins a windup"); firstDeadline[0]=machine[0].reaper.windup.strikeAt(); h.assertTrue(victims.stream().allMatch(Entity::isAlive) && machine[0].energy.stored()==4500,"Windup alone never damages or charges"); })
            .thenExecuteAfter(10,() -> h.assertTrue(victims.stream().allMatch(Entity::isAlive) && machine[0].energy.stored()==4500,"Ten following real ticks still precede the strike"))
            .thenWaitUntil(() -> { h.assertTrue(victims.stream().filter(Entity::isAlive).count()==2,"First real ticker kill"); firstKillTick[0]=level.getGameTime(); h.assertTrue(firstKillTick[0]>=firstDeadline[0],"First strike occurs after its twelve-tick preparation"); })
            .thenExecuteAfter(18,() -> h.assertTrue(victims.stream().filter(Entity::isAlive).count()==2 && machine[0].energy.stored()==3000,"No second kill during the following eighteen ticks"))
            .thenWaitUntil(() -> { h.assertTrue(victims.stream().filter(Entity::isAlive).count()==1,"Second real ticker kill"); h.assertTrue(level.getGameTime()-firstKillTick[0]>=20 && machine[0].energy.stored()==1500,"At least twenty real ticks and exactly 1500 CuE per death"); })
            .thenExecute(() -> {
                machine[0].redstoneMode=1;
                for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(target).inflate(5))) item.discard();
            })
        );
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void passiveModeNeverOverridesProtectedVictims(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(61008,70,61008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); m.reaper.settings(ReaperRule.defaults(),0,0); m.energy.add(3000,false); var wild=cow(level,target); process(m,level);
            h.assertTrue(wild.isAlive() && m.energy.stored()==3000,"Default hostile mode never damages a wild passive cow");
            var named=cow(level,target); named.setCustomName(Component.literal("Protected name")); var baby=cow(level,target); baby.setAge(-5000);
            var villager=EntityType.VILLAGER.create(level); var wolf=EntityType.WOLF.create(level); var horse=EntityType.HORSE.create(level);
            wolf.setOwnerUUID(UUID.randomUUID()); horse.setOwnerUUID(UUID.randomUUID());
            var protectedMobs=List.<Mob>of(named,baby,villager,wolf,horse);
            for(var mob:List.<Mob>of(villager,wolf,horse)) { mob.setPos(target.getX()+.5,target.getY()+.1,target.getZ()+.5); mob.setNoAi(true); mob.setNoGravity(true); level.addFreshEntity(mob); }
            var leashed=cow(level,target); leashed.setLeashedTo(named,false);
            var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ProtectedPlayer")); player.setPos(target.getX()+.5,target.getY()+.1,target.getZ()+.5); player.setHealth(20);
            m.reaper.settings(new ReaperRule(true,true,Set.of(),Set.of(),EnumSet.allOf(MobCategory.class)),0,0);
            process(m,level); process(m,level);
            h.assertTrue(!wild.isAlive() && m.energy.stored()==1500 && m.reaper.population==0,"Explicit passive mode pays only for the one permitted wild adult");
            h.assertTrue(protectedMobs.stream().allMatch(mob -> mob.isAlive() && mob.getHealth()==mob.getMaxHealth()) && leashed.isAlive() && leashed.getHealth()==leashed.getMaxHealth() && player.getHealth()==20,"Named, baby, NPC, owned wolf/horse, leashed mob and player remain undamaged");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pendingStrikeRevalidatesTargetsSettingsOutputEnergyAndIdentity(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(63008,70,63008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            for(int scenario=0;scenario<9;scenario++) {
                var m=machine(h,base,area); var victim=cow(level,target); m.energy.add(1500,false);
                for(int i=0;i<30 && m.reaper.windup==null;i++) ReaperLogic.process(m,level);
                h.assertTrue(m.reaper.windup!=null && m.energy.stored()==1500 && victim.isAlive(),"Prepared intent owns no resources");
                var pending=m.reaper.windup;
                switch(scenario) {
                    case 0 -> victim.setCustomName(Component.literal("Now protected"));
                    case 1 -> victim.setPos(target.getX()+2.5,target.getY()+.1,target.getZ()+.5);
                    case 2 -> victim.setAge(-5000);
                    case 3 -> { m.inventory.setStackInSlot(2,new ItemStack(Items.STONE,64)); m.inventory.setStackInSlot(3,new ItemStack(Items.STONE,64)); }
                    case 4 -> m.energy.spend(1000);
                    case 5 -> m.setOwner(UUID.randomUUID());
                    case 6 -> m.reaper.settings(ReaperRule.defaults(),0,0);
                    case 7 -> m.redstoneMode=1;
                    case 8 -> victim.discard();
                }
                m.reaper.windup=new ReaperState.Windup(pending.action(),pending.target(),level.getGameTime(),pending.plan());
                var replacement=scenario==8?cow(level,target):null;
                ReaperLogic.process(m,level);
                h.assertTrue(m.reaper.windup==null && m.energy.stored()==(scenario==4?500:1500) && (scenario==8 || victim.isAlive() && victim.getHealth()==victim.getMaxHealth()) && (replacement==null || replacement.isAlive()),"Changed preparation is cancelled without paid damage or UUID retargeting: scenario="+scenario);
                victim.discard(); if(replacement!=null) replacement.discard();
            }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void unloadAndNbtRestoreDiscardUnpaidWindup(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(65008,70,65008); var target=base.east(2); var area=new WorkArea(target,target);
        loaded(h,area,()->{
            var m=machine(h,base,area); var victim=cow(level,target); m.energy.add(1500,false); ReaperLogic.process(m,level);
            h.assertTrue(m.reaper.windup!=null && victim.isAlive(),"Fixture begins unpaid preparation");
            var restored=(CopperMachineBlockEntity)BlockEntity.loadStatic(base,m.getBlockState(),m.saveWithFullMetadata());
            h.assertTrue(restored!=null && restored.reaper.windup==null && restored.energy.stored()==1500,"NBT restore never resumes an old authority or spends a preparation fee");
            m.onChunkUnloaded(); h.assertTrue(m.reaper.windup==null && m.energy.stored()==1500 && victim.isAlive(),"Chunk unload cancels the transient intent without a fee or damage");
        });
    }
    @GameTest(template = "empty")
    public static void cosmeticPacketsRejectUnboundedOrNonfiniteVisuals(GameTestHelper h) {
        var origin=BlockPos.ZERO; int rejected=0;
        for(int scenario=0;scenario<5;scenario++) try {
            new io.github.gerbesh.unusualcopper.network.MachineEffects.Reap(new ResourceLocation("minecraft","overworld"),origin,UUID.randomUUID(),scenario==0?4:1,1,UUID.randomUUID(),scenario==1?new net.minecraft.world.phys.Vec3(Double.NaN,0,0):scenario==2?new net.minecraft.world.phys.Vec3(100,0,0):net.minecraft.world.phys.Vec3.atCenterOf(origin),scenario==3?origin.east(33):origin,scenario==4?new int[]{1,2,3,4,5}:new int[0]);
        } catch (IllegalArgumentException expected) { rejected++; }
        h.assertTrue(rejected==5,"Phase, coordinates, destination and icon count are bounded"); h.succeed();
    }
}
