package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Loaded entities only. Event updates and rotating section buckets avoid unbounded world queries. */
public final class ItemEntityIndex {
    private static final Map<ServerLevel, Map<Long, LinkedHashMap<UUID, WeakReference<ItemEntity>>>> LEVELS = new IdentityHashMap<>();
    private ItemEntityIndex() {}
    private static void add(ServerLevel level, long section, ItemEntity item) {
        LEVELS.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(item.getUUID(), new WeakReference<>(item));
    }
    private static void remove(ServerLevel level, long section, UUID id) {
        var sections = LEVELS.get(level); if (sections == null) return;
        var bucket = sections.get(section); if (bucket == null) return;
        bucket.remove(id); if (bucket.isEmpty()) sections.remove(section);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) add(level, SectionPos.asLong(item.blockPosition()), item);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) remove(level, SectionPos.asLong(item.blockPosition()), item.getUUID());
    }
    @SubscribeEvent public static void move(EntityEvent.EnteringSection event) {
        if (event.getEntity() instanceof ItemEntity item && item.level() instanceof ServerLevel level) {
            var sections = LEVELS.get(level); var bucket = sections == null ? null : sections.get(event.getPackedOldPos());
            // Constructor/setPos can emit this before EntityJoinLevelEvent accepts the entity.
            if (bucket == null || !bucket.containsKey(item.getUUID())) return;
            remove(level, event.getPackedOldPos(), item.getUUID()); add(level, event.getPackedNewPos(), item);
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level); }
    public static List<ItemEntity> sample(ServerLevel level, CopperMachineBlockEntity machine, int limit) {
        var area = machine.workArea(); var sections = LEVELS.get(level);
        if (area == null || sections == null) return List.of();
        var keys = new ArrayList<Long>(8);
        for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4; x++)
            for (int y = area.min().getY() >> 4; y <= area.max().getY() >> 4; y++)
                for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) keys.add(SectionPos.asLong(x, y, z));
        var result = new ArrayList<ItemEntity>(limit);
        for (int visited = 0; visited < keys.size() && result.size() < limit; visited++) {
            long key = keys.get(Math.floorMod(machine.scanCursor++, keys.size())); var bucket = sections.get(key); if (bucket == null) continue;
            int remaining = Math.min(bucket.size(), limit - result.size());
            for (int i = 0; i < remaining; i++) {
                var iterator = bucket.entrySet().iterator(); var entry = iterator.next(); iterator.remove();
                var item = entry.getValue().get();
                // Count retired entries against this wave too, so cleanup is bounded.
                result.add(item);
                if (item != null && item.isAlive() && SectionPos.asLong(item.blockPosition()) == key) bucket.put(entry.getKey(), entry.getValue());
            }
            if (bucket.isEmpty()) sections.remove(key);
        }
        return result;
    }
}
