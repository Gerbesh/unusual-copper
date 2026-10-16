package io.github.gerbesh.unusualcopper.probe;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.itempipe.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Reproducible, sustained workload in an owned ordinary Forge server, never a live world. */
public final class PerformanceProbe {
    private static final BlockPos BASE=new BlockPos(256,72,256);
    private static final UUID OWNER=UUID.fromString("830368d6-75c1-4c58-96e7-99b66349f8e9");
    private static final String[] PHASES={"baseline-before","idle-50","active-50","idle-100","active-100","churn-100","baseline-after"};
    private static final int WARM=Integer.getInteger("unusualcopper.perfWarm",200),SAMPLES=Integer.getInteger("unusualcopper.perfSamples",600);
    private static final List<Site> SITES=new ArrayList<>();
    private static final Set<BlockPos> ENERGY=new LinkedHashSet<>(),ITEMS=new LinkedHashSet<>();
    private static final List<BlockPos> SUPPLY=new ArrayList<>(),SINKS=new ArrayList<>();
    private static final Map<String,Long> PRODUCTS=new TreeMap<>();
    private static ServerLevel level;
    private static BufferedWriter csv;
    private static int phase=-1,phaseTick;
    private static long bodyStart,harnessNanos,phaseWall,wallPrevious;
    private static boolean running,churn;
    private static long[] witness;
    private static final class Site {
        final int id;final MachineKind kind;final BlockPos pos,storage,root;
        final List<Mob> mobs=new ArrayList<>();CopperMachineBlockEntity machine;
        Site(int id,MachineKind kind){this.id=id;this.kind=kind;pos=BASE.offset(2+(id%10)*6,0,2+(id/10)*6);storage=pos.east(2);root=pos.south(2);}
    }
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        if(!Boolean.getBoolean("unusualcopper.performanceProbe"))return;
        try{
            level=e.getServer().overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,e.getServer());
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,e.getServer());
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,e.getServer());
            level.setDayTime(6000);level.setWeatherParameters(0,0,false,false);
            for(int x=-1;x<=4;x++)for(int z=-1;z<=4;z++){
                var p=BASE.offset(x*16,0,z*16);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);level.getChunkAt(p);
            }
            for(var entity:level.getEntitiesOfClass(Entity.class,new net.minecraft.world.phys.AABB(BASE.offset(-5,-2,-5),BASE.offset(65,18,65))))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            for(int x=-3;x<63;x++)for(int z=-3;z<63;z++){
                level.setBlock(BASE.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=-1;y<13;y++)level.setBlock(BASE.offset(x,y,z),y==-1?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            var kinds=new ArrayList<MachineKind>();
            add(kinds,MachineKind.SOLAR_INFUSER,10);add(kinds,MachineKind.ESSENCE_SPLITTER,10);add(kinds,MachineKind.RESONANCE_SMELTER,18);
            add(kinds,MachineKind.COPPER_CELL,2);add(kinds,MachineKind.COPPER_CELL_II,3);add(kinds,MachineKind.COPPER_CELL_III,3);
            add(kinds,MachineKind.ENERGY_DISTRIBUTOR,2);add(kinds,MachineKind.ENERGY_MONITOR,2);
            for(var kind:new MachineKind[]{MachineKind.AURA_CULTIVATOR,MachineKind.MAGNETIC_COLLECTOR,MachineKind.GROWTH_SHEPHERD,MachineKind.MOB_REAPER,MachineKind.ARBORIST})add(kinds,kind,8);
            kinds.addAll(List.of(MachineKind.COPPER_SENTINEL,MachineKind.AETHERIC_BREWER,MachineKind.ENCHANTMENT_RESONATOR,MachineKind.COPPER_SENTINEL,MachineKind.AETHERIC_BREWER,MachineKind.ENCHANTMENT_RESONATOR,MachineKind.COPPER_SENTINEL,MachineKind.AETHERIC_BREWER,MachineKind.AETHERIC_BREWER,MachineKind.COPPER_SENTINEL));
            check(kinds.size()==100,"Incorrect benchmark mix");
            for(int i=0;i<100;i++){var site=new Site(i,kinds.get(i));SITES.add(site);
                if(site.kind.hasInventory())level.setBlock(site.storage,Blocks.BARREL.defaultBlockState(),3);
                if(site.kind==MachineKind.AURA_CULTIVATOR){
                    for(int x=-1;x<=1;x++)for(int z=1;z<=3;z++){
                        level.setBlock(site.pos.offset(x,-1,z),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),3);
                        level.setBlock(site.pos.offset(x,0,z),Blocks.WHEAT.defaultBlockState(),3);
                    }
                    level.setBlock(site.pos.offset(2,-1,2),Blocks.WATER.defaultBlockState(),3);
                }
                if(site.kind==MachineKind.ARBORIST){level.setBlock(site.root.below(),Blocks.DIRT.defaultBlockState(),3);growTree(site);}
            }
            for(int row=0;row<10;row++)for(int x=0;x<60;x++)ENERGY.add(BASE.offset(x,-1,2+row*6));
            for(int z=0;z<60;z++)ENERGY.add(BASE.offset(0,-1,z));
            for(int row=0;row<5;row++)for(int x=0;x<60;x++)ITEMS.add(BASE.offset(x,0,1+row*6));
            for(int z=0;z<=25;z++)ITEMS.add(BASE.offset(-1,0,z));
            for(int row=0;row<5;row++){
                var source=BASE.offset(-2,0,1+row*6);var sink=BASE.offset(60,0,1+row*6);
                level.setBlock(source,Blocks.BARREL.defaultBlockState(),3);level.setBlock(sink,Blocks.BARREL.defaultBlockState(),3);SUPPLY.add(source);SINKS.add(sink);
            }
            check(ENERGY.size()==650&&ITEMS.size()==326,"Unexpected node count");
            csv=Files.newBufferedWriter(Path.of("performance-ticks.csv"));
            csv.write("phase,tick,body_ns,harness_ns,wall_ns,churn,heap_bytes");
            for(var name:PerformanceRecorder.NAMES)csv.write(","+name+"_ns,"+name+"_calls");csv.newLine();
            running=true;nextPhase();
            LogUtils.getLogger().info("CuE PERFORMANCE SETUP: 100 mixed machines, 650 energy + 326 item nodes, 36 forced chunks, warm={} samples={} per phase",WARM,SAMPLES);
        }catch(Exception failure){throw new IllegalStateException("Benchmark setup failed",failure);}
    }
    private static void add(List<MachineKind> kinds,MachineKind kind,int count){for(int i=0;i<count;i++)kinds.add(kind);}
    private static void side(CopperMachineBlockEntity m,Direction side,SideMode wanted){for(int i=0;i<5&&m.sideMode(side)!=wanted;i++)m.cycleSide(side);check(m.sideMode(side)==wanted,"Benchmark port rejected");}
    private static boolean active(){return phase==2||phase==4||phase==5;}
    private static void install(int count){
        for(var pos:ENERGY)level.setBlock(pos,ModContent.CRYSTALLINE_ENERGY_PIPE.get().defaultBlockState(),3);
        for(var pos:ITEMS)level.setBlock(pos,ModContent.ITEM_PIPE.get().defaultBlockState(),3);
        for(int row=0;row<5;row++){
            var p=BASE.offset(-1,0,1+row*6);var pipe=(ItemPipeBlockEntity)level.getBlockEntity(p);pipe.owner(OWNER);pipe.rule(Direction.WEST).mode=PipeMode.EXTRACT;pipe.changed();
            for(int col=0;col<10;col++){
                var body=(ItemPipeBlockEntity)level.getBlockEntity(BASE.offset(4+col*6,0,1+row*6));body.rule(Direction.SOUTH).mode=PipeMode.DISABLED;body.changed();
            }
        }
        for(var site:SITES){
            if(count==50&&site.id%2!=0||site.machine!=null)continue;
            level.setBlock(site.pos,ModContent.MACHINES.get(site.kind).get().defaultBlockState(),3);
            site.machine=(CopperMachineBlockEntity)level.getBlockEntity(site.pos);var m=site.machine;m.setOwner(OWNER);m.energy.add(site.kind.capacity,false);m.redstoneMode=1;
            if(site.kind.produces()||site.kind.consumes())side(m,Direction.DOWN,site.kind.produces()&&!site.kind.isCell()&&site.kind!=MachineKind.ENERGY_DISTRIBUTOR?SideMode.ENERGY_OUTPUT:SideMode.ENERGY_INPUT);
            if(site.kind.acceptsInputs())side(m,Direction.NORTH,SideMode.ITEM_INPUT);
            if(site.kind.hasInventory()){
                var player=FakePlayerFactory.get(level,new GameProfile(OWNER,"CuEPerformance"));player.setPos(site.pos.getX()+.5,site.pos.getY()+.5,site.pos.getZ()+.5);
                check(m.bind(site.storage,Direction.UP,player),"Benchmark storage binding failed: "+site.kind);
            }
            if(site.kind.usesArea()){
                var player=FakePlayerFactory.get(level,new GameProfile(OWNER,"CuEPerformance"));
                check(m.setWorkArea(WorkArea.between(site.pos.offset(-1,0,1),site.pos.offset(1,1,3)),player),"Benchmark work area rejected");
            }
            if(site.kind==MachineKind.SOLAR_INFUSER)m.inventory.setStackInSlot(1,new ItemStack(ModContent.SOLAR_LENS.get()));
            if(site.kind==MachineKind.ARBORIST){m.arborist.points.add(new ArboristState.Point(site.root,TreeSpecies.OAK));m.inventory.setStackInSlot(0,new ItemStack(Items.OAK_SAPLING,64));m.inventory.setStackInSlot(1,new ItemStack(Items.DIAMOND_AXE));}
            if(site.kind==MachineKind.MOB_REAPER)m.reaper.settings(new ReaperRule(true,true,Set.of(),Set.of(),Set.of(MobCategory.CREATURE)),2,1);
            if(site.kind==MachineKind.AETHERIC_BREWER){m.brewer.pending=16;m.brewer.maintain=true;}
        }
    }
    private static void remove(){
        for(var site:SITES){
            if(site.machine!=null){level.setBlock(site.pos,Blocks.AIR.defaultBlockState(),3);site.machine=null;}
            for(var mob:site.mobs)if(!mob.isRemoved())mob.discard();
            site.mobs.clear();
        }
        for(var p:ENERGY)level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        for(var p:ITEMS)level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        for(var drop:level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(BASE.offset(-5,-2,-5),BASE.offset(65,18,65))))drop.discard();
    }
    private static void nextPhase()throws IOException{
        PerformanceRecorder.recording=false;phase++;phaseTick=0;phaseWall=wallPrevious=System.nanoTime();
        if(phase==PHASES.length){csv.close();running=false;LogUtils.getLogger().info("CuE PERFORMANCE PASS: seven complete phases; products={}",PRODUCTS);level.getServer().halt(false);return;}
        if(phase==0||phase==6)remove();else install(phase<3?50:100);
        for(var site:SITES)if(site.machine!=null)site.machine.redstoneMode=active()?0:1;
        witness=snapshot();PerformanceRecorder.take();
        LogUtils.getLogger().info("CuE PERFORMANCE PHASE START: {}",PHASES[phase]);
    }
    private static long[] snapshot(){
        long[] r=new long[MachineKind.values().length*2];for(var s:SITES)if(s.machine!=null){r[s.kind.ordinal()]+=s.machine.energy.consumed();r[s.kind.ordinal()+MachineKind.values().length]+=s.machine.energy.produced();}return r;
    }
    private static void phaseDone()throws IOException{
        var now=snapshot();var delta=new TreeMap<String,Long>();for(var k:MachineKind.values()){long value=now[k.ordinal()]-witness[k.ordinal()];if(value!=0)delta.put(k.id,value);}
        // A short diagnostic run may be shorter than a machine's scheduled cycle.
        if(active()&&WARM+SAMPLES>=200){
            // The forestry retry/planting cycle can straddle a single churn window.
            // Both steady active phases still require actual paid forestry work.
            for(var kind:new MachineKind[]{MachineKind.RESONANCE_SMELTER,MachineKind.AURA_CULTIVATOR,MachineKind.MAGNETIC_COLLECTOR,MachineKind.GROWTH_SHEPHERD,MachineKind.MOB_REAPER,MachineKind.ARBORIST,MachineKind.AETHERIC_BREWER,MachineKind.COPPER_SENTINEL}){
                if(phase==5&&kind==MachineKind.ARBORIST)continue;
                check(delta.getOrDefault(kind.id,0L)>0,"Inactive benchmark family "+kind+" during "+PHASES[phase]);
            }
        }
        csv.flush();LogUtils.getLogger().info("CuE PERFORMANCE PHASE PASS: {}; elapsedMs={}; physicalCuEConsumed={}; products={}",PHASES[phase],(System.nanoTime()-phaseWall)/1_000_000,delta,PRODUCTS);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void feed(TickEvent.ServerTickEvent e){
        if(!running||e.phase!=TickEvent.Phase.START)return;long begin=System.nanoTime();churn=false;
        try{
            if(phaseTick%20==0)maintain();
            if(phase==5){
                int part=phaseTick%40;if(part==0||part==5){churn=true;level.setBlock(BASE.offset(0,-1,30),part==0?Blocks.AIR.defaultBlockState():ModContent.CRYSTALLINE_ENERGY_PIPE.get().defaultBlockState(),3);}
                int itemPart=phaseTick%80;if(itemPart==0||itemPart==5){churn=true;level.setBlock(BASE.offset(-1,0,13),itemPart==0?Blocks.AIR.defaultBlockState():ModContent.ITEM_PIPE.get().defaultBlockState(),3);}
            }
            PerformanceRecorder.recording=phaseTick>=WARM;PerformanceRecorder.take();
        }catch(Exception failure){throw new IllegalStateException("Benchmark maintenance failed",failure);}
        harnessNanos=System.nanoTime()-begin;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void startTick(TickEvent.ServerTickEvent e){if(running&&e.phase==TickEvent.Phase.START)bodyStart=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void endTick(TickEvent.ServerTickEvent e){
        if(!running||e.phase!=TickEvent.Phase.END)return;long end=System.nanoTime();var totals=PerformanceRecorder.take();
        try{
            if(phaseTick>=WARM){
                var runtime=Runtime.getRuntime();csv.write(PHASES[phase]+","+(phaseTick-WARM)+","+(end-bodyStart)+","+harnessNanos+","+(end-wallPrevious)+","+(churn?1:0)+","+(runtime.totalMemory()-runtime.freeMemory()));
                for(int i=0;i<PerformanceRecorder.NAMES.length;i++)csv.write(","+totals[i]+","+totals[i+PerformanceRecorder.NAMES.length]);csv.newLine();
            }
            wallPrevious=end;phaseTick++;
            if(phaseTick==WARM+SAMPLES){phaseDone();nextPhase();}
        }catch(Exception failure){throw new IllegalStateException("Benchmark evidence failed",failure);}
    }
    private static void growTree(Site site){
        // Deterministic renewable test-only tree. Random bonemeal can be blocked by
        // leftover leaves, silently turning the next phase into an idle benchmark.
        var state=level.getBlockState(site.root);
        if(!state.is(Blocks.AIR)&&!state.is(Blocks.OAK_SAPLING))return;
        for(int y=0;y<6;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            level.setBlock(site.root.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        for(int y=0;y<4;y++)level.setBlock(site.root.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)
            level.setBlock(site.root.offset(x,3,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
    }
    private static Mob mob(Site site,EntityType<? extends Mob> type){
        var mob=type.create(level);mob.setPos(site.pos.getX()+.5,site.pos.getY()+.1,site.pos.getZ()+2.5);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        if(mob instanceof Cow cow)cow.setAge(site.kind==MachineKind.GROWTH_SHEPHERD?-24000:0);
        if(type==EntityType.ZOMBIE)mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));
        check(level.addFreshEntity(mob),"Benchmark entity rejected");site.mobs.add(mob);return mob;
    }
    private static void fill(BlockPos pos,int slot,Item item,int count){var barrel=(BarrelBlockEntity)level.getBlockEntity(pos);var old=barrel.getItem(slot);if(!old.is(item)||old.getCount()<count)barrel.setItem(slot,new ItemStack(item,count));}
    private static void maintain(){
        for(var source:SUPPLY)fill(source,0,Items.COPPER_INGOT,64);
        for(var sink:SINKS){var barrel=(BarrelBlockEntity)level.getBlockEntity(sink);for(int i=0;i<barrel.getContainerSize();i++)if(!barrel.getItem(i).isEmpty()){PRODUCTS.merge("transport_items",(long)barrel.getItem(i).getCount(),Long::sum);barrel.setItem(i,ItemStack.EMPTY);}}
        for(var site:SITES){
            // Baseline and not-yet-installed slots must not generate synthetic load.
            if(site.machine==null)continue;
            if(site.kind.hasInventory()){
                var barrel=(BarrelBlockEntity)level.getBlockEntity(site.storage);
                for(int i=0;i<barrel.getContainerSize();i++){var stack=barrel.getItem(i);boolean product=switch(site.kind){
                    case SOLAR_INFUSER->stack.is(ModContent.ESSENCE.get());case RESONANCE_SMELTER->stack.is(Items.IRON_INGOT);case MAGNETIC_COLLECTOR->stack.is(Items.COPPER_INGOT);
                    case MOB_REAPER->stack.is(Items.BEEF)||stack.is(Items.LEATHER);case ARBORIST->stack.is(Items.OAK_LOG)||stack.is(Items.STICK)||stack.is(Items.APPLE);
                    case AETHERIC_BREWER->stack.is(Items.POTION)&&PotionUtils.getPotion(stack)!=Potions.WATER;default->false;
                };if(product&&!stack.isEmpty()){PRODUCTS.merge(site.kind.id,(long)stack.getCount(),Long::sum);barrel.setItem(i,ItemStack.EMPTY);}}
                switch(site.kind){
                    case SOLAR_INFUSER->fill(site.storage,0,Items.COPPER_INGOT,64);
                    case ESSENCE_SPLITTER->fill(site.storage,0,ModContent.ESSENCE.get(),64);
                    case RESONANCE_SMELTER->fill(site.storage,0,Items.RAW_IRON,64);
                    case ARBORIST->{fill(site.storage,0,Items.OAK_SAPLING,64);fill(site.storage,1,Items.DIAMOND_AXE,1);}
                    case AETHERIC_BREWER->{fill(site.storage,0,Items.GLASS_BOTTLE,64);fill(site.storage,1,Items.NETHER_WART,64);fill(site.storage,2,Items.MAGMA_CREAM,64);}
                    default->{}
                }
            }
            if(site.kind==MachineKind.AURA_CULTIVATOR)for(int x=-1;x<=1;x++)for(int z=1;z<=3;z++){
                var p=site.pos.offset(x,0,z);var state=level.getBlockState(p);if(state.is(Blocks.WHEAT)&&state.getValue(CropBlock.AGE)==7)level.setBlock(p,Blocks.WHEAT.defaultBlockState(),3);
            }
            if(site.kind==MachineKind.ARBORIST&&(site.machine==null||site.machine.arborist.job==null))growTree(site);
            int wanted=site.kind==MachineKind.MOB_REAPER?4:site.kind==MachineKind.GROWTH_SHEPHERD?3:site.kind==MachineKind.COPPER_SENTINEL?1:0;
            site.mobs.removeIf(entity->!entity.isAlive()||entity.isRemoved());while(site.mobs.size()<wanted)mob(site,site.kind==MachineKind.COPPER_SENTINEL?EntityType.ZOMBIE:EntityType.COW);
            if(site.kind==MachineKind.GROWTH_SHEPHERD&&phaseTick%200==0)for(var m:site.mobs)((Cow)m).setAge(-24000);
            if(site.kind==MachineKind.MAGNETIC_COLLECTOR){var box=new net.minecraft.world.phys.AABB(site.pos.offset(-1,0,1),site.pos.offset(2,2,4));if(level.getEntitiesOfClass(ItemEntity.class,box).isEmpty()){
                var item=new ItemEntity(level,site.pos.getX()+.5,site.pos.getY()+.2,site.pos.getZ()+2.5,new ItemStack(Items.COPPER_INGOT,16));item.setNoGravity(true);item.setDeltaMovement(0,0,0);item.setNoPickUpDelay();level.addFreshEntity(item);
            }}
        }
    }
}
