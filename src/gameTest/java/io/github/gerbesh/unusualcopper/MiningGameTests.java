package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.gametest.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class MiningGameTests {
    static final ResourceLocation MOD_ORE=new ResourceLocation("fixture_mining","malachite");
    @SubscribeEvent public static void register(RegisterEvent e){e.register(Registries.BLOCK,MOD_ORE,()->new Block(BlockBehaviour.Properties.of().strength(3)));}
    record Fixture(CopperMachineBlockEntity machine,BlockPos ore,BarrelBlockEntity output){}
    static Fixture fixture(GameTestHelper h,ItemStack tool){var p=new BlockPos(1,4,1);var ore=p.below(2);h.setBlock(p,ModContent.MACHINES.get(MachineKind.RESONANT_EXTRACTOR).get());h.setBlock(ore,Blocks.COPPER_ORE);h.setBlock(p.east(2),Blocks.BARREL);var m=(CopperMachineBlockEntity)h.getBlockEntity(p);var chest=(BarrelBlockEntity)h.getBlockEntity(p.east(2));var actor=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MiningFixture"));m.setOwner(actor.getUUID());h.assertTrue(m.bind(chest.getBlockPos(),Direction.UP,actor),"Fixture output bound");m.mining.area=new MiningFootprint(m.getBlockPos().getX(),m.getBlockPos().getZ(),m.getBlockPos().getX(),m.getBlockPos().getZ());m.mining.lastOwner=m.owner();m.mining.finished=true;m.inventory.setStackInSlot(0,tool);m.energy.restore(200000);var target=h.absolutePos(ore);var job=new MiningState.Job(target,h.getLevel().getBlockState(target),tool,20,1234);job.progress=io.github.gerbesh.unusualcopper.gameplay.MiningTiming.FLIGHT_TICKS;m.mining.jobs.add(job);settleTickets(h,m);return new Fixture(m,target,chest);}
    // Old transaction scenarios require a ready admission, rather than accidentally exercising the loading gate.
    static void settleTickets(GameTestHelper h,CopperMachineBlockEntity m){
        var plan=MiningChunkPlan.create(m.mining.area,m.getBlockPos().getX(),m.getBlockPos().getZ(),m.boundInventory().getX(),m.boundInventory().getZ());
        // Explicit fixture setup only: all service chunks are present before exercising production admission.
        for(long key:plan.chunks().keySet()){var chunk=new net.minecraft.world.level.ChunkPos(key);h.getLevel().getChunk(chunk.x,chunk.z);}
        MiningChunkLoading.changed(h.getLevel(),m);
        for(int i=0;i<16&&m.mining.chunkLoading==MiningChunkStatus.LOADING;i++)
            MiningChunkLoading.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,h.getLevel().getServer()));
        h.assertTrue(m.mining.chunkLoading==MiningChunkStatus.READY,"Fixture chunk tickets admitted");
    }
    static void process(GameTestHelper h,Fixture f){MiningLogic.process(f.machine,h.getLevel(),64,Long.MAX_VALUE);}
    static int owned(CopperMachineBlockEntity m,net.minecraft.world.item.Item item){int n=0;for(int slot=2;slot<4;slot++){var s=m.inventory.getStackInSlot(slot);if(s.is(item))n+=s.getCount();}for(var s:m.mining.recovery.items())if(s.is(item))n+=s.getCount();return n;}
    @GameTest(template="empty") public static void actualOreIsReplacedBySolidHostAndToolPaysOnce(GameTestHelper h){var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));process(h,f);h.assertTrue(h.getLevel().getBlockState(f.ore).is(Blocks.STONE),"Ore becomes stone, never air");h.assertTrue(owned(f.machine,Items.RAW_COPPER)>=2,"Real copper loot retained");h.assertTrue(f.machine.inventory.getStackInSlot(0).getDamageValue()==1&&f.machine.mining.mined==1,"One real tool use per removed ore");process(h,f);h.assertTrue(f.machine.mining.mined==1,"Finished beam cannot mint the same ore again");h.succeed();}
    @GameTest(template="empty") public static void silkTouchUsesTheInsertedPickaxe(GameTestHelper h){var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.enchant(Enchantments.SILK_TOUCH,1);var f=fixture(h,tool);process(h,f);h.assertTrue(owned(f.machine,Items.COPPER_ORE)==1&&owned(f.machine,Items.RAW_COPPER)==0,"Silk touch loot is the ore block");h.succeed();}
    @GameTest(template="empty") public static void fullOutputPreservesOreEnergyAndPickaxe(GameTestHelper h){var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));for(int i=0;i<f.output.getContainerSize();i++)f.output.setItem(i,new ItemStack(Items.COBBLESTONE,64));int energy=f.machine.energy.stored();process(h,f);h.assertTrue(h.getLevel().getBlockState(f.ore).is(Blocks.COPPER_ORE)&&f.machine.energy.stored()==energy&&f.machine.inventory.getStackInSlot(0).getDamageValue()==0,"Full output performs no mining transaction");h.succeed();}
    @GameTest(template="empty") public static void missingEnergyAndLastDurabilityDoNotDestroyOre(GameTestHelper h){var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));f.machine.energy.restore(0);process(h,f);h.assertTrue(h.getLevel().getBlockState(f.ore).is(Blocks.COPPER_ORE),"No free ore without CuE");f.machine.energy.restore(200000);var tool=f.machine.inventory.getStackInSlot(0);tool.setDamageValue(tool.getMaxDamage()-1);process(h,f);h.assertTrue(h.getLevel().getBlockState(f.ore).is(Blocks.COPPER_ORE)&&!tool.isEmpty(),"Last durability is preserved");h.succeed();}
    @GameTest(template="empty") public static void foreignTagAndInventoryGuardWork(GameTestHelper h){var foreign=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(MOD_ORE);h.assertTrue(MiningRules.ore(foreign.defaultBlockState()),"A foreign namespace ores/malachite tag is recognized");h.assertTrue(!MiningRules.ore(Blocks.CHEST.defaultBlockState()),"Block entities are never ore targets");h.assertTrue(!MiningRules.ore(Blocks.COAL_ORE.defaultBlockState()),"Explicit blacklist overrides a standard ore tag");h.succeed();}
    public static final class DenyBreak {final BlockPos target;DenyBreak(BlockPos p){target=p;}@SubscribeEvent public void deny(net.minecraftforge.event.level.BlockEvent.BreakEvent e){if(e.getPos().equals(target))e.setCanceled(true);}}
    @GameTest(template="empty") public static void cancelledBreakEventPreservesOreAndProducesNoLoot(GameTestHelper h){var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var denied=new DenyBreak(f.ore);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(denied);try{process(h,f);h.assertTrue(h.getLevel().getBlockState(f.ore).is(Blocks.COPPER_ORE)&&owned(f.machine,Items.RAW_COPPER)==0&&f.machine.mining.mined==0,"Forge veto is authoritative");}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(denied);}h.succeed();}
    @GameTest(template="empty") public static void changedTargetCannotMintCachedLoot(GameTestHelper h){var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));h.getLevel().setBlock(f.ore,Blocks.STONE.defaultBlockState(),2);process(h,f);h.assertTrue(f.machine.mining.mined==0&&owned(f.machine,Items.RAW_COPPER)==0&&f.machine.inventory.getStackInSlot(0).getDamageValue()==0,"Changed block invalidates the scheduled beam");h.succeed();}
    @GameTest(template="empty") public static void replacementAndRecoverySurviveSaveWithoutReplayingBeams(GameTestHelper h){var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));process(h,f);var tag=f.machine.saveWithFullMetadata();f.machine.load(tag);h.assertTrue(f.machine.mining.jobs.isEmpty(),"Visual/planned jobs are not replayed after reload");h.assertTrue(f.machine.mining.mined==1&&owned(f.machine,Items.RAW_COPPER)>=2,"Owned output and completed mining survive");h.assertTrue(h.getLevel().getBlockState(f.ore).is(Blocks.STONE),"Physical depletion persists");h.succeed();}
    @GameTest(template="empty") public static void drainingPreviousLootDoesNotPauseOtherFlights(GameTestHelper h){
        var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine;var job=m.mining.jobs.get(0);job.progress=10;job.advancedAt=h.getLevel().getGameTime()-7;
        m.inventory.setStackInSlot(2,new ItemStack(Items.RAW_COPPER,2));
        MiningLogic.process(m,h.getLevel(),64,Long.MIN_VALUE);
        h.assertTrue(job.progress==17&&m.status()==MachineStatus.WORKING,"Admitted flight progresses while earlier loot drains, even without optional work budget");
        h.assertTrue(m.mining.nextWork==0&&m.mining.mined==0&&h.getLevel().getBlockState(f.ore).is(Blocks.COPPER_ORE),"No artificial retry delay or unbudgeted physical mining");h.succeed();
    }
    @GameTest(template="empty") public static void scanningCannotStarveFlightBookkeeping(GameTestHelper h){
        var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine;var job=m.mining.jobs.get(0);job.progress=10;job.advancedAt=h.getLevel().getGameTime()-9;m.mining.finished=false;
        MiningLogic.process(m,h.getLevel(),64,Long.MIN_VALUE);
        h.assertTrue(job.progress==19&&m.mining.scanned==0,"Flight bookkeeping precedes scanning and needs no external hooks");
        h.assertTrue(m.mining.mined==0&&h.getLevel().getBlockState(f.ore).is(Blocks.COPPER_ORE),"Expired budget must not replace a world block");h.succeed();
    }
    @GameTest(template="empty") public static void arrivedOreWaitsForOwnedLootWithoutDuplicatingIt(GameTestHelper h){
        var f=fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine;m.inventory.setStackInSlot(2,new ItemStack(Items.RAW_COPPER,2));int energy=m.energy.stored();
        process(h,f);h.assertTrue(m.mining.mined==0&&m.mining.jobs.get(0).progress==MiningTiming.FLIGHT_TICKS&&m.status()==MachineStatus.OUTPUT_FULL,"Output backlog holds the transaction only after arrival");
        h.assertTrue(m.energy.stored()>=energy-1&&m.inventory.getStackInSlot(0).getDamageValue()==0,"Waiting cannot consume mining price or tool durability");
        BoundStorageLogic.tick(m,h.getLevel());process(h,f);
        h.assertTrue(m.mining.mined==1&&h.getLevel().getBlockState(f.ore).is(Blocks.STONE),"Commit resumes as soon as prior loot drains");h.succeed();
    }
}
