package io.github.gerbesh.unusualcopper.probe;

import io.github.gerbesh.unusualcopper.gameplay.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Separate, reobfuscated test mod. Never packaged into the production artifact. */
@Mod("unusualcopper_probe")
public final class ProductionProbe {
    private static ServerLevel level; private static long started; private static boolean done;
    private static UUID cancelledVictim;
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) { if (event.getEntity().getUUID().equals(cancelledVictim)) event.setCanceled(true); }
    private static final BlockPos TARGET = new BlockPos(130, 70, 130);
    private static final class Checkpoint extends net.minecraft.world.level.saveddata.SavedData {
        UUID animal; int round, emergency;
        static Checkpoint load(net.minecraft.nbt.CompoundTag tag) { var value = new Checkpoint(); value.animal = tag.hasUUID("Animal") ? tag.getUUID("Animal") : null; value.round = tag.getInt("Round"); value.emergency=tag.getInt("Emergency"); return value; }
        @Override public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) { if (animal != null) tag.putUUID("Animal", animal); tag.putInt("Round",round); tag.putInt("Emergency",emergency); return tag; }
    }
    public ProductionProbe() { MinecraftForge.EVENT_BUS.register(ProductionProbe.class); }
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private static <T extends Mob> T spawn(EntityType<T> type, BlockPos pos) {
        var mob = type.create(level); mob.setPos(pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5); mob.setNoAi(true); mob.setNoGravity(true); mob.setInvulnerable(true); check(level.addFreshEntity(mob), "Probe mob spawn rejected"); return mob;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("unusualcopper.productionProbe")) return;
        level = event.getServer().overworld(); started = level.getGameTime(); level.setChunkForced(TARGET.getX() >> 4, TARGET.getZ() >> 4, true); level.getChunkAt(TARGET);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (done || level == null || event.level != level || event.phase != TickEvent.Phase.END) return;
        if (level.getGameTime() - started > 400) throw new IllegalStateException("Production probe could not load entity region");
        if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(TARGET.getX() >> 4, TARGET.getZ() >> 4)) || !level.isPositionEntityTicking(TARGET)) return;
        var checkpoint = level.getDataStorage().computeIfAbsent(Checkpoint::load, Checkpoint::new, "unusualcopper_production_population_probe");
        if (checkpoint.animal != null && level.getEntity(checkpoint.animal) == null) return;
        done = true;
        var area = WorkArea.between(TARGET, TARGET.east(2));
        for (var mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(area.min(), area.max().offset(1,1,1)).inflate(2))) mob.discard();
        var cows = new ArrayList<Cow>(); for (int i = 0; i < 21; i++) cows.add(spawn(EntityType.COW, TARGET));
        Horse horse = spawn(EntityType.HORSE, TARGET.east()); Wolf wolf = spawn(EntityType.WOLF, TARGET.east(2));
        var rule = new ReaperRule(true, true, Set.of(), Set.of(), EnumSet.allOf(MobCategory.class));
        var ledger = MobPopulations.ledger(level, UUID.randomUUID(), TARGET.west(2), area, rule);
        check(ledger.step(16) <= 16 && !ledger.ready(), "Production cold scan did not respect its budget"); ledger.step(16);
        check(ledger.ready() && ledger.count() == 23, "Production population count differs: " + ledger.count());
        horse.setOwnerUUID(UUID.randomUUID()); check(ledger.count() == 22, "SRG horse owner hook failed"); horse.setOwnerUUID(null); check(ledger.count() == 23, "SRG horse owner removal failed");
        wolf.setOwnerUUID(UUID.randomUUID()); check(ledger.count() == 22, "SRG wolf data hook failed"); wolf.setOwnerUUID(null); check(ledger.count() == 23, "SRG wolf owner removal failed");
        var cow = cows.get(0); cow.setCustomName(Component.literal("Protected")); check(ledger.count() == 22, "SRG custom name hook failed"); cow.setCustomName(null);
        cow.setCustomName(Component.literal("Bulk protected")); var snapshot = cow.getEntityData().getNonDefaultValues(); cow.setCustomName(null); cow.getEntityData().assignValues(snapshot); check(ledger.count() == 22, "SRG bulk metadata hook failed"); cow.setCustomName(null);
        cow.setLeashedTo(cows.get(1), false); check(ledger.count() == 22, "SRG leash hook failed"); cow.dropLeash(false, false); check(ledger.count() == 23, "SRG leash removal failed");
        cow.setPos(TARGET.getX() + .5, TARGET.getY() + .1, TARGET.getZ() + 1.5); check(ledger.count() == 22, "SRG same-section position hook failed");
        cow.setPos(TARGET.getX() + .5, TARGET.getY() + .1, TARGET.getZ() + .5); cow.setAge(-5000); check(ledger.count() == 22, "SRG age hook failed"); cow.setAge(0); check(ledger.count() == 23, "SRG adulthood hook failed");
        cow.setHealth(0); check(ledger.count() == 22, "SRG health hook failed");
        cows.forEach(Mob::discard); horse.discard(); wolf.discard(); check(ledger.count() == 0, "SRG entity removal hook failed");
        var machinePos=TARGET.west(2);
        if (checkpoint.emergency>0) {
            var restored=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(machinePos);
            check(restored!=null && restored.reaper.lootCount()==checkpoint.emergency && restored.reaper.loot().get(0).getHoverName().getString().equals("Production emergency loot"),"Emergency loot lost count or NBT on real process restart");
        }
        level.setBlock(machinePos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TARGET).inflate(4))) item.discard();
        level.setBlock(machinePos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.MOB_REAPER).get().defaultBlockState(),3);
        var machine=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(machinePos); machine.energy.add(3000,false);
        var previousPlayer=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ProductionAttacker"));
        var cancelled=spawn(EntityType.COW,TARGET); cancelled.setInvulnerable(false); cancelled.setLastHurtByPlayer(previousPlayer); cancelledVictim=cancelled.getUUID();
        check(!ReaperLoot.kill(machine,cancelled,level,1500) && cancelled.isAlive() && cancelled.getHealth()==cancelled.getMaxHealth() && cancelled.getKillCredit()==previousPlayer && machine.energy.stored()==3000,"SRG cancellation failed health/credit/energy restore"); cancelledVictim=null; cancelled.discard();
        var victim=spawn(EntityType.COW,TARGET); victim.setInvulnerable(false); victim.setLastHurtByPlayer(previousPlayer);
        check(ReaperLoot.kill(machine,victim,level,1500) && ((ReaperVictim)victim).unusualcopper$deathCommitted() && victim.getKillCredit()==null && machine.energy.stored()==1500,"SRG real death or player credit suppression failed");
        check(!machine.inventory.getStackInSlot(2).isEmpty() && level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,new net.minecraft.world.phys.AABB(TARGET).inflate(4)).isEmpty(),"SRG real loot capture or zero XP failed"); victim.discard();
        var emergency=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,5); emergency.setHoverName(Component.literal("Production emergency loot")); machine.reaper.hold(emergency); ((net.minecraft.world.level.block.entity.BlockEntity)machine).setChanged(); checkpoint.emergency=5;
        var savedPos = TARGET.east(8);
        if (checkpoint.animal == null) checkpoint.animal = spawn(EntityType.COW, savedPos).getUUID();
        var savedArea = new WorkArea(savedPos,savedPos); var loaded = MobPopulations.ledger(level, UUID.randomUUID(), TARGET, savedArea, rule); loaded.step(16);
        check(loaded.ready() && loaded.count() == 1 && loaded.target().getUUID().equals(checkpoint.animal), "Original persisted cow is not indexed exactly once");
        checkpoint.round++; checkpoint.setDirty();
        LogUtils.getLogger().info("CuE PRODUCTION REAPER PASS: real paid death, no player credit/XP, accepted loot, cancelled death restores health/credit/energy, emergency loot checkpoint round={}",checkpoint.round);
        LogUtils.getLogger().info("CuE PRODUCTION POPULATION PASS: exact 23-mob count; bounded bootstrap; SRG position, name, age, health, wolf/horse ownership, leash and removal hooks; persisted round={}", checkpoint.round);
        level.setChunkForced(TARGET.getX() >> 4, TARGET.getZ() >> 4, false); level.getServer().halt(false);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { if (level != null) level.setChunkForced(TARGET.getX() >> 4, TARGET.getZ() >> 4, false); }
}
