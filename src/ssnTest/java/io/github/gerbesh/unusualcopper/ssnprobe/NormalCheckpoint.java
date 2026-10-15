package io.github.gerbesh.unusualcopper.ssnprobe;

import com.lothrazar.storagenetwork.api.*;
import com.lothrazar.storagenetwork.block.main.TileMain;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** Normal process restart witness, not a claim of cross-file crash atomicity. */
final class NormalCheckpoint {
    private static final class Receipt extends SavedData {
        boolean pending;
        static Receipt load(CompoundTag tag){var value=new Receipt();value.pending=tag.getBoolean("Pending");return value;}
        @Override public CompoundTag save(CompoundTag tag){tag.putBoolean("Pending",pending);return tag;}
    }
    private static Receipt receipt(ServerLevel level){return level.getDataStorage().computeIfAbsent(Receipt::load,Receipt::new,"unusualcopper_ssn_normal_checkpoint_v1");}
    private static BlockPos position(BlockPos main){return main.west(8).south(5);}
    static void restore(ServerLevel level,BlockPos main,BlockPos bridge,UUID owner){
        var receipt=receipt(level);if(!receipt.pending)return;
        var master=(TileMain)level.getBlockEntity(main);var machine=(CopperMachineBlockEntity)level.getBlockEntity(position(main));
        check(master!=null&&machine!=null&&owner.equals(machine.owner())&&bridge.equals(machine.boundInventory())&&machine.energy.stored()==92500&&machine.brewer.job!=null&&machine.brewer.job.stage==1&&machine.brewer.job.batch==3&&PotionUtils.getPotion(machine.brewer.job.current)==Potions.AWKWARD,"Native region-file paid brewer checkpoint did not restore its exact owner/binding/intermediate/CuE");
        machine.redstoneMode=0;
        for(int i=0;i<40;i++){master.getNetwork().doRefresh(new DimPos(level,main));BoundStorageLogic.tick(machine,level);machine.brewer.nextWork=0;BrewerLogic.process(machine,level);}
        var output=PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.FIRE_RESISTANCE);int count=master.getNetwork().getAmount(new com.lothrazar.storagenetwork.capability.handler.ItemStackMatcher(output,false,true));
        check(machine.brewer.job==null&&machine.brewer.recovery.count()==0&&machine.boundCargoCount()==0&&machine.energy.stored()==85000&&count==3,"Native restart did not finish exactly three paid potions through its original bridge");
        machine.redstoneMode=1;((BlockEntity)machine).setChanged();receipt.pending=false;receipt.setDirty();
        UnusualCopper.LOGGER.info("CuE SSN DISK RESTORE PASS: actual region-file paid awkward batch, original owner/bridge/master, three physical fire-resistance potions and exactly 15000 total CuE");
    }
    static void write(ServerLevel level,BlockPos main,BlockPos bridge,UUID owner){
        var pos=position(main);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,ModContent.MACHINES.get(MachineKind.AETHERIC_BREWER).get().defaultBlockState(),3);var machine=(CopperMachineBlockEntity)level.getBlockEntity(pos);machine.setOwner(owner);machine.energy.add(100000,false);machine.brewer.pending=3;
        ServerPlayer actor=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"SSNBridgeOwner"));actor.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);check(machine.bind(bridge,Direction.UP,actor),"Native disk witness could not bind its original bridge");machine.inventory.setStackInSlot(0,new ItemStack(Items.GLASS_BOTTLE,3));machine.inventory.setStackInSlot(1,new ItemStack(Items.NETHER_WART));machine.brewer.nextWork=0;BrewerLogic.process(machine,level);
        check(machine.brewer.job!=null&&machine.brewer.job.stage==1&&machine.brewer.job.batch==3&&machine.energy.stored()==92500,"Native disk witness failed to commit exactly one real wart stage");
        var source=(BarrelBlockEntity)level.getBlockEntity(main.east(3).south(2));source.setItem(0,new ItemStack(Items.MAGMA_CREAM));source.setChanged();machine.redstoneMode=1;((BlockEntity)machine).setChanged();var receipt=receipt(level);receipt.pending=true;receipt.setDirty();
        UnusualCopper.LOGGER.info("CuE SSN DISK CHECKPOINT: actual three-bottle awkward job, original bridge, one paid wart stage, 92500 CuE, native magma source and redstone pause");
    }
    private static void check(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
    private NormalCheckpoint(){}
}
