package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;

public final class SentinelLogic implements MachineLogic {
    private static final TagKey<EntityType<?>> EXCLUDED=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation(UnusualCopper.MOD_ID,"sentinel_blacklist"));
    private static final TagKey<EntityType<?>> HOSTILE=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation(UnusualCopper.MOD_ID,"sentinel_hostile"));
    public static final ResourceKey<DamageType> BEAM=ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(UnusualCopper.MOD_ID,"sentinel_beam"));
    public static int radius(CopperMachineBlockEntity m) { return m.sentinel.focused?ServerConfig.sentinelFocusedRange():ServerConfig.sentinelRange(); }
    public static Vec3 origin(CopperMachineBlockEntity m) { return Vec3.atCenterOf(m.getBlockPos()).add(0,1.15,0); }
    @Override public void tick(CopperMachineBlockEntity m,ServerLevel level) {
        SentinelScheduler.register(level,m); m.sentinel.active=false;
        if(!SentinelScheduler.permitted(level,m)) { m.status(MachineStatus.SENTINEL_LIMIT); return; }
        if(!ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner())) { m.status(MachineStatus.PROTECTED); return; }
        if(!m.energy.spend(ServerConfig.sentinelUpkeep())) { m.status(MachineStatus.NO_ENERGY); return; }
        m.sentinel.active=true;
        if(level.getGameTime()>=m.sentinel.nextScan) SentinelScheduler.request(level,m);
        if(m.status()==MachineStatus.IDLE||m.status()==MachineStatus.REDSTONE_PAUSED) m.status(MachineStatus.SCANNING);
    }
    public static boolean valid(CopperMachineBlockEntity m,ServerLevel level,Mob mob) {
        if(mob==null||!mob.isAlive()||mob.isRemoved()||mob.level()!=level||level.getEntity(mob.getUUID())!=mob||mob.hasCustomName()||mob.isInvulnerable()||mob instanceof NeutralMob||mob instanceof TamableAnimal||mob instanceof AbstractHorse||mob instanceof AbstractVillager||mob.isPassenger()||mob.isLeashed()||mob.getType().is(EXCLUDED)) return false;
        if(mob.getType().getCategory()!=MobCategory.MONSTER && !mob.getType().is(HOSTILE)) return false;
        // Vanilla spiders stop acquiring targets at brightness >= .5, unlike NeutralMob types.
        if(mob instanceof net.minecraft.world.entity.monster.Spider && mob.getLightLevelDependentMagicValue()>=.5F && (mob.getTarget()==null||!mob.getTarget().isAlive())) return false;
        var from=origin(m); var to=mob.getBoundingBox().getCenter(); double dx=to.x-from.x,dz=to.z-from.z;
        if(dx*dx+dz*dz>radius(m)*radius(m)||Math.abs(to.y-from.y)>ServerConfig.sentinelHeight()) return false;
        if(!level.getWorldBorder().isWithinBounds(mob.blockPosition())||!ProtectionPolicy.allowed(level,mob.blockPosition(),m.owner())) return false;
        var box=mob.getBoundingBox();
        for(double x:new double[]{box.minX,box.maxX}) for(double z:new double[]{box.minZ,box.maxZ}) if(!ProtectionPolicy.allowed(level,net.minecraft.core.BlockPos.containing(x,box.minY,z),m.owner())) return false;
        return SentinelScheduler.available(level,m,mob.getUUID());
    }
    public static boolean visible(CopperMachineBlockEntity m,ServerLevel level,Mob mob) {
        var from=origin(m); var to=mob.getBoundingBox().getCenter();
        // Clip would consult missing chunks: verify every crossed chunk before ray tracing.
        double length=from.distanceTo(to); int steps=(int)Math.ceil(length*2);
        for(int i=0;i<=steps;i++) if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(from.lerp(to,steps==0?0:(double)i/steps)))) return false;
        return !ServerConfig.sentinelLos()||level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.MISS;
    }
    public static void process(CopperMachineBlockEntity m,ServerLevel level) {
        if(m.isRemoved()||level.getBlockEntity(m.getBlockPos())!=m||!m.energyEnabled()||!m.sentinel.active||!SentinelScheduler.permitted(level,m)||!ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner())) return;
        m.sentinel.nextScan=level.getGameTime()+ServerConfig.sentinelScan();
        if(level.getGameTime()<m.sentinel.nextShot) return;
        if(m.energy.stored()<ServerConfig.sentinelCost()) { m.status(MachineStatus.NO_ENERGY); return; }
        Mob target=m.sentinel.target==null?null:level.getEntity(m.sentinel.target) instanceof Mob mob?mob:null;
        if(!valid(m,level,target)||!visible(m,level,target)) {
            var candidates=EntityIndexes.sentinelMobs(level,m,radius(m),ServerConfig.sentinelHeight()+2,ServerConfig.sentinelCandidates()); m.inspected=candidates.size();
            target=candidates.stream().filter(mob->valid(m,level,mob)).sorted(Comparator.comparingDouble(mob->mob.distanceToSqr(origin(m)))).filter(mob->visible(m,level,mob)).findFirst().orElse(null);
        }
        m.sentinel.target=target==null?null:target.getUUID();
        if(target==null) { m.status(MachineStatus.NO_TARGETS); return; }
        if(!valid(m,level,target)||!visible(m,level,target)) return;
        int cost=ServerConfig.sentinelCost(); if(!m.energy.spend(cost)) { m.status(MachineStatus.NO_ENERGY); return; }
        float health=target.getHealth(),absorption=target.getAbsorptionAmount(); boolean accepted=false;
        try { accepted=target.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BEAM)),ServerConfig.sentinelDamage()); }
        catch(RuntimeException hookFailure) { UnusualCopper.LOGGER.error("Sentinel damage hook failed",hookFailure); }
        boolean committed=accepted||target.getHealth()<health||target.getAbsorptionAmount()<absorption||!target.isAlive();
        if(committed) {
            m.sentinel.nextShot=level.getGameTime()+ServerConfig.sentinelInterval(); SentinelScheduler.lock(level,m,target.getUUID()); m.grown++; m.status(MachineStatus.WORKING);
            io.github.gerbesh.unusualcopper.network.MachineEffects.sentinel(level,m,target.getBoundingBox().getCenter());
        } else { m.energy.refundSpent(cost); m.status(MachineStatus.SHOT_DENIED); }
        m.setChanged();
    }
}
