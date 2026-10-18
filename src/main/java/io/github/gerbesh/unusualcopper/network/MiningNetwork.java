package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.menu.MiningMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

public final class MiningNetwork {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("unusualcopper","mining"),()->"2","2"::equals,"2"::equals);
    public record Entry(ResourceLocation id,ItemStack icon,int found,boolean enabled){}
    public record Reply(int menu,BlockPos pos,UUID runtime,int energy,int cost,int status,int[] area,int scanned,int total,int mined,int experience,boolean enabled,boolean faulted,BlockPos output,List<Entry> entries,int chunksLoaded,int chunksTotal,int chunkState){}
    public record Toggle(int menu,BlockPos pos,UUID runtime,ResourceLocation block){}
    public record Extraction(ResourceLocation dimension,BlockPos origin,UUID runtime,BlockPos target,BlockPos output,double sphereX,double sphereY,double sphereZ,int block,ItemStack loot){}
    private static Consumer<CopperMachineBlockEntity> fields=m->{};
    public static void fieldReceiver(Consumer<CopperMachineBlockEntity> receiver){fields=receiver;}
    public static void trackField(CopperMachineBlockEntity machine){fields.accept(machine);}
    private static Consumer<Reply> receiver=m->{};private static Consumer<Extraction> effects=m->{};
    public static void clientReceiver(Consumer<Reply> c){receiver=c;}public static void effectsReceiver(Consumer<Extraction> c){effects=c;}
    public static void send(ServerPlayer p,Reply r){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),r);}
    public static void toggle(Toggle p){CHANNEL.sendToServer(p);}
    public static void extracted(ServerLevel level,CopperMachineBlockEntity m,BlockPos target,BlockState block,ItemStack loot){var a=m.mining.area;if(a==null)return;var r=new Extraction(level.dimension().location(),m.getBlockPos(),m.runtimeIdentity,target,m.boundInventory()==null?m.getBlockPos():m.boundInventory(),(a.minX()+a.maxX()+1)/2.0,m.getBlockPos().getY()+15.0,(a.minZ()+a.maxZ()+1)/2.0,Block.getId(block),loot.isEmpty()?ItemStack.EMPTY:loot.copyWithCount(1));CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(m.getBlockPos().getX()+.5,m.getBlockPos().getY()+15,m.getBlockPos().getZ()+.5,224,level.dimension())),r);}
    public static void register(){
        CHANNEL.registerMessage(0,Reply.class,(r,b)->{b.writeVarInt(r.menu);b.writeBlockPos(r.pos);b.writeUUID(r.runtime);b.writeVarInt(r.energy);b.writeVarInt(r.cost);b.writeVarInt(r.status);b.writeVarIntArray(r.area);b.writeVarInt(r.scanned);b.writeVarInt(r.total);b.writeVarInt(r.mined);b.writeVarInt(r.experience);b.writeBoolean(r.enabled);b.writeBoolean(r.faulted);b.writeBoolean(r.output!=null);if(r.output!=null)b.writeBlockPos(r.output);b.writeVarInt(r.entries.size());for(var e:r.entries){b.writeResourceLocation(e.id);b.writeItem(e.icon);b.writeVarInt(e.found);b.writeBoolean(e.enabled);}b.writeVarInt(r.chunksLoaded);b.writeVarInt(r.chunksTotal);b.writeVarInt(r.chunkState);},b->{int menu=b.readVarInt();var pos=b.readBlockPos();var runtime=b.readUUID();int energy=b.readVarInt(),cost=b.readVarInt(),status=b.readVarInt();var area=b.readVarIntArray(4);int scanned=b.readVarInt(),total=b.readVarInt(),mined=b.readVarInt(),xp=b.readVarInt();boolean enabled=b.readBoolean(),fault=b.readBoolean();var output=b.readBoolean()?b.readBlockPos():null;int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Too many ore types");var list=new ArrayList<Entry>();for(int i=0;i<n;i++)list.add(new Entry(b.readResourceLocation(),b.readItem(),b.readVarInt(),b.readBoolean()));int loaded=b.readVarInt(),count=b.readVarInt(),loading=b.readVarInt();if(loaded<0||count<loaded||count>io.github.gerbesh.unusualcopper.gameplay.MiningChunkPlan.MAX_TICKETS||loading<0||loading>=io.github.gerbesh.unusualcopper.gameplay.MiningChunkStatus.values().length)throw new IllegalArgumentException("Invalid mining chunks");return new Reply(menu,pos,runtime,energy,cost,status,area,scanned,total,mined,xp,enabled,fault,output,List.copyOf(list),loaded,count,loading);},(r,s)->{var c=s.get();c.enqueueWork(()->receiver.accept(r));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Toggle.class,(r,b)->{b.writeVarInt(r.menu);b.writeBlockPos(r.pos);b.writeUUID(r.runtime);b.writeResourceLocation(r.block);},b->new Toggle(b.readVarInt(),b.readBlockPos(),b.readUUID(),b.readResourceLocation()),(r,s)->{var c=s.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof MiningMenu menu&&menu.containerId==r.menu&&menu.pos.equals(r.pos))menu.toggle(p,r.runtime,r.block);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Extraction.class,(r,b)->{b.writeResourceLocation(r.dimension);b.writeBlockPos(r.origin);b.writeUUID(r.runtime);b.writeBlockPos(r.target);b.writeBlockPos(r.output);b.writeDouble(r.sphereX);b.writeDouble(r.sphereY);b.writeDouble(r.sphereZ);b.writeVarInt(r.block);b.writeItem(r.loot);},b->new Extraction(b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readBlockPos(),b.readBlockPos(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readItem()),(r,s)->{var c=s.get();c.enqueueWork(()->effects.accept(r));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private MiningNetwork(){}
}
