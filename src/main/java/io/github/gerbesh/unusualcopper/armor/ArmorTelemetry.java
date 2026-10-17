package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Main-server-thread, owner-only display data; never serialized into armor or broadcast nearby. */
public final class ArmorTelemetry {
    private static final Map<ServerPlayer,ArmorEnergyHistory> SAMPLES=new WeakHashMap<>();
    private record Limited(int tick,long mask) {}
    private static final Map<ServerPlayer,Limited> LIMITED=new WeakHashMap<>();
    private ArmorTelemetry(){}
    public static void record(ServerPlayer player,ArmorModuleType module,int amount){
        SAMPLES.computeIfAbsent(player,p->new ArmorEnergyHistory()).record(player.tickCount,amount,module.ordinal());
    }
    public static int perSecond(ServerPlayer player){var sample=SAMPLES.get(player);return sample==null?0:sample.perSecond(player.tickCount);}
    public static long recent(ServerPlayer player){var sample=SAMPLES.get(player);return sample==null?0:sample.active(player.tickCount);}
    /** Recent server-side refusal, never client asserted or stored on the armor stack. */
    public static void limited(ServerPlayer player,java.util.List<ArmorModuleType> modules) {
        long mask=0L;
        for(var module:modules)mask|=1L<<module.ordinal();
        if(mask==0)LIMITED.remove(player);
        else LIMITED.put(player,new Limited(player.tickCount,mask));
    }
    public static long limited(ServerPlayer player) {
        var value=LIMITED.get(player);
        return value==null||player.tickCount<value.tick()||player.tickCount-value.tick()>12?0:value.mask();
    }
    public static void clear(ServerPlayer player){SAMPLES.remove(player);LIMITED.remove(player);}
}
