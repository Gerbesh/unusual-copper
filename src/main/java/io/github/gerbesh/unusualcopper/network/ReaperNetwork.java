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
import java.util.function.Consumer;

public final class ReaperNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "reaper"), () -> "1", "1"::equals, "1"::equals);
    private static Consumer<Reply> receiver = ignored -> {};
    public record Settings(int menu, ReaperSettings values) {}
    public record Reply(int menu, BlockPos pos, ReaperSettings values, int result) {}
    private static ReaperSettings read(FriendlyByteBuf b) { return new ReaperSettings(b.readInt(), b.readInt(), b.readBoolean(), b.readBoolean(), b.readUtf(1024), b.readUtf(1024), b.readUtf(256)); }
    private static void write(ReaperSettings v, FriendlyByteBuf b) { b.writeInt(v.trigger()); b.writeInt(v.keep()); b.writeBoolean(v.passive()); b.writeBoolean(v.adults()); b.writeUtf(v.types(),1024); b.writeUtf(v.tags(),1024); b.writeUtf(v.categories(),256); }
    public static void register() {
        CHANNEL.registerMessage(0, Settings.class, (v,b) -> { b.writeVarInt(v.menu); write(v.values,b); }, b -> new Settings(b.readVarInt(),read(b)), (v,supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && player.containerMenu instanceof ReaperMenu menu && menu.containerId == v.menu) menu.configure(v.values,player);
            }); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Reply.class, (v,b) -> { b.writeVarInt(v.menu); b.writeBlockPos(v.pos); write(v.values,b); b.writeByte(v.result); }, b -> new Reply(b.readVarInt(),b.readBlockPos(),read(b),b.readUnsignedByte()), (v,supplier) -> {
            var context=supplier.get(); context.enqueueWork(() -> receiver.accept(v)); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clientReceiver(Consumer<Reply> handler) { receiver=handler; }
    public static void settings(int menu, ReaperSettings values) { CHANNEL.sendToServer(new Settings(menu,values)); }
    public static void reply(ServerPlayer player, Reply reply) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),reply); }
    private ReaperNetwork() {}
}
