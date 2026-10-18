package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Main-thread payment batches. Reservation NBT stays with the physical nested cell. */
public final class ArmorPayments {
    private static final Map<ServerPlayer,State> STATES=new WeakHashMap<>();
    private static final ArmorEventJournal EVENTS=new ArmorEventJournal();
    private static final Map<ItemStack,UUID> DIRTY=new IdentityHashMap<>();
    private static final Set<ServerPlayer> OWNERS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Debit> DEBITS=new ArrayList<>();
    private static final class Debit {
        final ServerPlayer player;final ItemStack chest;final UUID batch;final ArmorModuleType module;
        final int amount;final IntSupplier confirmed;final Runnable after;boolean released;
        Debit(ServerPlayer p,ItemStack c,UUID b,ArmorModuleType m,int a,IntSupplier receipt,Runnable fx){
            player=p;chest=c;batch=b;module=m;amount=a;confirmed=receipt;after=fx;
        }
    }
    private static final class Grant {
        final ItemStack chest,piece;final UUID batch;final int profile,level,cost;
        final ArmorModuleType module;final ArmorModuleSettings settings;
        final ArmorTickReceipt receipt=new ArmorTickReceipt();Debit debit;
        Grant(ServerPlayer p,ItemStack c,ArmorModuleType m,int charge,UUID b){
            chest=c;piece=p.getItemBySlot(m.slot);batch=b;module=m;cost=charge;
            profile=ArmorData.profile(c);level=ArmorData.moduleLevel(piece,m);settings=ArmorData.settings(piece,m,profile);
        }
        boolean matches(ServerPlayer p,ItemStack c){
            return p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&ArmorRuntime.completeSet(p)
                    &&chest==c&&chest==p.getItemBySlot(EquipmentSlot.CHEST)&&piece==p.getItemBySlot(module.slot)&&!debit.released
                    &&batch.equals(ArmorData.reservationBatch(c))&&ArmorData.upgradeSafe(c)&&ArmorData.upgradeSafe(piece)
                    &&profile==ArmorData.profile(c)&&level==ArmorData.moduleLevel(piece,module)&&cost==module.cue(level)
                    &&settings.equals(ArmorData.settings(piece,module,profile));
        }
    }
    private static final class State {
        final int tick;boolean continuous;
        final EnumMap<ArmorModuleType,ArmorPowerScheduler.Demand> power=new EnumMap<>(ArmorModuleType.class);
        final EnumMap<ArmorModuleType,Grant> grants=new EnumMap<>(ArmorModuleType.class);
        State(int tick){this.tick=tick;}
    }
    private static State state(ServerPlayer p){
        int tick=p.server.getTickCount();var current=STATES.get(p);
        if(current==null||current.tick!=tick){current=new State(tick);STATES.put(p,current);}return current;
    }
    private ArmorPayments(){}
    public static boolean beginContinuous(ServerPlayer p){var s=state(p);if(s.continuous)return false;s.continuous=true;return true;}
    public static List<ArmorPowerScheduler.Demand> committedPower(ServerPlayer p){return List.copyOf(state(p).power.values());}
    private static void track(ServerPlayer p,ItemStack chest,UUID batch){DIRTY.put(chest,Objects.requireNonNull(batch));OWNERS.add(p);}
    private static Grant grant(ServerPlayer p,ItemStack chest,ArmorModuleType module,int cost,UUID batch){
        var g=new Grant(p,chest,module,cost,batch);
        g.debit=new Debit(p,chest,batch,module,cost,()->g.receipt.confirmed(cost),g.receipt::after);
        DEBITS.add(g.debit);state(p).grants.put(module,g);
        state(p).power.put(module,new ArmorPowerScheduler.Demand(module,g.settings.priority(),cost));return g;
    }
    /** Reserve the complete plan before the first effect. Unrun effects are refunded on exception. */
    public static boolean execute(ServerPlayer p,ItemStack chest,ArmorPowerScheduler.Plan plan,
                                  Map<ArmorModuleType,Runnable> actions){
        if(plan.cost()<=0||!ArmorData.reserve(chest,plan.cost()))return false;
        UUID batch=ArmorData.reservationBatch(chest);track(p,chest,batch);int untracked=plan.cost();
        var allocated=new ArrayList<Grant>();
        try{
            // Publish the complete allocation before invoking code which can raise more events.
            for(var module:plan.accepted()){
                int charge=plan.charges().get(module);allocated.add(grant(p,chest,module,charge,batch));untracked-=charge;
            }
            for(var g:allocated)if(g.matches(p,chest)){g.receipt.applied();actions.get(g.module).run();}
        }finally{
            if(untracked>0)ArmorData.release(chest,untracked,batch);
            for(var g:allocated)if(g.receipt.untouched()&&ArmorData.release(chest,g.cost,batch)){
                g.debit.released=true;state(p).grants.remove(g.module);state(p).power.remove(g.module);
            }
        }
        return true;
    }
    /** Reuse an allocation made by an earlier event; never ask the scheduler for it twice. */
    public static void applyFunded(ServerPlayer p,ItemStack chest,Map<ArmorModuleType,Runnable> actions){
        for(var entry:new ArrayList<>(actions.entrySet())){
            var g=state(p).grants.get(entry.getKey());if(g==null)continue;
            if(g.matches(p,chest)){g.receipt.applied();entry.getValue().run();}
            actions.remove(entry.getKey());
        }
    }
    /** Events pay independently of continuous power quotas, but share the same durable debit. */
    public static boolean event(ServerPlayer p,ItemStack chest,ArmorModuleType module,Object event,int amount,BooleanSupplier applied){
        Objects.requireNonNull(applied);
        return eventQuoted(p,chest,module,event,amount,()->applied.getAsBoolean()?amount:0,null);
    }
    /** Reserve the maximum before the operation; settle only its confirmed own contribution. */
    public static boolean eventQuoted(ServerPlayer p,ItemStack chest,ArmorModuleType module,Object event,int amount,IntSupplier confirmed,Runnable after){
        Objects.requireNonNull(confirmed);
        if(!EVENTS.reserve(p,event,amount,cost->ArmorData.reserve(chest,cost)))return false;
        UUID batch=ArmorData.reservationBatch(chest);track(p,chest,batch);
        DEBITS.add(new Debit(p,chest,batch,module,amount,confirmed,after));return true;
    }
    /** One level-priced allocation per tick, shared by early events and the continuous plan. */
    public static boolean tickEvent(ServerPlayer p,ItemStack chest,ArmorModuleType module,Object event,BooleanSupplier applied,Runnable after){
        Objects.requireNonNull(applied);var s=state(p);
        var piece=p.getItemBySlot(module.slot);int profile=ArmorData.profile(chest);
        int level=ArmorData.moduleLevel(piece,module),cost=module.cue(level);
        var settings=ArmorData.settings(piece,module,profile);
        if(EVENTS.contains(event))return false;
        var existing=s.grants.get(module);
        if(existing!=null){
            if(!existing.matches(p,chest)||!existing.receipt.add(applied,after))return false;
            EVENTS.markQuery(event);return true;
        }
        var request=new ArmorPowerScheduler.Demand(module,settings.priority(),cost);
        var plan=ArmorPowerScheduler.plan(List.of(request),CopperBatteryItem.stored(ArmorData.battery(chest)),committedPower(p));
        ArmorTelemetry.updateLimited(p,plan.limited(),List.of(module));
        if(plan.accepted().isEmpty())return false;
        if(!EVENTS.reserve(p,event,cost,amount->ArmorData.reserve(chest,amount)))return false;
        UUID batch=ArmorData.reservationBatch(chest);track(p,chest,batch);
        return grant(p,chest,module,cost,batch).receipt.add(applied,after);
    }
    public static boolean mining(ServerPlayer p,ItemStack chest,PlayerEvent.BreakSpeed event,float before){
        return tickEvent(p,chest,ArmorModuleType.MINING_SERVO,event,()->!event.isCanceled()&&event.getNewSpeed()>before,null);
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void settle(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)settleAll();}
    @SubscribeEvent public static void stop(ServerStoppingEvent event){settleAll();STATES.clear();EVENTS.clear();}
    /** Events cancelled later in the hook chain release their reservation before the charge write. */
    private static void settleAll(){
        try{
            for(var debit:DEBITS){
                if(debit.released)continue;
                int confirmed=debit.amount;boolean verified=false;
                try{int value=debit.confirmed.getAsInt();confirmed=ArmorReceiptPolicy.charged(debit.amount,value);verified=value>=0&&value<=debit.amount;}
                catch(RuntimeException invalidReceipt){UnusualCopper.LOGGER.debug("Armor receipt failed; keeping its authorized charge",invalidReceipt);}
                int charged=confirmed;
                int refund=debit.amount-charged;
                if(refund>0&&!ArmorData.release(debit.chest,refund,debit.batch)){
                    charged=debit.amount;
                    UnusualCopper.LOGGER.debug("Armor reservation was externally settled before refund");
                }
                if(charged>0){
                    ArmorTelemetry.record(debit.player,debit.module,charged);
                    if(verified&&confirmed>0&&debit.after!=null)try{debit.after.run();}
                    catch(RuntimeException visualFailure){UnusualCopper.LOGGER.debug("Armor post-payment effect failed",visualFailure);}
                }
            }
            for(var entry:DIRTY.entrySet())ArmorData.settle(entry.getKey(),entry.getValue());
            for(var player:OWNERS)if(!player.isRemoved()){
                player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            }
        }finally{DEBITS.clear();DIRTY.clear();OWNERS.clear();STATES.clear();EVENTS.endBatch();}
    }
}
