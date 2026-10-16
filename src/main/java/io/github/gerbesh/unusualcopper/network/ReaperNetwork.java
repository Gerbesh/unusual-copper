package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.gameplay.ReaperSettings;
import io.github.gerbesh.unusualcopper.menu.ReaperMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.*;
import io.github.gerbesh.unusualcopper.gameplay.WorkArea;
import java.util.function.Consumer;

public final class ReaperNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "reaper"), () -> "2", "2"::equals, "2"::equals);
    private static Consumer<Reply> receiver = ignored -> {};
    public record Settings(int menu, ReaperSettings values) {}
    public record Species(ResourceLocation type,int total,int adults) {}
    public record Choose(int menu,UUID runtime,WorkArea area,ReaperSettings values) {
        public Choose{Objects.requireNonNull(runtime);Objects.requireNonNull(area);Objects.requireNonNull(values);if(values.trigger()<0||values.trigger()>1_000_000||values.keep()<0||values.keep()>values.trigger()||!values.passive()||!values.tags().isEmpty()||!values.categories().isEmpty())throw new IllegalArgumentException("Invalid creature selection");}
        static void encode(Choose v,FriendlyByteBuf b){b.writeVarInt(v.menu);b.writeUUID(v.runtime);b.writeBlockPos(v.area.min());b.writeBlockPos(v.area.max());var s=v.values;b.writeVarInt(s.trigger());b.writeVarInt(s.keep());b.writeBoolean(s.adults());b.writeUtf(s.types(),1024);}
        static Choose decode(FriendlyByteBuf b){int menu=b.readVarInt();var runtime=b.readUUID();var area=new WorkArea(b.readBlockPos(),b.readBlockPos());return new Choose(menu,runtime,area,new ReaperSettings(b.readVarInt(),b.readVarInt(),true,b.readBoolean(),b.readUtf(1024),"",""));}
    }
    public record Reply(int menu, BlockPos pos, ReaperSettings values, int result,UUID runtime,WorkArea area,boolean complete,List<Species> species) { public Reply {species=List.copyOf(species);} }
    private static void area(WorkArea area,FriendlyByteBuf b){b.writeBoolean(area!=null);if(area!=null){b.writeBlockPos(area.min());b.writeBlockPos(area.max());}}
    private static WorkArea area(FriendlyByteBuf b){return b.readBoolean()?new WorkArea(b.readBlockPos(),b.readBlockPos()):null;}
    private static ReaperSettings read(FriendlyByteBuf b) { return new ReaperSettings(b.readInt(), b.readInt(), b.readBoolean(), b.readBoolean(), b.readUtf(1024), b.readUtf(1024), b.readUtf(256)); }
    private static void write(ReaperSettings v, FriendlyByteBuf b) { b.writeInt(v.trigger()); b.writeInt(v.keep()); b.writeBoolean(v.passive()); b.writeBoolean(v.adults()); b.writeUtf(v.types(),1024); b.writeUtf(v.tags(),1024); b.writeUtf(v.categories(),256); }
    public static void register() {
        CHANNEL.registerMessage(0, Settings.class, (v,b) -> { b.writeVarInt(v.menu); write(v.values,b); }, b -> new Settings(b.readVarInt(),read(b)), (v,supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && player.containerMenu instanceof ReaperMenu menu && menu.containerId == v.menu) menu.configure(v.values,player);
            }); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Reply.class, (v,b) -> { b.writeVarInt(v.menu); b.writeBlockPos(v.pos); write(v.values,b); b.writeByte(v.result); b.writeUUID(v.runtime);area(v.area,b);b.writeBoolean(v.complete);b.writeVarInt(v.species.size());for(var s:v.species){b.writeResourceLocation(s.type);b.writeVarInt(s.total);b.writeVarInt(s.adults);} }, b -> {
            int menu=b.readVarInt();var pos=b.readBlockPos();var values=read(b);int result=b.readUnsignedByte();var runtime=b.readUUID();var area=area(b);boolean complete=b.readBoolean();int count=b.readVarInt();if(count<0||count>136)throw new IllegalArgumentException("Too many species");var species=new ArrayList<Species>();for(int i=0;i<count;i++)species.add(new Species(b.readResourceLocation(),b.readVarInt(),b.readVarInt()));return new Reply(menu,pos,values,result,runtime,area,complete,species);
        }, (v,supplier) -> {
            var context=supplier.get(); context.enqueueWork(() -> receiver.accept(v)); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,Choose.class,Choose::encode,Choose::decode,(v,supplier)->{var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p!=null&&p.containerMenu instanceof ReaperMenu m&&m.containerId==v.menu)m.choose(v,p);});c.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void clientReceiver(Consumer<Reply> handler) { receiver=handler; }
    public static void settings(int menu, ReaperSettings values) { CHANNEL.sendToServer(new Settings(menu,values)); }
    public static void choose(Choose choice){CHANNEL.sendToServer(choice);}
    public static void reply(ServerPlayer player, Reply reply) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),reply); }
    private ReaperNetwork() {}
}
