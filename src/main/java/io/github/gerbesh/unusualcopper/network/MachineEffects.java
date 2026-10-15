package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
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
    public static final int WINDUP=0, STRIKE=1, CANCEL=2, TRANSFER=3;
    /** Registry IDs only: no stack count, private NBT, inventory state or client-side authority. */
    public record Reap(ResourceLocation dimension, BlockPos origin, UUID action, int phase, int entity, UUID target, Vec3 from, BlockPos to, int[] icons) {
        public Reap {
            origin=origin.immutable(); to=to.immutable(); icons=icons.clone();
            if(phase<0 || phase>3 || icons.length>4 || !Double.isFinite(from.x) || !Double.isFinite(from.y) || !Double.isFinite(from.z) || from.distanceToSqr(Vec3.atCenterOf(origin))>40*40 || to.distSqr(origin)>32*32 || Arrays.stream(icons).anyMatch(id -> id<=0)) throw new IllegalArgumentException("Invalid cosmetic event");
        }
        @Override public int[] icons() { return icons.clone(); }
        static Reap decode(FriendlyByteBuf b) { return new Reap(b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readUnsignedByte(),b.readInt(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBlockPos(),b.readVarIntArray(4)); }
        static void encode(Reap v,FriendlyByteBuf b) { b.writeResourceLocation(v.dimension); b.writeBlockPos(v.origin); b.writeUUID(v.action); b.writeByte(v.phase); b.writeInt(v.entity); b.writeUUID(v.target); b.writeDouble(v.from.x); b.writeDouble(v.from.y); b.writeDouble(v.from.z); b.writeBlockPos(v.to); b.writeVarIntArray(v.icons); }
    }
    public record Forestry(ResourceLocation dimension,BlockPos origin,boolean planted,List<BlockPos> positions) {
        public Forestry {
            origin=origin.immutable(); positions=positions.stream().map(BlockPos::immutable).toList();
            if(positions.isEmpty()||positions.size()>4) throw new IllegalArgumentException("Invalid forestry burst");
            for(var pos:positions) if(origin.distSqr(pos)>32*32) throw new IllegalArgumentException("Forestry burst outside range");
        }
        static Forestry decode(FriendlyByteBuf b) {
            var dimension=b.readResourceLocation(); var origin=b.readBlockPos(); boolean planted=b.readBoolean(); int count=b.readUnsignedByte();
            if(count<1||count>4) throw new IllegalArgumentException("Invalid forestry burst");
            var points=new ArrayList<BlockPos>(); for(int i=0;i<count;i++) points.add(b.readBlockPos()); return new Forestry(dimension,origin,planted,points);
        }
        static void encode(Forestry v,FriendlyByteBuf b) { b.writeResourceLocation(v.dimension); b.writeBlockPos(v.origin); b.writeBoolean(v.planted); b.writeByte(v.positions.size()); v.positions.forEach(b::writeBlockPos); }
    }
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "machine_effects"), () -> "3", "3"::equals, "3"::equals);
    private static Consumer<Wave> receiver = ignored -> {};
    private static Consumer<Reap> reaperReceiver = ignored -> {};
    private static Consumer<Forestry> forestryReceiver = ignored -> {};
    private MachineEffects() {}
    public static void register() {
        CHANNEL.registerMessage(0, Wave.class, Wave::encode, Wave::decode, (wave, supplier) -> {
            var context = supplier.get(); context.enqueueWork(() -> { if (Float.isFinite(wave.radius) && wave.radius >= 0 && wave.radius <= 32) receiver.accept(wave); }); context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Reap.class,Reap::encode,Reap::decode,(effect,supplier) -> {
            var context=supplier.get(); context.enqueueWork(() -> reaperReceiver.accept(effect)); context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,Forestry.class,Forestry::encode,Forestry::decode,(effect,supplier) -> {
            var context=supplier.get(); context.enqueueWork(()->forestryReceiver.accept(effect)); context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clientReceiver(Consumer<Wave> handler) { receiver = handler; }
    public static void reaperReceiver(Consumer<Reap> handler) { reaperReceiver=handler; }
    public static void forestryReceiver(Consumer<Forestry> handler) { forestryReceiver=handler; }
    public static void forestry(ServerLevel level,CopperMachineBlockEntity machine,boolean planted,List<BlockPos> changed) {
        if(changed.isEmpty()) return;
        try {
            var pos=machine.getBlockPos(); var effect=new Forestry(level.dimension().location(),pos,planted,changed);
            CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,64,level.dimension())),effect);
        } catch(RuntimeException unavailable) { UnusualCopper.LOGGER.debug("Skipped unavailable cosmetic forestry event",unavailable); }
    }
    private static void send(ServerLevel level,java.util.function.Supplier<Reap> event) {
        try {
            var effect=event.get(); var pos=effect.origin();
            CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,64,level.dimension())),effect);
        } catch (RuntimeException unavailable) { UnusualCopper.LOGGER.debug("Skipped unavailable cosmetic reaper event",unavailable); }
    }
    public static void reaperWindup(ServerLevel level,CopperMachineBlockEntity machine,Mob target,UUID action) {
        send(level,() -> new Reap(level.dimension().location(),machine.getBlockPos(),action,WINDUP,target.getId(),target.getUUID(),target.position().add(0,target.getBbHeight()*.55,0),machine.getBlockPos(),new int[0]));
    }
    public static void reaperCancel(ServerLevel level,CopperMachineBlockEntity machine,UUID action) {
        send(level,() -> new Reap(level.dimension().location(),machine.getBlockPos(),action,CANCEL,-1,new UUID(0,0),Vec3.atCenterOf(machine.getBlockPos()),machine.getBlockPos(),new int[0]));
    }
    public static void reaperStrike(ServerLevel level,CopperMachineBlockEntity machine,Mob target,UUID action,int[] icons) {
        send(level,() -> new Reap(level.dimension().location(),machine.getBlockPos(),action,STRIKE,target.getId(),target.getUUID(),target.position().add(0,target.getBbHeight()*.55,0),machine.getBlockPos(),icons));
    }
    public static void reaperTransfer(ServerLevel level,CopperMachineBlockEntity machine,BlockPos storage,ItemStack moved) {
        send(level,() -> new Reap(level.dimension().location(),machine.getBlockPos(),UUID.randomUUID(),TRANSFER,-1,new UUID(0,0),Vec3.atCenterOf(machine.getBlockPos()),storage,new int[]{BuiltInRegistries.ITEM.getId(moved.getItem())}));
    }
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
