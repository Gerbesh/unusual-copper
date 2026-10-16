package io.github.gerbesh.unusualcopper.compat.ssn;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Public APIs of the tested SSN 1.13.2 build only. No terminal/client access or bulk item enumeration. */
public final class SsnAccess {
    public static final int NODE_LIMIT=4096;
    public record Root(BlockPos main,Object network,long revision){}
    public record Port(BlockPos link,BlockPos target,Direction side,int priority){}
    public record Topology(List<Port> ports,boolean complete){public Topology{ports=List.copyOf(ports);}}
    public record Live(Object link,BlockEntity linkEntity,BlockEntity targetEntity,String direction){}
    private static boolean initialized,supported;
    private static Class<?> mainType,linkType;
    private static Capability<Object> connectable,storage;
    private static Method mainPos,nodePos,world,pos,network,refreshing,nodes,size,target,priority,direction,filter,filtered;
    @SuppressWarnings("unchecked") private static void initialize(){
        if(initialized)return;initialized=true;var mods=ModList.get();
        if(!mods.getModContainerById("storagenetwork").map(c->c.getModInfo().getVersion().toString().equals("1.13.2")).orElse(false))return;
        if(mods.isLoaded("ssn_progressive")&&!mods.getModContainerById("ssn_progressive").map(c->java.util.Set.of("1.2.4","1.3.0","1.3.1","1.4.0","1.4.1").contains(c.getModInfo().getVersion().toString())).orElse(false))return;
        try{
            mainType=Class.forName("com.lothrazar.storagenetwork.block.main.TileMain");linkType=Class.forName("com.lothrazar.storagenetwork.capability.CapabilityConnectableLink");
            var caps=Class.forName("com.lothrazar.storagenetwork.registry.StorageNetworkCapabilities");connectable=(Capability<Object>)caps.getField("CONNECTABLE_CAPABILITY").get(null);storage=(Capability<Object>)caps.getField("CONNECTABLE_ITEM_STORAGE_CAPABILITY").get(null);
            var node=Class.forName("com.lothrazar.storagenetwork.api.IConnectable");mainPos=node.getMethod("getMainPos");nodePos=node.getMethod("getPos");
            var dim=Class.forName("com.lothrazar.storagenetwork.api.DimPos");world=dim.getMethod("getWorld");pos=dim.getMethod("getBlockPos");
            network=mainType.getMethod("getNetwork");var nw=Class.forName("com.lothrazar.storagenetwork.block.main.NetworkModule");refreshing=nw.getMethod("shouldRefresh");nodes=nw.getMethod("getConnectables");size=nw.getMethod("getConnectableSize");nw.getMethod("doRefresh",dim);
            var port=Class.forName("com.lothrazar.storagenetwork.api.IConnectableLink");target=port.getMethod("getTargetPos");priority=port.getMethod("getPriority");direction=port.getMethod("getSupportedTransferDirection");filter=linkType.getMethod("getFilter");filtered=filter.getReturnType().getMethod("isStackFiltered",ItemStack.class);
            supported=true;
        }catch(ReflectiveOperationException|LinkageError incompatible){UnusualCopper.LOGGER.error("SSN 1.13.2 bridge API unavailable; the optional bridge is disabled",incompatible);}
    }
    public static boolean supported(){initialize();return supported;}
    private static BlockPos position(Object dim,ServerLevel level)throws ReflectiveOperationException{return dim!=null&&world.invoke(dim)==level?(BlockPos)pos.invoke(dim):null;}
    /** Server-side topology identifier only: no inventory access or protection-provider initialization on chunk load. */
    public static BlockPos controllerPosition(ServerLevel level,BlockPos port)throws ReflectiveOperationException{
        if(!supported()||!level.hasChunkAt(port))return null;var entity=level.getBlockEntity(port);if(entity==null||entity.isRemoved())return null;if(mainType.isInstance(entity))return port.immutable();var node=entity.getCapability(connectable,null).orElse(null);return node==null?null:position(mainPos.invoke(node),level);
    }
    public static Root root(ServerLevel level,BlockPos port,UUID owner)throws ReflectiveOperationException{
        if(!supported()||!level.hasChunkAt(port)||!ProtectionPolicy.allowed(level,port,owner))return null;var entity=level.getBlockEntity(port);if(entity==null||entity.isRemoved())return null;
        BlockPos main=port;
        if(!mainType.isInstance(entity)){var node=entity.getCapability(connectable,null).orElse(null);if(node==null)return null;main=position(mainPos.invoke(node),level);}
        if(main==null||!level.hasChunkAt(main)||!ProtectionPolicy.allowed(level,main,owner))return null;var master=level.getBlockEntity(main);if(!mainType.isInstance(master)||master.isRemoved())return null;
        var nw=network.invoke(master);if(!(nw instanceof SsnNetworkRevision revision)||(boolean)refreshing.invoke(nw))return null;
        return new Root(main.immutable(),nw,revision.unusualcopper$networkRevision());
    }
    public static Topology topology(ServerLevel level,Root root)throws ReflectiveOperationException{
        int count=(int)size.invoke(root.network);if(count<0||count>NODE_LIMIT)return new Topology(List.of(),false);
        var connected=(Set<?>)nodes.invoke(root.network);if(connected.size()>NODE_LIMIT)return new Topology(List.of(),false);
        var positions=new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));boolean complete=connected.size()==count;
        for(var node:connected){var here=position(nodePos.invoke(node),level);if(here==null){complete=false;continue;}positions.add(here.immutable());}
        var result=new ArrayList<Port>();var targets=new HashSet<BlockPos>();
        for(var here:positions){
            if(!level.hasChunkAt(here)){complete=false;continue;}var entity=level.getBlockEntity(here);if(entity==null){complete=false;continue;}var link=entity.getCapability(storage,null).orElse(null);if(link==null)continue;
            if(link.getClass()!=linkType){complete=false;continue;}var to=position(target.invoke(link),level);if(to==null){complete=false;continue;}
            var face=Direction.fromDelta(to.getX()-here.getX(),to.getY()-here.getY(),to.getZ()-here.getZ());if(face==null){complete=false;continue;}
            BlockPos canonical=to;
            if(level.hasChunkAt(to)){var state=level.getBlockState(to);if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){var second=to.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));if(second.asLong()<canonical.asLong())canonical=second;}}
            else complete=false;
            if(targets.add(canonical))result.add(new Port(here.immutable(),to.immutable(),face.getOpposite(),(int)priority.invoke(link)));
        }
        result.sort(Comparator.comparingInt(Port::priority).thenComparingLong(p->p.link.asLong()));return new Topology(result,complete);
    }
    public static Live live(ServerLevel level,Root root,Port port,UUID owner)throws ReflectiveOperationException{
        if(!(root.network instanceof SsnNetworkRevision version)||version.unusualcopper$networkRevision()!=root.revision||(boolean)refreshing.invoke(root.network))return null;
        if(!level.hasChunkAt(port.link)||!level.hasChunkAt(port.target)||!ProtectionPolicy.allowed(level,port.link,owner)||!ProtectionPolicy.allowed(level,port.target,owner))return null;
        var entity=level.getBlockEntity(port.link);var inventory=level.getBlockEntity(port.target);if(entity==null||inventory==null||inventory.isRemoved())return null;
        var node=entity.getCapability(connectable,null).orElse(null);var link=entity.getCapability(storage,null).orElse(null);if(node==null||link==null||link.getClass()!=linkType||!root.main.equals(position(mainPos.invoke(node),level))||!port.target.equals(position(target.invoke(link),level)))return null;
        return new Live(link,entity,inventory,((Enum<?>)direction.invoke(link)).name());
    }
    public static boolean accepts(Live live,ItemStack stack)throws ReflectiveOperationException{return !(boolean)filtered.invoke(filter.invoke(live.link),stack.copy());}
    public static boolean reads(Live live){return live.direction.equals("BOTH")||live.direction.equals("OUT");}
    // The pinned native link's legacy IN state rejects insertStack; its GUI exposes BOTH/OUT.
    public static boolean writes(Live live){return live.direction.equals("BOTH");}
    private SsnAccess(){}
}
