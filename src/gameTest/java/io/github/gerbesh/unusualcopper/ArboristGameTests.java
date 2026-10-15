package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArboristGameTests {
    private static void loaded(GameTestHelper h,BlockPos root,Runnable test) {
        PopulationGameTests.loaded(h,new WorkArea(root.offset(-7,0,-7),root.offset(8,15,8)),()->{ clear(h.getLevel(),root); test.run(); });
    }
    static void clear(ServerLevel level,BlockPos root) {
        for(int x=-8;x<=8;x++) for(int z=-8;z<=8;z++) {
            for(int y=0;y<=34;y++) level.setBlock(root.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(root.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
        }
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(12))) item.discard();
    }
    static void grow(ServerLevel level,BlockPos root,TreeSpecies species,boolean four,long seed) {
        for(var p:four?List.of(root,root.east(),root.south(),root.east().south()):List.of(root)) level.setBlock(p,species.sapling.defaultBlockState().setValue(SaplingBlock.STAGE,1),2);
        ((SaplingBlock)species.sapling).advanceTree(level,root,level.getBlockState(root),RandomSource.create(seed));
    }
    static TreeScan inspect(ServerLevel level,BlockPos root,TreeSpecies species) {
        var scan=new TreeScan(root,species,null); for(int i=0;i<300 && scan.result()==TreeScan.Result.SCANNING;i++) scan.step(level,32); return scan;
    }
    static CopperMachineBlockEntity machine(GameTestHelper h,BlockPos root,TreeSpecies species) {
        var level=h.getLevel(); var pos=root.west(5); level.setBlock(pos,Blocks.AIR.defaultBlockState(),3); level.setBlock(pos,ModContent.MACHINES.get(MachineKind.ARBORIST).get().defaultBlockState(),3);
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2))) item.discard();
        var m=(CopperMachineBlockEntity)level.getBlockEntity(pos); var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArboristOwner")); player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        h.assertTrue(m.togglePlantation(root,player),"Owner marks a specific sapling/trunk point");
        m.inventory.setStackInSlot(0,new ItemStack(species.sapling.asItem(),4)); m.inventory.setStackInSlot(1,new ItemStack(Items.DIAMOND_AXE)); return m;
    }
    static void untilAdmitted(CopperMachineBlockEntity m,ServerLevel level) { for(int i=0;i<300;i++) { ArboristLogic.process(m,level); if(m.status()!=MachineStatus.TREE_SCANNING) break; } }
    static int owned(CopperMachineBlockEntity m,Item item) { int n=0; for(int slot=2;slot<=3;slot++) if(m.inventory.getStackInSlot(slot).is(item)) n+=m.inventory.getStackInSlot(slot).getCount(); for(var stack:m.arborist.recovery.items()) if(stack.is(item)) n+=stack.getCount(); return n; }

    @GameTest(template="empty",timeoutTicks=300)
    public static void realVanillaSaplingTreesUseExplicitAdaptersAndBoundedSteps(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(70008,70,70008);
        loaded(h,root,()->{
            for(var species:TreeSpecies.values()) {
                clear(level,root); grow(level,root,species,species.requiresFour,7);
                var scan=new TreeScan(root,species,null); int calls=0;
                while(scan.result()==TreeScan.Result.SCANNING && calls++<300) h.assertTrue(scan.step(level,16)<=16,"Every scan step honors its node budget");
                h.assertTrue(scan.result()==TreeScan.Result.READY || scan.result()==TreeScan.Result.TOO_LARGE && scan.treeBlocks()>256,"Actual vanilla tree recognized or explicitly exceeds the GDD's tree-block limit: "+species+", result="+scan.result()+", logs="+scan.logs().size()+", treeBlocks="+scan.treeBlocks());
                h.assertTrue(scan.logs().size()>=3 && scan.logs().size()<=256,"Tree log list is bounded");
            }
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void naturalOakIsHarvestedReplantedAndExactlyCharged(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(72008,70,72008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); h.assertTrue(scan.result()==TreeScan.Result.READY,"Oak fixture recognized"); int logs=scan.logs().size();
            var m=machine(h,root,TreeSpecies.OAK); int fee=logs*250+120; m.energy.add(fee,false);
            for(int i=0;i<400 && m.grown==0;i++) ArboristLogic.process(m,level);
            h.assertTrue(m.grown==1 && m.arborist.job==null && m.energy.stored()==0 && owned(m,Items.OAK_LOG)==logs,"Real loot and exact 250/log + 120/sapling payment: status="+m.status()+", logs="+logs+", energy="+m.energy.stored()+", output="+owned(m,Items.OAK_LOG));
            h.assertTrue(level.getBlockState(root).is(Blocks.OAK_SAPLING) && m.inventory.getStackInSlot(0).getCount()==3,"One real reserved sapling is replanted");
            h.assertTrue(m.inventory.getStackInSlot(1).is(Items.DIAMOND_AXE) && m.inventory.getStackInSlot(1).getDamageValue()==logs,"Actual axe wears once per log and returns to its slot");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void houseNearNaturalCrownIsNeverHarvested(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(74008,70,74008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); level.setBlock(root.east(),Blocks.OAK_PLANKS.defaultBlockState(),3);
            var m=machine(h,root,TreeSpecies.OAK); m.energy.add(50000,false); untilAdmitted(m,level);
            h.assertTrue(m.status()==MachineStatus.BUILDING_NEARBY && level.getBlockState(root).is(Blocks.OAK_LOG) && level.getBlockState(root.east()).is(Blocks.OAK_PLANKS) && m.energy.stored()==50000 && m.arborist.job==null,"A house with log posts and a natural crown is rejected before resources or world blocks change");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void insufficientFullCycleResourcesLeaveTheWholeTreeStanding(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(76008,70,76008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var logs=inspect(level,root,TreeSpecies.OAK).logs(); var m=machine(h,root,TreeSpecies.OAK);
            m.energy.add(logs.size()*250+119,false); untilAdmitted(m,level);
            h.assertTrue(m.status()==MachineStatus.NO_ENERGY && logs.stream().allMatch(log->level.getBlockState(log.pos()).equals(log.state())) && m.arborist.job==null,"Missing one CuE for the full cycle blocks the first cut");
            m.energy.add(1,false); m.inventory.setStackInSlot(0,ItemStack.EMPTY); untilAdmitted(m,level);
            h.assertTrue(m.status()==MachineStatus.NEED_SAPLINGS && level.getBlockState(root).is(Blocks.OAK_LOG),"A matching sapling is reserved before any cut");
            m.inventory.setStackInSlot(0,new ItemStack(Items.OAK_SAPLING)); var worn=new ItemStack(Items.WOODEN_AXE); worn.setDamageValue(worn.getMaxDamage()-1); m.inventory.setStackInSlot(1,worn); untilAdmitted(m,level);
            h.assertTrue(m.status()==MachineStatus.NO_TOOL && level.getBlockState(root).is(Blocks.OAK_LOG),"An axe must survive the entire planned trunk");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void admittedJobSurvivesNbtAndCutsAtMostFourLogsPerStep(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(78008,70,78008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.SPRUCE,false,7); var logs=inspect(level,root,TreeSpecies.SPRUCE).logs(); var m=machine(h,root,TreeSpecies.SPRUCE); m.energy.add(logs.size()*250+120,false); untilAdmitted(m,level);
            h.assertTrue(m.arborist.job!=null && m.arborist.job.cut<=4 && m.inventory.getStackInSlot(1).isEmpty(),"Four-log step and private real tool escrow");
            var saved=m.saveWithFullMetadata(); var restored=(CopperMachineBlockEntity)BlockEntity.loadStatic(m.getBlockPos(),m.getBlockState(),saved);
            h.assertTrue(restored!=null && restored.arborist.job!=null && restored.arborist.job.cut==m.arborist.job.cut && restored.arborist.job.tool.getDamageValue()==m.arborist.job.cut && !m.getUpdateTag().contains("Arborist"),"Job, real tool wear and private state survive NBT");
            m.load(saved); for(int i=0;i<100 && m.arborist.job!=null;i++) { int before=m.arborist.job.cut; ArboristLogic.process(m,level); if(m.arborist.job!=null) h.assertTrue(m.arborist.job.cut-before<=4,"Every continuation cuts at most four logs"); }
            h.assertTrue(m.grown==1 && m.energy.stored()==0 && owned(m,Items.SPRUCE_LOG)==logs.size() && level.getBlockState(root).is(Blocks.SPRUCE_SAPLING),"Restored job finishes with exact output and no repeat loot");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void darkOakReservesAndActuallyPlacesFourSaplings(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(80008,70,80008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.DARK_OAK,true,7); var scan=inspect(level,root,TreeSpecies.DARK_OAK); h.assertTrue(scan.result()==TreeScan.Result.READY && scan.roots().size()==4,"Real dark oak has four planting roots");
            var m=machine(h,root,TreeSpecies.DARK_OAK); m.energy.add(scan.logs().size()*250+480,false);
            m.inventory.setStackInSlot(0,new ItemStack(Items.DARK_OAK_SAPLING,3)); untilAdmitted(m,level);
            h.assertTrue(m.status()==MachineStatus.NEED_SAPLINGS && scan.logs().stream().allMatch(log->level.getBlockState(log.pos()).equals(log.state())),"Three saplings cannot start a four-sapling tree");
            m.inventory.setStackInSlot(0,new ItemStack(Items.DARK_OAK_SAPLING,4));
            for(int i=0;i<400 && m.grown==0;i++) ArboristLogic.process(m,level);
            h.assertTrue(m.grown==1 && m.energy.stored()==0 && owned(m,Items.DARK_OAK_LOG)==scan.logs().size() && scan.roots().stream().allMatch(p->level.getBlockState(p).is(Blocks.DARK_OAK_SAPLING)),"Four real placements cost 480 CuE after exact real log loot: status="+m.status());
        });
    }
    public static final class DenyBreak {
        final Set<BlockPos> targets; public DenyBreak(List<TreeScan.Log> logs) { targets=new HashSet<>(); logs.forEach(log->targets.add(log.pos())); }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void broken(net.minecraftforge.event.level.BlockEvent.BreakEvent event) { if(targets.contains(event.getPos())) event.setCanceled(true); }
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void forgeCancellationRetainsTheWholeTreeAndReservedItems(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(82008,70,82008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); var m=machine(h,root,TreeSpecies.OAK); int fee=scan.logs().size()*250+120; m.energy.add(fee,false);
            var deny=new DenyBreak(scan.logs()); net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(deny);
            try { untilAdmitted(m,level); h.assertTrue(m.status()==MachineStatus.BREAK_DENIED && m.energy.stored()==fee && m.arborist.job!=null && m.arborist.job.cut==0 && m.arborist.job.saplings.getCount()==1 && scan.logs().stream().allMatch(log->level.getBlockState(log.pos()).equals(log.state())),"A denied Forge break keeps the real tree, tool/sapling reserve and energy"); }
            finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); }
            for(int i=0;i<100 && m.arborist.job!=null;i++) ArboristLogic.process(m,level);
            h.assertTrue(m.grown==1 && m.energy.stored()==0 && owned(m,Items.OAK_LOG)==scan.logs().size(),"The same pending job resumes after permission returns without repeat loot");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void actualGlmSeesPostBreakWorldAndSilkToolExactlyOncePerLog(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(84008,70,84008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); var m=machine(h,root,TreeSpecies.OAK); var tool=new ItemStack(Items.DIAMOND_AXE); tool.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH,1); m.inventory.setStackInSlot(1,tool); m.energy.add(scan.logs().size()*250+120,false);
            var probe=new ArboristLootFixture.Probe(); scan.logs().forEach(log->ArboristLootFixture.PROBES.put(log.pos(),probe));
            try {
                for(int i=0;i<400 && m.grown==0;i++) ArboristLogic.process(m,level);
                h.assertTrue(m.grown==1 && probe.calls==scan.logs().size() && probe.silk && probe.removed && owned(m,Items.EMERALD)==scan.logs().size()*3 && owned(m,Items.OAK_LOG)==scan.logs().size(),"One genuine loot-table/GLM evaluation after each actual removal, using the reserved Silk Touch axe");
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(8)).isEmpty(),"Fitting actual drops are not duplicated into the world");
            } finally { scan.logs().forEach(log->ArboristLootFixture.PROBES.remove(log.pos())); }
        });
    }
    public static final class DenyPlant {
        final BlockPos root; DenyPlant(BlockPos root) { this.root=root; }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) { if(event.getPos().equals(root)) event.setCanceled(true); }
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void cancelledPlacementKeepsTheSaplingAndItsFeeUntilResume(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(88008,70,88008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); var m=machine(h,root,TreeSpecies.OAK); m.energy.add(scan.logs().size()*250+120,false);
            var deny=new DenyPlant(root); net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(deny);
            try {
                for(int i=0;i<400 && m.status()!=MachineStatus.PLANT_DENIED;i++) ArboristLogic.process(m,level);
                h.assertTrue(m.status()==MachineStatus.PLANT_DENIED && m.arborist.job!=null && m.arborist.job.planted==0 && m.arborist.job.saplings.getCount()==1 && m.energy.stored()==120 && level.getBlockState(root).isAir(),"Cancelled Forge placement restores block/item and keeps the unspent 120 CuE");
            } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); }
            ArboristLogic.process(m,level); h.assertTrue(m.grown==1 && m.energy.stored()==0 && level.getBlockState(root).is(Blocks.OAK_SAPLING),"The same real sapling is placed once after permission returns");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void constructionDuringAJobStopsTheNextCutAndCancelReturnsEscrow(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(90008,70,90008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.SPRUCE,false,7); var scan=inspect(level,root,TreeSpecies.SPRUCE); var m=machine(h,root,TreeSpecies.SPRUCE); int fee=scan.logs().size()*250+120; m.energy.add(fee,false); untilAdmitted(m,level);
            h.assertTrue(m.arborist.job!=null && m.arborist.job.cut==4,"First physical step cuts exactly four spruce logs"); var next=m.arborist.job.logs.get(4).pos(); var construction=next.east(); level.setBlock(construction,Blocks.OAK_PLANKS.defaultBlockState(),3);
            ArboristLogic.process(m,level); h.assertTrue(m.status()==MachineStatus.BUILDING_NEARBY && m.arborist.job.cut==4 && m.energy.stored()==fee-1000 && level.getBlockState(next).is(Blocks.SPRUCE_LOG) && level.getBlockState(construction).is(Blocks.OAK_PLANKS),"A new building neighbor blocks the next actual operation");
            var owner=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(m.owner(),"ArboristOwner")); owner.setPos(m.getBlockPos().getX()+.5,m.getBlockPos().getY()+.5,m.getBlockPos().getZ()+.5);
            var menu=new io.github.gerbesh.unusualcopper.menu.CopperMachineMenu(9,owner.getInventory(),m); h.assertTrue(menu.clickMenuButton(owner,1),"Owner cancels through the real menu handler");
            int saplings=m.inventory.getStackInSlot(0).getCount(),tools=0; for(var stack:m.arborist.recovery.items()) { if(stack.is(Items.SPRUCE_SAPLING)) saplings+=stack.getCount(); if(stack.is(Items.DIAMOND_AXE)) { tools+=stack.getCount(); h.assertTrue(stack.getDamageValue()==4,"Recovered tool retains its actual wear"); } }
            h.assertTrue(m.arborist.job==null && saplings==4 && tools==1 && m.energy.stored()==fee-1000 && owned(m,Items.SPRUCE_LOG)==4,"Cancel returns the exact remaining saplings/tool and never refunds committed wood"); m.redstoneMode=1;
        });
    }
    public static final class DenyBonusSpawn {
        final Set<BlockPos> positions; DenyBonusSpawn(List<TreeScan.Log> logs) { positions=new HashSet<>(); logs.forEach(log->positions.add(log.pos())); }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event) { if(event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.EMERALD) && positions.contains(item.blockPosition())) event.setCanceled(true); }
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void cancelledLootSpawnsNeverBecomeMachineInventory(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(92008,70,92008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); var m=machine(h,root,TreeSpecies.OAK); m.energy.add(scan.logs().size()*250+120,false);
            var probe=new ArboristLootFixture.Probe(); scan.logs().forEach(log->ArboristLootFixture.PROBES.put(log.pos(),probe)); var deny=new DenyBonusSpawn(scan.logs()); net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(deny);
            try {
                for(int i=0;i<400 && m.grown==0;i++) ArboristLogic.process(m,level);
                h.assertTrue(m.grown==1 && probe.calls==scan.logs().size() && owned(m,Items.EMERALD)==0 && owned(m,Items.OAK_LOG)==scan.logs().size(),"Cancelled genuine bonus spawns are not minted into inventory; normal accepted log loot remains exact");
            } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); scan.logs().forEach(log->ArboristLootFixture.PROBES.remove(log.pos())); }
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void tileDropsRuleIsRespectedByMachineLootCapture(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(94008,70,94008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); var m=machine(h,root,TreeSpecies.OAK); m.energy.add(scan.logs().size()*250+120,false);
            var rule=level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS); boolean old=rule.get(); rule.set(false,level.getServer());
            try { for(int i=0;i<400 && m.grown==0;i++) ArboristLogic.process(m,level); h.assertTrue(m.grown==1 && m.energy.stored()==0 && owned(m,Items.OAK_LOG)==0 && level.getBlockState(root).is(Blocks.OAK_SAPLING),"doTileDrops=false suppresses captured loot as well as real world item spawns"); }
            finally { rule.set(old,level.getServer()); }
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void oversizedModifierLootConservesTenThousandItemsAndPausesTheTree(GameTestHelper h) {
        var level=h.getLevel(); var root=new BlockPos(96008,70,96008);
        loaded(h,root,()->{
            grow(level,root,TreeSpecies.OAK,false,7); var scan=inspect(level,root,TreeSpecies.OAK); var m=machine(h,root,TreeSpecies.OAK); int fee=scan.logs().size()*250+120; m.energy.add(fee,false);
            var probe=new ArboristLootFixture.Probe(); probe.stone=10000; ArboristLootFixture.PROBES.put(scan.logs().get(0).pos(),probe);
            try {
                untilAdmitted(m,level); int world=0;
                for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(12))) {
                    var stack=entity.getItem(); if(stack.is(Items.STONE)) world+=stack.getCount();
                    else if(stack.is(ModContent.CARGO_BUNDLE.get())) for(var raw:stack.getOrCreateTag().getList("Cargo",net.minecraft.nbt.Tag.TAG_COMPOUND)) { var row=(net.minecraft.nbt.CompoundTag)raw; if(ItemStack.of(row.getCompound("Stack")).is(Items.STONE)) world+=row.getInt("Count"); }
                }
                h.assertTrue(m.arborist.job!=null && m.arborist.job.cut==1 && m.energy.stored()==fee-250 && owned(m,Items.STONE)+world==10000 && world>0,"All oversized real modifier loot survives in machine escrow and a real world parcel; only one cut is paid");
                ArboristLogic.process(m,level); h.assertTrue(m.arborist.job.cut==1 && m.status()==MachineStatus.OUTPUT_FULL,"An undrained emergency buffer stops the next cut");
            } finally { ArboristLootFixture.PROBES.remove(scan.logs().get(0).pos()); m.redstoneMode=1; }
        });
    }
}
