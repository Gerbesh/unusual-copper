package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Consumer;

/** S2C observations only. Configuration uses vanilla's validated container buttons. */
public final class EnergyControlNetwork {
    public record View(int menu,BlockPos pos,int kind,int priority,int redstone,int[] limits,int[] priorities,int[] modes,
                       long stored,long capacity,long produced,long consumed,long transferred,long deficit,int ticks,int nodes,int devices) {
        static void encode(View v,FriendlyByteBuf b){
            b.writeVarInt(v.menu);b.writeBlockPos(v.pos);b.writeVarInt(v.kind);b.writeVarInt(v.priority);b.writeVarInt(v.redstone);
            for(int i=0;i<6;i++){b.writeVarInt(v.limits[i]);b.writeVarInt(v.priorities[i]);b.writeVarInt(v.modes[i]);}
            b.writeLong(v.stored);b.writeLong(v.capacity);b.writeLong(v.produced);b.writeLong(v.consumed);b.writeLong(v.transferred);b.writeLong(v.deficit);
            b.writeVarInt(v.ticks);b.writeVarInt(v.nodes);b.writeVarInt(v.devices);
        }
        static View decode(FriendlyByteBuf b){
            int menu=b.readVarInt();var pos=b.readBlockPos();int kind=b.readVarInt(),priority=b.readVarInt(),redstone=b.readVarInt();
            int[] limits=new int[6],priorities=new int[6],modes=new int[6];for(int i=0;i<6;i++){limits[i]=b.readVarInt();priorities[i]=b.readVarInt();modes[i]=b.readVarInt();}
            return new View(menu,pos,kind,priority,redstone,limits,priorities,modes,b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt());
        }
    }
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"energy_control"),()->"1","1"::equals,"1"::equals);
    private static Consumer<View> receiver=ignored->{};
    private EnergyControlNetwork(){}
    public static void register(){CHANNEL.registerMessage(0,View.class,View::encode,View::decode,(message,supplier)->{var context=supplier.get();context.enqueueWork(()->receiver.accept(message));context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    public static void clientReceiver(Consumer<View> handler){receiver=handler;}
    public static void send(ServerPlayer player,View view){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),view);}
}
