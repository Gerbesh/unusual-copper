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

/** Main-thread payment batches. Reservation NBT stays with the physical nested cell. */
public final class ArmorPayments {
    private static final Map<ServerPlayer,State> STATES=new WeakHashMap<>();
    private static final ArmorEventJournal EVENTS=new ArmorEventJournal();
    private static final Map<ItemStack,UUID> DIRTY=new IdentityHashMap<>();
    private static final Set<ServerPlayer> OWNERS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Debit> DEBITS=new ArrayList<>();
    private record Debit(ServerPlayer player,ItemStack chest,UUID batch,ArmorModuleType module,int amount,BooleanSupplier applied){}
    private static final class State {
        final int tick;boolean continuous;
        final EnumMap<ArmorModuleType,ArmorPowerScheduler.Demand> power=new EnumMap<>(ArmorModuleType.class);
        ItemStack miningChest,miningLegs;ArmorModuleSettings miningSettings;int miningProfile;
        final List<BooleanSupplier> miningApplications=new ArrayList<>();
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
    /** Reserve the complete plan before the first effect. Unrun effects are refunded on exception. */
    public static boolean execute(ServerPlayer p,ItemStack chest,ArmorPowerScheduler.Plan plan,
                                  Map<ArmorModuleType,Runnable> actions){
        if(plan.cost()<=0||!ArmorData.reserve(chest,plan.cost()))return false;
        UUID batch=ArmorData.reservationBatch(chest);track(p,chest,batch);int unrun=plan.cost();
        try{
            for(var module:plan.accepted()){
                int charge=plan.charges().get(module);actions.get(module).run();unrun-=charge;
                state(p).power.put(module,new ArmorPowerScheduler.Demand(module,
                        ArmorData.settings(p.getItemBySlot(module.slot),module,ArmorData.profile(chest)).priority(),charge));
                DEBITS.add(new Debit(p,chest,batch,module,charge,()->true));
            }
        }finally{if(unrun>0)ArmorData.release(chest,unrun,batch);}
        return true;
    }
    /** Events pay independently of continuous power quotas, but share the same durable debit. */
    public static boolean event(ServerPlayer p,ItemStack chest,ArmorModuleType module,Object event,int amount,BooleanSupplier applied){
        if(!EVENTS.reserve(p,event,amount,cost->ArmorData.reserve(chest,cost)))return false;
        UUID batch=ArmorData.reservationBatch(chest);track(p,chest,batch);
        DEBITS.add(new Debit(p,chest,batch,module,amount,applied));return true;
    }
    /** One 8 CuE mining allocation per server tick, even with repeated BreakSpeed queries. */
    public static boolean mining(ServerPlayer p,ItemStack chest,PlayerEvent.BreakSpeed event,float before){
        var s=state(p);var module=ArmorModuleType.MINING_SERVO;
        var piece=p.getItemBySlot(EquipmentSlot.LEGS);int profile=ArmorData.profile(chest);
        var settings=ArmorData.settings(piece,module,profile);
        if(EVENTS.contains(event))return false;
        if(s.miningChest!=null){
            if(s.miningChest!=chest||s.miningLegs!=piece||s.miningProfile!=profile||!s.miningSettings.equals(settings))return false;
            if(s.miningApplications.size()>=64)return false;
            EVENTS.markQuery(event);
            s.miningApplications.add(()->!event.isCanceled()&&event.getNewSpeed()>before);return true;
        }
        var request=new ArmorPowerScheduler.Demand(module,settings.priority(),module.cue);
        var plan=ArmorPowerScheduler.plan(List.of(request),CopperBatteryItem.stored(ArmorData.battery(chest)),committedPower(p));
        ArmorTelemetry.updateLimited(p,plan.limited(),List.of(module));
        if(plan.accepted().isEmpty())return false;
        if(!event(p,chest,module,event,module.cue,()->s.miningApplications.stream().anyMatch(BooleanSupplier::getAsBoolean)))return false;
        s.miningChest=chest;s.miningLegs=piece;s.miningProfile=profile;s.miningSettings=settings;s.power.put(module,request);
        s.miningApplications.add(()->!event.isCanceled()&&event.getNewSpeed()>before);return true;
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void settle(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)settleAll();}
    @SubscribeEvent public static void stop(ServerStoppingEvent event){settleAll();STATES.clear();EVENTS.clear();}
    /** Events cancelled later in the hook chain release their reservation before the charge write. */
    private static void settleAll(){
        try{
            for(var debit:DEBITS){
                if(!debit.applied.getAsBoolean()){
                    if(!ArmorData.release(debit.chest,debit.amount,debit.batch))
                        UnusualCopper.LOGGER.debug("Armor reservation was externally settled before cancellation refund");
                }else ArmorTelemetry.record(debit.player,debit.module,debit.amount);
            }
            for(var entry:DIRTY.entrySet())ArmorData.settle(entry.getKey(),entry.getValue());
            for(var player:OWNERS)if(!player.isRemoved()){
                player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            }
        }finally{DEBITS.clear();DIRTY.clear();OWNERS.clear();STATES.clear();EVENTS.endBatch();}
    }
}
