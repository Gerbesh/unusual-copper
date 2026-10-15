package io.github.gerbesh.unusualcopper.compat.ssn;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;

/** Logical SSN endpoint. Every physical operation still passes through the loaded protected Forge handler. */
public final class SsnStorage implements StorageEndpoint {
    private final ServerLevel level;private final BlockPos bridge;private final UUID actor;private BlockPos knownMain;
    public SsnStorage(ServerLevel level,BlockPos bridge,UUID actor){this.level=level;this.bridge=bridge.immutable();this.actor=actor;}
    private State resolve(){if(!level.hasChunkAt(bridge)||!ProtectionPolicy.allowed(level,bridge,actor))return null;return level.getBlockEntity(bridge) instanceof SsnBridgeBlockEntity be&&!be.isRemoved()&&be.allows(actor)&&be.enabled()?be.state(actor):null;}
    @Override public boolean isAvailable(){var state=resolve();return state!=null&&state.available();}
    @Override public StorageIdentity identity(){
        if(level.hasChunkAt(bridge)&&level.getBlockEntity(bridge) instanceof SsnBridgeBlockEntity be){
            if(be.knownMain()!=null)knownMain=be.knownMain();
            try{var current=SsnAccess.controllerPosition(level,bridge.relative(be.getBlockState().getValue(SsnBridgeBlock.FACING)));if(current!=null)knownMain=current.immutable();}catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        return new StorageIdentity(level.dimension(),knownMain==null?bridge:knownMain,Direction.UP);
    }
    @Override public View snapshot(Predicate<ItemStack> filter,int limit){var state=resolve();return state==null?new View(List.of(),0,false):state.snapshot(filter,limit);}
    @Override public int simulateExtract(ItemKey key,int amount){var state=resolve();return state==null?0:state.extract(key,amount,true).stream().mapToInt(ItemStack::getCount).sum();}
    @Override public List<ItemStack> extract(ItemKey key,int amount){var state=resolve();return state==null?List.of():state.extract(key,amount,false);}
    @Override public ItemStack insert(ItemStack stack,boolean simulate){var state=resolve();return state==null?stack.copy():state.insert(stack,simulate);}
    @Override public Count count(ItemKey key){var state=resolve();return state==null?new Count(0,false):state.count(key);}
    @Override public Space space(){var state=resolve();return state==null?Space.UNAVAILABLE:state.space();}

    static final class Source {
        final SsnAccess.Port port;final HandlerStorageEndpoint endpoint;
        Source(ServerLevel level,SsnAccess.Port port,UUID actor){this.port=port;endpoint=new HandlerStorageEndpoint(level,port.target(),port.side(),actor);}
    }
    private record Stamp(InventoryChanges.Token link,InventoryChanges.Token target,long targetRevision,InventoryChanges.Token partner,long partnerRevision,WeakReference<IItemHandler> capability,int slots,boolean readable){
        // Native refresh marks unchanged links dirty. Their target, role and key-specific filter are checked live;
        // inventory revisions and handler identity, rather than that bookkeeping dirty mark, govern item counts.
        boolean same(Stamp other){return other!=null&&link==other.link&&target==other.target&&partner==other.partner&&targetRevision==other.targetRevision&&partnerRevision==other.partnerRevision&&capability.get()!=null&&capability.get()==other.capability.get()&&slots==other.slots&&readable==other.readable;}
    }
    private static final class Total {Stamp stamp;long amount;boolean complete;}
    private static final class Row {final Total[] totals;boolean complete;int cursor;Row(int size){totals=new Total[size];for(int i=0;i<size;i++)totals[i]=new Total();}}
    static final class State {
        private static final int PORT_BUDGET=8,KEY_LIMIT=16;
        private final ServerLevel level;private final BlockPos port;private final UUID actor;private final java.util.function.Consumer<BlockPos> connected;
        private WeakReference<Object> network=new WeakReference<>(null);
        private long revision=Long.MIN_VALUE,built=Long.MIN_VALUE,workTick=Long.MIN_VALUE,warned=Long.MIN_VALUE;
        private int work,scanCursor,spaceCursor,spaceVisited,rowCursor,insertionCursor,simulationCursor,simulatedStart=-1;
        private ItemKey simulatedKey;private long simulatedTick;
        private final Map<ItemKey,Integer> extractionCursors=new LinkedHashMap<>();
        private final LinkedHashMap<ItemKey,Integer> hints=new LinkedHashMap<>(64,.75F,true);
        private final LinkedHashMap<ItemKey,Row> rows=new LinkedHashMap<>(16,.75F,true);
        private List<Source> sources=List.of();private boolean catalogComplete;private BlockPos main;
        State(ServerLevel level,BlockPos port,UUID actor,java.util.function.Consumer<BlockPos> connected){this.level=level;this.port=port.immutable();this.actor=actor;this.connected=connected;}
        private void failed(Exception failure){long tick=level.getGameTime();if(warned==Long.MIN_VALUE||tick-warned>=100){warned=tick;UnusualCopper.LOGGER.warn("Optional SSN bridge operation paused at {}",port,failure);}}
        private SsnAccess.Root prepare()throws ReflectiveOperationException{
            var root=SsnAccess.root(level,port,actor);if(root==null)return null;long tick=level.getGameTime();
            if(network.get()!=root.network()||revision!=root.revision()||built==Long.MIN_VALUE||tick-built>=20){
                var topology=SsnAccess.topology(level,root);var refs=sources.stream().map(s->s.port).toList();
                if(network.get()!=root.network()||!refs.equals(topology.ports())){sources=topology.ports().stream().map(p->new Source(level,p,actor)).toList();rows.clear();extractionCursors.clear();hints.clear();scanCursor=spaceCursor=spaceVisited=insertionCursor=simulationCursor=0;simulatedStart=-1;simulatedKey=null;}
                network=new WeakReference<>(root.network());revision=root.revision();main=root.main();connected.accept(main);catalogComplete=topology.complete();built=tick;
            }
            if(workTick!=tick){workTick=tick;work=0;}return root;
        }
        boolean available(){try{return prepare()!=null;}catch(ReflectiveOperationException|RuntimeException failure){failed(failure);return false;}}
        private SsnAccess.Live live(SsnAccess.Root root,Source source)throws ReflectiveOperationException{var live=SsnAccess.live(level,root,source.port,actor);return live!=null&&source.endpoint.isAvailable()?live:null;}
        private Stamp stamp(Source source,SsnAccess.Live live,ItemKey key)throws ReflectiveOperationException{
            var target=source.port.target();var partner=target;var block=level.getBlockState(target);if(block.getBlock() instanceof ChestBlock&&block.getValue(ChestBlock.TYPE)!=ChestType.SINGLE)partner=target.relative(ChestBlock.getConnectedDirection(block));
            if(!level.hasChunkAt(partner)||!ProtectionPolicy.allowed(level,partner,actor))return null;var other=level.getBlockEntity(partner);if(other==null)return null;var cap=live.targetEntity().getCapability(ForgeCapabilities.ITEM_HANDLER,source.port.side()).orElse(null);if(cap==null)return null;
            var a=InventoryChanges.watch(level,live.linkEntity());var b=InventoryChanges.watch(level,live.targetEntity());var c=InventoryChanges.watch(level,other);
            return new Stamp(a,b,b.revision(),c,c.revision(),new WeakReference<>(cap),cap.getSlots(),SsnAccess.reads(live)&&SsnAccess.accepts(live,key.stack(1)));
        }
        Count count(ItemKey key){
            try{
                var root=prepare();if(root==null)return new Count(0,false);
                if(!rows.containsKey(key)&&rows.size()>=KEY_LIMIT)rows.remove(rows.keySet().iterator().next());var row=rows.computeIfAbsent(key,ignored->new Row(sources.size()));
                long amount=0;boolean complete=catalogComplete;
                int start=row.cursor;for(int n=0;n<sources.size();n++){
                    int i=(start+n)%sources.size();
                    var source=sources.get(i);var live=live(root,source);var total=row.totals[i];
                    if(live==null){total.complete=false;complete=false;continue;}var before=stamp(source,live,key);if(before==null){total.complete=false;complete=false;continue;}
                    if(!before.same(total.stamp))total.complete=false;
                    if(!total.complete&&work<PORT_BUDGET){work++;row.cursor=(i+1)%sources.size();var result=before.readable?source.endpoint.count(key):new Count(0,true);var afterLive=live(root,source);var after=afterLive==null?null:stamp(source,afterLive,key);total.stamp=after;total.amount=result.amount();total.complete=result.complete()&&before.same(after);}
                    if(total.complete)amount+=total.amount;else complete=false;
                }
                row.complete=complete;return new Count(amount,complete);
            }catch(ReflectiveOperationException|RuntimeException failure){failed(failure);return new Count(0,false);}
        }
        void advance(){if(rows.isEmpty())return;var pending=rows.entrySet().stream().filter(e->!e.getValue().complete).map(Map.Entry::getKey).toList();if(!pending.isEmpty())count(pending.get(Math.floorMod(rowCursor++,pending.size())));}
        View snapshot(Predicate<ItemStack> predicate,int limit){
            try{
                var root=prepare();int budget=Math.min(32,Math.max(0,limit));if(root==null||sources.isEmpty()||budget==0)return new View(List.of(),0,root!=null&&catalogComplete&&sources.isEmpty());
                var candidates=new LinkedHashSet<Integer>();int bestPriority=sources.get(0).port.priority();
                for(var hint:hints.entrySet())if(hint.getValue()<sources.size()&&sources.get(hint.getValue()).port.priority()==bestPriority&&predicate.test(hint.getKey().stack(1))){candidates.add(hint.getValue());if(candidates.size()>=PORT_BUDGET)break;}
                for(int checked=0;checked<sources.size()&&candidates.size()<PORT_BUDGET;checked++)candidates.add(Math.floorMod(scanCursor++,sources.size()));
                var entries=new ArrayList<Entry>();int visited=0;boolean complete=catalogComplete;
                for(int index:candidates){if(entries.size()>=budget){complete=false;break;}visited++;var source=sources.get(index);var live=live(root,source);if(live==null){complete=false;continue;}if(!SsnAccess.reads(live))continue;
                    var view=source.endpoint.snapshot(stack->true,budget-entries.size());complete&=view.complete();
                    for(var entry:view.entries()){
                        var stack=entry.key().stack(entry.count());if(!SsnAccess.accepts(live,stack))continue;
                        Integer prior=hints.get(entry.key());if(prior==null||sources.get(index).port.priority()<=sources.get(prior).port.priority()){
                            if(hints.size()>=64&&!hints.containsKey(entry.key()))hints.remove(hints.keySet().iterator().next());hints.put(entry.key(),index);
                        }
                        if(predicate.test(stack)){entries.add(entry);if(extractionCursors.size()>=64&&!extractionCursors.containsKey(entry.key()))extractionCursors.remove(extractionCursors.keySet().iterator().next());extractionCursors.put(entry.key(),index);}
                    }
                }
                return new View(entries,Math.floorMod(scanCursor,sources.size()),complete&&visited==sources.size());
            }catch(ReflectiveOperationException|RuntimeException failure){failed(failure);return new View(List.of(),0,false);}
        }
        List<ItemStack> extract(ItemKey key,int amount,boolean simulate){
            try{
                var root=prepare();if(root==null||sources.isEmpty()||amount<=0)return List.of();int cursor=extractionCursors.getOrDefault(key,0);
                for(int n=0;n<PORT_BUDGET&&cursor<sources.size();n++,cursor++){
                    var source=sources.get(cursor);var live=live(root,source);if(live==null||!SsnAccess.reads(live)||!SsnAccess.accepts(live,key.stack(1)))continue;
                    int possible=source.endpoint.simulateExtract(key,amount);if(possible<=0)continue;
                    if(extractionCursors.size()>=64&&!extractionCursors.containsKey(key))extractionCursors.remove(extractionCursors.keySet().iterator().next());extractionCursors.put(key,cursor);
                    if(simulate)return List.of(key.stack(possible));
                    var current=live(root,source);if(current==null||!SsnAccess.reads(current)||!SsnAccess.accepts(current,key.stack(1)))continue;
                    var actual=source.endpoint.extract(key,Math.min(possible,amount));extractionCursors.put(key,0);return actual;
                }
                if(extractionCursors.size()>=64&&!extractionCursors.containsKey(key))extractionCursors.remove(extractionCursors.keySet().iterator().next());extractionCursors.put(key,cursor>=sources.size()?0:cursor);return List.of();
            }catch(ReflectiveOperationException|RuntimeException failure){failed(failure);return List.of();}
        }
        ItemStack insert(ItemStack stack,boolean simulate){
            var remaining=stack.copy();
            try{
                var root=prepare();if(root==null||remaining.isEmpty())return remaining;
                int start;
                if(simulate){
                    // Failed probes must keep paging across large networks without moving the real transfer cursor.
                    if(simulatedKey==null||!simulatedKey.matches(stack))simulationCursor=insertionCursor;
                    start=simulationCursor;
                }else{
                    start=simulatedStart>=0&&simulatedKey!=null&&simulatedTick==level.getGameTime()&&simulatedKey.matches(stack)?simulatedStart:insertionCursor;
                }
                int i=start;for(int n=0;n<PORT_BUDGET&&i<sources.size()&&!remaining.isEmpty();n++,i++){
                    var source=sources.get(i);var live=live(root,source);if(live!=null&&SsnAccess.writes(live)&&SsnAccess.accepts(live,remaining))remaining=source.endpoint.insert(remaining,simulate);
                }
                if(simulate){
                    simulatedKey=ItemKey.of(stack);simulatedTick=level.getGameTime();
                    if(remaining.getCount()<stack.getCount()){simulatedStart=start;simulationCursor=start;}
                    else{simulatedStart=-1;simulationCursor=i>=sources.size()?0:i;}
                }else{
                    simulatedStart=-1;simulatedKey=null;
                    insertionCursor=remaining.getCount()<stack.getCount()?0:i>=sources.size()?0:i;
                    simulationCursor=insertionCursor;
                }
                return remaining;
            }catch(ReflectiveOperationException|RuntimeException failure){failed(failure);return remaining;}
        }
        Space space(){
            try{
                var root=prepare();if(root==null)return Space.UNAVAILABLE;if(sources.isEmpty())return catalogComplete?Space.FULL:Space.UNAVAILABLE;boolean scanning=!catalogComplete;
                for(int i=0;i<Math.min(PORT_BUDGET,sources.size());i++){var source=sources.get(Math.floorMod(spaceCursor++,sources.size()));spaceVisited++;var live=live(root,source);if(live==null){scanning=true;continue;}if(!SsnAccess.writes(live))continue;var result=source.endpoint.space();if(result==Space.AVAILABLE){spaceVisited=0;return result;}if(result==Space.SCANNING||result==Space.UNAVAILABLE)scanning=true;}
                if(spaceVisited>=sources.size()){spaceVisited=0;return scanning?Space.SCANNING:Space.FULL;}return Space.SCANNING;
            }catch(ReflectiveOperationException|RuntimeException failure){failed(failure);return Space.UNAVAILABLE;}
        }
    }
}
