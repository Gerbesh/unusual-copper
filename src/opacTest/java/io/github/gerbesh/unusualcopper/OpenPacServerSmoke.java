package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.gameplay.WorkArea;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.CultivatorLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Ordinary dedicated server supplies the profile cache required by OpenPAC's lifecycle. */
@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID)
public final class OpenPacServerSmoke {
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("unusualcopper.opacSmoke")) return;
        var server = event.getServer(); var level = server.overworld(); var pos = new BlockPos(1024, 70, 512);
        var owner = UUID.fromString("7f9d4e0e-73b9-4d0c-89ae-e50a232fa21e"); var stranger = UUID.fromString("17b844a5-3ef8-4b52-a376-cdc1d76cd392");
        server.getProfileCache().add(new com.mojang.authlib.GameProfile(owner, "CuEOwner")); server.getProfileCache().add(new com.mojang.authlib.GameProfile(stranger, "CuEGuest"));
        // Explicit setup in the isolated fixture world. Runtime automation never loads chunks.
        level.getChunkAt(pos); level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity)level.getBlockEntity(pos); barrel.setItem(0, new ItemStack(Items.COPPER_INGOT, 8));
        Object manager = null;
        try {
            var apiType = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var managerType = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            var api = apiType.getMethod("get", MinecraftServer.class).invoke(null, server);
            manager = apiType.getMethod("getServerClaimsManager").invoke(api);
            managerType.getMethod("claim", ResourceLocation.class, UUID.class, int.class, int.class, int.class, boolean.class)
                    .invoke(manager, level.dimension().location(), owner, -1, pos.getX() >> 4, pos.getZ() >> 4, false);
            check(ProtectionPolicy.allowed(level, pos, owner), "Actual claim did not allow its owner");
            check(!ProtectionPolicy.allowed(level, pos, stranger) && !ProtectionPolicy.allowed(level, pos, null), "Actual claim allowed a foreign or ownerless actor");
            var denied = new HandlerStorageEndpoint(level, pos, Direction.UP, stranger); var key = ItemKey.of(new ItemStack(Items.COPPER_INGOT));
            check(!denied.isAvailable() && denied.simulateExtract(key, 8) == 0 && denied.extract(key, 8).isEmpty() && barrel.getItem(0).getCount() == 8, "Denied endpoint modified inventory");
            var allowed = new HandlerStorageEndpoint(level, pos, Direction.UP, owner);
            check(allowed.extract(key, 8).stream().mapToInt(ItemStack::getCount).sum() == 8 && barrel.getItem(0).isEmpty(), "Owner endpoint could not perform the real extraction");
            UnusualCopper.LOGGER.info("CuE OPENPAC CLAIM PASS: actual 0.30.1 owner allowed, foreign and null-owner inventory automation denied");
            // The machine itself is outside the claim: this exercises target permission, too.
            var machinePos = pos.offset(-1, 0, 3); var cropPos = pos.offset(1, 0, 3);
            level.getChunkAt(machinePos); level.setDayTime(6000);
            level.setBlock(machinePos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(machinePos, ModContent.MACHINES.get(MachineKind.AURA_CULTIVATOR).get().defaultBlockState(), 3);
            var cultivator = (CopperMachineBlockEntity)level.getBlockEntity(machinePos);
            var actor = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(owner, "CuEOwner"));
            actor.setPos(machinePos.getX() + .5, machinePos.getY() + .5, machinePos.getZ() + .5);
            level.setBlock(cropPos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
            level.setBlock(cropPos, Blocks.WHEAT.defaultBlockState(), 3);
            check(cultivator.setWorkArea(new WorkArea(cropPos, cropPos), actor), "Claim owner could not select the crop area");
            cultivator.energy.add(140, false); cultivator.setOwner(stranger);
            check(ProtectionPolicy.allowed(level, machinePos, stranger), "Fixture machine unexpectedly falls inside a claim");
            CultivatorLogic.process(cultivator, level);
            check(cultivator.status() == MachineStatus.PROTECTED && cultivator.energy.stored() == 140 && level.getBlockState(cropPos).getValue(CropBlock.AGE) == 0,
                    "Foreign crop automation changed a claimed plant or charged energy");
            cultivator.setOwner(owner); CultivatorLogic.process(cultivator, level);
            check(cultivator.grown == 1 && cultivator.energy.stored() == 0 && level.getBlockState(cropPos).getValue(CropBlock.AGE) > 0,
                    "Claim owner could not perform one paid crop growth");
            UnusualCopper.LOGGER.info("CuE OPENPAC CULTIVATOR PASS: foreign target access denied without charge; owner grew claimed wheat for exactly 140 CuE");
            var collectorPos = machinePos.south(); level.setBlock(collectorPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(collectorPos, ModContent.MACHINES.get(MachineKind.MAGNETIC_COLLECTOR).get().defaultBlockState(), 3);
            var collector = (CopperMachineBlockEntity)level.getBlockEntity(collectorPos);
            actor.setPos(collectorPos.getX() + .5, collectorPos.getY() + .5, collectorPos.getZ() + .5);
            check(collector.setWorkArea(new WorkArea(cropPos, cropPos), actor), "Claim owner could not select collector target");
            var ground = new net.minecraft.world.entity.item.ItemEntity(level, cropPos.getX() + .5, cropPos.getY() + .5, cropPos.getZ() + .5, new ItemStack(Items.IRON_INGOT, 8));
            ground.setNoPickUpDelay(); level.addFreshEntity(ground); collector.energy.add(100, false); collector.setOwner(stranger);
            io.github.gerbesh.unusualcopper.machine.logic.CollectorLogic.process(collector, level);
            check(ground.isAlive() && collector.energy.stored() == 100 && collector.status() == MachineStatus.PROTECTED, "Collector took a foreign claimed item");
            collector.setOwner(owner); io.github.gerbesh.unusualcopper.machine.logic.CollectorLogic.process(collector, level);
            check(ground.isRemoved() && collector.inventory.getStackInSlot(2).getCount() == 8 && collector.energy.stored() < 100, "Claim owner could not collect the real stack");
            UnusualCopper.LOGGER.info("CuE OPENPAC COLLECTOR PASS: foreign target access denied without charge; owner collected eight real ingots");
            var shepherdPos = machinePos.north(); level.setBlock(shepherdPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(shepherdPos, ModContent.MACHINES.get(MachineKind.GROWTH_SHEPHERD).get().defaultBlockState(), 3);
            var shepherd = (CopperMachineBlockEntity)level.getBlockEntity(shepherdPos);
            actor.setPos(shepherdPos.getX() + .5, shepherdPos.getY() + .5, shepherdPos.getZ() + .5);
            check(shepherd.setWorkArea(new WorkArea(cropPos, cropPos), actor), "Claim owner could not select the shepherd area");
            var calf = net.minecraft.world.entity.EntityType.COW.create(level); calf.setPos(cropPos.getX() + .5, cropPos.getY() + .1, cropPos.getZ() + .5); calf.setNoAi(true); calf.setNoGravity(true); calf.setAge(-5000); level.addFreshEntity(calf);
            shepherd.energy.add(400, false); shepherd.setOwner(stranger);
            io.github.gerbesh.unusualcopper.machine.logic.ShepherdLogic.process(shepherd, level);
            check(calf.getAge() == -5000 && shepherd.energy.stored() == 400 && shepherd.status() == MachineStatus.PROTECTED, "Foreign shepherd changed a claimed calf or charged energy");
            shepherd.setOwner(owner); shepherd.nextWaveTick = 0; io.github.gerbesh.unusualcopper.machine.logic.ShepherdLogic.process(shepherd, level);
            check(calf.getAge() == -2600 && shepherd.energy.stored() == 0 && shepherd.grown == 1, "Claim owner could not pay for exactly 2400 ticks of calf growth");
            calf.discard();
            UnusualCopper.LOGGER.info("CuE OPENPAC SHEPHERD PASS: foreign calf access denied without charge; owner advanced age by 2400 for exactly 400 CuE");
            var reaperPos=shepherdPos.east(); level.setBlock(reaperPos,Blocks.AIR.defaultBlockState(),3); level.setBlock(reaperPos,ModContent.MACHINES.get(MachineKind.MOB_REAPER).get().defaultBlockState(),3);
            var reaper=(CopperMachineBlockEntity)level.getBlockEntity(reaperPos); actor.setPos(reaperPos.getX()+.5,reaperPos.getY()+.5,reaperPos.getZ()+.5);
            check(reaper.setWorkArea(new WorkArea(cropPos,cropPos),actor),"Claim owner could not select reaper area");
            reaper.reaper.settings(new io.github.gerbesh.unusualcopper.gameplay.ReaperRule(true,true,java.util.Set.of(new ResourceLocation("minecraft","cow")),java.util.Set.of(),java.util.Set.of()),0,0);
            var victim=net.minecraft.world.entity.EntityType.COW.create(level); victim.setPos(cropPos.getX()+.5,cropPos.getY()+.1,cropPos.getZ()+.5); victim.setNoAi(true); victim.setNoGravity(true); level.addFreshEntity(victim);
            reaper.energy.add(1500,false); reaper.setOwner(stranger);
            for(int i=0;i<20;i++) { io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.process(reaper,level); if(reaper.reaper.windup!=null) { var pending=reaper.reaper.windup; reaper.reaper.windup=new io.github.gerbesh.unusualcopper.machine.ReaperState.Windup(pending.action(),pending.target(),level.getGameTime(),pending.plan()); continue; } if(reaper.status()!=MachineStatus.SCANNING) break; }
            check(victim.isAlive() && victim.getHealth()==victim.getMaxHealth() && reaper.energy.stored()==1500 && reaper.status()==MachineStatus.PROTECTED,"Foreign reaper check failed: hp="+victim.getHealth()+", energy="+reaper.energy.stored()+", status="+reaper.status()+", next="+reaper.nextWaveTick+", now="+level.getGameTime());
            reaper.setOwner(owner); reaper.nextWaveTick=0;
            for(int i=0;i<20 && reaper.reaper.windup==null;i++) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.process(reaper,level);
            check(reaper.reaper.windup!=null && victim.isAlive() && reaper.energy.stored()==1500,"Claim owner could not begin unpaid preparation");
            managerType.getMethod("unclaim",ResourceLocation.class,int.class,int.class).invoke(manager,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4);
            managerType.getMethod("claim",ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class).invoke(manager,level.dimension().location(),stranger,-1,pos.getX()>>4,pos.getZ()>>4,false);
            var pendingClaim=reaper.reaper.windup; reaper.reaper.windup=new io.github.gerbesh.unusualcopper.machine.ReaperState.Windup(pendingClaim.action(),pendingClaim.target(),level.getGameTime(),pendingClaim.plan());
            io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.process(reaper,level);
            check(victim.isAlive() && victim.getHealth()==victim.getMaxHealth() && reaper.energy.stored()==1500 && reaper.reaper.windup==null && reaper.status()==MachineStatus.PROTECTED,"Claim changed during preparation but stale permission still attacked");
            managerType.getMethod("unclaim",ResourceLocation.class,int.class,int.class).invoke(manager,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4);
            managerType.getMethod("claim",ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class).invoke(manager,level.dimension().location(),owner,-1,pos.getX()>>4,pos.getZ()>>4,false);
            reaper.nextWaveTick=0;
            UnusualCopper.LOGGER.info("CuE OPENPAC REAPER WINDUP PASS: claim ownership changed after preparation; commit rechecked permission and cancelled without damage or fee");
            for(int i=0;i<20;i++) { io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.process(reaper,level); if(reaper.reaper.windup!=null) { var pending=reaper.reaper.windup; reaper.reaper.windup=new io.github.gerbesh.unusualcopper.machine.ReaperState.Windup(pending.action(),pending.target(),level.getGameTime(),pending.plan()); continue; } if(reaper.status()!=MachineStatus.SCANNING) break; }
            check(!victim.isAlive() && reaper.energy.stored()==0 && !reaper.inventory.getStackInSlot(2).isEmpty(),"Claim owner could not pay for a real death and actual loot"); victim.discard();
            UnusualCopper.LOGGER.info("CuE OPENPAC REAPER PASS: foreign target damage denied without charge; owner killed target for exactly 1500 CuE and kept real loot");
            var forestryPos=machinePos.north(3); var tree=cropPos;
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                level.setBlock(tree.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
                for(int y=0;y<10;y++) level.setBlock(tree.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(int y=0;y<8;y++) level.setBlock(tree.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0||z!=0) level.setBlock(tree.offset(x,7,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
            level.setBlock(forestryPos,Blocks.AIR.defaultBlockState(),3); level.setBlock(forestryPos,ModContent.MACHINES.get(MachineKind.ARBORIST).get().defaultBlockState(),3);
            var forestry=(CopperMachineBlockEntity)level.getBlockEntity(forestryPos); actor.setPos(forestryPos.getX()+.5,forestryPos.getY()+.5,forestryPos.getZ()+.5);
            check(forestry.togglePlantation(tree,actor),"Owner could not mark claimed plantation"); forestry.inventory.setStackInSlot(0,new ItemStack(Items.OAK_SAPLING,4)); forestry.inventory.setStackInSlot(1,new ItemStack(Items.DIAMOND_AXE)); forestry.energy.add(2120,false); forestry.setOwner(stranger);
            for(int i=0;i<300;i++) { io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic.process(forestry,level); if(forestry.status()!=MachineStatus.TREE_SCANNING) break; }
            check(forestry.status()==MachineStatus.PROTECTED && forestry.arborist.job==null && forestry.energy.stored()==2120 && level.getBlockState(tree.above(7)).is(Blocks.OAK_LOG),"Foreign forestry modified the claimed tree or consumed reserve");
            forestry.setOwner(owner); forestry.arborist.scan=null; forestry.arborist.retryAt=0;
            for(int i=0;i<300 && forestry.arborist.job==null;i++) io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic.process(forestry,level);
            check(forestry.arborist.job!=null && forestry.arborist.job.cut==4 && forestry.energy.stored()==1120,"Owner did not commit exactly four physical cuts");
            managerType.getMethod("unclaim",ResourceLocation.class,int.class,int.class).invoke(manager,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4);
            managerType.getMethod("claim",ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class).invoke(manager,level.dimension().location(),stranger,-1,pos.getX()>>4,pos.getZ()>>4,false);
            io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic.process(forestry,level);
            check(forestry.status()==MachineStatus.PROTECTED && forestry.arborist.job.cut==4 && forestry.energy.stored()==1120 && level.getBlockState(tree).is(Blocks.OAK_LOG),"Changed claims did not stop the remaining trunk");
            managerType.getMethod("unclaim",ResourceLocation.class,int.class,int.class).invoke(manager,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4);
            managerType.getMethod("claim",ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class).invoke(manager,level.dimension().location(),owner,-1,pos.getX()>>4,pos.getZ()>>4,false);
            for(int i=0;i<20 && forestry.arborist.job!=null;i++) io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic.process(forestry,level);
            check(forestry.grown==1 && forestry.energy.stored()==0 && forestry.inventory.getStackInSlot(2).getCount()==8 && forestry.inventory.getStackInSlot(1).getDamageValue()==8 && level.getBlockState(tree).is(Blocks.OAK_SAPLING),"Claim-restored tree did not complete exact loot, tool wear and one placement"); forestry.redstoneMode=1;
            UnusualCopper.LOGGER.info("CuE OPENPAC ARBORIST PASS: foreign tree denied; claim change stopped after four cuts; permission restore completed eight exact logs, 2120 CuE, tool wear and one sapling");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("OpenPAC fixture API failed", error); }
        finally {
            if (manager != null) try { Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI").getMethod("unclaim", ResourceLocation.class, int.class, int.class).invoke(manager, level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not clear fixture claim", error); }
        }
        server.halt(false);
    }
}
