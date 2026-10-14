package io.github.gerbesh.unusualcopper.energy;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.machine.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import io.github.gerbesh.unusualcopper.config.ServerConfig;

/** Transient graph of loaded pipes. Inventories and energy remain in their block entities. */
public final class EnergyNetworks {
    private static final Map<ServerLevel, WorldNetwork> WORLDS = new IdentityHashMap<>();
    private EnergyNetworks() {}
    public static void add(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel server) { var world = WORLDS.computeIfAbsent(server, ignored -> new WorldNetwork()); world.pipes.add(pos.immutable()); world.dirty = true; }
    }
    public static void remove(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel server && WORLDS.containsKey(server)) { var world = WORLDS.get(server); world.pipes.remove(pos); world.dirty = true; }
    }
    public static void dirty(LevelAccessor level) {
        if (level instanceof ServerLevel server && WORLDS.containsKey(server)) WORLDS.get(server).dirty = true;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel server) WORLDS.remove(server); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        WorldNetwork world = WORLDS.get(level);
        if (world != null) world.tick(level);
    }
    private record Port(BlockPos pipe, BlockPos pos, Direction side, CopperMachineBlockEntity machine) {}
    private record PathKey(BlockPos from, BlockPos to) {}
    private static final class Component {
        final Set<BlockPos> pipes;
        final List<Port> ports = new ArrayList<>();
        final Map<PathKey, List<BlockPos>> paths = new HashMap<>();
        Component(Set<BlockPos> pipes) { this.pipes = pipes; }
        List<BlockPos> path(BlockPos from, BlockPos to) {
            return paths.computeIfAbsent(new PathKey(from, to), key -> {
                Map<BlockPos, BlockPos> previous = new HashMap<>(); ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                queue.add(from); previous.put(from, from);
                while (!queue.isEmpty()) {
                    BlockPos current = queue.remove(); if (current.equals(to)) break;
                    for (Direction side : Direction.values()) { BlockPos next = current.relative(side); if (pipes.contains(next) && !previous.containsKey(next)) { previous.put(next, current); queue.add(next); } }
                }
                if (!previous.containsKey(to)) return List.of();
                List<BlockPos> path = new ArrayList<>(); BlockPos current = to;
                while (!current.equals(from)) { path.add(current); current = previous.get(current); } path.add(from); return List.copyOf(path);
            });
        }
    }
    private static final class WorldNetwork {
        final Set<BlockPos> pipes = new HashSet<>();
        List<Component> components = List.of();
        boolean dirty = true;
        int cursor;
        void rebuild(ServerLevel level) {
            pipes.removeIf(pos -> !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof EnergyPipeBlockEntity));
            Set<BlockPos> unseen = new HashSet<>(pipes); List<Component> built = new ArrayList<>();
            while (!unseen.isEmpty()) {
                BlockPos start = unseen.iterator().next(); ArrayDeque<BlockPos> queue = new ArrayDeque<>(); Set<BlockPos> group = new HashSet<>(); queue.add(start); unseen.remove(start);
                while (!queue.isEmpty()) { BlockPos pos = queue.remove(); group.add(pos); for (Direction side : Direction.values()) { BlockPos neighbor = pos.relative(side); if (unseen.remove(neighbor)) queue.add(neighbor); } }
                if (group.size() > ServerConfig.networkLimit()) continue;
                Component component = new Component(Set.copyOf(group));
                for (BlockPos pipe : group) for (Direction side : Direction.values()) {
                    BlockPos pos = pipe.relative(side);
                    if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof CopperMachineBlockEntity machine) component.ports.add(new Port(pipe, pos, side.getOpposite(), machine));
                }
                component.ports.sort(Comparator.comparingLong(port -> port.pos.asLong())); built.add(component);
            }
            components = built; dirty = false;
        }
        void tick(ServerLevel level) {
            if (dirty) rebuild(level);
            Map<BlockPos, Integer> used = new HashMap<>(); int attempts = 0; cursor++;
            for (int networkIndex = 0; networkIndex < components.size(); networkIndex++) {
                Component component = components.get(Math.floorMod(networkIndex + cursor, components.size()));
                List<Port> sources = component.ports.stream().filter(port -> port.machine.energyEnabled() && port.machine.sideMode(port.side) == SideMode.ENERGY_OUTPUT && port.machine.kind.produces()).toList();
                List<Port> sinks = component.ports.stream().filter(port -> port.machine.energyEnabled() && port.machine.sideMode(port.side) == SideMode.ENERGY_INPUT && port.machine.kind.consumes()).toList();
                for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                    Port source = sources.get(Math.floorMod(sourceIndex + cursor, sources.size()));
                    for (int sinkIndex = 0; sinkIndex < sinks.size(); sinkIndex++) {
                        Port sink = sinks.get(Math.floorMod(sinkIndex + cursor, sinks.size()));
                        if (source.pos.equals(sink.pos) || (source.machine.kind == MachineKind.COPPER_CELL && sink.machine.kind == MachineKind.COPPER_CELL)) continue;
                        if (++attempts > ServerConfig.transferBudget()) return;
                        List<BlockPos> path = component.path(source.pipe, sink.pipe); if (path.isEmpty()) continue;
                        int amount = Math.min(ServerConfig.pipeRate() - used.getOrDefault(source.pos, 0), ServerConfig.pipeRate() - used.getOrDefault(sink.pos, 0));
                        for (BlockPos node : path) amount = Math.min(amount, ServerConfig.pipeRate() - used.getOrDefault(node, 0));
                        amount = Math.min(amount, source.machine.energy.extract(amount, true));
                        amount = Math.min(amount, sink.machine.energy.receive(amount, true));
                        if (amount <= 0) continue;
                        int extracted = source.machine.energy.extract(amount, false);
                        int accepted = sink.machine.energy.receive(extracted, false);
                        if (accepted < extracted) source.machine.energy.add(extracted - accepted, false);
                        if (accepted == 0) continue;
                        used.merge(source.pos, accepted, Integer::sum); used.merge(sink.pos, accepted, Integer::sum);
                        for (BlockPos node : path) used.merge(node, accepted, Integer::sum);
                    }
                }
            }
        }
    }
}
