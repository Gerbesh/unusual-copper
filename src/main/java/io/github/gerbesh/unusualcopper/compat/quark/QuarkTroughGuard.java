package io.github.gerbesh.unusualcopper.compat.quark;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional Quark 4.0-462 integration. Reads Quark's own range/limit; does not create love or consume food. */
public final class QuarkTroughGuard {
    private record Api(Field range,Field maxAnimals,Field cooldown,Object none) {}
    private record Pause(long until,int limit,double range) {}
    // A refused meal returns NONE, so Quark does not start its eating cooldown.
    // Cache only full-population refusals to avoid repeating the spatial query for every hungry animal.
    private static final Map<BlockEntity,Pause> FULL=new WeakHashMap<>();
    private static Api api;
    private static boolean initialized,failed;

    private static synchronized Api api() {
        if(initialized)return api;
        initialized=true;
        var quark=ModList.get().getModContainerById("quark");
        if(quark.isEmpty())return null;
        String version=quark.get().getModInfo().getVersion().toString();
        if(!version.equals("4.0-462")) {
            UnusualCopper.LOGGER.warn("Quark trough guard supports 4.0-462; leaving version {} unchanged",version);
            return null;
        }
        try {
            var module=Class.forName("org.violetmoon.quark.content.automation.module.FeedingTroughModule");
            var result=Class.forName("org.violetmoon.quark.content.automation.block.be.FeedingTroughBlockEntity$FeedResult");
            api=new Api(module.getField("range"),module.getField("maxAnimals"),module.getField("cooldown"),result.getField("NONE").get(null));
        }catch(ReflectiveOperationException|LinkageError incompatible) {
            UnusualCopper.LOGGER.error("Cannot initialize Quark trough guard; original behavior retained",incompatible);
        }
        return api;
    }
    /** Null allows the original method; NONE cancels before its first side effect. */
    public static Object deniedResult(BlockEntity trough,Animal animal) {
        var current=api();if(current==null)return null;
        if(!(trough.getLevel() instanceof ServerLevel level) || animal.level()!=level)return current.none();
        try {
            int limit=current.maxAnimals().getInt(null);
            if(!TroughFeedingPolicy.permits(0,limit,animal.getAge(),animal.canFallInLove()))return current.none();
            double range=current.range().getDouble(null);
            if(!Double.isFinite(range)||range<0)return current.none();
            var pause=FULL.get(trough);long now=level.getGameTime();
            if(pause!=null && pause.limit()==limit && pause.range()==range && now<pause.until())return current.none();
            FULL.remove(trough);
            int population=level.getEntitiesOfClass(Animal.class,new AABB(trough.getBlockPos()).inflate(range)).size();
            if(population>=limit) {
                FULL.put(trough,new Pause(now+Math.max(1,current.cooldown().getInt(null)),limit,range));
                return current.none();
            }
            return TroughFeedingPolicy.permits(population,limit,animal.getAge(),animal.canFallInLove())?null:current.none();
        }catch(ReflectiveOperationException|RuntimeException failure) {
            if(!failed)UnusualCopper.LOGGER.error("Quark trough guard failed; feeding paused to preserve food",failure);
            failed=true;return current.none();
        }
    }
    private QuarkTroughGuard() {}
}
