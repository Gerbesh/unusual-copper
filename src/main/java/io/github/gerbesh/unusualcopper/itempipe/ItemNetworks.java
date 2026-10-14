package io.github.gerbesh.unusualcopper.itempipe;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Physical transport: the graph holds positions; actual transit items live in their source pipe. */
public final class ItemNetworks {
    private static final Map<ServerLevel, World> WORLDS = new IdentityHashMap<>();
    private ItemNetworks() {}
    public static void add(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel server) { var world = WORLDS.computeIfAbsent(server, ignored -> new World()); world.positions.add(pos.immutable()); world.dirty = true; }
    }
    public static void remove(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel server && WORLDS.containsKey(server)) { var world = WORLDS.get(server); world.positions.remove(pos); world.dirty = true; }
    }
    public static void dirty(LevelAccessor level) { if (level instanceof ServerLevel server && WORLDS.containsKey(server)) WORLDS.get(server).dirty = true; }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel server) WORLDS.remove(server); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && WORLDS.containsKey(level)) WORLDS.get(level).tick(level);
    }
    private static ItemPipeBlockEntity pipe(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity entity && !entity.isRemoved() ? entity : null;
    }
    private record Port(BlockPos pipe, Direction side, HandlerStorageEndpoint inventory) {}
    private record Route(BlockPos from, BlockPos to) {}
    private static final class Component {
        final Set<BlockPos> nodes = new HashSet<>();
        final Map<BlockPos, Set<BlockPos>> edges = new HashMap<>();
        final Map<BlockPos, List<Port>> sources = new HashMap<>();
        final List<Port> targets = new ArrayList<>();
        final Map<Route, List<BlockPos>> routes = new LinkedHashMap<>();
        List<BlockPos> path(BlockPos from, BlockPos to) {
            var key = new Route(from, to);
            var cached = routes.get(key);
            if (cached != null) return cached;
            var previous = new HashMap<BlockPos, BlockPos>(); var queue = new ArrayDeque<BlockPos>();
            previous.put(from, from); queue.add(from);
            while (!queue.isEmpty()) {
                var current = queue.remove(); if (current.equals(to)) break;
                for (var next : edges.getOrDefault(current, Set.of())) if (nodes.contains(next) && !previous.containsKey(next)) { previous.put(next, current); queue.add(next); }
            }
            if (!previous.containsKey(to)) return List.of();
            var path = new ArrayList<BlockPos>(); var current = to;
            while (!current.equals(from)) { path.add(current); current = previous.get(current); } path.add(from);
            if (routes.size() >= 256) routes.remove(routes.keySet().iterator().next());
            var result = List.copyOf(path); routes.put(key, result); return result;
        }
    }
    private static final class Build {
        final Set<BlockPos> unseen;
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final List<Component> ready = new ArrayList<>();
        Component current;
        UUID owner;
        Build(Set<BlockPos> positions) { unseen = new HashSet<>(positions); }
        boolean step(ServerLevel level) {
            for (int work = 0; work < ServerConfig.itemRebuildBudget(); work++) {
                if (queue.isEmpty()) {
                    if (current != null && current.nodes.size() <= ServerConfig.networkLimit()) ready.add(current);
                    current = null;
                    if (unseen.isEmpty()) return true;
                    var pos = unseen.iterator().next(); unseen.remove(pos);
                    var entity = pipe(level, pos); if (entity == null) continue;
                    current = new Component(); owner = entity.owner(); queue.add(pos);
                }
                var pos = queue.remove(); var entity = pipe(level, pos); if (entity == null) continue;
                current.nodes.add(pos);
                for (var side : Direction.values()) {
                    var next = pos.relative(side); var neighbor = pipe(level, next);
                    if (neighbor != null) {
                        if (entity.rule(side).mode == PipeMode.AUTO && neighbor.rule(side.getOpposite()).mode == PipeMode.AUTO && Objects.equals(owner, neighbor.owner())) {
                            current.edges.computeIfAbsent(pos, ignored -> new HashSet<>()).add(next);
                            if (unseen.remove(next)) queue.add(next);
                        }
                        continue;
                    }
                    if (!level.hasChunkAt(next) || current.nodes.size() > ServerConfig.networkLimit() || level.getBlockEntity(next) == null) continue;
                    var mode = entity.effective(side);
                    if (mode != PipeMode.EXTRACT && mode != PipeMode.INSERT) continue;
                    var port = new Port(pos, side, new HandlerStorageEndpoint(level, next, side.getOpposite(), owner));
                    if (mode == PipeMode.EXTRACT) current.sources.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(port); else current.targets.add(port);
                }
            }
            return false;
        }
    }
    private static final class World {
        final Set<BlockPos> positions = new HashSet<>();
        List<Component> components = List.of();
        boolean dirty = true;
        Build building;
        int cursor;
        boolean livePath(ServerLevel level, List<BlockPos> path, UUID owner) {
            for (int i = 0; i < path.size(); i++) {
                var entity = pipe(level, path.get(i));
                if (entity == null || !entity.enabled() || !Objects.equals(owner, entity.owner()) || !ProtectionPolicy.allowed(level, path.get(i), owner)) return false;
                if (i + 1 < path.size()) {
                    var next = path.get(i + 1);
                    var direction = Direction.fromDelta(next.getX() - path.get(i).getX(), next.getY() - path.get(i).getY(), next.getZ() - path.get(i).getZ());
                    var other = pipe(level, next);
                    if (direction == null || entity.rule(direction).mode != PipeMode.AUTO || other == null || other.rule(direction.getOpposite()).mode != PipeMode.AUTO) return false;
                }
            }
            return true;
        }
        int routeRoom(List<BlockPos> path, Map<BlockPos, Integer> used) {
            int room = ServerConfig.itemRate();
            for (var node : path) room = Math.min(room, ServerConfig.itemRate() - used.getOrDefault(node, 0));
            return Math.max(0, room);
        }
        List<Port> orderedTargets(ServerLevel level, Component component) {
            var targets = new ArrayList<>(component.targets);
            targets.sort(Comparator.<Port>comparingInt(port -> { var entity = pipe(level, port.pipe); return entity == null ? Integer.MIN_VALUE : entity.rule(port.side).priority; }).reversed().thenComparingLong(port -> port.pipe.asLong()));
            var ordered = new ArrayList<Port>();
            for (int start = 0; start < targets.size();) {
                var first = pipe(level, targets.get(start).pipe); int priority = first == null ? Integer.MIN_VALUE : first.rule(targets.get(start).side).priority;
                int end = start + 1;
                while (end < targets.size()) { var other = pipe(level, targets.get(end).pipe); if ((other == null ? Integer.MIN_VALUE : other.rule(targets.get(end).side).priority) != priority) break; end++; }
                for (int i = 0; i < end - start; i++) ordered.add(targets.get(start + Math.floorMod(i + cursor, end - start)));
                start = end;
            }
            return ordered;
        }
        void tick(ServerLevel level) {
            if (dirty) { building = new Build(positions); dirty = false; }
            if (building != null) {
                if (!building.step(level)) return;
                components = List.copyOf(building.ready); building = null;
            }
            if (level.getGameTime() % ServerConfig.itemInterval() != 0) return;
            cursor++; int attempts = 0;
            var used = new HashMap<BlockPos, Integer>();
            for (int network = 0; network < components.size(); network++) {
                var component = components.get(Math.floorMod(network + cursor, components.size()));
                var targets = orderedTargets(level, component);
                var origins = new ArrayList<>(component.nodes); origins.sort(Comparator.comparingLong(BlockPos::asLong));
                for (int originIndex = 0; originIndex < origins.size(); originIndex++) {
                    var origin = origins.get(Math.floorMod(originIndex + cursor, origins.size())); var sourcePipe = pipe(level, origin);
                    if (sourcePipe == null) continue;
                    if (!sourcePipe.enabled()) { sourcePipe.status = ItemPipeBlockEntity.Status.PAUSED; continue; }
                    if (sourcePipe.faulted) { sourcePipe.status = ItemPipeBlockEntity.Status.FAULT; continue; }
                    var cargo = sourcePipe.cargo();
                    if (!cargo.isEmpty()) {
                        for (var target : targets) {
                            if (++attempts > ServerConfig.itemTransferBudget()) return;
                            if (deliver(level, component, sourcePipe, target, used)) break;
                        }
                        continue;
                    }
                    sourcePipe.status = ItemPipeBlockEntity.Status.IDLE;
                    for (var source : component.sources.getOrDefault(origin, List.of())) {
                        if (!source.pipe.equals(origin) || sourcePipe.effective(source.side) != PipeMode.EXTRACT) continue;
                        if (++attempts > ServerConfig.itemTransferBudget()) return;
                        var rule = sourcePipe.rule(source.side);
                        var view = source.inventory.snapshot(rule::accepts, 32);
                        for (var entry : view.entries()) {
                            int available = entry.count();
                            if (rule.reserve > 0) { var count = source.inventory.count(entry.key()); if (!count.complete()) continue; available = (int)Math.max(0, Math.min(Integer.MAX_VALUE, count.amount() - rule.reserve)); }
                            for (var target : targets) {
                                if (++attempts > ServerConfig.itemTransferBudget()) return;
                                int amount = room(level, component, sourcePipe, target, entry.key(), available, source.inventory.identity().pos(), used);
                                if (amount <= 0) continue;
                                amount = Math.min(amount, source.inventory.simulateExtract(entry.key(), amount));
                                if (amount <= 0) continue;
                                var extracted = source.inventory.extract(entry.key(), amount);
                                if (extracted.isEmpty()) continue;
                                sourcePipe.hold(extracted, source.inventory.identity().pos(), source.side);
                                sourcePipe.faulted = extracted.stream().anyMatch(stack -> !entry.key().matches(stack)) || extracted.stream().mapToLong(ItemStack::getCount).sum() > amount;
                                if (!sourcePipe.faulted) deliver(level, component, sourcePipe, target, used);
                                break;
                            }
                            if (sourcePipe.cargoCount() > 0) break;
                        }
                        if (sourcePipe.cargoCount() > 0) break;
                    }
                }
            }
        }
        int room(ServerLevel level, Component component, ItemPipeBlockEntity source, Port target, ItemKey key, int amount, BlockPos excluded, Map<BlockPos, Integer> used) {
            var targetPipe = pipe(level, target.pipe);
            if (targetPipe == null || targetPipe.effective(target.side) != PipeMode.INSERT || (excluded != null && excluded.equals(target.inventory.identity().pos()))) return 0;
            var rule = targetPipe.rule(target.side); if (!rule.accepts(key.stack(1))) return 0;
            var path = component.path(source.getBlockPos(), target.pipe);
            if (path.isEmpty() || !livePath(level, path, source.owner())) return 0;
            int offered = Math.min(amount, Math.min(routeRoom(path, used), key.stack(1).getMaxStackSize()));
            if (rule.minimum > 0 || rule.maximum != Integer.MAX_VALUE) {
                var count = target.inventory.count(key); if (!count.complete()) return 0;
                boolean active = rule.restocking(key, count.amount()); targetPipe.setChanged(); if (!active) return 0;
                offered = (int)Math.min(offered, Math.max(0, (long)rule.maximum - count.amount()));
            }
            if (offered <= 0) return 0;
            var remaining = target.inventory.insert(key.stack(offered), true);
            return key.matches(remaining) || remaining.isEmpty() ? Math.max(0, offered - remaining.getCount()) : 0;
        }
        boolean deliver(ServerLevel level, Component component, ItemPipeBlockEntity source, Port target, Map<BlockPos, Integer> used) {
            var cargo = source.cargo(); if (cargo.isEmpty()) return false;
            var stored = cargo.get(0); var key = ItemKey.of(stored);
            int offered = room(level, component, source, target, key, stored.getCount(), source.originInventory(), used);
            if (offered <= 0) { source.status = ItemPipeBlockEntity.Status.NO_ROUTE; return false; }
            var remainder = target.inventory.insert(stored.copyWithCount(offered), false);
            var after = new ArrayList<ItemStack>();
            if (stored.getCount() > offered) after.add(stored.copyWithCount(stored.getCount() - offered));
            if (!remainder.isEmpty()) after.add(remainder.copy());
            after.addAll(cargo.subList(1, cargo.size())); source.replaceCargo(after);
            if (!remainder.isEmpty() && (!key.matches(remainder) || remainder.getCount() > offered)) { source.faulted = true; source.status = ItemPipeBlockEntity.Status.FAULT; source.setChanged(); return false; }
            int moved = offered - remainder.getCount();
            var targetPipe = pipe(level, target.pipe);
            if (moved > 0 && targetPipe != null && targetPipe.rule(target.side).minimum > 0) {
                var count = target.inventory.count(key);
                if (count.complete()) { targetPipe.rule(target.side).restocking(key, count.amount()); targetPipe.setChanged(); }
            }
            for (var node : component.path(source.getBlockPos(), target.pipe)) used.merge(node, moved, Integer::sum);
            source.status = moved > 0 ? ItemPipeBlockEntity.Status.TRANSFERRING : ItemPipeBlockEntity.Status.BUFFERED;
            return moved > 0;
        }
    }
}
