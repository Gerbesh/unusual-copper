package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.network.ArmorNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.*;

/** Configures only the sender's currently worn physical items on the server thread. */
public final class ArmorControlServer {
    public static final List<EquipmentSlot> PIECES=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final Set<ServerPlayer> PENDING=Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Session {
        UUID nonce=UUID.randomUUID();long revision;int lastRequest=Integer.MIN_VALUE,lastAction=Integer.MIN_VALUE;
        final ItemStack[] gear=new ItemStack[4];final String[] configuration=new String[4];
        void refresh(ServerPlayer p){
            boolean changed=false;
            for(int i=0;i<4;i++){var item=p.getItemBySlot(PIECES.get(i));var state=ArmorData.configuration(item);
                if(gear[i]!=item||!state.equals(configuration[i]))changed=true;
                gear[i]=item;configuration[i]=state;}
            if(changed){nonce=UUID.randomUUID();revision++;}
        }
    }
    private ArmorControlServer(){}
    public static void request(ServerPlayer player){
        var session=SESSIONS.computeIfAbsent(player,p->new Session());
        if(session.lastRequest==player.tickCount)return;session.lastRequest=player.tickCount;
        reply(player,session,ArmorNetwork.Result.OK);
    }
    public static void action(ServerPlayer player,ArmorNetwork.Action action){
        var session=SESSIONS.computeIfAbsent(player,p->new Session());session.refresh(player);
        if(session.lastAction==player.tickCount)return;session.lastAction=player.tickCount;
        if(!ArmorControlPolicy.current(session.nonce,session.revision,action.session(),action.revision())){reply(player,session,ArmorNetwork.Result.STALE);return;}
        if(player.isSpectator()||!player.isAlive()||!ArmorControlPolicy.valid(action.operation(),action.piece(),action.module(),action.value(),ArmorModuleType.values().length)){
            reply(player,session,ArmorNetwork.Result.INVALID);return;
        }
        var item=player.getItemBySlot(PIECES.get(action.piece()));
        if(!(item.getItem() instanceof ProgrammableArmorItem)||!ArmorData.upgradeSafe(item)){reply(player,session,ArmorNetwork.Result.INVALID);return;}
        boolean changed;
        if(action.operation()==ArmorControlPolicy.Operation.PROFILE)changed=ArmorData.profile(item,action.value());
        else{
            var module=ArmorModuleType.values()[action.module()];var chest=player.getItemBySlot(EquipmentSlot.CHEST);int profile=ArmorData.profile(chest);
            if(!ArmorData.modules(item).contains(module)){reply(player,session,ArmorNetwork.Result.INVALID);return;}
            var old=ArmorData.settings(item,module,profile);
            var next=switch(action.operation()){
                case ENABLE -> new ArmorModuleSettings(action.value()!=0,old.trigger(),old.minimumPercent(),old.armed(),old.priority());
                case TRIGGER -> new ArmorModuleSettings(old.enabled(),ArmorModuleSettings.Trigger.values()[action.value()],old.minimumPercent(),old.armed(),old.priority());
                case MINIMUM -> new ArmorModuleSettings(old.enabled(),old.trigger(),action.value(),old.armed(),old.priority());
                case ARM -> new ArmorModuleSettings(old.enabled(),old.trigger(),old.minimumPercent(),action.value()!=0,old.priority());
                case PRIORITY -> new ArmorModuleSettings(old.enabled(),old.trigger(),old.minimumPercent(),old.armed(),action.value());
                default -> old;
            };
            changed=ArmorData.settings(item,module,profile,next);
        }
        if(changed){session.revision++;session.refresh(player);player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
        reply(player,session,changed?ArmorNetwork.Result.OK:ArmorNetwork.Result.INVALID);
    }
    private static void reply(ServerPlayer player,Session session,ArmorNetwork.Result result){session.refresh(player);ArmorNetwork.send(player,snapshot(player,session,result));}
    private static ArmorNetwork.Snapshot snapshot(ServerPlayer player,Session session,ArmorNetwork.Result result){
        var chest=player.getItemBySlot(EquipmentSlot.CHEST);int profile=ArmorData.profile(chest);var battery=ArmorData.battery(chest);
        var pieces=new ArrayList<ArmorNetwork.Piece>(4);
        for(var slot:PIECES){var item=player.getItemBySlot(slot);if(item.getItem() instanceof ProgrammableArmorItem armor){
            var rows=ArmorData.modules(item).stream().map(module->new ArmorNetwork.Row(module,ArmorData.settings(item,module,profile))).toList();
            pieces.add(new ArmorNetwork.Piece(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),armor.tier,ArmorData.slots(armor),rows));
        }else pieces.add(new ArmorNetwork.Piece("",0,0,List.of()));}
        return new ArmorNetwork.Snapshot(session.nonce,session.revision,ArmorRuntime.completeSet(player),profile,CopperBatteryItem.stored(battery),CopperBatteryItem.capacity(battery),
                ArmorTelemetry.perSecond(player),ArmorTelemetry.recent(player),ArmorTelemetry.limited(player),ArmorRuntime.conditions(player),
                ArmorRuntime.flightStatus(player),pieces,result);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player)||player.tickCount%10!=0)return;
        PENDING.add(player);
    }
    /** Publish settled balances, after the LOW-priority armor payment batch. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void publish(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(var player:PENDING){
            if(player.isRemoved())continue;
            boolean wearing=PIECES.stream().anyMatch(slot->player.getItemBySlot(slot).getItem() instanceof ProgrammableArmorItem);
            if(wearing)reply(player,SESSIONS.computeIfAbsent(player,p->new Session()),ArmorNetwork.Result.OK);
            else{var session=SESSIONS.remove(player);if(session!=null)reply(player,session,ArmorNetwork.Result.OK);}
        }
        PENDING.clear();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){SESSIONS.remove(player);PENDING.remove(player);}}
}
