package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

/** Cosmetic server-to-client route samples only. No item NBT, amount, entities or client requests. */
public final class ItemTransitNetwork {
    public record Trail(ResourceLocation dimension,int item,List<BlockPos> path){
        public static final int MAX_NODES=258;
        public Trail{if(item<0||path.size()<3||path.size()>MAX_NODES)throw new IllegalArgumentException("Invalid item trail");path=path.stream().map(BlockPos::immutable).toList();for(int i=1;i<path.size();i++){var a=path.get(i-1);var b=path.get(i);long distance=Math.abs((long)a.getX()-b.getX())+Math.abs((long)a.getY()-b.getY())+Math.abs((long)a.getZ()-b.getZ());if(distance!=1)throw new IllegalArgumentException("Disconnected item trail");}}
        public int duration(){return Math.max(20,Math.min(160,(path.size()-1)*4));}
        public static void encode(Trail t,FriendlyByteBuf b){b.writeResourceLocation(t.dimension);b.writeVarInt(t.item);b.writeVarInt(t.path.size());for(var p:t.path)b.writeBlockPos(p);}
        public static Trail decode(FriendlyByteBuf b){var dim=b.readResourceLocation();int item=b.readVarInt(),n=b.readVarInt();if(n<3||n>MAX_NODES)throw new IllegalArgumentException("Too many trail nodes");var path=new ArrayList<BlockPos>();for(int i=0;i<n;i++)path.add(b.readBlockPos());return new Trail(dim,item,path);}
    }
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"item_transit"),()->"1","1"::equals,"1"::equals);
    private static Consumer<Trail> receiver=ignored->{};
    public static void register(){CHANNEL.registerMessage(0,Trail.class,Trail::encode,Trail::decode,(v,s)->{var c=s.get();c.enqueueWork(()->receiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    public static void clientReceiver(Consumer<Trail> handler){receiver=handler;}
    public static void sent(ServerLevel level,List<BlockPos> reversed,BlockPos origin,BlockPos destination,ItemStack item){
        if(origin==null||reversed.size()+2>Trail.MAX_NODES)return;
        try{var nodes=new ArrayList<BlockPos>();nodes.add(origin);for(int i=reversed.size()-1;i>=0;i--)nodes.add(reversed.get(i));nodes.add(destination);var trail=new Trail(level.dimension().location(),BuiltInRegistries.ITEM.getId(item.getItem()),nodes);
            for(var player:level.players())if(nodes.stream().anyMatch(p->player.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)<=64*64))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),trail);
        }catch(RuntimeException cosmetic){UnusualCopper.LOGGER.debug("Skipped item pipe visual",cosmetic);}
    }
    private ItemTransitNetwork(){}
}
