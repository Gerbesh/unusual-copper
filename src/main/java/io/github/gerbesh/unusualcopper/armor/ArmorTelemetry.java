package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Main-server-thread, owner-only display data; never serialized into armor or broadcast nearby. */
public final class ArmorTelemetry {
    private static final Map<ServerPlayer,ArmorEnergyHistory> SAMPLES=new WeakHashMap<>();
    private static final Map<ServerPlayer,java.util.EnumMap<ArmorModuleType,Integer>> LIMITED=new WeakHashMap<>();
    private ArmorTelemetry(){}
    public static void record(ServerPlayer player,ArmorModuleType module,int amount){
        SAMPLES.computeIfAbsent(player,p->new ArmorEnergyHistory()).record(player.tickCount,amount,module.ordinal());
    }
    public static int perSecond(ServerPlayer player){var sample=SAMPLES.get(player);return sample==null?0:sample.perSecond(player.tickCount);}
    public static long recent(ServerPlayer player){var sample=SAMPLES.get(player);return sample==null?0:sample.active(player.tickCount);}
    /** Recent server-side refusal, never client asserted or stored on the armor stack. */
    public static void limited(ServerPlayer player,java.util.List<ArmorModuleType> modules) {
        LIMITED.remove(player);updateLimited(player,modules,java.util.List.of());
    }
    public static void updateLimited(ServerPlayer player,java.util.Collection<ArmorModuleType> denied,java.util.Collection<ArmorModuleType> evaluated){
        var ticks=LIMITED.computeIfAbsent(player,p->new java.util.EnumMap<>(ArmorModuleType.class));
        for(var module:evaluated)ticks.remove(module);
        for(var module:denied)ticks.put(module,player.tickCount);
        if(ticks.isEmpty())LIMITED.remove(player);
    }
    public static long limited(ServerPlayer player) {
        var value=LIMITED.get(player);
        if(value==null)return 0;long mask=0;
        for(var entry:value.entrySet())if(player.tickCount>=entry.getValue()&&player.tickCount-entry.getValue()<=12)mask|=1L<<entry.getKey().ordinal();
        return mask;
    }
    public static void clear(ServerPlayer player){SAMPLES.remove(player);LIMITED.remove(player);}
}
