package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.armor.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

/** Owner controls/status and bounded public body pulses; accepts no stacks, prices or target UUIDs. */
public final class ArmorNetwork {
    public enum Result { OK,STALE,INVALID }
    public record Request(){}
    public record Action(UUID session,long revision,int piece,int module,ArmorControlPolicy.Operation operation,int value){}
    public record Row(ArmorModuleType module,ArmorModuleSettings settings,int level){
        public Row{Objects.requireNonNull(module);Objects.requireNonNull(settings);if(level<1||level>module.maxLevel())throw new IllegalArgumentException("Module level");}
        public Row(ArmorModuleType module,ArmorModuleSettings settings){this(module,settings,1);}
    }
    public record Piece(String item,int tier,int sockets,List<Row> modules){public Piece{modules=List.copyOf(modules);}}
    public record Snapshot(UUID session,long revision,boolean complete,int profile,int charge,int capacity,int perSecond,
                           long recent,long limited,int conditions,ArmorRuntime.FlightStatus flight,List<Piece> pieces,Result result,int disabledSenses){
        public Snapshot{pieces=List.copyOf(pieces);Objects.requireNonNull(flight);if(disabledSenses<0||disabledSenses>3)throw new IllegalArgumentException("Sense policy");}
        public Snapshot(UUID session,long revision,boolean complete,int profile,int charge,int capacity,int perSecond,long recent,long limited,int conditions,ArmorRuntime.FlightStatus flight,List<Piece> pieces,Result result){
            this(session,revision,complete,profile,charge,capacity,perSecond,recent,limited,conditions,flight,pieces,result,0);
        }
    }
    // Both sides must understand reserved battery NBT as unavailable charge.
    private static final String PROTOCOL="7";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID,"armor_control"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private static Consumer<Snapshot> receiver=ignored->{};
    private static Consumer<ArmorSenseSnapshot> senseReceiver=ignored->{};
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,(v,b)->{},b->new Request(),(v,s)->{var c=s.get();c.enqueueWork(()->{if(c.getSender()!=null)ArmorControlServer.request(c.getSender());});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Action.class,(v,b)->{b.writeUUID(v.session);b.writeVarLong(v.revision);b.writeVarInt(v.piece);b.writeVarInt(v.module);b.writeEnum(v.operation);b.writeVarInt(v.value);},
                b->new Action(b.readUUID(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readEnum(ArmorControlPolicy.Operation.class),b.readVarInt()),
                (v,s)->{var c=s.get();c.enqueueWork(()->{if(c.getSender()!=null)ArmorControlServer.action(c.getSender(),v);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Snapshot.class,Codec::encode,Codec::decode,(v,s)->{var c=s.get();c.enqueueWork(()->receiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,ArmorSenseSnapshot.class,ArmorSenseSnapshot.Codec::encode,ArmorSenseSnapshot.Codec::decode,(v,s)->{var c=s.get();c.enqueueWork(()->senseReceiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4,ArmorEffectEvents.Burst.class,ArmorEffectEvents.Burst::encode,ArmorEffectEvents.Burst::decode,(v,s)->{var c=s.get();c.enqueueWork(()->effectReceiver.accept(v));c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private static Consumer<ArmorEffectEvents.Burst> effectReceiver=v->{};
    public static void effectReceiver(Consumer<ArmorEffectEvents.Burst> receiver){effectReceiver=Objects.requireNonNull(receiver);}
    public static void effect(ServerPlayer p,ArmorEffectEvents.Burst burst){CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),burst);}
    /** Wire validation can be exercised without initializing Forge's live channel. */
    public static final class Codec {
    private Codec(){}
    public static void encode(Snapshot v,FriendlyByteBuf b){
        b.writeUUID(v.session);b.writeVarLong(v.revision);b.writeBoolean(v.complete);b.writeVarInt(v.profile);b.writeVarInt(v.charge);b.writeVarInt(v.capacity);
        b.writeVarInt(v.perSecond);b.writeLong(v.recent);b.writeLong(v.limited);b.writeVarInt(v.conditions);b.writeVarInt(v.disabledSenses);
        b.writeBoolean(v.flight.session());b.writeVarInt(v.flight.ticksLeft());b.writeVarInt(v.flight.heightLeft());
        b.writeEnum(v.result);b.writeVarInt(v.pieces.size());
        for(var p:v.pieces){b.writeUtf(p.item,128);b.writeVarInt(p.tier);b.writeVarInt(p.sockets);b.writeVarInt(p.modules.size());
            for(var row:p.modules){var s=row.settings;b.writeEnum(row.module);b.writeVarInt(row.level);b.writeBoolean(s.enabled());b.writeEnum(s.trigger());b.writeVarInt(s.minimumPercent());b.writeBoolean(s.armed());b.writeVarInt(s.priority());}}
    }
    public static Snapshot decode(FriendlyByteBuf b){
        var session=b.readUUID();long revision=b.readVarLong();boolean complete=b.readBoolean();int profile=b.readVarInt(),charge=b.readVarInt(),capacity=b.readVarInt(),spent=b.readVarInt();
        long recent=b.readLong(),limited=b.readLong();int conditions=b.readVarInt(),disabledSenses=b.readVarInt();
        var flight=new ArmorRuntime.FlightStatus(b.readBoolean(),b.readVarInt(),b.readVarInt());
        var result=b.readEnum(Result.class);int count=b.readVarInt();
        if(revision<0||profile<0||profile>3||charge<0||capacity<charge||capacity>250_000_000||spent<0||count!=4||conditions<0||conditions>255||limited<0||limited>>>ArmorModuleType.values().length!=0)throw new IllegalArgumentException("Armor snapshot");
        var pieces=new ArrayList<Piece>(4);
        for(int i=0;i<count;i++){
            String item=b.readUtf(128);int tier=b.readVarInt(),sockets=b.readVarInt(),n=b.readVarInt();
            if(tier<0||tier>3||sockets<0||sockets>4||n<0||n>sockets)throw new IllegalArgumentException("Armor piece");
            if(tier==0?(!item.isEmpty()||sockets!=0||n!=0):(item.isEmpty()||ResourceLocation.tryParse(item)==null))throw new IllegalArgumentException("Armor item identity");
            var rows=new ArrayList<Row>(n);var seen=EnumSet.noneOf(ArmorModuleType.class);
            for(int k=0;k<n;k++){var module=b.readEnum(ArmorModuleType.class);if(!seen.add(module))throw new IllegalArgumentException("Duplicate module");
                int level=b.readVarInt();if(level>tier)throw new IllegalArgumentException("Module exceeds chassis");
                rows.add(new Row(module,new ArmorModuleSettings(b.readBoolean(),b.readEnum(ArmorModuleSettings.Trigger.class),b.readVarInt(),b.readBoolean(),b.readVarInt()),level));}
            pieces.add(new Piece(item,tier,sockets,rows));
        }
        return new Snapshot(session,revision,complete,profile,charge,capacity,spent,recent,limited,conditions,flight,pieces,result,disabledSenses);
    }
    }
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void action(Action action){CHANNEL.sendToServer(action);}
    public static void send(ServerPlayer player,Snapshot value){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),value);}
    public static void receiver(Consumer<Snapshot> handler){receiver=handler;}
    public static void senseReceiver(Consumer<ArmorSenseSnapshot> handler){senseReceiver=handler;}
    public static void sendSense(ServerPlayer player,ArmorSenseSnapshot value){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),value);}
    private ArmorNetwork(){}
}
