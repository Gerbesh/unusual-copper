package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.BrewerLogic;
import io.github.gerbesh.unusualcopper.network.BrewerNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

public final class BrewerMenu extends AbstractContainerMenu {
    public final BlockPos pos;private final CopperMachineBlockEntity machine;private final Player viewer;
    private boolean catalogueSent;private long lastReply=Long.MIN_VALUE,lastConfigure=Long.MIN_VALUE;
    public BrewerNetwork.Reply snapshot;public List<BrewerNetwork.Option> options=List.of();public int serial;
    public BrewerMenu(int id,Inventory inventory,FriendlyByteBuf b){this(id,inventory,null,b.readBlockPos());}
    public BrewerMenu(int id,Inventory inventory,CopperMachineBlockEntity machine){this(id,inventory,machine,machine.getBlockPos());}
    private BrewerMenu(int id,Inventory inventory,CopperMachineBlockEntity machine,BlockPos pos){super(ModContent.BREWER_MENU.get(),id);this.machine=machine;this.pos=pos;viewer=inventory.player;}
    public static void open(ServerPlayer player,CopperMachineBlockEntity machine){if(machine.brewer!=null&&machine.canAccess(player))NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->new BrewerMenu(id,inv,machine),machine.getDisplayName()),machine.getBlockPos());}
    @Override public boolean stillValid(Player player){return machine==null||!machine.isRemoved()&&machine.brewer!=null&&player.level().hasChunkAt(pos)&&player.level().getBlockEntity(pos)==machine&&machine.canAccess(player)&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    public void receive(BrewerNetwork.Reply reply){if(reply.menu()==containerId&&reply.pos().equals(pos)){snapshot=reply;if(!reply.options().isEmpty())options=reply.options();serial++;}}
    public boolean configure(UUID runtime,BrewerNetwork.Settings settings,ServerPlayer player){
        if(machine==null||!stillValid(player)||!machine.runtimeIdentity.equals(runtime))return false;long now=player.level().getGameTime();
        if(lastConfigure!=Long.MIN_VALUE&&now-lastConfigure<2){reply(player,3);return false;}lastConfigure=now;
        if(machine.brewer.job!=null){reply(player,4);return false;}
        var plan=PotionPlans.find(settings.potion(),settings.vessel());
        if(plan==null||settings.count()<1||settings.count()>4096||settings.priority()<0||settings.priority()>9){reply(player,2);return false;}
        var s=machine.brewer;var oldReagent=machine.inventory.getStackInSlot(1);
        if(!oldReagent.isEmpty()&&(plan.steps().isEmpty()||!oldReagent.is(plan.steps().get(0).ingredient().getItem()))){s.recovery.retain(oldReagent);machine.inventory.setStackInSlot(1,ItemStack.EMPTY);}
        s.potion=settings.potion();s.vessel=settings.vessel();s.pending=settings.count();s.maintain=settings.maintain();s.priority=settings.priority();s.requestedBatch=0;s.nextWork=0;machine.setChanged();reply(player,1);return true;
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(machine==null||!stillValid(player)||!(player instanceof ServerPlayer server))return false;
        if(button==0){NetworkHooks.openScreen(server,machine,pos);return true;}
        if(button==1){BrewerLogic.cancel(machine);reply(server,1);return true;}return false;
    }
    private void reply(ServerPlayer player,int result){
        var s=machine.brewer;var plan=s.job==null?PotionPlans.find(s.potion,s.vessel):s.job.plan;
        int batch=s.job==null?(s.requestedBatch>0?s.requestedBatch:machine.status()==MachineStatus.STOCK_REACHED?0:Math.min(3,s.pending)):s.job.batch,stage=s.job==null?0:s.job.stage;var requirements=new LinkedHashMap<Item,Integer>();
        if(batch>0&&plan!=null){if(s.job==null)requirements.put(Items.GLASS_BOTTLE,batch);for(int i=stage;i<plan.steps().size();i++)requirements.merge(plan.steps().get(i).ingredient().getItem(),1,Integer::sum);}
        var counts=new HashMap<Item,Integer>();for(int i=0;i<2;i++){var stack=machine.inventory.getStackInSlot(i);if(!stack.isEmpty())counts.merge(stack.getItem(),stack.getCount(),Integer::sum);}
        boolean complete=true;var endpoint=machine.boundInventory()==null||machine.getBlockPos().distSqr(machine.boundInventory())>32*32?null:machine.boundEndpoint((ServerLevel)player.level());
        if(endpoint!=null){if(endpoint.isAvailable()){var view=endpoint.snapshot(stack->requirements.containsKey(stack.getItem()),256);complete=view.complete();for(var entry:view.entries())counts.merge(entry.key().stack(1).getItem(),Math.min(1_000_000,entry.count()),(a,b)->(int)Math.min(1_000_000,(long)a+b));}else complete=false;}
        var needs=new ArrayList<BrewerNetwork.Need>();requirements.forEach((item,n)->needs.add(new BrewerNetwork.Need(new ItemStack(item),n,counts.getOrDefault(item,0))));
        var catalogue=catalogueSent?List.<BrewerNetwork.Option>of():PotionPlans.catalogue().stream().map(p->new BrewerNetwork.Option(p,BrewerLogic.costEach(p))).toList();
        BrewerNetwork.reply(player,new BrewerNetwork.Reply(containerId,pos,machine.runtimeIdentity,new BrewerNetwork.Settings(s.potion,s.vessel,s.pending,s.maintain,s.priority),result,catalogue,needs,complete,machine.energy.stored(),machine.status().ordinal(),stage,plan==null?0:plan.stages(),batch,s.job!=null,s.job==null?null:new BrewerNetwork.Option(s.job.plan,s.job.costEach)));catalogueSent=true;lastReply=player.level().getGameTime();
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine!=null&&viewer instanceof ServerPlayer server&&stillValid(server)&&(lastReply==Long.MIN_VALUE||server.level().getGameTime()-lastReply>=10))reply(server,0);}
}
