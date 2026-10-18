package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.network.MiningNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class MiningLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m,ServerLevel level){if(level.getGameTime()>=m.mining.nextWork)MiningScheduler.request(level,m);}
    public static boolean geometry(ServerLevel level,BlockPos origin,MiningFootprint area){
        for(int x:new int[]{area.minX(),area.maxX()})for(int z:new int[]{area.minZ(),area.maxZ()}){
            if(!MiningFootprint.reachable(origin.getX(),origin.getZ(),x,z)||!level.getWorldBorder().isWithinBounds(new BlockPos(x,origin.getY(),z)))return false;
        }return origin.getY()>level.getMinBuildHeight()&&origin.getY()<level.getMaxBuildHeight();
    }
    private static boolean access(CopperMachineBlockEntity m,ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)&&!level.isOutsideBuildHeight(pos)&&ProtectionPolicy.allowed(level,pos,m.owner());}
    private static boolean sameTool(ItemStack a,ItemStack b){var first=a.copy();var second=b.copy();if(first.isDamageableItem())first.setDamageValue(0);if(second.isDamageableItem())second.setDamageValue(0);return ItemStack.matches(first,second);}
    private static void pause(CopperMachineBlockEntity m,MachineStatus status){m.status(status);for(var j:m.mining.jobs)j.advancedAt=Long.MIN_VALUE;m.mining.nextWork=m.getLevel().getGameTime()+10;}
    private static boolean pendingOutput(CopperMachineBlockEntity m){return !m.mining.recovery.empty()||m.boundCargoCount()>0||!m.inventory.getStackInSlot(2).isEmpty()||!m.inventory.getStackInSlot(3).isEmpty();}
    public static void process(CopperMachineBlockEntity m,ServerLevel level,int budget,long deadline){
        if(m.mining==null||m.mining.processing)return;m.mining.processing=true;
        try{processInternal(m,level,budget,deadline);}finally{m.mining.processing=false;}
    }
    private static void processInternal(CopperMachineBlockEntity m,ServerLevel level,int budget,long deadline){
        var s=m.mining;if(s==null||m.isRemoved())return;
        if(!Objects.equals(s.lastOwner,m.owner())){s.lastOwner=m.owner();s.jobs.clear();s.targets.clear();s.actor=null;}
        if(!s.enabled||!m.energyEnabled()){s.jobs.clear();m.status(MachineStatus.REDSTONE_PAUSED);return;}
        if(s.faulted){pause(m,MachineStatus.MINING_FAULT);return;}
        if(s.area==null||!geometry(level,m.getBlockPos(),s.area)){pause(m,MachineStatus.NO_AREA);return;}
        if(!access(m,level,m.getBlockPos())){s.jobs.clear();pause(m,MachineStatus.PROTECTED);return;}
        var chunks = MiningChunkLoading.blockedStatus(level,m);
        if(chunks!=null){pause(m,chunks);return;}
        s.recovery.drain(m,4);
        if(m.boundInventory()==null){pause(m,MachineStatus.NO_STORAGE);return;}
        var output=m.boundEndpoint(level);if(output==null||!output.isAvailable()){pause(m,MachineStatus.UNLOADED);return;}
        if(output.space()!=io.github.gerbesh.unusualcopper.storage.StorageEndpoint.Space.AVAILABLE){pause(m,MachineStatus.OUTPUT_FULL);return;}
        var tool=m.inventory.getStackInSlot(0);if(!MiningRules.durable(tool)){s.jobs.clear();pause(m,MachineStatus.NO_TOOL);return;}
        if(!s.lastTool.isEmpty()&&!sameTool(s.lastTool,tool))s.rescan();s.lastTool=tool.copy();
        if(level.getGameTime()%20==0&&s.lastHolding!=level.getGameTime()&&(!s.finished||!s.jobs.isEmpty()||!s.targets.isEmpty())){
            var fee=MiningFootprint.holding(s.area.columns(),s.holdingQuarters);
            if(fee.cost()>0&&!m.energy.spend(fee.cost())){pause(m,MachineStatus.NO_ENERGY);return;}
            s.holdingQuarters=fee.quarters();s.lastHolding=level.getGameTime();m.setChanged();
        }
        if(s.y==Integer.MAX_VALUE)s.y=m.getBlockPos().getY()-1;
        int top=m.getBlockPos().getY()-1,bottom=level.getMinBuildHeight();
        // Advance every admitted flight before any optional scanning or expensive mining hooks.
        // The five-entry bound makes this bookkeeping constant; commits still obey the deadline.
        long now=level.getGameTime();
        for(var job:s.jobs){job.progress=MiningTiming.advance(job.progress,job.advancedAt==Long.MIN_VALUE?1:now-job.advancedAt);job.advancedAt=now;}
        boolean committed=false;
        for(var it=s.jobs.iterator();it.hasNext()&&System.nanoTime()<deadline;){var job=it.next();
            if(!sameTool(job.tool,m.inventory.getStackInSlot(0))||!MiningRules.durable(m.inventory.getStackInSlot(0))){it.remove();continue;}
            if(!access(m,level,job.pos)){pause(m,MachineStatus.UNLOADED);return;}
            if(!level.getBlockState(job.pos).equals(job.state)||!MiningRules.ore(job.state)){it.remove();continue;}
            if(job.progress<job.ticks||committed||pendingOutput(m))continue;
            m.operationCost=job.cost;var result=mine(m,level,job);
            if(result==MachineStatus.WORKING){it.remove();committed=true;}
            else if(result==MachineStatus.OUTPUT_FULL||result==MachineStatus.NO_ENERGY){pause(m,result);return;}
            else{it.remove();pause(m,result);return;}
        }
        // Existing loot has one owner and drains through the normal output path. It blocks new
        // admissions/commits, but does not stop other already admitted flights halfway to the sphere.
        boolean backlog=pendingOutput(m);
        int reserved=s.jobs.stream().mapToInt(j->j.cost).sum();
        while(!backlog&&s.jobs.size()<5&&!s.targets.isEmpty()&&System.nanoTime()<deadline){
            var pos=s.targets.peekFirst();if(!level.hasChunkAt(pos))break;if(!access(m,level,pos)){pause(m,MachineStatus.PROTECTED);return;}
            var state=level.getBlockState(pos);var id=ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if(!MiningRules.ore(state)||s.disabled.contains(id)||!tool.isCorrectToolForDrops(state)){s.targets.removeFirst();continue;}
            float hardness=state.getDestroySpeed(level,pos);if(!Float.isFinite(hardness)||hardness<0){s.targets.removeFirst();continue;}int cost=MiningFootprint.price(ServerConfig.miningCost(),s.area.columns(),top-pos.getY(),hardness);
            m.operationCost=cost;if((long)reserved+cost>m.energy.stored()){if(s.jobs.isEmpty())pause(m,MachineStatus.NO_ENERGY);break;}
            float speed=tool.getDestroySpeed(state);int efficiency=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY,tool);if(speed>1&&efficiency>0)speed+=efficiency*efficiency+1;
            s.jobs.add(new MiningState.Job(pos,state,tool,MiningFootprint.duration(hardness,speed)+s.jobs.size(),cost));reserved+=cost;s.targets.removeFirst();
        }
        while(!backlog&&!s.finished&&s.targets.size()<256&&budget-->0&&System.nanoTime()<deadline){
            var pos=new BlockPos(s.area.x(s.column),s.y,s.area.z(s.column));
            // A future column waiting for its chunk must not pause flights in already loaded chunks.
            if(!level.hasChunkAt(pos))break;
            if(!ProtectionPolicy.allowed(level,pos,m.owner())){s.jobs.clear();pause(m,MachineStatus.PROTECTED);return;}
            var state=level.getBlockState(pos);
            if(MiningRules.ore(state)&&state.getDestroySpeed(level,pos)>=0){var id=ForgeRegistries.BLOCKS.getKey(state.getBlock());if(s.discovered.containsKey(id)||s.discovered.size()<256)s.discovered.merge(id,1,Integer::sum);if(!s.disabled.contains(id))s.targets.addLast(pos.immutable());}
            s.scanned++;if(--s.y<bottom){s.y=top;if(++s.column>=s.area.columns()){s.finished=true;break;}}
        }
        if(backlog&&s.jobs.stream().allMatch(job->job.progress>=job.ticks))m.status(MachineStatus.OUTPUT_FULL);
        else if(committed||!s.jobs.isEmpty())m.status(MachineStatus.WORKING);
        else if(!s.targets.isEmpty()&&m.energy.stored()<m.operationCost)m.status(MachineStatus.NO_ENERGY);
        else if(!s.finished&&!level.hasChunkAt(new BlockPos(s.area.x(s.column),s.y,s.area.z(s.column))))m.status(MachineStatus.UNLOADED);
        else m.status(s.finished&&s.targets.isEmpty()?MachineStatus.MINING_EXHAUSTED:MachineStatus.SCANNING);
        m.setChanged();
    }
    private static MachineStatus mine(CopperMachineBlockEntity m,ServerLevel level,MiningState.Job job){
        var s=m.mining;var tool=m.inventory.getStackInSlot(0);if(!MiningRules.durable(tool))return MachineStatus.NO_TOOL;
        if(m.energy.stored()<job.cost)return MachineStatus.NO_ENERGY;
        if(s.actor==null){s.actor=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(m.owner()==null?new UUID(0,3):m.owner(),"[CuEMiner]"));s.actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);}
        var actor=s.actor;actor.getAbilities().instabuild=false;actor.setPos(job.pos.getX()+.5,job.pos.getY()+.5,job.pos.getZ()+.5);actor.setItemInHand(InteractionHand.MAIN_HAND,tool.copy());
        try{
            if(!access(m,level,job.pos)||!level.mayInteract(actor,job.pos)||!job.state.canHarvestBlock(level,job.pos,actor))return MachineStatus.PROTECTED;
            int xp=ForgeHooks.onBlockBreakEvent(level,net.minecraft.world.level.GameType.SURVIVAL,actor,job.pos);if(xp<0)return MachineStatus.BREAK_DENIED;
            if(!access(m,level,job.pos)||m.isRemoved()||level.getBlockEntity(m.getBlockPos())!=m||!level.getBlockState(job.pos).equals(job.state)||!ItemStack.matches(tool,m.inventory.getStackInSlot(0)))return MachineStatus.TREE_CHANGED;
            if(!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)||level.restoringBlockSnapshots)return MachineStatus.BREAK_DENIED;
            var before=tool.copy();var owner=m.owner();var binding=m.boundInventory();var side=m.boundSide();var area=s.area;
            if(job.drops==null)job.drops=Block.getDrops(job.state,level,job.pos,null,actor,before).stream().map(ItemStack::copy).toList();var drops=job.drops;int stacks=0;
            var endpoint=m.boundEndpoint(level);if(endpoint==null||!endpoint.isAvailable())return MachineStatus.NO_STORAGE;
            for(var stack:drops){if(stack.isEmpty())continue;stacks+=(int)Math.min(65,((long)stack.getCount()+stack.getMaxStackSize()-1)/stack.getMaxStackSize());if(stacks>64)return MachineStatus.UNSUPPORTED;if(!endpoint.insert(stack.copy(),true).isEmpty())return MachineStatus.OUTPUT_FULL;}
            if(m.isRemoved()||level.getBlockEntity(m.getBlockPos())!=m||!s.enabled||!m.energyEnabled()||!s.jobs.contains(job)||s.area!=area||!Objects.equals(owner,m.owner())||!Objects.equals(binding,m.boundInventory())||side!=m.boundSide()||m.energy.stored()<job.cost||!access(m,level,job.pos)||!level.getBlockState(job.pos).equals(job.state)||!ItemStack.matches(before,m.inventory.getStackInSlot(0)))return MachineStatus.TREE_CHANGED;
            var replacement=MiningRules.replacement(level,job.pos,job.state);if(replacement.equals(job.state)||MiningRules.ore(replacement))return MachineStatus.UNSUPPORTED;boolean replaced=false;
            if(!m.energy.spend(job.cost))return MachineStatus.NO_ENERGY;
            try{replaced=level.setBlock(job.pos,replacement,2);}finally{
                if(level.getBlockState(job.pos).equals(replacement)){
                    // Every irreversible world replacement immediately acquires its loot and price, before tool/neighbor hooks.
                    for(var stack:drops)s.recovery.retain(stack);s.mined++;s.discovered.computeIfPresent(ForgeRegistries.BLOCKS.getKey(job.state.getBlock()),(id,n)->Math.max(0,n-1));s.experience=(int)Math.min(Integer.MAX_VALUE,(long)s.experience+Math.max(0,xp));m.setChanged();
                }else{m.energy.restore(Math.min(m.energy.capacity(),m.energy.stored()+job.cost));m.setChanged();}
            }
            if(!replaced)return MachineStatus.BREAK_DENIED;
            try{actor.getMainHandItem().mineBlock(level,job.state,job.pos,actor);}finally{m.inventory.setStackInSlot(0,actor.getMainHandItem().copy());m.setChanged();}
            level.updateNeighborsAt(job.pos,replacement.getBlock());
            MiningNetwork.extracted(level,m,job.pos,job.state,drops.isEmpty()?ItemStack.EMPTY:drops.get(0));
            s.recovery.drain(m,4);return MachineStatus.WORKING;
        }finally{actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
    }
}
