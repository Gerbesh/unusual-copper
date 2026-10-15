package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Exact live membership with budgeted cold starts; never rescans a world's entity list. */
public final class MobPopulations {
    private static final Map<ServerLevel, State> LEVELS = new IdentityHashMap<>();
    private static long tagEpoch;
    private MobPopulations() {}
    public static int tracked(ServerLevel level) { var state = LEVELS.get(level); return state == null ? 0 : state.rows.size(); }
    public static void added(Entity entity) {
        if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level)) return;
        var state = LEVELS.computeIfAbsent(level, State::new); var old = state.rows.get(mob.getUUID());
        if (old != null) state.remove(old);
        var row = new Row(++state.sequence, mob); state.rows.put(mob.getUUID(), row); state.refresh(row, true);
    }
    public static void changed(Entity entity) {
        if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level)) return;
        var state = LEVELS.get(level); if (state == null) return;
        var row = state.rows.get(entity.getUUID()); if (row != null && row.entity.get() == mob) state.refresh(row, false);
    }
    public static void removed(Entity entity) {
        if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel level)) return;
        var state = LEVELS.get(level); if (state == null) return;
        var row = state.rows.get(entity.getUUID()); if (row != null && row.entity.get() == entity) state.remove(row);
    }
    public static Ledger ledger(ServerLevel level, UUID machine, BlockPos pos, WorkArea area, ReaperRule rule) {
        var state = LEVELS.computeIfAbsent(level, State::new); var ledger = state.ledgers.get(machine);
        if (ledger == null || ledger.epoch != tagEpoch || !ledger.area.equals(area) || !ledger.rule.equals(rule)) {
            if (ledger != null) state.retire(ledger);
            ledger = new Ledger(state, machine, pos, area, rule); state.ledgers.put(machine, ledger);
            for (long key : ledger.sections) state.subscribers.computeIfAbsent(key, ignored -> new HashSet<>()).add(ledger);
        }
        ledger.lastUse = level.getGameTime(); return ledger;
    }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event) { tagEpoch++; }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var state = LEVELS.get(level); if (state == null) return;
        for (int i = Math.min(256, state.pending.size()); i > 0; i--) {
            var iterator = state.pending.entrySet().iterator(); var row = iterator.next().getValue(); iterator.remove(); state.refresh(row, false);
            if (row.pending && state.rows.get(row.id) == row) state.pending.put(row.id, row);
        }
        for (int i = Math.min(8, state.ledgers.size()); i > 0; i--) {
            var entry = state.cleanup == null ? state.ledgers.firstEntry() : state.ledgers.higherEntry(state.cleanup);
            if (entry == null) entry = state.ledgers.firstEntry(); if (entry == null) break;
            state.cleanup = entry.getKey(); var ledger = entry.getValue();
            if (level.getGameTime() - ledger.lastUse > 100 || !level.hasChunkAt(ledger.pos)) state.retire(ledger);
        }
    }
    private static int flags(Mob mob) {
        int flags = (mob.isAlive() ? 1 : 0) | (mob.isBaby() ? 2 : 0) | (mob.hasCustomName() ? 4 : 0) | (mob.isLeashed() ? 8 : 0);
        if (mob instanceof TamableAnimal pet) { if (pet.isTame()) flags |= 16; if (pet.getOwnerUUID() != null) flags |= 128; }
        if (mob instanceof AbstractHorse horse) { if (horse.isTamed()) flags |= 32; if (horse.getOwnerUUID() != null) flags |= 64; }
        return flags;
    }
    private static final class Row {
        final long serial; final UUID id; final WeakReference<Mob> entity;
        BlockPos pos; long section; int flags; boolean pending = true, removed;
        Row(long serial, Mob mob) { this.serial = serial; id = mob.getUUID(); entity = new WeakReference<>(mob); }
    }
    private static final class State {
        final ServerLevel level; long sequence; UUID cleanup;
        final Map<UUID, Row> rows = new HashMap<>();
        final Map<Long, NavigableMap<Long, Row>> sections = new HashMap<>();
        final Map<Long, Set<Ledger>> subscribers = new HashMap<>();
        final NavigableMap<UUID, Ledger> ledgers = new TreeMap<>();
        final LinkedHashMap<UUID, Row> pending = new LinkedHashMap<>();
        State(ServerLevel level) { this.level = level; }
        void refresh(Row row, boolean force) {
            if (row.removed || rows.get(row.id) != row) return;
            var mob = row.entity.get(); if (mob == null || mob.isRemoved()) { remove(row); return; }
            var pos = mob.blockPosition(); long section = SectionPos.asLong(pos); int flags = MobPopulations.flags(mob);
            boolean pending = level.getEntity(row.id) != mob;
            if (!force && row.pos != null && row.pos.equals(pos) && row.flags == flags && row.pending == pending) return;
            long oldSection = row.pos == null ? section : row.section;
            if (row.pos != null && oldSection != section) { var bucket = sections.get(oldSection); if (bucket != null) { bucket.remove(row.serial); if (bucket.isEmpty()) sections.remove(oldSection); } }
            row.pos = pos.immutable(); row.section = section; row.flags = flags; row.pending = pending;
            sections.computeIfAbsent(section, ignored -> new TreeMap<>()).put(row.serial, row);
            if (pending) this.pending.put(row.id, row); else this.pending.remove(row.id);
            notify(row, oldSection);
        }
        void notify(Row row, long oldSection) {
            var old = subscribers.get(oldSection); if (old != null) for (var ledger : old) ledger.touch(row);
            var next = subscribers.get(row.section); if (next != null && row.section != oldSection) for (var ledger : next) if (old == null || !old.contains(ledger)) ledger.touch(row);
        }
        void remove(Row row) {
            if (rows.get(row.id) != row) return;
            rows.remove(row.id); pending.remove(row.id); row.removed = true;
            var bucket = sections.get(row.section); if (bucket != null) { bucket.remove(row.serial); if (bucket.isEmpty()) sections.remove(row.section); }
            notify(row, row.section);
        }
        void retire(Ledger ledger) {
            ledger.retired = true;
            ledgers.remove(ledger.machine);
            for (long key : ledger.sections) { var set = subscribers.get(key); if (set != null) { set.remove(ledger); if (set.isEmpty()) subscribers.remove(key); } }
        }
    }
    public static final class Ledger {
        private final State state; private final UUID machine; private final BlockPos pos; private final WorkArea area; private final ReaperRule rule;
        private final long upper, epoch; private long lastUse, cursor; private int sectionCursor;
        private boolean retired;
        private final List<Long> sections = new ArrayList<>(8);
        private final LinkedHashMap<UUID, Row> members = new LinkedHashMap<>();
        private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
        private Ledger(State state, UUID machine, BlockPos pos, WorkArea area, ReaperRule rule) {
            this.state = state; this.machine = machine; this.pos = pos.immutable(); this.area = area; this.rule = rule; upper = state.sequence; epoch = tagEpoch;
            for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4; x++) for (int y = area.min().getY() >> 4; y <= area.max().getY() >> 4; y++) for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) sections.add(SectionPos.asLong(x,y,z));
        }
        private void touch(Row row) {
            var mob = row.entity.get(); boolean inArea = !row.removed && row.pos != null && area.contains(row.pos);
            if (inArea && row.pending) pending.add(row.id); else pending.remove(row.id);
            if (inArea && !row.pending && mob != null && rule.matches(mob)) members.putIfAbsent(row.id, row); else members.remove(row.id);
        }
        /** Counts each examined row, including stale ones, against the caller's budget. */
        public int step(int budget) {
            if (retired) return 0;
            int used = 0;
            while (sectionCursor < sections.size() && used < budget) {
                var bucket = state.sections.get(sections.get(sectionCursor)); var entry = bucket == null ? null : bucket.higherEntry(cursor);
                if (entry == null || entry.getKey() > upper) { sectionCursor++; cursor = 0; continue; }
                cursor = entry.getKey(); used++; state.refresh(entry.getValue(), false); touch(entry.getValue());
            }
            while (sectionCursor == sections.size() && !pending.isEmpty() && used < budget) {
                var iterator = pending.iterator(); var id = iterator.next(); iterator.remove(); used++;
                var row = state.rows.get(id); if (row != null) { state.refresh(row, false); touch(row); }
            }
            return used;
        }
        public boolean ready() { return !retired && sectionCursor == sections.size() && pending.isEmpty() && epoch == tagEpoch; }
        public int count() { return members.size(); }
        public Mob target() {
            if (!ready() || members.isEmpty()) return null;
            var iterator = members.entrySet().iterator(); var entry = iterator.next(); iterator.remove(); var row = entry.getValue(); state.refresh(row, false); touch(row);
            var mob = row.entity.get();
            return members.containsKey(row.id) && mob != null && state.level.getEntity(row.id) == mob && rule.matches(mob) ? mob : null;
        }
    }
}
