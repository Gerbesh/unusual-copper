package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.config.ArmorSenseConfig;
import io.github.gerbesh.unusualcopper.gameplay.MiningRules;
import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.network.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import java.util.*;

/** Main-thread, owner-only sensing. Admission comes exclusively from an already reserved effect. */
public final class ArmorSensors {
    private static final Map<UUID,Session> SESSIONS=new LinkedHashMap<>();
    private static int turn;
    private static final class Session {
        final ServerPlayer player;
        int paidTick=-1,visor,scanner,profile,lastRay=-100,lastSend=-100,validationCursor;
        ArmorScanCursor cursor;int radius,roundDone=-100;boolean gaps;
        final Map<BlockPos,Integer> ores=new LinkedHashMap<>();
        ArmorSenseSnapshot.Target target;
        ArmorSenseSnapshot.DiagnosticState diagnostic=ArmorSenseSnapshot.DiagnosticState.AIM;
        String dimension="";ArmorSenseSnapshot last;
        net.minecraft.world.item.ItemStack helmet,chest;String headConfig,chestConfig;
        Session(ServerPlayer player){this.player=player;}
    }
    private ArmorSensors(){}
    public static void paid(ServerPlayer player,ArmorModuleType module,int level){
        if(module!=ArmorModuleType.OPTIC_VISOR&&module!=ArmorModuleType.FIELD_SCANNER)throw new IllegalArgumentException("Sense module");
        var s=SESSIONS.compute(player.getUUID(),(id,old)->old==null||old.player!=player?new Session(player):old);
        int now=player.server.getTickCount();
        if(s.paidTick!=now){
            s.paidTick=now;s.visor=s.scanner=0;
            s.helmet=player.getItemBySlot(EquipmentSlot.HEAD);s.chest=player.getItemBySlot(EquipmentSlot.CHEST);
            s.headConfig=ArmorData.configuration(s.helmet);s.chestConfig=ArmorData.configuration(s.chest);
        }
        if(module==ArmorModuleType.OPTIC_VISOR)s.visor=level;else s.scanner=level;
    }
    private static int equipped(Session s,ArmorModuleType module,int paid){
        if(paid==0)return 0;
        var helmet=s.player.getItemBySlot(EquipmentSlot.HEAD);
        var chest=s.player.getItemBySlot(EquipmentSlot.CHEST);
        return ArmorData.modules(helmet).contains(module)&&ArmorData.moduleLevel(helmet,module)==paid
                &&ArmorData.settings(helmet,module,ArmorData.profile(chest)).enabled()?paid:0;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||SESSIONS.isEmpty())return;
        var sessions=new ArrayList<>(SESSIONS.values());
        var budget=new ArmorScanBudget(ArmorSenseConfig.totalBudget(),ArmorScanBudget.fairSlice(ArmorSenseConfig.totalBudget(),ArmorSenseConfig.personalBudget(),sessions.size()));
        var rotation=new ArmorScanBudget.Rotation(turn,sessions.size());
        for(int index=0;index<sessions.size();index++){
            var s=sessions.get(rotation.at(index));var p=s.player;int now=p.server.getTickCount();
            boolean valid=s.paidTick==now&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isCreative()&&ArmorRuntime.completeSet(p)
                    &&s.helmet==p.getItemBySlot(EquipmentSlot.HEAD)&&s.chest==p.getItemBySlot(EquipmentSlot.CHEST)
                    &&s.headConfig.equals(ArmorData.configuration(s.helmet))&&s.chestConfig.equals(ArmorData.configuration(s.chest));
            int visor=valid?equipped(s,ArmorModuleType.OPTIC_VISOR,s.visor):0;
            int scanner=valid&&ArmorSenseConfig.diagnostics()?equipped(s,ArmorModuleType.FIELD_SCANNER,s.scanner):0;
            if(visor==0&&scanner==0){
                if(!p.isRemoved())ArmorNetwork.sendSense(p,empty(p));SESSIONS.remove(p.getUUID());continue;
            }
            String dimension=p.level().dimension().location().toString();int profile=ArmorData.profile(p.getItemBySlot(EquipmentSlot.CHEST));
            if(!dimension.equals(s.dimension)||s.profile!=profile){s.cursor=null;s.ores.clear();s.target=null;s.lastRay=-100;}
            if(s.last!=null&&s.last.scanner()!=scanner){s.target=null;s.lastRay=-100;}
            s.dimension=dimension;s.profile=profile;
            var permissions=new HashMap<Long,Boolean>();
            int before=budget.used();
            if(budget.remainingFor(p.getUUID())<64&&(scanner>0||visor==2&&ArmorSenseConfig.ores()))rotation.deferred(index);
            if(scanner>0&&now-s.lastRay>=5&&budget.remainingFor(p.getUUID())>=64)scanTarget(s,scanner,budget,permissions,now);
            if(visor==2&&ArmorSenseConfig.ores())scanOres(s,budget,permissions,now);
            else{s.cursor=null;s.ores.clear();}
            if(budget.used()>before)rotation.worked(index);
            var scan=visor<2?ArmorSenseSnapshot.ScanState.OFF:!ArmorSenseConfig.ores()?ArmorSenseSnapshot.ScanState.DISABLED
                    :s.cursor!=null&&s.cursor.complete()?s.gaps?ArmorSenseSnapshot.ScanState.PARTIAL:ArmorSenseSnapshot.ScanState.READY:ArmorSenseSnapshot.ScanState.SEARCHING;
            var value=new ArmorSenseSnapshot(dimension,profile,visor,scanner,ArmorRuntime.conditions(p),scan,
                    visor==2&&s.cursor!=null?s.cursor.checked():0,visor==2&&s.cursor!=null?s.cursor.total():0,
                    visor==2?s.ores.entrySet().stream().filter(entry->now-entry.getValue()<=24&&p.blockPosition().distSqr(entry.getKey())<=s.radius*s.radius).map(Map.Entry::getKey).toList():List.of(),scanner>0&&now-s.lastRay<=10?s.target:null,
                    scanner==0?ArmorSenseSnapshot.DiagnosticState.OFF:now-s.lastRay>10?ArmorSenseSnapshot.DiagnosticState.BUDGET:s.diagnostic);
            boolean activation=s.last==null||s.last.visor()!=visor||s.last.scanner()!=scanner||s.last.profile()!=profile||!s.last.dimension().equals(dimension);
            if(activation||now-s.lastSend>=5){ArmorNetwork.sendSense(p,value);s.last=value;s.lastSend=now;}
        }
        turn=rotation.next();
    }
    private static ArmorSenseSnapshot empty(ServerPlayer p){return new ArmorSenseSnapshot(p.level().dimension().location().toString(),0,0,0,0,ArmorSenseSnapshot.ScanState.OFF,0,0,List.of(),null);}
    private static boolean access(Session s,BlockPos pos,Map<Long,Boolean> permissions){
        var level=s.player.serverLevel();
        return !level.isOutsideBuildHeight(pos)&&level.getWorldBorder().isWithinBounds(pos)&&level.hasChunkAt(pos)
                &&permissions.computeIfAbsent(ChunkPos.asLong(pos),ignored->ProtectionPolicy.allowed(level,pos,s.player.getUUID()));
    }
    private static BlockState loadedState(Session s,BlockPos pos){
        var chunk=s.player.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null?null:chunk.getBlockState(pos);
    }
    private static void scanOres(Session s,ArmorScanBudget budget,Map<Long,Boolean> permissions,int now){
        var p=s.player;int radius=ArmorSenseConfig.radius();var center=p.blockPosition();
        if(s.cursor==null||s.radius!=radius||s.cursor.center().distSqr(center)>9){
            s.radius=radius;s.cursor=new ArmorScanCursor(center,radius);s.ores.clear();s.validationCursor=0;s.gaps=false;
        }else if(s.cursor.complete()&&now-s.roundDone>=20){s.cursor=new ArmorScanCursor(center,radius);s.gaps=false;}
        if(!budget.remaining(p.getUUID()))return;
        boolean wasComplete=s.cursor.complete();
        var known=new ArrayList<>(s.ores.keySet());int validated=0;
        for(int i=0;i<Math.min(16,known.size());i++){
            var pos=known.get(Math.floorMod(s.validationCursor+i,known.size()));
            if(!budget.take(p.getUUID()))break;
            validated++;
            var state=center.distSqr(pos)<=radius*radius&&access(s,pos,permissions)?loadedState(s,pos):null;
            if(state!=null&&MiningRules.ore(state))s.ores.put(pos,now);
            else s.ores.remove(pos);
        }
        s.validationCursor+=validated;
        while(!s.cursor.complete()&&budget.take(p.getUUID())){
            var pos=s.cursor.next();
            if(center.distSqr(pos)>radius*radius||p.level().isOutsideBuildHeight(pos))continue;
            if(!access(s,pos,permissions)){s.gaps=true;continue;}
            var state=loadedState(s,pos);if(state==null){s.gaps=true;continue;}
            if(MiningRules.ore(state)&&(s.ores.containsKey(pos)||s.ores.size()<ArmorSenseSnapshot.MAX_ORES))s.ores.put(pos,now);
        }
        if(s.cursor.complete()&&!wasComplete)s.roundDone=now;
    }
    /** A shape-aware ray through a bounded, read-only view: it cannot load chunks or read inventories. */
    private static void scanTarget(Session s,int scanner,ArmorScanBudget budget,Map<Long,Boolean> permissions,int now){
        var view=new SafeView(s,budget,permissions);var p=s.player;
        try{
            var eye=p.getEyePosition();var hit=view.clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
            if(view.exhausted)return; // Keep a previously approved result only for its short lease.
            s.target=null;s.lastRay=now;s.diagnostic=view.failure;
            if(view.denied||hit.getType()!=HitResult.Type.BLOCK)return;
            var pos=hit.getBlockPos();var state=view.states.get(pos);
            if(state==null||state.isAir()||eye.distanceToSqr(hit.getLocation())>64.01)return;
            int energy=0,capacity=0,status=-1,ports=0;
            if(state.getBlock() instanceof io.github.gerbesh.unusualcopper.machine.CopperMachineBlock){
                if(!access(s,pos,permissions)){s.diagnostic=ArmorSenseSnapshot.DiagnosticState.PROTECTED;return;}
                var chunk=p.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                if(chunk==null){s.diagnostic=ArmorSenseSnapshot.DiagnosticState.UNLOADED;return;}
                if(chunk.getBlockEntity(pos) instanceof CopperMachineBlockEntity m&&m.canAccess(p)){
                    energy=m.energy.stored();capacity=m.energy.capacity();status=m.status().ordinal();
                    if(scanner>=2)for(var side:Direction.values())ports|=m.sideMode(side).ordinal()<<(side.ordinal()*3);
                }
            }
            s.target=new ArmorSenseSnapshot.Target(pos,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),energy,capacity,status,ports);
            s.diagnostic=ArmorSenseSnapshot.DiagnosticState.READY;
        }catch(RuntimeException incompatibleShape){s.target=null;s.lastRay=now;s.diagnostic=ArmorSenseSnapshot.DiagnosticState.UNSUPPORTED;}
    }
    private static final class SafeView implements BlockGetter {
        final Session session;final ArmorScanBudget budget;final Map<Long,Boolean> permissions;
        final Map<BlockPos,BlockState> states=new HashMap<>();boolean denied,exhausted;
        ArmorSenseSnapshot.DiagnosticState failure=ArmorSenseSnapshot.DiagnosticState.AIM;
        SafeView(Session session,ArmorScanBudget budget,Map<Long,Boolean> permissions){this.session=session;this.budget=budget;this.permissions=permissions;}
        @Override public BlockState getBlockState(BlockPos pos){
            var known=states.get(pos);if(known!=null)return known;
            if(states.size()>=64){denied=true;failure=ArmorSenseSnapshot.DiagnosticState.UNSUPPORTED;return Blocks.BARRIER.defaultBlockState();}
            if(!budget.take(session.player.getUUID())){exhausted=true;return Blocks.BARRIER.defaultBlockState();}
            if(session.player.blockPosition().distSqr(pos)>121||!access(session,pos,permissions)){
                denied=true;failure=session.player.serverLevel().hasChunkAt(pos)?ArmorSenseSnapshot.DiagnosticState.PROTECTED:ArmorSenseSnapshot.DiagnosticState.UNLOADED;
                return Blocks.BARRIER.defaultBlockState();
            }
            var state=loadedState(session,pos);
            if(state==null){denied=true;failure=ArmorSenseSnapshot.DiagnosticState.UNLOADED;return Blocks.BARRIER.defaultBlockState();}
            states.put(pos.immutable(),state);return state;
        }
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return null;}
        @Override public int getHeight(){return session.player.level().getHeight();}
        @Override public int getMinBuildHeight(){return session.player.level().getMinBuildHeight();}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){SESSIONS.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppingEvent event){SESSIONS.clear();turn=0;}
}
