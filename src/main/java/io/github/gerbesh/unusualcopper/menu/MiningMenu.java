package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class MiningMenu extends AbstractContainerMenu {
    public final BlockPos pos;private final CopperMachineBlockEntity machine;private final Player viewer;private long replied=Long.MIN_VALUE,changed=Long.MIN_VALUE;
    public MiningNetwork.Reply snapshot;
    public MiningMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,null,b.readBlockPos());}
    public MiningMenu(int id,Inventory inv,CopperMachineBlockEntity m){this(id,inv,m,m.getBlockPos());}
    private MiningMenu(int id,Inventory inv,CopperMachineBlockEntity m,BlockPos p){super(ModContent.MINING_MENU.get(),id);machine=m;pos=p;viewer=inv.player;IItemHandler items=m==null?new ItemStackHandler(4):m.inventory;
        addSlot(new SlotItemHandler(items,0,27,52));addSlot(new SlotItemHandler(items,2,66,52));addSlot(new SlotItemHandler(items,3,84,52));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,16+col*18,167+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,16+col*18,225));
    }
    @Override public boolean stillValid(Player p){return machine==null||p==viewer&&p.level()==machine.getLevel()&&!machine.isRemoved()&&machine.mining!=null&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==machine&&machine.canAccess(p)&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    public void receive(MiningNetwork.Reply r){if(r.menu()==containerId&&r.pos().equals(pos))snapshot=r;}
    public void toggle(ServerPlayer p,UUID runtime,ResourceLocation block){if(machine==null||!stillValid(p)||!machine.runtimeIdentity.equals(runtime))return;long now=p.level().getGameTime();if(changed!=Long.MIN_VALUE&&now-changed<2)return;changed=now;var s=machine.mining;if(!s.discovered.containsKey(block)&&!s.disabled.contains(block))return;if(!s.disabled.remove(block)){if(s.disabled.size()>=256)return;s.disabled.add(block);}s.rescan();machine.setChanged();reply(p);}
    @Override public boolean clickMenuButton(Player p,int button){if(machine==null||!stillValid(p)||!(p instanceof ServerPlayer server))return false;var s=machine.mining;
        switch(button){case 0->{s.enabled=!s.enabled;s.jobs.clear();}case 1->{s.faulted=false;s.rescan();}case 2->{machine.clearBinding();s.jobs.clear();}case 3->{if(s.area==null)return false;AreaNetwork.preview(server,new BlockPos(s.area.minX(),pos.getY(),s.area.minZ()),new BlockPos(s.area.maxX(),pos.getY(),s.area.maxZ()),true);}case 4->{if(s.experience>0){int amount=Math.min(10000,s.experience);s.experience-=amount;machine.setChanged();server.giveExperiencePoints(amount);}}case 6->{if(!io.github.gerbesh.unusualcopper.item.AreaWandItem.beginMiningBinding(server,machine)){server.displayClientMessage(net.minecraft.network.chat.Component.translatable("mining.unusualcopper.need_wand"),true);return false;}return true;}case 5->{if(!io.github.gerbesh.unusualcopper.item.AreaWandItem.beginMiningSelection(server,machine)){server.displayClientMessage(net.minecraft.network.chat.Component.translatable("mining.unusualcopper.need_wand"),true);return false;}return true;}default->{return false;}}
        io.github.gerbesh.unusualcopper.gameplay.MiningChunkLoading.changed(server.serverLevel(),machine);
        machine.setChanged();reply(server);return true;
    }
    @Override public ItemStack quickMoveStack(Player p,int index){if(!stillValid(p)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var current=slot.getItem();var copy=current.copy();if(index<3){if(!moveItemStackTo(current,3,39,true))return ItemStack.EMPTY;}else if(!moveItemStackTo(current,0,1,false))return ItemStack.EMPTY;if(current.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return copy;}
    private void reply(ServerPlayer p){var s=machine.mining;var entries=new ArrayList<MiningNetwork.Entry>();var ids=new TreeSet<>(s.discovered.keySet());ids.addAll(s.disabled);for(var id:ids){var block=ForgeRegistries.BLOCKS.getValue(id);if(block!=null&&entries.size()<256)entries.add(new MiningNetwork.Entry(id,new ItemStack(block),s.discovered.getOrDefault(id,0),!s.disabled.contains(id)));}var a=s.area;int[] area=a==null?new int[0]:new int[]{a.minX(),a.minZ(),a.maxX(),a.maxZ()};int total=a==null?0:a.columns()*(pos.getY()-p.level().getMinBuildHeight());MiningNetwork.send(p,new MiningNetwork.Reply(containerId,pos,machine.runtimeIdentity,machine.energy.stored(),machine.operationCost,machine.status().ordinal(),area,s.scanned,total,s.mined,s.experience,s.enabled,s.faulted,machine.boundInventory(),List.copyOf(entries),s.chunksLoaded,s.chunksTotal,s.chunkLoading.ordinal()));replied=p.level().getGameTime();}
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine!=null&&viewer instanceof ServerPlayer p&&stillValid(p)&&(replied==Long.MIN_VALUE||p.level().getGameTime()-replied>=10))reply(p);}
}
