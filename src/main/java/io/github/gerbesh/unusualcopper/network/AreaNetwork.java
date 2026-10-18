package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Consumer;

public final class AreaNetwork {
    public record Preview(ResourceLocation dimension, BlockPos min, BlockPos max, boolean valid) {
        static Preview decode(FriendlyByteBuf buffer) { return new Preview(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBoolean()); }
        static void encode(Preview preview, FriendlyByteBuf buffer) { buffer.writeResourceLocation(preview.dimension); buffer.writeBlockPos(preview.min); buffer.writeBlockPos(preview.max); buffer.writeBoolean(preview.valid); }
    }
    /** Owner-only immutable first corner; hover updates never need a network packet. */
    public record Selection(UUID token,ResourceLocation dimension,BlockPos anchor,BlockPos first,boolean mining) {
        public static Selection decode(FriendlyByteBuf b){return new Selection(b.readUUID(),b.readResourceLocation(),b.readBlockPos(),b.readBlockPos(),b.readBoolean());}
        public static void encode(Selection s,FriendlyByteBuf b){b.writeUUID(s.token);b.writeResourceLocation(s.dimension);b.writeBlockPos(s.anchor);b.writeBlockPos(s.first);b.writeBoolean(s.mining);}
    }
    // Protocol 3 accompanies the coolant recipe wire extension; mixed pre-reactor clients must not join.
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "area"), () -> "3", "3"::equals, "3"::equals);
    private static Consumer<Preview> receiver = ignored -> {};
    private static Consumer<Selection> selectionReceiver=ignored->{};
    private AreaNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(0, Preview.class, Preview::encode, Preview::decode, (message, supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> receiver.accept(message)); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Selection.class,Selection::encode,Selection::decode,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->selectionReceiver.accept(message));context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clientReceiver(Consumer<Preview> handler) { receiver = handler; }
    public static void selectionReceiver(Consumer<Selection> handler){selectionReceiver=handler;}
    public static void selection(ServerPlayer player,Selection value){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),value);}
    public static void preview(ServerPlayer player, BlockPos min, BlockPos max, boolean valid) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Preview(player.level().dimension().location(), min, max, valid)); }
}
