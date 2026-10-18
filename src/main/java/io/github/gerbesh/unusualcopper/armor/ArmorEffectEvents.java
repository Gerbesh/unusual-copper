package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.network.ArmorNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Disposable public body effects; never sends charge, health, inventory or private settings. */
public final class ArmorEffectEvents {
    public enum Kind { THERMAL,HEAL,STRENGTH }
    public record Burst(ResourceLocation dimension,int entity,Kind kind,int level){
        public Burst {
            Objects.requireNonNull(dimension);Objects.requireNonNull(kind);
            if(dimension.toString().length()>128||entity<0||level<1||level>(kind==Kind.THERMAL?1:2))throw new IllegalArgumentException("Armor effect");
        }
        public static void encode(Burst v,FriendlyByteBuf b){b.writeUtf(v.dimension.toString(),128);b.writeVarInt(v.entity);b.writeEnum(v.kind);b.writeVarInt(v.level);}
        public static Burst decode(FriendlyByteBuf b){return new Burst(new ResourceLocation(b.readUtf(128)),b.readVarInt(),b.readEnum(Kind.class),b.readVarInt());}
    }
    private static final Map<ServerPlayer,EnumMap<Kind,Integer>> PENDING=new IdentityHashMap<>();
    public static void emit(ServerPlayer p,Kind kind,int level){
        if(p.server.getPlayerList().getPlayer(p.getUUID())!=p||p.isRemoved())return;
        PENDING.computeIfAbsent(p,key->new EnumMap<>(Kind.class)).merge(kind,level,Math::max);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        try{for(var entry:PENDING.entrySet())if(!entry.getKey().isRemoved())for(var fx:entry.getValue().entrySet())
            ArmorNetwork.effect(entry.getKey(),new Burst(entry.getKey().level().dimension().location(),entry.getKey().getId(),fx.getKey(),fx.getValue()));}
        finally{PENDING.clear();}
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){PENDING.clear();}
    private ArmorEffectEvents(){}
}
