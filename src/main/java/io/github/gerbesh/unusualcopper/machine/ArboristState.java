package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.storage.OwnedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class ArboristState {
    public record Point(BlockPos pos,TreeSpecies species) { public Point { pos=pos.immutable(); } }
    public final List<Point> points=new ArrayList<>();
    public final OwnedItems recovery=new OwnedItems();
    public TreeScan scan;
    public int nextPoint;
    public long retryAt;
    public boolean faulted;
    public Job job;
    public net.minecraftforge.common.util.FakePlayer actor;
    public static final class Job {
        public final TreeSpecies species; public final List<TreeScan.Log> logs; public final List<BlockPos> roots; public final UUID owner;
        public final Map<BlockPos,Integer> logIndices;
        public final int logCost,plantCost;
        public int cut,planted;
        public ItemStack tool,saplings;
        public Job(TreeSpecies species,List<TreeScan.Log> logs,List<BlockPos> roots,UUID owner,int logCost,int plantCost,ItemStack tool,ItemStack saplings) {
            this.species=species; this.logs=List.copyOf(logs); this.roots=List.copyOf(roots); this.owner=owner; this.logCost=logCost; this.plantCost=plantCost; this.tool=tool; this.saplings=saplings;
            var indices=new HashMap<BlockPos,Integer>(); for(int i=0;i<logs.size();i++) indices.put(logs.get(i).pos(),i); logIndices=Map.copyOf(indices);
        }
        public int remainingCost() { return Math.addExact(Math.multiplyExact(logs.size()-cut,logCost),Math.multiplyExact(roots.size()-planted,plantCost)); }
        CompoundTag save() {
            var t=new CompoundTag(); t.putString("Species",species.name()); if(owner!=null) t.putUUID("Owner",owner); t.putInt("LogCost",logCost); t.putInt("PlantCost",plantCost); t.putInt("Cut",cut); t.putInt("Planted",planted);
            t.put("Tool",tool.save(new CompoundTag())); t.put("Saplings",saplings.save(new CompoundTag()));
            var tree=new ListTag(); for(var log:logs) { var row=new CompoundTag(); row.putLong("Pos",log.pos().asLong()); row.put("State",NbtUtils.writeBlockState(log.state())); tree.add(row); } t.put("Logs",tree);
            var sites=new ListTag(); for(var root:roots) sites.add(LongTag.valueOf(root.asLong())); t.put("Roots",sites); return t;
        }
        static Job load(CompoundTag t) {
            var species=TreeSpecies.valueOf(t.getString("Species")); var logs=new ArrayList<TreeScan.Log>(); var roots=new ArrayList<BlockPos>();
            for(var raw:t.getList("Logs",Tag.TAG_COMPOUND)) { var row=(CompoundTag)raw; logs.add(new TreeScan.Log(BlockPos.of(row.getLong("Pos")),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),row.getCompound("State")))); }
            for(var raw:t.getList("Roots",Tag.TAG_LONG)) roots.add(BlockPos.of(((LongTag)raw).getAsLong()));
            if(logs.isEmpty()||logs.size()>256||(roots.size()!=1&&roots.size()!=4)||logs.stream().anyMatch(log->!log.state().is(species.log))) throw new IllegalArgumentException("Invalid forestry plan");
            var job=new Job(species,logs,roots,t.hasUUID("Owner")?t.getUUID("Owner"):null,t.getInt("LogCost"),t.getInt("PlantCost"),ItemStack.of(t.getCompound("Tool")),ItemStack.of(t.getCompound("Saplings")));
            job.cut=t.getInt("Cut"); job.planted=t.getInt("Planted");
            if(job.cut<0||job.cut>logs.size()||job.planted<0||job.planted>roots.size()||job.logCost<1||job.logCost>10000||job.plantCost<1||job.plantCost>10000) throw new IllegalArgumentException("Invalid forestry progress"); job.remainingCost(); return job;
        }
    }
    public void cancel() {
        if(job!=null) { recovery.retain(job.tool); recovery.retain(job.saplings); job=null; }
        scan=null; faulted=false;
    }
    public CompoundTag save() {
        var t=new CompoundTag(); var sites=new ListTag(); for(var point:points) { var row=new CompoundTag(); row.putLong("Pos",point.pos().asLong()); row.putString("Species",point.species().name()); sites.add(row); }
        t.put("Points",sites); t.put("Recovery",recovery.save()); if(job!=null) t.put("Job",job.save()); t.putInt("NextPoint",nextPoint); t.putLong("RetryAt",retryAt); t.putBoolean("Faulted",faulted); return t;
    }
    public void load(CompoundTag t) {
        points.clear(); scan=null; job=null; actor=null; recovery.load(t.getList("Recovery",Tag.TAG_COMPOUND)); faulted=t.getBoolean("Faulted"); nextPoint=t.getInt("NextPoint"); retryAt=t.getLong("RetryAt");
        for(var raw:t.getList("Points",Tag.TAG_COMPOUND)) try { var row=(CompoundTag)raw; if(points.size()<16) points.add(new Point(BlockPos.of(row.getLong("Pos")),TreeSpecies.valueOf(row.getString("Species")))); } catch(IllegalArgumentException invalid) { faulted=true; }
        if(t.contains("Job")) try { job=Job.load(t.getCompound("Job")); } catch(RuntimeException invalid) { var old=t.getCompound("Job"); recovery.retain(ItemStack.of(old.getCompound("Tool"))); recovery.retain(ItemStack.of(old.getCompound("Saplings"))); faulted=true; }
    }
}
