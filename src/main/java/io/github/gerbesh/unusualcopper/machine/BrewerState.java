package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.gameplay.PotionPlans;
import io.github.gerbesh.unusualcopper.storage.OwnedItems;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class BrewerState {
    public ResourceLocation potion=new ResourceLocation("minecraft","fire_resistance");
    public int vessel,pending=16,priority,requestedBatch;
    public boolean maintain,faulted;
    public long nextWork;
    public final OwnedItems recovery=new OwnedItems();
    public Job job;
    public static final class Job {
        public final PotionPlans.Plan plan; public final int batch,costEach; public final UUID owner;
        public int stage; public ItemStack current=ItemStack.EMPTY;
        public Job(PotionPlans.Plan plan,int batch,int costEach,UUID owner) {this.plan=plan;this.batch=batch;this.costEach=costEach;this.owner=owner;}
        public int price() { return (int)((long)batch*costEach*(stage+1)/plan.stages()-(long)batch*costEach*stage/plan.stages()); }
        CompoundTag save() {
            var t=new CompoundTag(); t.putString("Potion",plan.potion().toString()); t.putInt("Vessel",plan.vessel()); t.put("Output",plan.output().save(new CompoundTag()));
            var steps=new ListTag(); for(var step:plan.steps()) {var row=new CompoundTag();row.put("Ingredient",step.ingredient().save(new CompoundTag()));row.put("Output",step.output().save(new CompoundTag()));steps.add(row);} t.put("Steps",steps);
            t.putInt("Batch",batch);t.putInt("CostEach",costEach);t.putInt("Stage",stage);t.put("Current",current.save(new CompoundTag()));if(owner!=null)t.putUUID("Owner",owner);return t;
        }
        static Job load(CompoundTag t) {
            int batch=t.getInt("Batch"),cost=t.getInt("CostEach"),stage=t.getInt("Stage"),vessel=t.getInt("Vessel");
            var output=ItemStack.of(t.getCompound("Output"));var steps=new ArrayList<PotionPlans.Step>();
            for(var raw:t.getList("Steps",Tag.TAG_COMPOUND)) {var row=(CompoundTag)raw;var ingredient=ItemStack.of(row.getCompound("Ingredient"));var result=ItemStack.of(row.getCompound("Output"));if(ingredient.isEmpty()||PotionPlans.vessel(result)<0)throw new IllegalArgumentException("Invalid brewing step");steps.add(new PotionPlans.Step(ingredient,result));}
            if(batch<1||batch>3||cost<1||cost>1000000||steps.size()>PotionPlans.MAX_STEPS||stage<0||stage>=Math.max(1,steps.size())||PotionPlans.vessel(output)!=vessel)throw new IllegalArgumentException("Invalid brewing job");
            var job=new Job(new PotionPlans.Plan(new ResourceLocation(t.getString("Potion")),vessel,output,steps),batch,cost,t.hasUUID("Owner")?t.getUUID("Owner"):null);job.stage=stage;job.current=ItemStack.of(t.getCompound("Current"));
            if(stage>0 && (job.current.isEmpty()||!net.minecraft.world.item.ItemStack.isSameItemSameTags(job.current,steps.get(stage-1).output())))throw new IllegalArgumentException("Invalid intermediate potion");return job;
        }
    }
    public void cancel() {if(job!=null&&!job.current.isEmpty())for(int i=0;i<job.batch;i++)recovery.retain(job.current.copyWithCount(1));job=null;requestedBatch=0;pending=0;maintain=false;faulted=false;}
    public CompoundTag save() {
        var t=new CompoundTag();t.putString("Potion",potion.toString());t.putInt("Vessel",vessel);t.putInt("Pending",pending);t.putInt("Priority",priority);t.putBoolean("Maintain",maintain);t.putBoolean("Faulted",faulted);t.putLong("NextWork",nextWork);t.put("Recovery",recovery.save());if(job!=null)t.put("Job",job.save());return t;
    }
    public void load(CompoundTag t) {
        job=null;requestedBatch=0;faulted=t.getBoolean("Faulted");recovery.load(t.getList("Recovery",Tag.TAG_COMPOUND));nextWork=Math.max(0,t.getLong("NextWork"));
        try {potion=new ResourceLocation(t.getString("Potion"));}catch(IllegalArgumentException bad){faulted=true;}
        vessel=Math.floorMod(t.getInt("Vessel"),3);pending=Math.max(0,Math.min(4096,t.getInt("Pending")));priority=Math.max(0,Math.min(9,t.getInt("Priority")));maintain=t.getBoolean("Maintain");
        if(t.contains("Job"))try {job=Job.load(t.getCompound("Job"));}catch(RuntimeException invalid){var old=t.getCompound("Job");var current=ItemStack.of(old.getCompound("Current"));for(int i=0;i<Math.max(0,Math.min(3,old.getInt("Batch")));i++)recovery.retain(current.copyWithCount(1));faulted=true;}
    }
}
