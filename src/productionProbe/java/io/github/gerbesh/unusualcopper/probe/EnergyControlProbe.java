package io.github.gerbesh.unusualcopper.probe;

import com.mojang.logging.LogUtils;
import io.github.gerbesh.unusualcopper.energy.EnergyNetworks;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

final class EnergyControlProbe {
    private static final UUID OWNER=UUID.fromString("76d5ee9e-f5f4-45f4-ad58-bac7a7ef0416");
    private static ServerLevel level;
    private static CopperMachineBlockEntity source,router,east,south,monitor;
    private static long started;
    private static int eastBefore,southBefore;
    private static EnergyNetworks.Sample before;
    private static boolean ready;
    private static void check(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
    private static final class Checkpoint extends SavedData {
        boolean written;
        static Checkpoint load(CompoundTag tag){var checkpoint=new Checkpoint();checkpoint.written=tag.getBoolean("Written");return checkpoint;}
        @Override public CompoundTag save(CompoundTag tag){tag.putBoolean("Written",written);return tag;}
    }
    private static CopperMachineBlockEntity place(BlockPos pos,MachineKind kind){level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,ModContent.MACHINES.get(kind).get().defaultBlockState(),3);return (CopperMachineBlockEntity)level.getBlockEntity(pos);}
    static void start(ServerLevel world,BlockPos anchor){
        level=world;var checkpoint=level.getDataStorage().computeIfAbsent(Checkpoint::load,Checkpoint::new,"unusualcopper_energy_control_probe");
        var savedPos=anchor.south(4);
        if(checkpoint.written){
            var entity=level.getBlockEntity(savedPos);check(entity instanceof CopperMachineBlockEntity,"Distributor region checkpoint disappeared");var saved=(CopperMachineBlockEntity)entity;
            check(saved.kind==MachineKind.ENERGY_DISTRIBUTOR&&OWNER.equals(saved.owner())&&saved.energy.stored()==4095&&saved.energyPriority()==2&&saved.branchLimit(Direction.NORTH)==65536&&saved.branchPriority(Direction.SOUTH)==0&&saved.redstoneMode==1,"Distributor region restore changed its buffer, owner or branch policy");
            LogUtils.getLogger().info("CuE PRODUCTION ENERGY CONTROL DISK RESTORE PASS: actual distributor region file, 4095 CuE, owner, high consumer priority, 65536 north limit, low south priority and pause");
        }else{
            var saved=place(savedPos,MachineKind.ENERGY_DISTRIBUTOR);saved.setOwner(OWNER);saved.energy.add(4095,false);saved.energyPriority(2);saved.branchLimit(Direction.NORTH,65536);saved.branchPriority(Direction.SOUTH,0);saved.redstoneMode=1;((BlockEntity)saved).setChanged();
        }
        checkpoint.written=true;checkpoint.setDirty();LogUtils.getLogger().info("CuE PRODUCTION ENERGY CONTROL DISK CHECKPOINT: distributor buffer and all private branch settings saved");
        for(int x=0;x<5;x++)for(int z=-1;z<3;z++)level.setBlock(anchor.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
        source=place(anchor,MachineKind.ESSENCE_SPLITTER);source.energy.add(50_000,false);
        router=place(anchor.east(2),MachineKind.ENERGY_DISTRIBUTOR);router.setOwner(OWNER);router.energy.add(4096,false);router.branchLimit(Direction.EAST,0);router.branchPriority(Direction.SOUTH,0);
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(OWNER,"EnergyProbe"));actor.setPos(anchor.getX()+2.5,anchor.getY()+.5,anchor.getZ()+.5);
        net.minecraft.world.inventory.AbstractContainerMenu menu=new io.github.gerbesh.unusualcopper.menu.EnergyControlMenu(11,actor.getInventory(),router);
        check(menu.clickMenuButton(actor,20+Direction.EAST.ordinal())&&menu.clickMenuButton(actor,20+Direction.EAST.ordinal())&&menu.clickMenuButton(actor,30+Direction.EAST.ordinal())&&menu.clickMenuButton(actor,0),"Ordinary energy menu rejected its owner");
        check(menu.clickMenuButton(actor,1)&&menu.clickMenuButton(actor,1)&&menu.clickMenuButton(actor,1)&&router.redstoneMode==0,"Ordinary redstone menu did not cycle safely");
        check(router.branchLimit(Direction.EAST)==256&&router.branchPriority(Direction.EAST)==2&&router.energyPriority()==2,"Ordinary energy menu changed a different branch");
        east=place(anchor.east(4),MachineKind.COPPER_CELL_III);south=place(anchor.east(2).south(2),MachineKind.COPPER_CELL_III);monitor=place(anchor.east(3).north(),MachineKind.ENERGY_MONITOR);
        for(var pos:new BlockPos[]{anchor.east(),anchor.east(3),anchor.east(2).south()})level.setBlock(pos,ModContent.CRYSTALLINE_ENERGY_PIPE.get().defaultBlockState(),3);
        started=level.getGameTime();ready=false;
    }
    static void tick(){
        if(level==null||ready)return;long elapsed=level.getGameTime()-started;
        if(elapsed==2){eastBefore=east.energy.stored();southBefore=south.energy.stored();before=EnergyNetworks.inspect(level,monitor);}
        if(elapsed==3){
            check(east.energy.stored()-eastBefore==256&&south.energy.stored()-southBefore==3840,"Ordinary relay priority, face cap or intake/output accounting differs");
            check(source.energy.stored()+router.energy.stored()+east.energy.stored()+south.energy.stored()==54_096,"Ordinary relay created or lost CuE");
            var now=EnergyNetworks.inspect(level,monitor);check(now.epoch()==before.epoch()&&now.transferred()-before.transferred()==256&&now.produced()==before.produced()&&now.consumed()==before.consumed(),"Ordinary monitor counted a network move as generation or consumption");
            check(monitor.energy.capacity()==0&&monitor.energy.consumed()==0,"Ordinary passive monitor charged energy");ready=true;
            LogUtils.getLogger().info("CuE PRODUCTION ENERGY CONTROL PASS: physical buffered distributor, high east branch 256 and low south branch 3840 CuE/tick; conserved charge and passive monitor distinguishes transfers from consumption");
        }
        check(elapsed<20||ready,"Energy control production sample timed out");
    }
    static boolean ready(){return ready;}
}
