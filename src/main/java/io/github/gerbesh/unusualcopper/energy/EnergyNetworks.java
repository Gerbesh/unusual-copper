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
    public record Sample(long epoch,long stored,long capacity,long produced,long consumed,long transferred,long demand,long delivered,int nodes,int devices) {}
    public static Sample inspect(ServerLevel level,CopperMachineBlockEntity observer) {
        var world=WORLDS.get(level);
        return world==null?new Sample(0,observer.energy.stored(),observer.energy.capacity(),observer.energy.produced(),observer.energy.consumed(),0,0,0,0,1):world.inspect(observer);
    }
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
    private record Face(BlockPos pos,Direction side) {}
    private record Source(Component component,Port port) {}
    private record ReactorPort(BlockPos pipe,BlockPos pos,Direction side,io.github.gerbesh.unusualcopper.reactor.ReactorBlockEntity reactor){}
    private static final class Flow { long demand,delivered; }
    private record Observation(Set<Component> components,long epoch) {}
    private static <T> void prioritize(List<T> values,java.util.function.ToIntFunction<T> priority,int cursor){
        values.sort(Comparator.comparingInt(priority).reversed());
        for(int first=0;first<values.size();){int end=first+1;while(end<values.size()&&priority.applyAsInt(values.get(end))==priority.applyAsInt(values.get(first)))end++;Collections.rotate(values.subList(first,end),-Math.floorMod(cursor,end-first));first=end;}
    }
    private static final class Component {
        final Set<BlockPos> pipes;
        final Map<BlockPos, Integer> tiers;
        final List<Port> ports = new ArrayList<>();
        final List<ReactorPort> reactors=new ArrayList<>();
        final Map<PathKey, List<BlockPos>> paths = new HashMap<>();
        long transferred;
        Component(Set<BlockPos> pipes, Map<BlockPos, Integer> tiers) { this.pipes = pipes; this.tiers = Map.copyOf(tiers); }
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
        long epoch;
        final Map<CopperMachineBlockEntity,Flow> flows=new IdentityHashMap<>();
        final Map<CopperMachineBlockEntity,Observation> observations=new IdentityHashMap<>();
        // Incremental topology builder is introduced below.
        private static final int REBUILD_BUDGET = 96;
        private Build building;
        private final class Build {
            final Set<BlockPos> unseen = new HashSet<>(pipes);
            final Map<BlockPos,Component> previous = new HashMap<>();
            final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            final List<Component> built = new ArrayList<>();
            Set<BlockPos> group;
            Map<BlockPos,Integer> tiers;
            List<Port> ports;
            List<ReactorPort> reactors;
            Build() { for(var c:components)for(var p:c.pipes)previous.put(p,c); }
            boolean valid(ServerLevel level,BlockPos pos) {
                return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof EnergyPipeBlockEntity;
            }
            void finish(ServerLevel level) {
                if(group==null)return;
                if(group.size()>ServerConfig.networkLimit()) {
                    for(var p:group)if(level.getBlockEntity(p) instanceof EnergyPipeBlockEntity pipe)pipe.clearAppearance();
                } else {
                    Component next=new Component(Set.copyOf(group),tiers);
                    next.ports.addAll(ports);
                    next.reactors.addAll(reactors);
                    next.ports.sort(Comparator.comparingLong(port->port.pos.asLong()));
                    Component old=previous.get(group.iterator().next());
                    built.add(old!=null&&old.pipes.equals(next.pipes)&&old.tiers.equals(next.tiers)&&old.ports.equals(next.ports)&&old.reactors.equals(next.reactors)?old:next);
                }
                group=null;tiers=null;ports=null;
            }
            boolean step(ServerLevel level) {
                for(int work=0;work<REBUILD_BUDGET;) {
                    if(queue.isEmpty()) {
                        finish(level);
                        if(unseen.isEmpty())return true;
                        BlockPos p=unseen.iterator().next();unseen.remove(p);work++;
                        if(!valid(level,p)){pipes.remove(p);continue;}
                        group=new HashSet<>();tiers=new HashMap<>();ports=new ArrayList<>();reactors=new ArrayList<>();
                        queue.add(p);
                    }
                    BlockPos p=queue.removeFirst();work++;
                    if(!valid(level,p)){pipes.remove(p);continue;}
                    group.add(p);
                    tiers.put(p,((EnergyPipeBlock)level.getBlockState(p).getBlock()).tier());
                    for(Direction d:Direction.values()){
                        BlockPos next=p.relative(d);
                        if(unseen.remove(next)){
                            if(valid(level,next))queue.addLast(next);
                            else pipes.remove(next);
                        }
                        if(level.hasChunkAt(next)&&level.getBlockEntity(next) instanceof CopperMachineBlockEntity m)
                            ports.add(new Port(p,next,d.getOpposite(),m));
                        if(level.hasChunkAt(next)&&level.getBlockEntity(next) instanceof io.github.gerbesh.unusualcopper.reactor.ReactorBlockEntity r)
                            reactors.add(new ReactorPort(p,next,d.getOpposite(),r));
                    }
                }
                return false;
            }
        }
        private void finishBuild(ServerLevel level) {
            components=List.copyOf(building.built);building=null;
            Set<CopperMachineBlockEntity> active=Collections.newSetFromMap(new IdentityHashMap<>());
            for(var c:components)for(var port:c.ports)active.add(port.machine);
            flows.keySet().retainAll(active);
            observations.keySet().removeIf(m->m.isRemoved()||!level.hasChunkAt(m.getBlockPos())||level.getBlockEntity(m.getBlockPos())!=m);
        }
        private boolean live(Port port,ServerLevel level){return !port.machine.isRemoved()&&level.hasChunkAt(port.pos)&&level.getBlockEntity(port.pos)==port.machine&&port.machine.energyEnabled();}
        void tick(ServerLevel level) {
            if(dirty){building=new Build();dirty=false;}
            if(building!=null){if(!building.step(level))return;finishBuild(level);}
            // Sample the previous ten ticks even when the transfer budget stops routing early.
            // No graph invalidation, persistent writes or per-tick packets for animation.
            if (level.getGameTime() % 10 == 0) for (var component : components) {
                int charge = 0;
                for(var port:component.reactors)if(!port.reactor.isRemoved()&&port.reactor.extract(port.side,1,true)>0)charge=15;
                for (var port : component.ports) if (live(port, level) && port.machine.kind.produces()
                        && port.machine.sideMode(port.side) == SideMode.ENERGY_OUTPUT && port.machine.energyFaceLimit(port.side) > 0
                        && port.machine.energy.stored() > 0) {
                    charge = Math.max(charge, 5 + (int)(10L * port.machine.energy.stored() / Math.max(1, port.machine.energy.capacity())));
                }
                for (var node : component.pipes) if (level.hasChunkAt(node) && level.getBlockEntity(node) instanceof EnergyPipeBlockEntity pipe) pipe.sampleAppearance(charge);
            }
            Map<BlockPos, Integer> used = new HashMap<>(),received=new HashMap<>(),extractedBy=new HashMap<>();Map<Face,Integer> faces=new HashMap<>();int attempts = 0; cursor++;
            List<Source> sources=new ArrayList<>();Map<Component,List<Port>> receivers=new IdentityHashMap<>();
            Map<CopperMachineBlockEntity,Integer> inputLimits=new IdentityHashMap<>();
            for (int networkIndex = 0; networkIndex < components.size(); networkIndex++) {
                Component component = components.get(networkIndex);
                List<Port> sinks=new ArrayList<>();
                for(int i=0;i<component.ports.size();i++){
                    Port port=component.ports.get(i);if(!live(port,level))continue;
                    if(port.machine.sideMode(port.side)==SideMode.ENERGY_OUTPUT&&port.machine.kind.produces()&&(port.machine.energy.stored()>0||port.machine.kind==MachineKind.ENERGY_DISTRIBUTOR)&&port.machine.energyFaceLimit(port.side)>0)sources.add(new Source(component,port));
                    if(port.machine.sideMode(port.side)==SideMode.ENERGY_INPUT&&port.machine.kind.consumes()){
                        sinks.add(port);inputLimits.merge(port.machine,port.machine.energyFaceLimit(port.side),Integer::sum);
                    }
                }
                // Rotate devices, rather than ports: attaching more input faces must not buy a larger share.
                Map<BlockPos,List<Port>> grouped=new LinkedHashMap<>();for(var sink:sinks)grouped.computeIfAbsent(sink.pos,ignored->new ArrayList<>()).add(sink);
                var groups=new ArrayList<>(grouped.values());prioritize(groups,group->group.get(0).machine.energyPriority(),cursor);
                receivers.put(component,groups.stream().flatMap(Collection::stream).toList());
            }
            for(var entry:inputLimits.entrySet()){
                var machine=entry.getKey();int request=Math.min(entry.getValue(),Math.min(machine.energyTransferRate(),machine.energy.capacity()-machine.energy.stored()));
                flows.computeIfAbsent(machine,ignored->new Flow()).demand+=Math.max(0,request);
            }
            // A distributor's high-priority face is serviced before its other components.
            // Ordinary sources retain equal priority and rotate with the existing cursor.
            prioritize(sources,source->source.port.machine.energyOutputPriority(source.port.side),cursor);
            // Physical reactor ports share a controller ledger across components and capability callers.
            for(var component:components)for(var port:component.reactors){
                var reactor=port.reactor;if(reactor.isRemoved()||!level.hasChunkAt(port.pos)||level.getBlockEntity(port.pos)!=reactor||!reactor.energyPort(port.side))continue;
                var core=reactor.core();if(core==null||!core.ready())continue;
                for(var sink:receivers.get(component)){
                    if(++attempts>ServerConfig.transferBudget())return;
                    if(core.owner!=null&&sink.machine.owner()!=null&&!core.owner.equals(sink.machine.owner()))continue;
                    List<BlockPos> path=component.path(port.pipe,sink.pipe);if(path.isEmpty())continue;
                    var face=new Face(sink.pos,sink.side);
                    int sinkUsed=(sink.machine.kind==MachineKind.ENERGY_DISTRIBUTOR?received:used).getOrDefault(sink.pos,0);
                    int amount=Math.min(sink.machine.energyTransferRate()-sinkUsed,sink.machine.energyFaceLimit(sink.side)-faces.getOrDefault(face,0));
                    for(var node:path)amount=Math.min(amount,ServerConfig.pipeRate(component.tiers.get(node))-used.getOrDefault(node,0));
                    amount=Math.min(reactor.extract(port.side,amount,true),sink.machine.energy.receive(amount,true));if(amount<=0)continue;
                    int taken=reactor.extract(port.side,amount,false),accepted=sink.machine.energy.receive(taken,false);if(accepted<taken)reactor.refund(port.side,taken-accepted);
                    if(accepted<=0)continue;used.merge(sink.pos,accepted,Integer::sum);received.merge(sink.pos,accepted,Integer::sum);faces.merge(face,accepted,Integer::sum);
                    for(int i=0;i<path.size();i++){var node=path.get(i);used.merge(node,accepted,Integer::sum);if(level.getBlockEntity(node) instanceof EnergyPipeBlockEntity pipe){
                        Direction entry=i+1<path.size()?Direction.fromDelta(path.get(i+1).getX()-node.getX(),path.get(i+1).getY()-node.getY(),path.get(i+1).getZ()-node.getZ()):port.side.getOpposite();
                        Direction exit=i>0?Direction.fromDelta(path.get(i-1).getX()-node.getX(),path.get(i-1).getY()-node.getY(),path.get(i-1).getZ()-node.getZ()):sink.side.getOpposite();pipe.transferred(entry,exit);
                    }}component.transferred+=accepted;flows.computeIfAbsent(sink.machine,ignored->new Flow()).delivered+=accepted;
                }
            }
            for(var route:sources){
                var source=route.port;var component=route.component;
                for(Port sink:receivers.get(component)){
                    if(source.pos.equals(sink.pos)||(source.machine.kind.isCell()&&sink.machine.kind.isCell()))continue;
                    if(source.machine.energy.stored()==0||sink.machine.energy.stored()==sink.machine.energy.capacity()||sink.machine.energyFaceLimit(sink.side)==0)continue;
                    if(++attempts>ServerConfig.transferBudget())return;
                    List<BlockPos> path=component.path(source.pipe,sink.pipe);if(path.isEmpty())continue;
                    var out=new Face(source.pos,source.side);var in=new Face(sink.pos,sink.side);
                    // A relay has independent intake/output budgets; passing one CuE is not charged twice.
                    int outputUsed=(source.machine.kind==MachineKind.ENERGY_DISTRIBUTOR?extractedBy:used).getOrDefault(source.pos,0);
                    int inputUsed=(sink.machine.kind==MachineKind.ENERGY_DISTRIBUTOR?received:used).getOrDefault(sink.pos,0);
                    int amount=Math.min(source.machine.energyTransferRate()-outputUsed,sink.machine.energyTransferRate()-inputUsed);
                    amount=Math.min(amount,source.machine.energyFaceLimit(source.side)-faces.getOrDefault(out,0));
                    amount=Math.min(amount,sink.machine.energyFaceLimit(sink.side)-faces.getOrDefault(in,0));
                    for(BlockPos node:path)amount=Math.min(amount,ServerConfig.pipeRate(component.tiers.get(node))-used.getOrDefault(node,0));
                    amount=Math.min(amount,source.machine.energy.extract(amount,true));amount=Math.min(amount,sink.machine.energy.receive(amount,true));
                    if(amount<=0)continue;
                    int extracted=source.machine.energy.extract(amount,false);int accepted=sink.machine.energy.receive(extracted,false);
                    if(accepted<extracted)source.machine.energy.refund(extracted-accepted);
                    if(accepted==0)continue;
                    used.merge(source.pos,accepted,Integer::sum);used.merge(sink.pos,accepted,Integer::sum);
                    extractedBy.merge(source.pos,accepted,Integer::sum);received.merge(sink.pos,accepted,Integer::sum);
                    faces.merge(out,accepted,Integer::sum);faces.merge(in,accepted,Integer::sum);
                    for(int i=0;i<path.size();i++) {
                        BlockPos node=path.get(i); used.merge(node,accepted,Integer::sum);
                        if(level.getBlockEntity(node) instanceof EnergyPipeBlockEntity pipe) {
                            Direction entry=i+1<path.size()?Direction.fromDelta(path.get(i+1).getX()-node.getX(),path.get(i+1).getY()-node.getY(),path.get(i+1).getZ()-node.getZ()):source.side.getOpposite();
                            Direction exit=i>0?Direction.fromDelta(path.get(i-1).getX()-node.getX(),path.get(i-1).getY()-node.getY(),path.get(i-1).getZ()-node.getZ()):sink.side.getOpposite();
                            pipe.transferred(entry,exit);
                        }
                    }
                    component.transferred+=accepted;flows.computeIfAbsent(sink.machine,ignored->new Flow()).delivered+=accepted;
                }
            }
        }
        Sample inspect(CopperMachineBlockEntity observer){
            Set<io.github.gerbesh.unusualcopper.reactor.ReactorBlockEntity> reactors=Collections.newSetFromMap(new IdentityHashMap<>());
            Set<CopperMachineBlockEntity> machines=Collections.newSetFromMap(new IdentityHashMap<>());
            machines.add(observer);
            Set<Component> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            long moved=0;int nodes=0;
            for(var component:components){
                if(component.ports.stream().noneMatch(port->port.machine==observer))continue;
                seen.add(component);
                for(var port:component.reactors){var core=port.reactor.core();if(core!=null&&core.ready())reactors.add(core);}
                nodes+=component.pipes.size();moved+=component.transferred;
                for(var port:component.ports)if(!port.machine.isRemoved()&&port.machine.getLevel() instanceof ServerLevel level&&level.hasChunkAt(port.pos)&&level.getBlockEntity(port.pos)==port.machine)machines.add(port.machine);
            }
            long stored=0,capacity=0,produced=0,consumed=0,demand=0,delivered=0;
            for(var machine:machines){stored+=machine.energy.stored();capacity+=machine.energy.capacity();produced+=machine.energy.produced();consumed+=machine.energy.consumed();var flow=flows.get(machine);if(flow!=null){demand+=flow.demand;delivered+=flow.delivered;}}
            for(var reactor:reactors){stored+=reactor.engine.stored;capacity+=io.github.gerbesh.unusualcopper.reactor.ReactorEngine.CAPACITY;produced+=reactor.engine.produced;}
            var observation=observations.get(observer);if(observation==null||!observation.components.equals(seen)){observation=new Observation(Set.copyOf(seen),++epoch);observations.put(observer,observation);}
            return new Sample(observation.epoch,stored,capacity,produced,consumed,moved,demand,delivered,nodes,machines.size()+reactors.size());
        }
    }
}
