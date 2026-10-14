package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.menu.ItemPipeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

public final class PipeNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "settings"), () -> "1", "1"::equals, "1"::equals);
    private static Consumer<Tags> receiver = ignored -> {};
    private PipeNetwork() {}
    public record Settings(int menu, int face, int priority, int minimum, int maximum, int reserve, String tag, boolean removeTag) {
        static Settings decode(FriendlyByteBuf buffer) { return new Settings(buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readUtf(128), buffer.readBoolean()); }
        static void encode(Settings value, FriendlyByteBuf buffer) {
            buffer.writeVarInt(value.menu); buffer.writeByte(value.face); buffer.writeInt(value.priority); buffer.writeInt(value.minimum); buffer.writeInt(value.maximum); buffer.writeInt(value.reserve); buffer.writeUtf(value.tag, 128); buffer.writeBoolean(value.removeTag);
        }
    }
    public record Tags(int menu, BlockPos pos, int face, List<ResourceLocation> tags) {
        public Tags { tags = List.copyOf(tags); }
        static Tags decode(FriendlyByteBuf buffer) {
            int menu = buffer.readVarInt(); var pos = buffer.readBlockPos(); int face = buffer.readUnsignedByte(); int count = buffer.readVarInt();
            if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid tag count");
            var tags = new ArrayList<ResourceLocation>(); for (int i = 0; i < count; i++) tags.add(new ResourceLocation(buffer.readUtf(128)));
            return new Tags(menu, pos, face, tags);
        }
        static void encode(Tags value, FriendlyByteBuf buffer) {
            buffer.writeVarInt(value.menu); buffer.writeBlockPos(value.pos); buffer.writeByte(value.face); buffer.writeVarInt(value.tags.size()); value.tags.forEach(tag -> buffer.writeUtf(tag.toString(), 128));
        }
    }
    public static void register() {
        CHANNEL.registerMessage(0, Settings.class, Settings::encode, Settings::decode, (message, supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && player.containerMenu instanceof ItemPipeMenu menu && menu.containerId == message.menu) menu.configure(message, player);
            }); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Tags.class, Tags::encode, Tags::decode, (message, supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> receiver.accept(message)); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clientReceiver(Consumer<Tags> handler) { receiver = handler; }
    public static void settings(Settings settings) { CHANNEL.sendToServer(settings); }
    public static void tags(ServerPlayer player, Tags tags) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), tags); }
}
