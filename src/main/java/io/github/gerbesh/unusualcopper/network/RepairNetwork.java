package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.RepairPrice;
import io.github.gerbesh.unusualcopper.menu.RepairMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

public final class RepairNetwork {
    public enum Result{NONE,QUOTED,APPLIED,STALE,INVALID,NO_XP,NO_ENERGY,PAUSED,XP_CANCELLED,XP_CHANGED,PROVIDER_ERROR}
    public record Select(int menu,UUID runtime,int points){}
    public record Confirm(int menu,UUID runtime,UUID nonce){}
    public record Quote(UUID nonce,RepairPrice price,ItemStack preview,int remaining){public Quote{preview=preview.copy();}@Override public ItemStack preview(){return preview.copy();}}
    public record Reply(int menu,BlockPos pos,UUID runtime,Quote quote,long xp,int energy,Result result,boolean enabled){}
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"repair"),()->"1","1"::equals,"1"::equals);
    private static Consumer<Reply> receiver=ignored->{};
    private static void encode(Reply r,FriendlyByteBuf b){b.writeVarInt(r.menu);b.writeBlockPos(r.pos);b.writeUUID(r.runtime);b.writeBoolean(r.quote!=null);if(r.quote!=null){var q=r.quote;b.writeUUID(q.nonce);b.writeVarInt(q.price.points());b.writeVarInt(q.price.cue());b.writeVarInt(q.price.xp());b.writeItem(q.preview());b.writeVarInt(q.remaining);}b.writeVarLong(r.xp);b.writeVarInt(r.energy);b.writeEnum(r.result);b.writeBoolean(r.enabled);}
    private static Reply decode(FriendlyByteBuf b){int menu=b.readVarInt();var pos=b.readBlockPos();var runtime=b.readUUID();var q=b.readBoolean()?new Quote(b.readUUID(),new RepairPrice(b.readVarInt(),b.readVarInt(),b.readVarInt()),b.readItem(),b.readVarInt()):null;return new Reply(menu,pos,runtime,q,b.readVarLong(),b.readVarInt(),b.readEnum(Result.class),b.readBoolean());}
    public static void register(){
        CHANNEL.registerMessage(0,Select.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeVarInt(v.points);},b->new Select(b.readVarInt(),b.readUUID(),b.readVarInt()),(v,s)->{var c=s.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof RepairMenu m&&m.containerId==v.menu)m.select(v.runtime,v.points,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Confirm.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeUUID(v.nonce);},b->new Confirm(b.readVarInt(),b.readUUID(),b.readUUID()),(v,s)->{var c=s.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof RepairMenu m&&m.containerId==v.menu)m.confirm(v.runtime,v.nonce,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Reply.class,RepairNetwork::encode,RepairNetwork::decode,(v,s)->{var c=s.get();c.enqueueWork(()->receiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void select(int menu,UUID runtime,int points){CHANNEL.sendToServer(new Select(menu,runtime,points));}
    public static void confirm(int menu,UUID runtime,UUID nonce){CHANNEL.sendToServer(new Confirm(menu,runtime,nonce));}
    public static void reply(ServerPlayer player,Reply reply){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply);}
    public static void clientReceiver(Consumer<Reply> handler){receiver=handler;}
    private RepairNetwork(){}
}
