package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.energy.EnergyNetworks;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.EnergyControlNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class EnergyControlMenu extends AbstractContainerMenu {
    private static final int[] LIMITS={0,64,256,1024,4096,16384,65536};
    public final BlockPos pos;
    private final CopperMachineBlockEntity machine;
    private final ServerPlayer viewer;
    private EnergyNetworks.Sample previous;
    private long previousAt,nextUpdate;
    public EnergyControlNetwork.View view;
    public EnergyControlMenu(int id,Inventory inventory,FriendlyByteBuf buffer){super(ModContent.ENERGY_MENU.get(),id);pos=buffer.readBlockPos();machine=null;viewer=null;}
    public EnergyControlMenu(int id,Inventory inventory,CopperMachineBlockEntity machine){super(ModContent.ENERGY_MENU.get(),id);this.pos=machine.getBlockPos();this.machine=machine;viewer=inventory.player instanceof ServerPlayer server?server:null;}
    public static void open(ServerPlayer player,CopperMachineBlockEntity machine){
        if(!machine.canAccess(player)||!ProtectionPolicy.allowed(player.serverLevel(),machine.getBlockPos(),player.getUUID())||player.distanceToSqr(machine.getBlockPos().getX()+.5,machine.getBlockPos().getY()+.5,machine.getBlockPos().getZ()+.5)>64)return;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,actor)->new EnergyControlMenu(id,inventory,machine),machine.getDisplayName()),machine.getBlockPos());
    }
    @Override public boolean stillValid(Player player){return machine==null||player instanceof ServerPlayer server&&server.serverLevel()==machine.getLevel()&&server.serverLevel().hasChunkAt(pos)&&server.serverLevel().getBlockEntity(pos)==machine&&!machine.isRemoved()&&machine.canAccess(player)&&ProtectionPolicy.allowed(server.serverLevel(),pos,player.getUUID())&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean clickMenuButton(Player player,int button){
        if(machine==null||!stillValid(player))return false;
        if(button==0&&machine.kind.consumes()){machine.energyPriority((machine.energyPriority()+1)%3);return true;}
        if(button==1&&machine.energy.capacity()>0){machine.redstoneMode=(machine.redstoneMode+1)%3;machine.setChanged();return true;}
        if(machine.kind!=MachineKind.ENERGY_DISTRIBUTOR)return false;
        if(button>=10&&button<16){machine.cycleSide(Direction.values()[button-10]);return true;}
        if(button>=20&&button<26){var side=Direction.values()[button-20];int current=machine.branchLimit(side),next=0;for(int limit:LIMITS)if(limit>current){next=limit;break;}machine.branchLimit(side,next);return true;}
        if(button>=30&&button<36){var side=Direction.values()[button-30];machine.branchPriority(side,(machine.branchPriority(side)+1)%3);return true;}
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(machine==null||viewer==null||!stillValid(viewer))return;
        long now=viewer.serverLevel().getGameTime();if(now<nextUpdate)return;nextUpdate=now+10;
        var sample=EnergyNetworks.inspect(viewer.serverLevel(),machine);int ticks=previous!=null&&previous.epoch()==sample.epoch()?(int)Math.min(200,Math.max(0,now-previousAt)):0;
        long produced=0,consumed=0,transferred=0,deficit=0;
        if(ticks>0){produced=Math.max(0,sample.produced()-previous.produced());consumed=Math.max(0,sample.consumed()-previous.consumed());transferred=Math.max(0,sample.transferred()-previous.transferred());deficit=Math.max(0,(sample.demand()-previous.demand())-(sample.delivered()-previous.delivered()));}
        int[] limits=new int[6],priorities=new int[6],modes=new int[6];for(var side:Direction.values()){int i=side.ordinal();limits[i]=machine.branchLimit(side);priorities[i]=machine.branchPriority(side);modes[i]=machine.sideMode(side).ordinal();}
        EnergyControlNetwork.send(viewer,new EnergyControlNetwork.View(containerId,pos,machine.kind.ordinal(),machine.energyPriority(),machine.redstoneMode,limits,priorities,modes,sample.stored(),sample.capacity(),produced,consumed,transferred,deficit,ticks,sample.nodes(),sample.devices()));
        previous=sample;previousAt=now;
    }
    public void receive(EnergyControlNetwork.View message){if(message.menu()==containerId&&message.pos().equals(pos))view=message;}
}
