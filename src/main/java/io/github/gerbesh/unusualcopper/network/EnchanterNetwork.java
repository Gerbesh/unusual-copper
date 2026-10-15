package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy.Offer;
import io.github.gerbesh.unusualcopper.menu.EnchanterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

public final class EnchanterNetwork {
    public enum Result{NONE,QUOTED,APPLIED,STALE,INVALID,NO_XP,NO_LAPIS,NO_ENERGY,DENIED,PAUSED,XP_CANCELLED,XP_CHANGED,PROVIDER_ERROR}
    public record Select(int menu,UUID runtime,ResourceLocation enchantment){}
    public record Confirm(int menu,UUID runtime,UUID nonce){}
    public record Quote(UUID nonce,Offer offer,ItemStack preview,int remaining){public Quote{preview=preview.copy();}@Override public ItemStack preview(){return preview.copy();}}
    public record Reply(int menu,BlockPos pos,UUID runtime,List<Offer> offers,Quote quote,long xp,int energy,int lapis,Result result,int redstone,boolean enabled){public Reply{pos=pos.immutable();offers=List.copyOf(offers);}}
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"enchanter"),()->"1","1"::equals,"1"::equals);
    private static Consumer<Reply> receiver=ignored->{};
    private static void offer(Offer v,FriendlyByteBuf b){b.writeResourceLocation(v.enchantment());b.writeVarInt(v.level());b.writeVarInt(v.xp());b.writeVarInt(v.cue());}
    private static Offer offer(FriendlyByteBuf b){return new Offer(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt());}
    private static void reply(Reply v,FriendlyByteBuf b){b.writeVarInt(v.menu);b.writeBlockPos(v.pos);b.writeUUID(v.runtime);b.writeVarInt(v.offers.size());v.offers.forEach(o->offer(o,b));b.writeBoolean(v.quote!=null);if(v.quote!=null){b.writeUUID(v.quote.nonce);offer(v.quote.offer,b);b.writeItem(v.quote.preview());b.writeVarInt(v.quote.remaining);}b.writeVarLong(v.xp);b.writeVarInt(v.energy);b.writeVarInt(v.lapis);b.writeEnum(v.result);b.writeByte(v.redstone);b.writeBoolean(v.enabled);}
    private static Reply reply(FriendlyByteBuf b){int menu=b.readVarInt();var pos=b.readBlockPos();var runtime=b.readUUID();int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Too many enchantment offers");var choices=new ArrayList<Offer>();for(int i=0;i<n;i++)choices.add(offer(b));Quote quote=b.readBoolean()?new Quote(b.readUUID(),offer(b),b.readItem(),b.readVarInt()):null;return new Reply(menu,pos,runtime,choices,quote,b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readEnum(Result.class),b.readUnsignedByte(),b.readBoolean());}
    public static void register(){
        CHANNEL.registerMessage(0,Select.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeResourceLocation(v.enchantment);},b->new Select(b.readVarInt(),b.readUUID(),b.readResourceLocation()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof EnchanterMenu m&&m.containerId==v.menu)m.select(v.runtime,v.enchantment,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Confirm.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeUUID(v.nonce);},b->new Confirm(b.readVarInt(),b.readUUID(),b.readUUID()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof EnchanterMenu m&&m.containerId==v.menu)m.confirm(v.runtime,v.nonce,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Reply.class,EnchanterNetwork::reply,EnchanterNetwork::reply,(v,supplier)->{var c=supplier.get();c.enqueueWork(()->receiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void select(int menu,UUID runtime,ResourceLocation enchantment){CHANNEL.sendToServer(new Select(menu,runtime,enchantment));}
    public static void confirm(int menu,UUID runtime,UUID nonce){CHANNEL.sendToServer(new Confirm(menu,runtime,nonce));}
    public static void reply(ServerPlayer player,Reply reply){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply);}
    public static void clientReceiver(Consumer<Reply> handler){receiver=handler;}
    private EnchanterNetwork(){}
}
