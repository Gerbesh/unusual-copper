package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import net.minecraftforge.common.ForgeHooks;
import java.util.*;

/** One marked tree at a time, with an exclusive tool/sapling escrow and full-cycle energy admission. */
public final class ArboristLogic implements MachineLogic {
    public static int nodeBudget() { return Math.min(io.github.gerbesh.unusualcopper.config.ServerConfig.arboristNodes(),io.github.gerbesh.unusualcopper.config.ServerConfig.areaBudget()); }
    @Override public void tick(CopperMachineBlockEntity m,ServerLevel level) {
        if(m.arborist.points.isEmpty() && m.arborist.job==null) { m.status(MachineStatus.NO_PLANTATIONS); return; }
        if(level.getGameTime()>=m.arborist.retryAt) AreaWorkScheduler.request(level,m);
    }
    private static boolean accessible(CopperMachineBlockEntity m,ServerLevel level,BlockPos pos) {
        return !m.isRemoved() && level.getBlockEntity(m.getBlockPos())==m && m.getBlockPos().distSqr(pos)<=32*32 && !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos) && ProtectionPolicy.allowed(level,pos,m.owner());
    }
    private static boolean durable(ItemStack tool,int logs) { return TreeSpecies.axe(tool) && (!tool.isDamageableItem() || tool.getMaxDamage()-tool.getDamageValue()>=logs); }
    private static net.minecraftforge.common.util.FakePlayer actor(CopperMachineBlockEntity m,ServerLevel level,UUID owner) {
        var online=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
        var cache=level.getServer().getProfileCache();
        var fallback=new com.mojang.authlib.GameProfile(owner==null?new UUID(0,1):owner,"[CuEArborist]");
        var profile=online!=null?online.getGameProfile():owner!=null && cache!=null?cache.get(owner).orElse(fallback):fallback;
        if(m.arborist.actor==null || !m.arborist.actor.getGameProfile().equals(profile)) m.arborist.actor=new net.minecraftforge.common.util.FakePlayer(level,profile);
        return m.arborist.actor;
    }
    private static void wait(CopperMachineBlockEntity m,ServerLevel level,MachineStatus status,int ticks) { m.status(status); m.arborist.retryAt=level.getGameTime()+ticks; m.setChanged(); }
    public static void process(CopperMachineBlockEntity m,ServerLevel level) {
        var state=m.arborist;
        if(!m.energyEnabled()) { m.status(MachineStatus.REDSTONE_PAUSED); return; }
        if(!accessible(m,level,m.getBlockPos())) { m.status(MachineStatus.PROTECTED); return; }
        state.recovery.drain(m);
        if(!state.recovery.empty() || m.boundCargoCount()>0) { wait(m,level,MachineStatus.OUTPUT_FULL,20); return; }
        if(state.faulted) { m.status(MachineStatus.TREE_FAULT); return; }
        if(state.job!=null) { work(m,level); return; }
        if(state.points.isEmpty()) { m.status(MachineStatus.NO_PLANTATIONS); return; }
        if(state.scan==null) {
            var point=state.points.get(Math.floorMod(state.nextPoint++,state.points.size()));
            if(!accessible(m,level,point.pos())) { wait(m,level,MachineStatus.PROTECTED,200); return; }
            state.scan=new TreeScan(point.pos(),point.species(),m.owner());
        }
        var scan=state.scan; m.inspected=scan.step(level,nodeBudget());
        if(scan.result()==TreeScan.Result.SCANNING) { m.status(MachineStatus.TREE_SCANNING); return; }
        if(scan.result()!=TreeScan.Result.READY) {
            var status=switch(scan.result()) { case WAITING -> MachineStatus.TREE_WAITING; case UNLOADED -> MachineStatus.UNLOADED; case PROTECTED -> MachineStatus.PROTECTED; case TOO_LARGE -> MachineStatus.TREE_TOO_LARGE; case BUILDING -> MachineStatus.BUILDING_NEARBY; case UNSUPPORTED -> MachineStatus.UNSUPPORTED; default -> MachineStatus.TREE_AMBIGUOUS; };
            state.scan=null; wait(m,level,status,200); return;
        }
        var logs=scan.logs(); var roots=scan.roots();
        if(!durable(m.inventory.getStackInSlot(1),logs.size())) { wait(m,level,MachineStatus.NO_TOOL,20); return; }
        var saplings=m.inventory.getStackInSlot(0);
        if(!saplings.is(scan.species.sapling.asItem()) || saplings.getCount()<roots.size()) { wait(m,level,MachineStatus.NEED_SAPLINGS,20); return; }
        int logCost=io.github.gerbesh.unusualcopper.config.ServerConfig.arboristCost(), plantCost=io.github.gerbesh.unusualcopper.config.ServerConfig.arboristPlantCost();
        int cost=logs.size()*logCost+roots.size()*plantCost;
        m.operationCost=cost;
        if(cost>m.kind.capacity) { state.scan=null; wait(m,level,MachineStatus.TREE_TOO_COSTLY,200); return; }
        if(m.energy.stored()<cost) { wait(m,level,MachineStatus.NO_ENERGY,20); return; }
        // Standard vanilla tree output fits the owned 64-stack buffer; arbitrary GLM excess remains real world loot.
        if(!m.inventory.getStackInSlot(2).isEmpty() || !m.inventory.getStackInSlot(3).isEmpty()) { wait(m,level,MachineStatus.OUTPUT_FULL,20); return; }
        var actor=actor(m,level,m.owner());
        for(var log:logs) if(!accessible(m,level,log.pos()) || !level.mayInteract(actor,log.pos())) { state.scan=null; wait(m,level,MachineStatus.PROTECTED,200); return; }
        for(var root:roots) if(!accessible(m,level,root.below()) || !level.mayInteract(actor,root.below())) { state.scan=null; wait(m,level,MachineStatus.PROTECTED,200); return; }
        for(var log:logs) if(!level.getBlockState(log.pos()).equals(log.state())) { state.scan=null; wait(m,level,MachineStatus.TREE_CHANGED,200); return; }
        state.job=new ArboristState.Job(scan.species,logs,roots,m.owner(),logCost,plantCost,m.inventory.extractItem(1,1,false),m.inventory.extractItem(0,roots.size(),false));
        state.scan=null; m.setChanged(); work(m,level);
    }
    private static void work(CopperMachineBlockEntity m,ServerLevel level) {
        var state=m.arborist; var job=state.job;
        m.duration=job.logs.size()+job.roots.size(); m.progress=job.cut+job.planted; m.operationCost=job.remainingCost();
        if(!Objects.equals(job.owner,m.owner())) { wait(m,level,MachineStatus.PROTECTED,20); return; }
        if(m.energy.stored()<job.remainingCost()) { wait(m,level,MachineStatus.NO_ENERGY,20); return; }
        if(job.cut<job.logs.size() && !durable(job.tool,job.logs.size()-job.cut)) { wait(m,level,MachineStatus.NO_TOOL,20); return; }
        var actor=actor(m,level,job.owner);
        var oldHand=actor.getItemInHand(InteractionHand.MAIN_HAND); var oldPos=actor.position(); boolean creative=actor.getAbilities().instabuild, sneaking=actor.isShiftKeyDown(); actor.getAbilities().instabuild=false; actor.setShiftKeyDown(false);
        var changed=new ArrayList<BlockPos>();
        try {
            for(int n=0;n<io.github.gerbesh.unusualcopper.config.ServerConfig.arboristLogs() && job.cut<job.logs.size();n++) {
                var log=job.logs.get(job.cut); var pos=log.pos();
                if(!accessible(m,level,pos) || !level.mayInteract(actor,pos)) { wait(m,level,MachineStatus.PROTECTED,20); return; }
                if(!level.getBlockState(pos).equals(log.state())) { state.faulted=true; wait(m,level,MachineStatus.TREE_CHANGED,200); return; }
                if(io.github.gerbesh.unusualcopper.config.ServerConfig.arboristBuildingGuard()) for(var neighbor:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))) {
                    if(!level.hasChunkAt(neighbor)) { wait(m,level,MachineStatus.UNLOADED,20); return; }
                    if(level.getBlockState(neighbor).is(job.species.log)) {
                        var index=job.logIndices.get(neighbor);
                        if(index==null||index<job.cut) { state.faulted=true; wait(m,level,MachineStatus.TREE_CHANGED,200); return; }
                    }
                    if(TreeScan.construction(level.getBlockState(neighbor),job.species)) { wait(m,level,MachineStatus.BUILDING_NEARBY,20); return; }
                }
                actor.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5); actor.setItemInHand(InteractionHand.MAIN_HAND,job.tool);
                if(!log.state().canHarvestBlock(level,pos,actor)) { wait(m,level,MachineStatus.BREAK_DENIED,20); return; }
                int xp=ForgeHooks.onBlockBreakEvent(level,net.minecraft.world.level.GameType.SURVIVAL,actor,pos);
                if(xp<0) { job.tool=actor.getMainHandItem(); wait(m,level,MachineStatus.BREAK_DENIED,20); return; }
                if(!accessible(m,level,pos) || !level.mayInteract(actor,pos)) { wait(m,level,MachineStatus.PROTECTED,20); return; }
                if(!level.getBlockState(pos).equals(log.state())) { state.faulted=true; wait(m,level,MachineStatus.TREE_CHANGED,200); return; }
                var before=job.tool.copy(); var block=log.state().getBlock(); block.playerWillDestroy(level,pos,log.state(),actor);
                if(!log.state().onDestroyedByPlayer(level,pos,actor,true,level.getFluidState(pos))) { wait(m,level,MachineStatus.BREAK_DENIED,20); return; }
                // The irreversible world change is already committed: retain its price/progress even if a later hook throws.
                job.cut++; m.energy.spend(job.logCost); changed.add(pos); m.setChanged();
                try {
                    block.destroy(level,pos,log.state()); job.tool.mineBlock(level,log.state(),pos,actor);
                    ArboristDrops.emit(m,level,pos,Block.getDrops(log.state(),level,pos,null,actor,before));
                    log.state().spawnAfterBreak(level,pos,before,true); if(xp>0) block.popExperience(level,pos,xp);
                } finally { job.tool=actor.getMainHandItem(); m.setChanged(); }
                if(!state.recovery.empty()) { wait(m,level,MachineStatus.OUTPUT_FULL,20); return; }
            }
            if(job.cut<job.logs.size()) { m.status(MachineStatus.WORKING); return; }
            m.status(MachineStatus.REPLANTING);
            if(job.planted<job.roots.size()) {
                var root=job.roots.get(job.planted);
                if(!accessible(m,level,root) || !accessible(m,level,root.below()) || !level.mayInteract(actor,root.below())) { wait(m,level,MachineStatus.PROTECTED,20); return; }
                if(!level.getBlockState(root).canBeReplaced() || !job.species.sapling.defaultBlockState().canSurvive(level,root) || job.saplings.isEmpty()) { wait(m,level,MachineStatus.PLANT_DENIED,20); return; }
                actor.setPos(root.getX()+.5,root.getY()+.5,root.getZ()+.5); actor.setItemInHand(InteractionHand.MAIN_HAND,job.saplings); int before=job.saplings.getCount();
                var result=job.saplings.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(root.below()).add(0,.5,0),Direction.UP,root.below(),false)));
                job.saplings=actor.getMainHandItem(); int spent=before-job.saplings.getCount();
                if(spent==1 && result.consumesAction()) { job.planted++; m.energy.spend(job.plantCost); m.setChanged(); io.github.gerbesh.unusualcopper.network.MachineEffects.forestry(level,m,true,List.of(root)); }
                else { if(spent!=0) state.faulted=true; wait(m,level,MachineStatus.PLANT_DENIED,20); return; }
            }
            if(job.planted==job.roots.size()) {
                if(m.inventory.getStackInSlot(1).isEmpty()) m.inventory.setStackInSlot(1,job.tool); else state.recovery.retain(job.tool);
                state.recovery.retain(job.saplings); state.job=null; state.retryAt=level.getGameTime()+200; m.progress=m.duration=m.operationCost=0; m.grown++; m.status(MachineStatus.WORKING); m.setChanged();
            }
        } finally { actor.setItemInHand(InteractionHand.MAIN_HAND,oldHand); actor.setPos(oldPos); actor.getAbilities().instabuild=creative; actor.setShiftKeyDown(sneaking); io.github.gerbesh.unusualcopper.network.MachineEffects.forestry(level,m,false,changed); }
    }
}
