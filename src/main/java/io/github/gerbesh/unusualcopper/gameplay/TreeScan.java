package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Bounded structural scan of one explicitly marked plantation. Reads loaded blocks only. */
public final class TreeScan {
    public enum Result { SCANNING, READY, WAITING, UNSUPPORTED, UNLOADED, PROTECTED, TOO_LARGE, BUILDING, AMBIGUOUS }
    public record Log(BlockPos pos,BlockState state) { public Log { pos=pos.immutable(); } }
    public final BlockPos point; public final TreeSpecies species; public final UUID owner;
    private final ArrayDeque<BlockPos> queue=new ArrayDeque<>();
    private final Set<BlockPos> seen=new HashSet<>();
    private final List<Log> logs=new ArrayList<>();
    private List<BlockPos> roots=List.of();
    private Result result=Result.SCANNING;
    private int leaves,treeBlocks,lowestLeaf=Integer.MAX_VALUE;
    public TreeScan(BlockPos point,TreeSpecies species,UUID owner) { this.point=point.immutable(); this.species=species; this.owner=owner; queue.add(this.point); seen.add(this.point); }
    public Result result() { return result; }
    public int treeBlocks() { return treeBlocks; }
    public List<Log> logs() { return logs.stream().sorted(Comparator.comparingInt((Log l)->l.pos().getY()).reversed().thenComparingLong(l->l.pos().asLong())).toList(); }
    public List<BlockPos> roots() { return roots; }
    public static boolean construction(BlockState state,TreeSpecies species) {
        if(state.is(species.log)) return false;
        if(state.is(species.leaves)) return state.getValue(LeavesBlock.PERSISTENT);
        return state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.hasBlockEntity()||(!state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof VineBlock) && !(species==TreeSpecies.JUNGLE && state.is(Blocks.COCOA)) && !state.is(Blocks.SNOW) && state.getFluidState().isEmpty() && !state.is(BlockTags.DIRT) && !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.SAND));
    }
    private boolean loaded(ServerLevel level,BlockPos pos) { return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos); }
    private void start(ServerLevel level) {
        if(!loaded(level,point) || !loaded(level,point.below())) { result=Result.UNLOADED; return; }
        var state=level.getBlockState(point);
        if(state.is(species.sapling)) { result=Result.WAITING; return; }
        if(!state.is(species.log)) { result=Result.UNSUPPORTED; return; }
        if(species.large) outer: for(int dx=-1;dx<=0;dx++) for(int dz=-1;dz<=0;dz++) {
            var base=point.offset(dx,0,dz); var four=List.of(base,base.east(),base.south(),base.east().south());
            if(four.stream().allMatch(p -> loaded(level,p) && level.getBlockState(p).is(species.log))) { roots=four; break outer; }
        }
        if(roots.isEmpty()) roots=List.of(point);
        if(species.requiresFour && roots.size()!=4) { result=Result.AMBIGUOUS; return; }
        for(var root:roots) {
            if(!loaded(level,root.below())) { result=Result.UNLOADED; return; }
            if(!ProtectionPolicy.allowed(level,root,owner) || !ProtectionPolicy.allowed(level,root.below(),owner)) { result=Result.PROTECTED; return; }
            if(!species.sapling.defaultBlockState().canSurvive(level,root) || level.getBlockState(root).getValue(RotatedPillarBlock.AXIS)!=Direction.Axis.Y) { result=Result.AMBIGUOUS; return; }
        }
    }
    public int step(ServerLevel level,int budget) {
        if(result!=Result.SCANNING) return 0;
        if(roots.isEmpty()) start(level);
        int used=0;
        while(result==Result.SCANNING && !queue.isEmpty() && used<budget) {
            var pos=queue.removeFirst(); used++;
            if(!loaded(level,pos)) { result=Result.UNLOADED; break; }
            var state=level.getBlockState(pos); boolean log=state.is(species.log), leaf=state.is(species.leaves);
            if(!log && !leaf) {
                if(io.github.gerbesh.unusualcopper.config.ServerConfig.arboristBuildingGuard() && construction(state,species)) result=Result.BUILDING;
                continue;
            }
            if(!ProtectionPolicy.allowed(level,pos,owner)) { result=Result.PROTECTED; break; }
            if(++treeBlocks>io.github.gerbesh.unusualcopper.config.ServerConfig.arboristTreeBlocks()) { result=Result.TOO_LARGE; break; }
            if(leaf) {
                if(state.getValue(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.DISTANCE)>=7) { result=Result.BUILDING; break; }
                leaves++; lowestLeaf=Math.min(lowestLeaf,pos.getY());
            } else {
                if(pos.getY()==point.getY() && !roots.contains(pos)) { result=Result.AMBIGUOUS; break; }
                if(!loaded(level,pos.below())) { result=Result.UNLOADED; break; }
                if(!roots.contains(pos) && species.sapling.defaultBlockState().canSurvive(level,pos)) { result=Result.AMBIGUOUS; break; }
                logs.add(new Log(pos,state));
            }
            for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dy==0 && dz==0) continue;
                var next=pos.offset(dx,dy,dz);
                if(next.getY()<point.getY()) continue;
                if(seen.add(next)) { if(seen.size()>8192) { result=Result.TOO_LARGE; break; } queue.addLast(next); }
            }
        }
        if(result==Result.SCANNING && queue.isEmpty()) {
            result=logs.size()>=3 && leaves>=6?Result.READY:Result.AMBIGUOUS;
            for(var log:logs) if(log.pos().getY()<lowestLeaf-2 && (!roots.stream().anyMatch(r->r.getX()==log.pos().getX() && r.getZ()==log.pos().getZ()) || log.state().getValue(RotatedPillarBlock.AXIS)!=Direction.Axis.Y)) result=Result.BUILDING;
        }
        return used;
    }
}
