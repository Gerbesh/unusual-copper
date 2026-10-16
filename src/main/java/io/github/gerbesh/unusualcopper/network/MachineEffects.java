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
    public record Beam(ResourceLocation dimension,BlockPos origin,Vec3 target) {
        public Beam {
            origin=origin.immutable();
            if(!Double.isFinite(target.x)||!Double.isFinite(target.y)||!Double.isFinite(target.z)||target.distanceToSqr(Vec3.atCenterOf(origin))>80*80) throw new IllegalArgumentException("Invalid sentinel beam");
        }
        static Beam decode(FriendlyByteBuf b) { return new Beam(b.readResourceLocation(),b.readBlockPos(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())); }
        static void encode(Beam v,FriendlyByteBuf b) { b.writeResourceLocation(v.dimension); b.writeBlockPos(v.origin); b.writeDouble(v.target.x); b.writeDouble(v.target.y); b.writeDouble(v.target.z); }
    }
    /** icon 0 = growth, -1 = harvest without a loot icon, positive = harvested item registry ID. */
    public record Plant(BlockPos pos,int icon) { public Plant {pos=pos.immutable();if(icon< -1)throw new IllegalArgumentException("Invalid plant icon");} }
    public record CultivationDelivery(ResourceLocation dimension,BlockPos origin,BlockPos storage,int icon) {
        public CultivationDelivery {
            origin=origin.immutable();storage=storage.immutable();
            if(origin.distSqr(storage)>32*32||icon<=0)throw new IllegalArgumentException("Invalid cultivation delivery");
        }
        static CultivationDelivery decode(FriendlyByteBuf b){return new CultivationDelivery(b.readResourceLocation(),b.readBlockPos(),b.readBlockPos(),b.readVarInt());}
        static void encode(CultivationDelivery v,FriendlyByteBuf b){b.writeResourceLocation(v.dimension);b.writeBlockPos(v.origin);b.writeBlockPos(v.storage);b.writeVarInt(v.icon);}
    }
    public record Cultivation(ResourceLocation dimension,BlockPos origin,BlockPos min,BlockPos max,float from,float to,List<Plant> plants) {
        public Cultivation {
            origin=origin.immutable();min=min.immutable();max=max.immutable();plants=List.copyOf(plants);
            var area=new io.github.gerbesh.unusualcopper.gameplay.WorkArea(min,max);
            double dx=Math.max(Math.abs((double)min.getX()-origin.getX()),Math.abs((double)max.getX()-origin.getX()));
            double dy=Math.max(Math.abs((double)min.getY()-origin.getY()),Math.abs((double)max.getY()-origin.getY()));
            double dz=Math.max(Math.abs((double)min.getZ()-origin.getZ()),Math.abs((double)max.getZ()-origin.getZ()));
            if(dx*dx+dy*dy+dz*dz>32*32 || !Float.isFinite(from)||!Float.isFinite(to)||from<0||to<from||to>33||plants.size()>8)
                throw new IllegalArgumentException("Invalid cultivation wave");
            for(var plant:plants)if(!area.contains(plant.pos()))throw new IllegalArgumentException("Plant outside wave area");
        }
        static Cultivation decode(FriendlyByteBuf b) {
            var dimension=b.readResourceLocation();var origin=b.readBlockPos();var min=b.readBlockPos();var max=b.readBlockPos();
            float from=b.readFloat(),to=b.readFloat();int size=b.readUnsignedByte();
            if(size>8)throw new IllegalArgumentException("Too many cultivation targets");
            var plants=new ArrayList<Plant>();for(int i=0;i<size;i++)plants.add(new Plant(b.readBlockPos(),b.readVarInt()));
            return new Cultivation(dimension,origin,min,max,from,to,plants);
        }
        static void encode(Cultivation v,FriendlyByteBuf b) {
            b.writeResourceLocation(v.dimension);b.writeBlockPos(v.origin);b.writeBlockPos(v.min);b.writeBlockPos(v.max);
            b.writeFloat(v.from);b.writeFloat(v.to);b.writeByte(v.plants.size());
            for(var plant:v.plants){b.writeBlockPos(plant.pos());b.writeVarInt(plant.icon());}
        }
    }
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(UnusualCopper.MOD_ID, "machine_effects"), () -> "5", "5"::equals, "5"::equals);
    private static Consumer<Wave> receiver = ignored -> {};
    private static Consumer<Reap> reaperReceiver = ignored -> {};
    private static Consumer<Forestry> forestryReceiver = ignored -> {};
    private static Consumer<Beam> beamReceiver=ignored->{};
    private static Consumer<Cultivation> cultivatorReceiver=ignored->{};
    private static Consumer<CultivationDelivery> cultivationDeliveryReceiver=ignored->{};
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
        CHANNEL.registerMessage(3,Beam.class,Beam::encode,Beam::decode,(effect,supplier)-> {
            var context=supplier.get(); context.enqueueWork(()->beamReceiver.accept(effect)); context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4,Cultivation.class,Cultivation::encode,Cultivation::decode,(effect,supplier)-> {
            var context=supplier.get();context.enqueueWork(()->cultivatorReceiver.accept(effect));context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5,CultivationDelivery.class,CultivationDelivery::encode,CultivationDelivery::decode,(effect,supplier)-> {
            var context=supplier.get();context.enqueueWork(()->cultivationDeliveryReceiver.accept(effect));context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void clientReceiver(Consumer<Wave> handler) { receiver = handler; }
    public static void reaperReceiver(Consumer<Reap> handler) { reaperReceiver=handler; }
    public static void forestryReceiver(Consumer<Forestry> handler) { forestryReceiver=handler; }
    public static void beamReceiver(Consumer<Beam> handler) { beamReceiver=handler; }
    public static void cultivatorReceiver(Consumer<Cultivation> handler) { cultivatorReceiver=handler; }
    public static void cultivationDeliveryReceiver(Consumer<CultivationDelivery> handler) { cultivationDeliveryReceiver=handler; }
    public static void cultivatorDelivery(ServerLevel level,CopperMachineBlockEntity machine,BlockPos storage,ItemStack moved) {
        try {
            var p=machine.getBlockPos();var effect=new CultivationDelivery(level.dimension().location(),p,storage,BuiltInRegistries.ITEM.getId(moved.getItem()));
            CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.getX()+.5,p.getY()+.5,p.getZ()+.5,80,level.dimension())),effect);
        }catch(RuntimeException unavailable){UnusualCopper.LOGGER.debug("Skipped unavailable cultivation delivery",unavailable);}
    }
    public static void cultivator(ServerLevel level,CopperMachineBlockEntity machine,float from,float to,Map<BlockPos,Integer> changes) {
        try {
            var p=machine.getBlockPos();var area=machine.workArea();if(area==null)return;
            var plants=changes.entrySet().stream().map(e->new Plant(e.getKey(),e.getValue())).toList();
            var effect=new Cultivation(level.dimension().location(),p,area.min(),area.max(),from,to,plants);
            CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.getX()+.5,p.getY()+.5,p.getZ()+.5,80,level.dimension())),effect);
        } catch(RuntimeException unavailable) { UnusualCopper.LOGGER.debug("Skipped unavailable cultivation effect",unavailable); }
    }
    public static void sentinel(ServerLevel level,CopperMachineBlockEntity m,Vec3 target) {
        try {
            var p=m.getBlockPos(); CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(p.getX()+.5,p.getY()+.5,p.getZ()+.5,96,level.dimension())),new Beam(level.dimension().location(),p,target));
        } catch(RuntimeException unavailable) { UnusualCopper.LOGGER.debug("Skipped unavailable sentinel effect",unavailable); }
    }
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
