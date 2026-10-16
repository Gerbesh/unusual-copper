package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.BrewerLogic;
import io.github.gerbesh.unusualcopper.network.BrewerNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.ItemKey;
import io.github.gerbesh.unusualcopper.storage.StableCount;
import io.github.gerbesh.unusualcopper.storage.StorageEndpoint;
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
    private BrewerNetwork.Settings preview;
    private List<BrewerNetwork.Key> visible=List.of();
    private long lastInspect=Long.MIN_VALUE;
    private final Map<net.minecraft.world.item.Item,StableCount> ingredientCounts=new LinkedHashMap<>();
    private List<net.minecraft.world.item.Item> wantedIngredients=List.of();
    private final Map<ItemKey,StableCount> observed=new LinkedHashMap<>();
    private List<ItemKey> wantedCounts=List.of();private int countCursor;
    private net.minecraft.core.BlockPos observedBinding;private net.minecraft.core.Direction observedSide;
    private void sampleCounts(){
        if(!Objects.equals(observedBinding,machine.boundInventory())||observedSide!=machine.boundSide()){observed.clear();ingredientCounts.clear();countCursor=0;observedBinding=machine.boundInventory();observedSide=machine.boundSide();}
        observed.keySet().retainAll(wantedCounts);
        var endpoint=machine.boundInventory()==null?null:machine.boundEndpoint((ServerLevel)machine.getLevel());
        // At most one network count per menu tick; the bridge retains its shared port budget.
        int size=wantedCounts.size()+wantedIngredients.size();if(size==0)return;int cursor=Math.floorMod(countCursor++,size);
        ingredientCounts.keySet().retainAll(wantedIngredients);
        StableCount value;StorageEndpoint.Count count;
        if(cursor<wantedIngredients.size()){var item=wantedIngredients.get(cursor);value=ingredientCounts.computeIfAbsent(item,k->new StableCount());count=endpoint==null?new StorageEndpoint.Count(0,true):endpoint.countItem(item,stack->true);}
        else{var key=wantedCounts.get(cursor-wantedIngredients.size());value=observed.computeIfAbsent(key,k->new StableCount());count=endpoint==null?new StorageEndpoint.Count(0,true):endpoint.count(key);}
        value.accept(count.amount(),count.complete());
    }
    public BrewerMenu(int id,Inventory inventory,FriendlyByteBuf b){this(id,inventory,null,b.readBlockPos());}
    public BrewerMenu(int id,Inventory inventory,CopperMachineBlockEntity machine){this(id,inventory,machine,machine.getBlockPos());}
    private BrewerMenu(int id,Inventory inventory,CopperMachineBlockEntity machine,BlockPos pos){super(ModContent.BREWER_MENU.get(),id);this.machine=machine;this.pos=pos;viewer=inventory.player;}
    public static void open(ServerPlayer player,CopperMachineBlockEntity machine){if(machine.brewer!=null&&machine.canAccess(player))NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->new BrewerMenu(id,inv,machine),machine.getDisplayName()),machine.getBlockPos());}
    @Override public boolean stillValid(Player player){return machine==null||player==viewer&&player.level()==machine.getLevel()&&!machine.isRemoved()&&machine.brewer!=null&&player.level().hasChunkAt(pos)&&player.level().getBlockEntity(pos)==machine&&machine.canAccess(player)&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&player.level() instanceof ServerLevel level&&ProtectionPolicy.allowed(level,pos,machine.owner());}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    public void receive(BrewerNetwork.Reply reply){if(reply.menu()==containerId&&reply.pos().equals(pos)){snapshot=reply;if(!reply.options().isEmpty())options=reply.options();serial++;}}
    public boolean inspect(BrewerNetwork.Inspect request,ServerPlayer player){
        if(machine==null||!stillValid(player)||!machine.runtimeIdentity.equals(request.runtime()))return false;
        long now=player.level().getGameTime();if(lastInspect!=Long.MIN_VALUE&&now-lastInspect<4)return false;lastInspect=now;
        var p=request.preview();if(p.count()<1||p.count()>4096||PotionPlans.find(p.potion(),p.vessel())==null||request.visible().size()>12)return false;
        for(var k:request.visible())if(PotionPlans.find(k.potion(),k.vessel())==null)return false;
        preview=p;visible=request.visible();return true;
    }
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
        int batch=s.job==null?(s.requestedBatch>0?s.requestedBatch:machine.status()==MachineStatus.STOCK_REACHED?0:Math.min(3,s.pending)):s.job.batch,stage=s.job==null?0:s.job.stage;
        var shown=preview==null?new BrewerNetwork.Settings(s.potion,s.vessel,Math.max(1,s.pending),s.maintain,s.priority):preview;
        var shownPlan=PotionPlans.find(shown.potion(),shown.vessel());var requirements=new LinkedHashMap<Item,Integer>();
        if(shownPlan!=null){requirements.put(Items.GLASS_BOTTLE,Math.min(3,shown.count()));for(var step:shownPlan.steps())requirements.merge(step.ingredient().getItem(),1,Integer::sum);}
        var counts=new HashMap<Item,Integer>();for(int i=0;i<2;i++){var stack=machine.inventory.getStackInSlot(i);if(!stack.isEmpty())counts.merge(stack.getItem(),stack.getCount(),Integer::sum);}
        boolean complete=true;var endpoint=machine.boundInventory()==null||machine.getBlockPos().distSqr(machine.boundInventory())>32*32?null:machine.boundEndpoint((ServerLevel)player.level());
        var requests=new LinkedHashSet<ItemKey>();var needs=new ArrayList<BrewerNetwork.Need>();
        for(var requirement:requirements.entrySet()){var item=requirement.getKey();var value=ingredientCounts.get(item);long external=endpoint==null?0:value==null?-1:value.amount();boolean known=endpoint==null||value!=null&&value.current();complete&=known;
            int local=counts.getOrDefault(item,0);int available=external<0?(local==0?-1:local):(int)Math.min(1_000_000,external+local);needs.add(new BrewerNetwork.Need(new ItemStack(item),requirement.getValue(),available));}
        var catalogue=catalogueSent?List.<BrewerNetwork.Option>of():PotionPlans.catalogue().stream().map(p->new BrewerNetwork.Option(p,BrewerLogic.costEach(p))).toList();
        var keys=new LinkedHashSet<BrewerNetwork.Key>(visible);keys.add(new BrewerNetwork.Key(shown.potion(),shown.vessel()));var stocks=new ArrayList<BrewerNetwork.Stock>();
        for(var key:keys){var p=PotionPlans.find(key.potion(),key.vessel());if(p==null)continue;var itemKey=ItemKey.of(p.output());long amount=0;boolean known=endpoint==null;
            requests.add(itemKey);if(endpoint!=null){var value=observed.get(itemKey);amount=value==null?-1:value.amount();known=value!=null&&value.current();}
            long local=0;for(int slot=2;slot<machine.inventory.getSlots();slot++){var stack=machine.inventory.getStackInSlot(slot);if(itemKey.matches(stack))local+=stack.getCount();}if(amount>=0)amount+=local;else if(local>0)amount=local;
            stocks.add(new BrewerNetwork.Stock(key,amount,known));
        }
        wantedCounts=List.copyOf(requests);wantedIngredients=List.copyOf(requirements.keySet());BrewerNetwork.reply(player,new BrewerNetwork.Reply(containerId,pos,machine.runtimeIdentity,new BrewerNetwork.Settings(s.potion,s.vessel,s.pending,s.maintain,s.priority),result,catalogue,needs,complete,machine.energy.stored(),machine.status().ordinal(),stage,plan==null?0:plan.stages(),batch,s.job!=null,s.job==null?null:new BrewerNetwork.Option(s.job.plan,s.job.costEach),shown,stocks,endpoint!=null&&endpoint.isAvailable()));catalogueSent=true;lastReply=player.level().getGameTime();
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine!=null&&viewer instanceof ServerPlayer server&&stillValid(server)){sampleCounts();if(lastReply==Long.MIN_VALUE||server.level().getGameTime()-lastReply>=10)reply(server,0);}}
}
