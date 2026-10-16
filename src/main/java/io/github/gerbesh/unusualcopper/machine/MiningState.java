package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.storage.OwnedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class MiningState {
    public MiningFootprint area;public int experience;public UUID visualIdentity;public long visualTick,visualRevision;public int column,y=Integer.MAX_VALUE,scanned,mined;public boolean finished,enabled=true,faulted;
    public long nextWork;public int holdingQuarters;public UUID lastOwner;public final Set<ResourceLocation> disabled=new HashSet<>();
    public boolean processing;public ItemStack lastTool=ItemStack.EMPTY;public long lastHolding=Long.MIN_VALUE;
    public final Map<ResourceLocation,Integer> discovered=new LinkedHashMap<>();
    public final ArrayDeque<BlockPos> targets=new ArrayDeque<>();public final List<Job> jobs=new ArrayList<>();
    public final OwnedItems recovery=new OwnedItems();
    public net.minecraftforge.common.util.FakePlayer actor;
    public static final class Job {
        private static final java.util.concurrent.atomic.AtomicLong IDS=new java.util.concurrent.atomic.AtomicLong();
        public long visualId=IDS.incrementAndGet();
        public final BlockPos pos;public final BlockState state;public final ItemStack tool;public final int ticks,cost;public int progress;public long advancedAt=Long.MIN_VALUE;public List<ItemStack> drops;
        public Job(BlockPos pos,BlockState state,ItemStack tool,int ticks,int cost){this.pos=pos.immutable();this.state=state;this.tool=tool.copy();this.ticks=MiningTiming.FLIGHT_TICKS;this.progress=MiningTiming.preparation(ticks);this.cost=cost;}
    }
    public void rescan(){column=scanned=0;y=Integer.MAX_VALUE;finished=false;targets.clear();jobs.clear();discovered.clear();nextWork=0;}
    public CompoundTag save(){var t=new CompoundTag();if(area!=null)t.putIntArray("Area",new int[]{area.minX(),area.minZ(),area.maxX(),area.maxZ()});t.putInt("HoldingQuarters",holdingQuarters);t.putBoolean("Enabled",enabled);t.putBoolean("Fault",faulted);t.putInt("Mined",mined);t.putInt("Experience",experience);t.put("Recovery",recovery.save());var list=new ListTag();disabled.stream().sorted().limit(256).forEach(id->list.add(StringTag.valueOf(id.toString())));t.put("Disabled",list);return t;}
    public void load(CompoundTag t){area=null;var a=t.getIntArray("Area");if(a.length==4)try{area=new MiningFootprint(a[0],a[1],a[2],a[3]);}catch(IllegalArgumentException ignored){}holdingQuarters=Math.floorMod(t.getInt("HoldingQuarters"),4);enabled=!t.contains("Enabled")||t.getBoolean("Enabled");faulted=t.getBoolean("Fault");mined=Math.max(0,t.getInt("Mined"));experience=Math.max(0,t.getInt("Experience"));disabled.clear();for(var raw:t.getList("Disabled",Tag.TAG_STRING)){var id=ResourceLocation.tryParse(raw.getAsString());if(id!=null&&disabled.size()<256)disabled.add(id);}recovery.load(t.getList("Recovery",Tag.TAG_COMPOUND));rescan();actor=null;}
    public void writeVisual(CompoundTag t,BlockPos origin){t.putBoolean("MiningEnabled",enabled);if(area!=null)t.putIntArray("MiningArea",new int[]{area.minX(),area.minZ(),area.maxX(),area.maxZ()});var list=new ListTag();for(var job:jobs){if(job.progress<0)continue;var row=new CompoundTag();row.putLong("Pos",job.pos.asLong());row.putLong("Flight",job.visualId);row.putInt("StateId",net.minecraft.world.level.block.Block.getId(job.state));row.putInt("Ticks",job.ticks);row.putInt("Progress",job.progress);list.add(row);}t.put("MiningBeams",list);}
}
