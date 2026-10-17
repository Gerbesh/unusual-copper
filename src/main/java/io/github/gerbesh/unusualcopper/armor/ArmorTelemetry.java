package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Main-server-thread, owner-only display data; never serialized into armor or broadcast nearby. */
public final class ArmorTelemetry {
    private static final Map<ServerPlayer,ArmorEnergyHistory> SAMPLES=new WeakHashMap<>();
    private ArmorTelemetry(){}
    public static void record(ServerPlayer player,ArmorModuleType module,int amount){
        SAMPLES.computeIfAbsent(player,p->new ArmorEnergyHistory()).record(player.tickCount,amount,module.ordinal());
    }
    public static int perSecond(ServerPlayer player){var sample=SAMPLES.get(player);return sample==null?0:sample.perSecond(player.tickCount);}
    public static long recent(ServerPlayer player){var sample=SAMPLES.get(player);return sample==null?0:sample.active(player.tickCount);}
    public static void clear(ServerPlayer player){SAMPLES.remove(player);}
}
