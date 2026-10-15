package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Event-maintained loaded entities, with separate rotating buckets for items and mobs. */
public final class EntityIndexes {
    private static final Index<ItemEntity> ITEMS = new Index<>(ItemEntity.class);
    private static final Index<Mob> MOBS = new Index<>(Mob.class);
    private static final List<Index<?>> INDEXES = List.of(ITEMS, MOBS);
    private EntityIndexes() {}
    public static List<ItemEntity> items(ServerLevel level, CopperMachineBlockEntity machine, int limit) { return ITEMS.sample(level, machine, limit); }
    public static List<Mob> mobs(ServerLevel level, CopperMachineBlockEntity machine, int limit) { return MOBS.sample(level, machine, limit); }
    public static List<Mob> sentinelMobs(ServerLevel level,CopperMachineBlockEntity machine,int radius,int height,int limit) {
        return MOBS.sample(level,machine,machine.getBlockPos().offset(-radius,-height,-radius),machine.getBlockPos().offset(radius,height,radius),limit);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level)
            for (var index : INDEXES) index.add(level, SectionPos.asLong(event.getEntity().blockPosition()), event.getEntity());
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) for (var index : INDEXES) index.remove(level, SectionPos.asLong(event.getEntity().blockPosition()), event.getEntity().getUUID());
    }
    @SubscribeEvent public static void move(EntityEvent.EnteringSection event) {
        if (event.getEntity().level() instanceof ServerLevel level) for (var index : INDEXES) index.move(level, event);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) for (var index : INDEXES) index.levels.remove(level);
    }
    private static final class Index<T extends Entity> {
        private final Class<T> type;
        private final Map<ServerLevel, Map<Long, LinkedHashMap<UUID, WeakReference<T>>>> levels = new IdentityHashMap<>();
        private Index(Class<T> type) { this.type = type; }
        private void add(ServerLevel level, long section, Entity entity) {
            if (type.isInstance(entity)) levels.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(entity.getUUID(), new WeakReference<>(type.cast(entity)));
        }
        private void remove(ServerLevel level, long section, UUID id) {
            var sections = levels.get(level); if (sections == null) return;
            var bucket = sections.get(section); if (bucket == null) return;
            bucket.remove(id); if (bucket.isEmpty()) sections.remove(section);
        }
        private void move(ServerLevel level, EntityEvent.EnteringSection event) {
            var sections = levels.get(level); var bucket = sections == null ? null : sections.get(event.getPackedOldPos());
            // Construction can emit movement before the entity is accepted by its world.
            if (bucket == null || !bucket.containsKey(event.getEntity().getUUID())) return;
            remove(level, event.getPackedOldPos(), event.getEntity().getUUID()); add(level, event.getPackedNewPos(), event.getEntity());
        }
        private List<T> sample(ServerLevel level, CopperMachineBlockEntity machine, int limit) {
            var area = machine.workArea(); if(area==null) return List.of();
            return sample(level,machine,area.min(),area.max(),limit);
        }
        private List<T> sample(ServerLevel level,CopperMachineBlockEntity machine,net.minecraft.core.BlockPos min,net.minecraft.core.BlockPos max,int limit) {
            var sections=levels.get(level); if(sections==null) return List.of();
            var keys = new ArrayList<Long>(8);
            for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
                for (int y = min.getY() >> 4; y <= max.getY() >> 4; y++)
                    for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) keys.add(SectionPos.asLong(x, y, z));
            var result = new ArrayList<T>(limit);
            for (int visited = 0; visited < keys.size() && result.size() < limit; visited++) {
                long key = keys.get(Math.floorMod(machine.scanCursor++, keys.size())); var bucket = sections.get(key); if (bucket == null) continue;
                int remaining = Math.min(bucket.size(), limit - result.size());
                for (int i = 0; i < remaining; i++) {
                    var iterator = bucket.entrySet().iterator(); var entry = iterator.next(); iterator.remove(); var entity = entry.getValue().get();
                    result.add(entity); // Retired entries also consume this wave's cleanup budget.
                    if (entity != null && entity.isAlive() && SectionPos.asLong(entity.blockPosition()) == key) bucket.put(entry.getKey(), entry.getValue());
                }
                if (bucket.isEmpty()) sections.remove(key);
            }
            return result;
        }
    }
}
