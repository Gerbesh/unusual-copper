package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.storage.ItemKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import java.util.*;

public final class BrewerLogic implements MachineLogic {
    public static int costEach(PotionPlans.Plan plan){return ServerConfig.brewerCost()+Math.max(0,plan.steps().size()-2)*ServerConfig.brewerExtra();}
    public static PotionPlans.Step next(CopperMachineBlockEntity m){var s=m.brewer;if(s.job!=null)return s.job.plan.steps().isEmpty()?null:s.job.plan.steps().get(s.job.stage);var plan=PotionPlans.find(s.potion,s.vessel);return plan==null||plan.steps().isEmpty()?null:plan.steps().get(0);}
    public static boolean ingredient(CopperMachineBlockEntity m,ItemStack stack){var next=next(m);return next!=null&&stack.is(next.ingredient().getItem());}
    public static int request(CopperMachineBlockEntity m,int slot){
        var s=m.brewer;if(s.faulted||s.requestedBatch==0&&s.job==null||!s.recovery.empty())return 0;
        if(slot==0)return s.job==null?Math.max(0,s.requestedBatch-m.inventory.getStackInSlot(0).getCount()):0;
        return next(m)==null?0:Math.max(0,1-m.inventory.getStackInSlot(1).getCount());
    }
    @Override public void tick(CopperMachineBlockEntity m,ServerLevel level){if(level.getGameTime()>=m.brewer.nextWork)BrewerScheduler.request(level,m);}
    public static int demand(CopperMachineBlockEntity m,ServerLevel level,PotionPlans.Plan plan){
        var s=m.brewer;if(!s.maintain)return Math.min(3,s.pending);
        var endpoint=m.boundInventory()==null||m.getBlockPos().distSqr(m.boundInventory())>32*32?null:m.boundEndpoint(level);
        if(endpoint==null||!endpoint.isAvailable()){m.status(MachineStatus.NO_STORAGE);return 0;}
        var key=ItemKey.of(plan.output());var count=endpoint.count(key);if(!count.complete()){m.status(MachineStatus.STOCK_UNKNOWN);return 0;}
        long reserved=BrewerScheduler.reserved(level,m,endpoint.identity(),key);if(reserved==Long.MAX_VALUE){m.status(MachineStatus.STOCK_UNKNOWN);return 0;}
        int missing=(int)Math.max(0,Math.min(3,(long)s.pending-count.amount()-reserved));if(missing==0)m.status(MachineStatus.STOCK_REACHED);return missing;
    }
    public static void process(CopperMachineBlockEntity m,ServerLevel level){
        if(m.isRemoved()||level.getBlockEntity(m.getBlockPos())!=m||!m.energyEnabled())return;var s=m.brewer;
        if(!ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner())){m.status(MachineStatus.PROTECTED);return;}
        if(level.getGameTime()<s.nextWork)return;s.nextWork=level.getGameTime()+ServerConfig.brewerInterval();
        s.recovery.drain(m);s.requestedBatch=0;
        if(!s.recovery.empty()||m.boundCargoCount()>0){m.status(MachineStatus.OUTPUT_FULL);return;}
        if(s.faulted){m.status(MachineStatus.BREW_FAULT);return;}
        var plan=s.job==null?PotionPlans.find(s.potion,s.vessel):s.job.plan;if(plan==null){m.status(MachineStatus.UNSUPPORTED);return;}
        if(s.job==null){
            if(!m.inventory.getStackInSlot(2).isEmpty()||!m.inventory.getStackInSlot(3).isEmpty()){m.status(MachineStatus.OUTPUT_FULL);return;}
            int batch=demand(m,level,plan);s.requestedBatch=batch;if(batch==0){if(!s.maintain)m.status(MachineStatus.IDLE);return;}
            var quote=new BrewerState.Job(plan,batch,costEach(plan),m.owner());
            if(m.energy.stored()<quote.price()){m.status(MachineStatus.NO_ENERGY);return;}
            if(!m.inventory.getStackInSlot(0).is(Items.GLASS_BOTTLE)||m.inventory.getStackInSlot(0).getCount()<batch){m.status(MachineStatus.NEED_BOTTLES);return;}
            if(!plan.steps().isEmpty()&&!ingredient(m,m.inventory.getStackInSlot(1))){m.status(MachineStatus.NEED_REAGENT);return;}
            s.job=quote;
        }
        var job=s.job;m.progress=job.stage;m.duration=job.plan.stages();m.operationCost=job.price();if(!Objects.equals(job.owner,m.owner())){m.status(MachineStatus.PROTECTED);return;}
        var step=job.plan.steps().isEmpty()?null:job.plan.steps().get(job.stage);var reagent=m.inventory.getStackInSlot(1).copyWithCount(1);
        if(step!=null&&!reagent.is(step.ingredient().getItem())){m.status(MachineStatus.NEED_REAGENT);return;}
        int price=job.price();if(m.energy.stored()<price){m.status(MachineStatus.NO_ENERGY);return;}
        var input=job.stage==0?PotionPlans.water():job.current.copy();ItemStack output,remainder;
        try{output=step==null?PotionPlans.water():BrewingRecipeRegistry.getOutput(input.copy(),reagent.copy());remainder=step!=null&&reagent.hasCraftingRemainingItem()?reagent.getCraftingRemainingItem():ItemStack.EMPTY;}
        catch(RuntimeException brokenRecipe){s.faulted=true;m.status(MachineStatus.BREW_FAULT);m.setChanged();io.github.gerbesh.unusualcopper.UnusualCopper.LOGGER.error("Brewing recipe provider failed before commit",brokenRecipe);return;}
        var expected=step==null?job.plan.output():step.output();
        if(output.isEmpty()||output.getCount()!=1||!ItemKey.of(expected).matches(output)){m.status(MachineStatus.RECIPE_CHANGED);return;}
        // Recipe providers run before the commit. Recheck physical inputs after their callbacks.
        if(m.isRemoved()||level.getBlockEntity(m.getBlockPos())!=m||!ProtectionPolicy.allowed(level,m.getBlockPos(),m.owner())||!Objects.equals(job.owner,m.owner())||m.energy.stored()<price||step!=null&&(!ItemKey.of(reagent).matches(m.inventory.getStackInSlot(1))||m.inventory.getStackInSlot(1).isEmpty())){m.status(MachineStatus.RECIPE_CHANGED);return;}
        if(job.stage==0&&(!m.inventory.getStackInSlot(0).is(Items.GLASS_BOTTLE)||m.inventory.getStackInSlot(0).getCount()<job.batch)){m.status(MachineStatus.NEED_BOTTLES);return;}
        if(job.stage==0)m.inventory.extractItem(0,job.batch,false);if(step!=null)m.inventory.extractItem(1,1,false);
        m.energy.spend(price);job.current=output.copyWithCount(1);job.stage++;s.recovery.retain(remainder);m.progress=job.stage;m.duration=job.plan.stages();m.operationCost=price;m.grown+=job.stage==job.plan.stages()?job.batch:0;
        if(job.stage==job.plan.stages()){
            for(int i=0;i<job.batch;i++)s.recovery.retain(job.current.copyWithCount(1));if(!s.maintain)s.pending=Math.max(0,s.pending-job.batch);s.job=null;m.progress=m.duration=m.operationCost=0;
        }
        m.status(MachineStatus.WORKING);m.setChanged();
    }
    public static void cancel(CopperMachineBlockEntity m){m.brewer.cancel();m.cancelOperation();m.setChanged();}
    public BrewerLogic(){}
}
