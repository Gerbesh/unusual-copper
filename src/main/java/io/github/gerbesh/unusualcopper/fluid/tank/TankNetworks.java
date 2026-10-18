package io.github.gerbesh.unusualcopper.fluid.tank;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;

/** Main-thread loaded-cube topology. Each physical cube saves only its own real quantity. */
public final class TankNetworks {
    public record Visual(net.minecraft.world.level.Level level,TankView view,Fluid fluid){}
    private static java.util.function.Consumer<Visual> visualReceiver=v->{};
    public static void visualReceiver(java.util.function.Consumer<Visual> receiver){visualReceiver=Objects.requireNonNull(receiver);}
    public static void acceptVisual(CopperMachineBlockEntity m){visualReceiver.accept(new Visual(m.getLevel(),m.tankView,m.visualFluid));}
    public interface Storage extends IFluidHandler {
        boolean fillWhole(FluidStack stack);
        boolean drainWhole(FluidStack stack);
    }
    private record FluidKey(Fluid fluid,CompoundTag tag){
        static FluidKey of(FluidStack stack){return stack.isEmpty()?null:new FluidKey(stack.getFluid(),stack.hasTag()?stack.getTag().copy():null);}
        FluidStack stack(int amount){var stack=new FluidStack(fluid,amount);if(tag!=null)stack.setTag(tag.copy());return stack;}
    }
    private static final class Node {
        final CopperMachineBlockEntity machine;final UUID identity=UUID.randomUUID();FluidKey key;UUID owner;TankView sent;long revision;
        Node(CopperMachineBlockEntity m){machine=m;key=FluidKey.of(m.water.getFluid());owner=m.owner();}
    }
    private static final class Component {
        final List<Node> members;final UUID owner;final boolean limited;final long built;
        final Map<BlockPos,List<Node>> groups=new HashMap<>();boolean dirty;
        Component(List<Node> members,UUID owner,boolean limited,long built){this.members=List.copyOf(members);this.owner=owner;this.limited=limited;this.built=built;}
    }
    private static final class World {
        final Map<BlockPos,Node> nodes=new HashMap<>();final Map<BlockPos,Component> cache=new HashMap<>();
        final Map<Long,Set<BlockPos>> chunks=new HashMap<>();final Map<Long,Long> unloading=new HashMap<>();
        final LinkedHashSet<BlockPos> pending=new LinkedHashSet<>(),quantities=new LinkedHashSet<>();
        long revision;
    }
    private record Group(World world,ServerLevel level,List<Node> nodes,Component component,TankView.State state){}
    private static final Map<ServerLevel,World> WORLDS=new IdentityHashMap<>();
    private static int worldCursor;
    private TankNetworks(){}
    private static boolean tank(CopperMachineBlockEntity m){return m.kind==MachineKind.COPPER_FLUID_TANK;}
    private static ServerLevel server(CopperMachineBlockEntity m){
        return m.getLevel() instanceof ServerLevel l&&l.getServer().isSameThread()?l:null;
    }
    private static boolean loaded(ServerLevel level,Node node){
        var m=node.machine;if(m.isRemoved()||m.tankUnloaded||m.getLevel()!=level)return false;
        var p=m.getBlockPos();var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
        return chunk!=null&&chunk.getBlockEntity(p)==m;
    }
    private static void dirty(World world,Component c){
        if(c==null)return;c.dirty=true;for(var n:c.members)world.pending.add(n.machine.getBlockPos());
    }
    public static void register(CopperMachineBlockEntity m){
        var level=server(m);if(!tank(m)||level==null||m.isRemoved()||m.tankUnloaded)return;
        var world=WORLDS.computeIfAbsent(level,key->new World());var p=m.getBlockPos();
        long chunkId=net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);
        if(world.unloading.getOrDefault(chunkId,Long.MIN_VALUE)>=level.getGameTime())return;
        var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);if(chunk==null||chunk.getBlockEntity(p)!=m)return;
        var old=world.nodes.get(p);if(old!=null&&old.machine==m)return;
        var node=new Node(m);node.revision=++world.revision;world.nodes.put(p,node);dirty(world,world.cache.get(p));
        world.chunks.computeIfAbsent(chunkId,key->new HashSet<>()).add(p);
        for(var d:Direction.values())dirty(world,world.cache.get(p.relative(d)));world.pending.add(p);
    }
    public static void remove(CopperMachineBlockEntity m){
        var level=server(m);if(level==null)return;var world=WORLDS.get(level);if(world==null)return;
        var p=m.getBlockPos();var old=world.nodes.get(p);if(old==null||old.machine!=m)return;
        world.nodes.remove(p);dirty(world,world.cache.remove(p));world.pending.remove(p);world.quantities.remove(p);
        long chunkId=net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);var chunk=world.chunks.get(chunkId);
        if(chunk!=null){chunk.remove(p);if(chunk.isEmpty())world.chunks.remove(chunkId);}
        for(var d:Direction.values())dirty(world,world.cache.get(p.relative(d)));
    }
    public static void changed(CopperMachineBlockEntity m){
        var level=server(m);if(!tank(m)||level==null||m.isRemoved()||m.tankUnloaded)return;register(m);var world=WORLDS.get(level);if(world==null)return;
        var p=m.getBlockPos();var n=world.nodes.get(p);if(n==null||n.machine!=m)return;
        n.revision=++world.revision;var key=FluidKey.of(m.water.getFluid());var c=world.cache.get(p);boolean ownerChanged=!Objects.equals(n.owner,m.owner());
        boolean structureChanged=!Objects.equals(key,n.key)||ownerChanged;
        if(structureChanged){n.key=key;n.owner=m.owner();dirty(world,c);}
        if(ownerChanged)for(var d:Direction.values())dirty(world,world.cache.get(p.relative(d)));
        if(c==null||c.dirty||structureChanged){world.pending.add(p);return;}
        // One representative per watched chunk updates the shared client plane, including off-screen cubes.
        var chunks=new HashSet<Long>();for(var node:c.groups.get(p)){
            var position=node.machine.getBlockPos();long chunk=net.minecraft.world.level.ChunkPos.asLong(position.getX()>>4,position.getZ()>>4);
            if(chunks.add(chunk))world.quantities.add(position);
        }
    }
    private static boolean allowed(ServerLevel level,Node node,UUID owner,Map<Long,Boolean> permissions){
        if(!loaded(level,node)||!Objects.equals(owner,node.machine.owner()))return false;
        var p=node.machine.getBlockPos();long chunk=net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);
        return permissions.computeIfAbsent(chunk,key->ProtectionPolicy.allowed(level,p,owner));
    }
    private static Component build(World world,ServerLevel level,Node root){
        UUID owner=root.machine.owner();var permissions=new HashMap<Long,Boolean>();
        var seen=new LinkedHashMap<BlockPos,Node>();var queue=new ArrayDeque<Node>();
        seen.put(root.machine.getBlockPos(),root);queue.add(root);boolean limited=false;
        search:while(!queue.isEmpty()){
            var current=queue.removeFirst();for(var d:Direction.values()){
                var p=current.machine.getBlockPos().relative(d);var next=world.nodes.get(p);
                if(next==null||seen.containsKey(p)||!allowed(level,next,owner,permissions))continue;
                seen.put(p,next);if(seen.size()>TankTopology.MAX_BLOCKS){limited=true;break search;}queue.addLast(next);
            }
        }
        var members=seen.values().stream().sorted(Comparator.comparing(n->n.machine.getBlockPos(),TankTopology.ORDER)).toList();
        var component=new Component(members,owner,limited,level.getGameTime());
        if(limited){for(var node:members)component.groups.put(node.machine.getBlockPos(),List.of(node));}
        else{
            var contents=new HashMap<BlockPos,FluidKey>();for(var n:members)contents.put(n.machine.getBlockPos(),n.key);
            for(var positions:TankTopology.partition(contents)){
                var group=positions.stream().map(world.nodes::get).toList();
                // Malformed legacy totals are kept per cube rather than overflowing an aggregate int.
                long amount=group.stream().mapToLong(n->n.machine.water.getFluidAmount()).sum();
                for(var p:positions)component.groups.put(p,amount>Integer.MAX_VALUE?List.of(world.nodes.get(p)):group);
            }
        }
        for(var n:members){
            var p=n.machine.getBlockPos();var previous=world.cache.put(p,component);
            if(previous==null||previous.dirty||previous.limited!=limited||!Objects.equals(previous.owner,owner)
                    ||!Objects.equals(previous.groups.get(p),component.groups.get(p)))world.pending.add(p);
        }
        return component;
    }
    private static Group group(CopperMachineBlockEntity m){
        var level=server(m);if(level==null||!tank(m)||m.isRemoved()||m.tankUnloaded)return null;
        register(m);var world=WORLDS.get(level);if(world==null)return null;var p=m.getBlockPos();var root=world.nodes.get(p);
        if(root==null||root.machine!=m||!loaded(level,root))return null;
        if(!ProtectionPolicy.allowed(level,p,m.owner()))return new Group(world,level,List.of(root),null,TankView.State.PROTECTED);
        var c=world.cache.get(p);
        if(c==null||c.dirty||level.getGameTime()-c.built>=20)c=build(world,level,root);
        return new Group(world,level,c.groups.get(p),c,c.limited?TankView.State.LIMIT:TankView.State.READY);
    }
    private static boolean valid(Group g){
        if(g==null||g.state==TankView.State.PROTECTED)return false;
        var checks=new HashMap<Long,Boolean>();UUID owner=g.nodes.get(0).machine.owner();
        for(var node:g.nodes)if(!allowed(g.level,node,owner,checks)){dirty(g.world,g.component);return false;}
        return true;
    }
    private static int[] amounts(Group group){return group.nodes.stream().mapToInt(n->n.machine.water.getFluidAmount()).toArray();}
    private static FluidKey key(Group group){for(var n:group.nodes)if(n.key!=null)return n.key;return null;}
    private static FluidStack contents(Group group){var key=key(group);return key==null?FluidStack.EMPTY:key.stack(TankVolumes.total(amounts(group)));}
    public static TankView view(CopperMachineBlockEntity m){
        var g=group(m);if(g==null)return m.tankView;
        var positions=g.nodes.stream().map(n->n.machine.getBlockPos()).toList();int amount=TankVolumes.total(amounts(g));
        var fluid=contents(g);var id=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        return new TankView(amount,g.nodes.size(),TankTopology.connections(m.getBlockPos(),positions),TankTopology.corners(m.getBlockPos(),positions),m.getBlockPos().equals(positions.get(0)),
                TankVolumes.fillAt(positions,m.getBlockPos(),amount),id==null?"minecraft:empty":id.toString(),g.state,
                g.nodes.get(0).identity,TankTopology.shape(positions),g.nodes.stream().mapToLong(n->n.revision).max().orElse(0),TankVolumes.surface(positions,amount));
    }
    private static void apply(Group g,FluidKey fluid,TankVolumes.Plan plan){
        int[] after=plan.amounts();for(int i=0;i<after.length;i++)if(g.nodes.get(i).machine.water.getFluidAmount()!=after[i])
            g.nodes.get(i).machine.water.setFluid(after[i]==0?FluidStack.EMPTY:fluid.stack(after[i]));
    }
    public static Storage handler(CopperMachineBlockEntity m){return new Storage(){
        @Override public boolean fillWhole(FluidStack stack){
            if(stack.isEmpty())return false;var g=group(m);if(!valid(g))return false;var fluid=FluidKey.of(stack);
            if(key(g)!=null&&!key(g).equals(fluid))return false;var plan=TankVolumes.fill(amounts(g),stack.getAmount());
            if(plan.moved()!=stack.getAmount())return false;apply(g,fluid,plan);return true;
        }
        @Override public boolean drainWhole(FluidStack stack){
            if(stack.isEmpty())return false;var g=group(m);if(!valid(g)||!Objects.equals(key(g),FluidKey.of(stack)))return false;
            var plan=TankVolumes.drain(amounts(g),stack.getAmount());if(plan.moved()!=stack.getAmount())return false;
            apply(g,key(g),plan);return true;
        }
        @Override public int getTanks(){return m.isRemoved()||m.tankUnloaded?0:1;}
        @Override public FluidStack getFluidInTank(int tank){
            if(tank!=0||m.isRemoved()||m.tankUnloaded)return FluidStack.EMPTY;var g=group(m);if(g!=null)return valid(g)?contents(g):FluidStack.EMPTY;
            if(m.getLevel() instanceof ServerLevel)return FluidStack.EMPTY;
            return m.visualFluid==net.minecraft.world.level.material.Fluids.EMPTY?FluidStack.EMPTY:new FluidStack(m.visualFluid,m.tankView.amount());
        }
        @Override public int getTankCapacity(int tank){return tank==0&&!m.isRemoved()&&!m.tankUnloaded?view(m).capacity():0;}
        @Override public boolean isFluidValid(int tank,FluidStack stack){
            if(tank!=0||stack.isEmpty())return false;var g=group(m);return valid(g)&&(key(g)==null||key(g).equals(FluidKey.of(stack)));
        }
        @Override public int fill(FluidStack stack,FluidAction action){
            if(stack.isEmpty())return 0;var g=group(m);if(!valid(g))return 0;var key=FluidKey.of(stack);
            if(key(g)!=null&&!key(g).equals(key))return 0;var plan=TankVolumes.fill(amounts(g),stack.getAmount());
            if(action.execute()&&plan.moved()>0)apply(g,key,plan);return plan.moved();
        }
        @Override public FluidStack drain(FluidStack stack,FluidAction action){
            if(stack.isEmpty())return FluidStack.EMPTY;var g=group(m);if(!valid(g)||!Objects.equals(key(g),FluidKey.of(stack)))return FluidStack.EMPTY;
            return drain(g,stack.getAmount(),action);
        }
        @Override public FluidStack drain(int amount,FluidAction action){var g=group(m);return valid(g)?drain(g,amount,action):FluidStack.EMPTY;}
        private FluidStack drain(Group g,int requested,FluidAction action){
            var key=key(g);if(key==null||requested<=0)return FluidStack.EMPTY;var plan=TankVolumes.drain(amounts(g),requested);
            if(action.execute()&&plan.moved()>0)apply(g,key,plan);return key.stack(plan.moved());
        }
    };}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(var e:WORLDS.entrySet())e.getValue().unloading.values().removeIf(until->until<e.getKey().getGameTime());
        var active=WORLDS.entrySet().stream().filter(e->!e.getValue().pending.isEmpty()||!e.getValue().quantities.isEmpty())
                .sorted(Comparator.comparing((Map.Entry<ServerLevel,World> e)->e.getKey().dimension().location().toString())).toList();
        if(active.isEmpty())return;int budget=64,empty=0,index=Math.floorMod(worldCursor,active.size());
        while(budget>0&&empty<active.size()){
            var entry=active.get(index);index=(index+1)%active.size();var level=entry.getKey();var world=entry.getValue();
            if(world.pending.isEmpty()&&world.quantities.isEmpty()){empty++;continue;}empty=0;budget--;
            boolean structural=!world.pending.isEmpty();var queue=structural?world.pending:world.quantities;
            var p=queue.iterator().next();queue.remove(p);
            var node=world.nodes.get(p);if(node==null||!loaded(level,node))continue;
            var snapshot=view(node.machine);if(structural?!sameStructure(snapshot,node.sent):!snapshot.equals(node.sent)){
                node.sent=snapshot;level.sendBlockUpdated(p,node.machine.getBlockState(),node.machine.getBlockState(),2);
            }
        }
        worldCursor=index;
    }
    private static boolean sameStructure(TankView a,TankView b){return b!=null&&a.identity().equals(b.identity())&&a.shape()==b.shape()&&a.blocks()==b.blocks()
            &&a.connections()==b.connections()&&a.corners()==b.corners()&&a.controller()==b.controller()&&a.state()==b.state()&&a.fluid().equals(b.fluid());}
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event){
        if(!(event.getLevel() instanceof ServerLevel level))return;var world=WORLDS.get(level);if(world==null)return;
        long key=event.getChunk().getPos().toLong();world.unloading.put(key,level.getGameTime()+1);
        var positions=world.chunks.get(key);if(positions!=null)for(var p:List.copyOf(positions)){var node=world.nodes.get(p);if(node!=null)remove(node.machine);}
    }
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event){
        if(event.getLevel() instanceof ServerLevel level&&WORLDS.containsKey(level))WORLDS.get(level).unloading.remove(event.getChunk().getPos().toLong());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)WORLDS.remove(level);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){WORLDS.clear();worldCursor=0;}
}
