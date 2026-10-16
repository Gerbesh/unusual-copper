package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID)
public final class CropHarvestGameTests {
    private static final Set<BlockPos> denyBreak=new HashSet<>(),denyPlant=new HashSet<>();
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent e) {if(denyBreak.contains(e.getPos()))e.setCanceled(true);}
    @SubscribeEvent public static void planting(BlockEvent.EntityPlaceEvent e) {if(denyPlant.contains(e.getPos()))e.setCanceled(true);}
    private static final BlockPos MACHINE=new BlockPos(0,1,0),CROP=new BlockPos(2,1,2),STORAGE=new BlockPos(3,1,0);
    private static CopperMachineBlockEntity setup(GameTestHelper h,boolean bind) {
        h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,h.getLevel().getServer());
        h.setBlock(MACHINE,ModContent.MACHINES.get(MachineKind.AURA_CULTIVATOR).get());
        h.setBlock(STORAGE,Blocks.BARREL);var barrel=(BarrelBlockEntity)h.getBlockEntity(STORAGE);barrel.setItem(0,new ItemStack(Items.WHEAT_SEEDS,32));
        var m=(CopperMachineBlockEntity)h.getBlockEntity(MACHINE);var actor=h.makeMockPlayer();
        h.assertTrue(m.setWorkArea(new WorkArea(h.absolutePos(CROP),h.absolutePos(CROP)),actor),"Area setup failed");
        if(bind)h.assertTrue(m.bind(h.absolutePos(STORAGE),Direction.UP,actor),"Cultivator storage binding failed");
        wheat(h);m.growthMode=2;m.redstoneMode=1;return m;
    }
    private static void wheat(GameTestHelper h) {
        h.setBlock(CROP.above(),Blocks.GLOWSTONE);h.setBlock(CROP.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));h.setBlock(CROP,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
    }
    private static int wheatCount(BarrelBlockEntity barrel) {int n=0;for(int i=0;i<27;i++)if(barrel.getItem(i).is(Items.WHEAT))n+=barrel.getItem(i).getCount();return n;}
    private static net.minecraft.world.entity.player.Player owner(GameTestHelper h,CopperMachineBlockEntity m) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(m.owner(),"HarvestTest"));
    }
    private static void drain(CopperMachineBlockEntity m,GameTestHelper h) {for(int i=0;i<24;i++){m.harvest.drain(m);BoundStorageLogic.tick(m,h.getLevel());}}

    @GameTest(template="empty",timeoutTicks=95)
    public static void realScheduledWaveHarvestsReplantsAndExportsToBoundBarrel(GameTestHelper h) {
        var m=setup(h,true);m.energy.add(140,false);m.redstoneMode=0;
        h.runAtTickTime(75,()->{
            var chest=(BarrelBlockEntity)h.getBlockEntity(STORAGE);
            h.assertTrue(h.getBlockState(CROP).is(Blocks.WHEAT) && h.getBlockState(CROP).getValue(CropBlock.AGE)==0 && wheatCount(chest)==1 && m.energy.stored()==0 && m.energy.consumed()==140,"Actual scheduled cultivation did not replant one crop and export its single wheat for 140 CuE");
            h.assertTrue(m.harvest.empty() && m.boundCargoCount()==0,"Accepted harvest was left in hidden escrow");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=35)
    public static void oneHarvestBatchReplantsEightCropsAndExportsTheirRealLoot(GameTestHelper h) {
        var m=setup(h,true);
        var min=new BlockPos(1,1,1);var max=new BlockPos(4,1,2);
        h.assertTrue(m.setWorkArea(WorkArea.between(h.absolutePos(min),h.absolutePos(max)),owner(h,m)),"Batch harvest area setup failed");
        for(int x=1;x<=4;x++)for(int z=1;z<=2;z++) {
            var p=new BlockPos(x,1,z);h.setBlock(p.above(),Blocks.GLOWSTONE);
            h.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
            h.setBlock(p,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        }
        h.runAfterDelay(10,()->{
            m.redstoneMode=0;m.energy.restore(1120);CultivatorLogic.process(m,h.getLevel());
            for(int x=1;x<=4;x++)for(int z=1;z<=2;z++)h.assertTrue(h.getBlockState(new BlockPos(x,1,z)).getValue(CropBlock.AGE)==0,"Wave harvested only one crop instead of the whole admitted batch");
            h.assertTrue(m.harvested==8 && m.energy.stored()==0 && m.energy.consumed()==1120 && m.harvest.empty(),"Batch lost energy accounting or could not merge compatible output stacks");
            drain(m,h);h.assertTrue(wheatCount((BarrelBlockEntity)h.getBlockEntity(STORAGE))==8 && m.harvest.empty() && m.boundCargoCount()==0,"Batch lost or duplicated genuine wheat during export");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=35)
    public static void missingBindingFullStorageNoEnergyAndRedstoneLeaveCropsIntact(GameTestHelper h) {
        var m=setup(h,false);m.energy.add(140,false);
        h.runAfterDelay(10,()->{
            m.redstoneMode=0;CultivatorLogic.process(m,h.getLevel());h.assertTrue(m.status()==MachineStatus.NO_STORAGE,"Harvest mode must explain missing storage");
            h.assertTrue(m.bind(h.absolutePos(STORAGE),Direction.UP,owner(h,m)),"Binding failed");var barrel=(BarrelBlockEntity)h.getBlockEntity(STORAGE);
            for(int i=0;i<27;i++)barrel.setItem(i,new ItemStack(Items.STONE,64));
            CultivatorLogic.process(m,h.getLevel());h.assertTrue(m.status()==MachineStatus.OUTPUT_FULL,"Full storage must pause harvesting");
            barrel.setItem(1,ItemStack.EMPTY);m.energy.spend(140);CultivatorLogic.process(m,h.getLevel());h.assertTrue(m.status()==MachineStatus.NO_ENERGY,"Unpaid harvesting was attempted");
            m.energy.add(140,false);m.redstoneMode=1;CultivatorLogic.process(m,h.getLevel());
            h.assertTrue(m.status()==MachineStatus.REDSTONE_PAUSED && m.energy.stored()==140 && h.getBlockState(CROP).getValue(CropBlock.AGE)==7,"A blocked harvest changed the plant or consumed CuE");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=35)
    public static void forgeBreakAndPlantDenialsDoNotConsumePlantsSeedsOrEnergy(GameTestHelper h) {
        var m=setup(h,true);m.energy.add(140,false);var pos=h.absolutePos(CROP);
        h.runAfterDelay(10,()->{
            m.redstoneMode=0;
            try {
                denyBreak.add(pos);CultivatorLogic.process(m,h.getLevel());h.assertTrue(m.status()==MachineStatus.BREAK_DENIED,"Break cancellation was bypassed");denyBreak.remove(pos);
                denyPlant.add(pos);CultivatorLogic.process(m,h.getLevel());h.assertTrue(m.status()==MachineStatus.PLANT_DENIED,"Replant cancellation was bypassed");
                h.assertTrue(m.energy.stored()==140 && m.harvest.empty() && h.getBlockState(CROP).getValue(CropBlock.AGE)==7 && ((BarrelBlockEntity)h.getBlockEntity(STORAGE)).getItem(0).getCount()==32,"Cancelled harvest consumed a physical resource");
            } finally {denyBreak.remove(pos);denyPlant.remove(pos);}
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void rejectedReceiverPersistsCargoAndDoesNotHarvestTwice(GameTestHelper h) {
        var m=setup(h,false);h.setBlock(STORAGE,net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(UnusualCopper.MOD_ID,"fixture_storage")));
        var receiver=(StorageGameTests.Fixture)h.getBlockEntity(STORAGE);receiver.inventory.setStackInSlot(0,new ItemStack(Items.WHEAT_SEEDS,32));receiver.rejectActual=true;
        h.assertTrue(m.bind(h.absolutePos(STORAGE),Direction.UP,owner(h,m)),"Fixture binding failed");m.energy.add(140,false);
        h.runAfterDelay(10,()->{
            m.redstoneMode=0;CultivatorLogic.process(m,h.getLevel());drain(m,h);
            h.assertTrue(m.boundCargoCount()>0 && h.getBlockState(CROP).getValue(CropBlock.AGE)==0,"Rejected insertion did not retain the genuine harvest");
            var saved=m.saveWithFullMetadata();m.load(saved);receiver.rejectActual=false;drain(m,h);drain(m,h);
            int wheat=0;for(int i=0;i<receiver.inventory.getSlots();i++)if(receiver.inventory.getStackInSlot(i).is(Items.WHEAT))wheat+=receiver.inventory.getStackInSlot(i).getCount();
            h.assertTrue(wheat==1 && m.harvest.empty() && m.boundCargoCount()==0 && m.growthMode==2 && m.energy.stored()==0,"Reload lost, duplicated or repeated a completed harvest");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void supportedCropAndBerryAdaptersReplantWithoutTouchingNeighbors(GameTestHelper h) {
        var m=setup(h,true);
        h.runAfterDelay(10,()->{
            m.redstoneMode=0;var barrel=(BarrelBlockEntity)h.getBlockEntity(STORAGE);
            var seedItems=List.of(Items.WHEAT_SEEDS,Items.CARROT,Items.POTATO,Items.BEETROOT_SEEDS,Items.NETHER_WART,Items.COCOA_BEANS);
            for(int i=0;i<seedItems.size();i++)barrel.setItem(i,new ItemStack(seedItems.get(i),16));
            var states=List.of(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7),Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE,7),Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE,3),Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,3),Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE,2).setValue(CocoaBlock.FACING,Direction.EAST),Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3),Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES,true));
            h.setBlock(CROP.east(),Blocks.JUNGLE_LOG);
            for(var before:states) {
                h.setBlock(CROP.below(),before.is(Blocks.NETHER_WART)?Blocks.SOUL_SAND:before.is(Blocks.SWEET_BERRY_BUSH)?Blocks.DIRT:Blocks.FARMLAND);
                h.setBlock(CROP.above(),Blocks.GLOWSTONE);h.setBlock(CROP,before);var plan=CropHarvest.plan(before);m.energy.restore(140);
                h.assertTrue(CropHarvest.harvest(m,h.getLevel(),h.absolutePos(CROP),before)==MachineStatus.WORKING,"Supported adapter failed: "+before);
                h.assertTrue(h.getBlockState(CROP).equals(plan.replacement()) && m.energy.stored()==0 && h.getBlockState(CROP.east()).is(Blocks.JUNGLE_LOG),"Adapter did not preserve its young plant or neighbor");drain(m,h);
            }
            h.succeed();
        });
    }
}
