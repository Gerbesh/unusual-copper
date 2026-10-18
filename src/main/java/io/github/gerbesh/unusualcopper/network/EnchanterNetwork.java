package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.EnchantmentCatalog;
import io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy.Offer;
import io.github.gerbesh.unusualcopper.menu.EnchanterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Consumer;

/** Owner-only quotes/status and a registry-sized catalog streamed in bounded pages. */
public final class EnchanterNetwork {
    public enum Result{NONE,QUOTED,APPLIED,STALE,INVALID,NO_XP,NO_LAPIS,NO_ENERGY,DENIED,PAUSED,XP_CANCELLED,XP_CHANGED,PROVIDER_ERROR}
    public record Select(int menu,UUID runtime,ResourceLocation enchantment){}
    public record Confirm(int menu,UUID runtime,UUID nonce){}
    public record Refresh(int menu,UUID runtime){}
    public record Quote(UUID nonce,Offer offer,ItemStack preview,int remaining){
        public Quote{if(nonce==null||offer==null||remaining<0||remaining>EnchanterMenu.QUOTE_TICKS)throw new IllegalArgumentException("Enchantment quote");preview=preview.copy();}
        @Override public ItemStack preview(){return preview.copy();}
    }
    public record Reply(int menu,BlockPos pos,UUID runtime,List<Offer> offers,Quote quote,long xp,int energy,int lapis,Result result,int redstone,boolean enabled,UUID catalog,int total){
        public Reply{pos=pos.immutable();offers=List.copyOf(offers);if(catalog==null||total<offers.size()||xp<0||energy<0||lapis<0||lapis>64||redstone<0||redstone>2)throw new IllegalArgumentException("Enchantment reply");}
        public Reply withOffers(List<Offer> loaded){return new Reply(menu,pos,runtime,loaded,quote,xp,energy,lapis,result,redstone,enabled,catalog,total);}
    }
    public record Page(int menu,BlockPos pos,UUID runtime,UUID catalog,int offset,int total,List<Offer> offers){
        public Page{pos=pos.immutable();offers=List.copyOf(offers);if(runtime==null||catalog==null||offset<0||total<0||offers.size()>EnchantmentCatalog.PAGE_SIZE||(long)offset+offers.size()>total)throw new IllegalArgumentException("Enchantment page");}
    }
    private static final String PROTOCOL="2";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"enchanter"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private static Consumer<Reply> receiver=ignored->{};
    private static Consumer<Page> pages=ignored->{};
    public static final class Codec {
        private Codec(){}
        private static void offer(Offer v,FriendlyByteBuf b){b.writeUtf(v.enchantment().toString(),32767);b.writeVarInt(v.level());b.writeVarInt(v.xp());b.writeVarInt(v.cue());}
        private static Offer offer(FriendlyByteBuf b){return new Offer(new ResourceLocation(b.readUtf(32767)),b.readVarInt(),b.readVarInt(),b.readVarInt());}
        private static void offers(List<Offer> values,FriendlyByteBuf b){
            if(values.size()>EnchantmentCatalog.PAGE_SIZE||values.stream().mapToLong(Offer::wireBudget).sum()>EnchantmentCatalog.PAGE_BYTES)throw new IllegalArgumentException("Unchunked enchantment catalog");
            b.writeVarInt(values.size());values.forEach(v->offer(v,b));
        }
        private static List<Offer> offers(FriendlyByteBuf b){
            int count=b.readVarInt();if(count<0||count>EnchantmentCatalog.PAGE_SIZE)throw new IllegalArgumentException("Enchantment page size");
            var result=new ArrayList<Offer>(count);int budget=0;for(int i=0;i<count;i++){var value=offer(b);budget+=value.wireBudget();if(budget>EnchantmentCatalog.PAGE_BYTES)throw new IllegalArgumentException("Enchantment page bytes");result.add(value);}return List.copyOf(result);
        }
        private static void size(int total,int registrySize){if(total<0||total>registrySize)throw new IllegalArgumentException("Unregistered enchantment count");}
        public static void encode(Reply v,FriendlyByteBuf b){
            b.writeVarInt(v.menu);b.writeBlockPos(v.pos);b.writeUUID(v.runtime);b.writeUUID(v.catalog);b.writeVarInt(v.total);offers(v.offers,b);
            b.writeBoolean(v.quote!=null);if(v.quote!=null){b.writeUUID(v.quote.nonce);offer(v.quote.offer,b);b.writeItem(v.quote.preview());b.writeVarInt(v.quote.remaining);}
            b.writeVarLong(v.xp);b.writeVarInt(v.energy);b.writeVarInt(v.lapis);b.writeEnum(v.result);b.writeByte(v.redstone);b.writeBoolean(v.enabled);
        }
        public static Reply decode(FriendlyByteBuf b,int registrySize){
            int menu=b.readVarInt();var pos=b.readBlockPos();var runtime=b.readUUID();var catalog=b.readUUID();int total=b.readVarInt();size(total,registrySize);var choices=offers(b);
            Quote quote=b.readBoolean()?new Quote(b.readUUID(),offer(b),b.readItem(),b.readVarInt()):null;
            return new Reply(menu,pos,runtime,choices,quote,b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readEnum(Result.class),b.readUnsignedByte(),b.readBoolean(),catalog,total);
        }
        public static void encodePage(Page v,FriendlyByteBuf b){b.writeVarInt(v.menu);b.writeBlockPos(v.pos);b.writeUUID(v.runtime);b.writeUUID(v.catalog);b.writeVarInt(v.offset);b.writeVarInt(v.total);offers(v.offers,b);}
        public static Page decodePage(FriendlyByteBuf b,int registrySize){
            int menu=b.readVarInt();var pos=b.readBlockPos();var runtime=b.readUUID();var catalog=b.readUUID();int offset=b.readVarInt(),total=b.readVarInt();size(total,registrySize);
            return new Page(menu,pos,runtime,catalog,offset,total,offers(b));
        }
    }
    private static int registrySize(){return ForgeRegistries.ENCHANTMENTS.getKeys().size();}
    public static void register(){
        CHANNEL.registerMessage(0,Select.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeResourceLocation(v.enchantment);},b->new Select(b.readVarInt(),b.readUUID(),b.readResourceLocation()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof EnchanterMenu m&&m.containerId==v.menu)m.select(v.runtime,v.enchantment,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Confirm.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeUUID(v.nonce);},b->new Confirm(b.readVarInt(),b.readUUID(),b.readUUID()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof EnchanterMenu m&&m.containerId==v.menu)m.confirm(v.runtime,v.nonce,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Reply.class,Codec::encode,b->Codec.decode(b,registrySize()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->receiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,Page.class,Codec::encodePage,b->Codec.decodePage(b,registrySize()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->pages.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4,Refresh.class,(v,b)->{b.writeVarInt(v.menu);b.writeUUID(v.runtime);},b->new Refresh(b.readVarInt(),b.readUUID()),(v,supplier)->{var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof EnchanterMenu m&&m.containerId==v.menu)m.refresh(v.runtime,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void select(int menu,UUID runtime,ResourceLocation enchantment){CHANNEL.sendToServer(new Select(menu,runtime,enchantment));}
    public static void confirm(int menu,UUID runtime,UUID nonce){CHANNEL.sendToServer(new Confirm(menu,runtime,nonce));}
    public static void refresh(int menu,UUID runtime){CHANNEL.sendToServer(new Refresh(menu,runtime));}
    public static void reply(ServerPlayer player,Reply reply){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply);}
    public static void page(ServerPlayer player,Page page){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),page);}
    public static void clientReceiver(Consumer<Reply> handler){receiver=handler;}
    public static void pageReceiver(Consumer<Page> handler){pages=handler;}
    private EnchanterNetwork(){}
}
