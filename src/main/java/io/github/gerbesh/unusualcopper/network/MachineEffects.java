package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Consumer;

/** Cosmetic server-to-client events; they never authorize an operation. */
public final class MachineEffects {
    public record Wave(ResourceLocation dimension, BlockPos origin, float radius, int[] entities) {
        public Wave { origin = origin.immutable(); entities = entities.clone(); if (entities.length > 64) throw new IllegalArgumentException("Too many wave targets"); }
        static Wave decode(FriendlyByteBuf buffer) { return new Wave(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readFloat(), buffer.readVarIntArray(64)); }
        static void encode(Wave wave, FriendlyByteBuf buffer) { buffer.writeResourceLocation(wave.dimension); buffer.writeBlockPos(wave.origin); buffer.writeFloat(wave.radius); buffer.writeVarIntArray(wave.entities); }
    }
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "machine_effects"), () -> "1", "1"::equals, "1"::equals);
    private static Consumer<Wave> receiver = ignored -> {};
    private MachineEffects() {}
    public static void register() {
        CHANNEL.registerMessage(0, Wave.class, Wave::encode, Wave::decode, (wave, supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> { if (Float.isFinite(wave.radius) && wave.radius >= 0 && wave.radius <= 32) receiver.accept(wave); }); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clientReceiver(Consumer<Wave> handler) { receiver = handler; }
    public static void shepherd(ServerLevel level, CopperMachineBlockEntity machine, List<Integer> affected) {
        var area = machine.workArea(); if (area == null) return;
        double x = Math.max(Math.abs(area.min().getX() - machine.getBlockPos().getX()), Math.abs(area.max().getX() - machine.getBlockPos().getX()));
        double z = Math.max(Math.abs(area.min().getZ() - machine.getBlockPos().getZ()), Math.abs(area.max().getZ() - machine.getBlockPos().getZ()));
        float radius = (float)Math.min(32, Math.max(1, Math.sqrt(x * x + z * z)));
        var pos = machine.getBlockPos();
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 64, level.dimension())),
                new Wave(level.dimension().location(), pos, radius, affected.stream().mapToInt(Integer::intValue).toArray()));
    }
}
