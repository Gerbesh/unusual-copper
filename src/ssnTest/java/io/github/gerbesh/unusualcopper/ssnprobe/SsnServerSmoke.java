package io.github.gerbesh.unusualcopper.ssnprobe;

import com.lothrazar.storagenetwork.api.*;
import com.lothrazar.storagenetwork.block.main.TileMain;
import com.lothrazar.storagenetwork.block.cable.link.TileCableLink;
import com.lothrazar.storagenetwork.capability.CapabilityConnectableLink;
import com.lothrazar.storagenetwork.registry.*;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.compat.ssn.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.util.*;

/** Real native SSN blocks/capabilities. Separate probe mod for dev and ordinary reobfuscated servers. */
@Mod("unusualcopper_ssn_probe")
public final class SsnServerSmoke {
    private static final BlockPos MAIN=new BlockPos(256,70,256),BRIDGE=MAIN.west(),SECOND=MAIN.east().north();
    private static final UUID OWNER=UUID.fromString("ab614215-90cb-4e16-b58f-d33cf54b5332");
    private static ServerLevel level;private static TileMain master;private static StorageEndpoint endpoint,second;
    private static long began;private static int phase;private static ProbeInventory large;
    private static long phaseAt;private static ChestBlockEntity firstHalf,secondHalf;
    private static CopperMachineBlockEntity brewerA,brewerB;
    private static long stockBegan;
    private static final List<BarrelBlockEntity> barrels=new ArrayList<>();private static final List<TileCableLink> links=new ArrayList<>();
    private static final Set<Long> forced=new HashSet<>();
    private static final ItemKey EXACT=ItemKey.of(named(1,"SSN exact"));
    public SsnServerSmoke(){ProbeInventory.register(FMLJavaModLoadingContext.get().getModEventBus());MinecraftForge.EVENT_BUS.register(SsnServerSmoke.class);}
    private static ItemStack named(int count,String name){var stack=new ItemStack(Items.DIAMOND,count);stack.setHoverName(Component.literal(name));return stack;}
    private static void check(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
    private static BlockPos barrelPos(int index){return MAIN.east(index+1).south(2);}
    private static void replace(BlockPos pos,Block block){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);}
    private static CapabilityConnectableLink capability(int index){return (CapabilityConnectableLink)links.get(index).getCapability(StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY,null).orElseThrow(()->new IllegalStateException("Native storage cap missing"));}
    private static void refresh(){master.getNetwork().doRefresh(new DimPos(level,MAIN));}
    private static void clearStores(){for(int i=0;i<19;i++){var be=level.getBlockEntity(barrelPos(i));if(be instanceof BarrelBlockEntity barrel){barrel.clearContent();barrel.setChanged();}else if(be instanceof ProbeInventory inventory)for(int slot=0;slot<512;slot++)inventory.inventory.setStackInSlot(slot,ItemStack.EMPTY);}}
    private static int amount(BarrelBlockEntity barrel,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<barrel.getContainerSize();i++)if(barrel.getItem(i).is(item))n+=barrel.getItem(i).getCount();return n;}
    private static CopperMachineBlockEntity brewer(BlockPos pos,BlockPos bridge){
        replace(pos,ModContent.MACHINES.get(MachineKind.AETHERIC_BREWER).get());var machine=(CopperMachineBlockEntity)level.getBlockEntity(pos);machine.setOwner(OWNER);machine.energy.add(100000,false);machine.brewer.maintain=true;machine.brewer.pending=16;
        ServerPlayer actor=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(OWNER,"SSNBridgeOwner"));actor.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);check(machine.bind(bridge,Direction.UP,actor),"Brewer could not bind a real SSN bridge");return machine;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void started(ServerStartedEvent event){
        if(!Boolean.getBoolean("unusualcopper.ssnSmoke"))return;level=event.getServer().overworld();began=level.getGameTime();
        for(int x=15;x<=17;x++)for(int z=15;z<=16;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);forced.add(net.minecraft.world.level.ChunkPos.asLong(x,z));}
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
        NormalCheckpoint.restore(level,MAIN,BRIDGE,OWNER);
        replace(MAIN,SsnRegistry.Blocks.MASTER.get());master=(TileMain)level.getBlockEntity(MAIN);
        for(int i=0;i<19;i++){
            var cable=MAIN.east(i+1);replace(cable,SsnRegistry.Blocks.KABEL.get());replace(cable.south(),SsnRegistry.Blocks.STORAGE_KABEL.get());var link=(TileCableLink)level.getBlockEntity(cable.south());link.setDirection(Direction.SOUTH);links.add(link);
            replace(barrelPos(i),Blocks.BARREL);barrels.add((BarrelBlockEntity)level.getBlockEntity(barrelPos(i)));
        }
        replace(BRIDGE,ModContent.SSN_BRIDGE.get());level.setBlock(BRIDGE,level.getBlockState(BRIDGE).setValue(SsnBridgeBlock.FACING,Direction.EAST),3);((SsnBridgeBlockEntity)level.getBlockEntity(BRIDGE)).owner(OWNER);
        replace(SECOND,ModContent.SSN_BRIDGE.get());level.setBlock(SECOND,level.getBlockState(SECOND).setValue(SsnBridgeBlock.FACING,Direction.SOUTH),3);((SsnBridgeBlockEntity)level.getBlockEntity(SECOND)).owner(OWNER);
        barrels.get(0).setItem(0,named(3,"SSN exact"));barrels.get(18).setItem(0,named(5,"SSN exact"));barrels.get(1).setItem(0,named(11,"Other NBT"));refresh();
        check(SsnAccess.supported(),"Installed SSN version/API was not recognized");check(master.getNetwork() instanceof SsnNetworkRevision&&((SsnNetworkRevision)master.getNetwork()).unusualcopper$networkRevision()>0,"Native network refresh Mixin did not run");
        endpoint=StorageEndpoints.at(level,BRIDGE,Direction.UP,OWNER);second=StorageEndpoints.at(level,SECOND,Direction.DOWN,OWNER);
        check(endpoint.isAvailable()&&second.isAvailable(),"Direct master or cable-attached bridge unavailable");check(endpoint.identity().sameInventory(second.identity())&&endpoint.identity().pos().equals(MAIN),"Two bridges do not share the actual native master identity");
        var initial=endpoint.count(EXACT);check(!initial.complete(),"Nineteen storage ports should require more than one eight-port count page");
        UnusualCopper.LOGGER.info("CuE SSN BRIDGE CONNECTED: actual 1.13.2 master/cable ports, native refresh revision and shared identity; progressive={}",ModList.get().isLoaded("ssn_progressive"));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(level==null||event.level!=level||event.phase!=TickEvent.Phase.END)return;if(level.getGameTime()-began>2000)throw new IllegalStateException("Native SSN probe timed out, phase="+phase+(brewerA==null?"":" brewerA="+brewerA.status()+" brewerB="+brewerB.status()));
        if(phase==0){
            var count=endpoint.count(EXACT);if(!count.complete())return;check(count.amount()==8,"Native nineteen-port exact NBT count differs: "+count);var other=endpoint.count(ItemKey.of(named(1,"Other NBT")));if(!other.complete())return;check(other.amount()==11,"Different native NBT stock was merged");
            refresh();var refreshed=endpoint.count(EXACT);check(refreshed.complete()&&refreshed.amount()==8,"Bookkeeping-only native refresh invalidated unchanged physical item counts");
            check(endpoint.simulateExtract(EXACT,2)==2&&barrels.get(0).getItem(0).getCount()==3,"SSN simulation charged physical inventory");var actual=endpoint.extract(EXACT,2);check(actual.size()==1&&EXACT.matches(actual.get(0))&&actual.get(0).getCount()==2&&barrels.get(0).getItem(0).getCount()==1,"Exact native withdrawal failed");
            check(endpoint.insert(EXACT.stack(2),true).isEmpty()&&barrels.get(0).getItem(0).getCount()==1,"Native insert simulation committed");check(endpoint.insert(EXACT.stack(2),false).isEmpty()&&barrels.get(0).getItem(0).getCount()==3,"Physical native return failed");
            var foreign=StorageEndpoints.at(level,BRIDGE,Direction.UP,UUID.randomUUID());check(!foreign.isAvailable()&&!foreign.count(EXACT).complete()&&foreign.extract(EXACT,1).isEmpty(),"Foreign bridge actor accessed a network");
            barrels.get(0).setItem(1,new ItemStack(Items.AMETHYST_SHARD));barrels.get(1).setItem(1,new ItemStack(Items.AMETHYST_SHARD));capability(1).setPriority(-5);((BlockEntity)links.get(1)).setChanged();refresh();
            var amethyst=ItemKey.of(new ItemStack(Items.AMETHYST_SHARD));check(endpoint.extract(amethyst,1).stream().mapToInt(ItemStack::getCount).sum()==1&&barrels.get(1).getItem(1).isEmpty()&&barrels.get(0).getItem(1).getCount()==1,"Native link priority was ignored");
            capability(1).setPriority(0);refresh();
            UnusualCopper.LOGGER.info("CuE SSN BRIDGE EXACT PASS: nineteen physical storages, bounded count, exact NBT, free simulations, actual extraction/return, ownership and native priority");
            replace(barrelPos(18),ProbeInventory.BLOCK.get());large=(ProbeInventory)level.getBlockEntity(barrelPos(18));large.inventory.setStackInSlot(511,new ItemStack(Items.EMERALD,17));large.reads=0;refresh();phase=1;
        }else if(phase==1){
            var emerald=ItemKey.of(new ItemStack(Items.EMERALD));int before=large.reads;var count=endpoint.count(emerald);check(large.reads-before<=256,"One native large-handler census page exceeded 256 reads");if(!count.complete())return;
            check(count.amount()==17&&large.reads>=512,"Native sided 512-slot storage was not counted exactly");
            var found=endpoint.simulateExtract(emerald,3);if(found==0)return;check(found==3,"Native far-slot simulation mismatch");var actual=endpoint.extract(emerald,3);check(actual.stream().mapToInt(ItemStack::getCount).sum()==3&&large.inventory.getStackInSlot(511).getCount()==14,"Native sided far-slot extraction failed");
            large.inventory.setStackInSlot(511,new ItemStack(Items.EMERALD,9));check(!endpoint.count(emerald).complete(),"Native mutation reused a completed network count");phase=2;
        }else if(phase==2){
            var count=endpoint.count(ItemKey.of(new ItemStack(Items.EMERALD)));if(!count.complete())return;check(count.amount()==9,"Native changed large storage did not rebuild");
            UnusualCopper.LOGGER.info("CuE SSN BRIDGE SIDED INDEX PASS: actual native link to a north-only 512-slot handler, last-slot read/extraction and dirty count invalidation");
            for(int i=0;i<18;i++)for(int slot=0;slot<27;slot++)barrels.get(i).setItem(slot,new ItemStack(Items.STONE,64));phase=3;
        }else if(phase==3){
            var stack=new ItemStack(Items.REDSTONE,5);if(!endpoint.insert(stack,true).isEmpty())return;check(large.inventory.getStackInSlot(0).isEmpty(),"Far-port simulation charged the native inventory");check(endpoint.insert(stack,false).isEmpty()&&large.inventory.getStackInSlot(0).is(Items.REDSTONE)&&large.inventory.getStackInSlot(0).getCount()==5,"Insertion did not reach the nineteenth native port after the full first pages");
            clearStores();barrels.get(0).setItem(0,named(4,"Filtered"));phase=12;
            UnusualCopper.LOGGER.info("CuE SSN BRIDGE FAR INSERT PASS: full first eighteen barrels, free pagination/simulation, five real items inserted into the nineteenth sided storage");
        }else if(phase==12){
            var key=ItemKey.of(named(1,"Filtered"));var cached=endpoint.count(key);if(!cached.complete())return;check(cached.amount()==4,"Allowlist fixture did not cache the original physical stock");var filtered=capability(0);filtered.setFilter(0,new ItemStack(Items.GOLD_INGOT));filtered.getFilter().setIsAllowlist(true);((BlockEntity)links.get(0)).setChanged();var masked=endpoint.count(key);check(!masked.complete()||masked.amount()==0,"A changed native filter reused its previous completed count");phase=4;
        }else if(phase==4){
            var blocked=ItemKey.of(named(1,"Filtered"));var count=endpoint.count(blocked);if(!count.complete())return;check(count.amount()==0,"Native allowlist leaked a filtered stack into network stock");check(endpoint.extract(blocked,1).isEmpty()&&barrels.get(0).getItem(0).getCount()==4,"Native allowlist allowed physical extraction");
            check(endpoint.insert(new ItemStack(Items.GOLD_INGOT),false).isEmpty()&&amount(barrels.get(0),Items.GOLD_INGOT)==1,"Native allowlist rejected an allowed insertion");
            var tag=capability(0).serializeNBT();tag.putString("way","OUT");capability(0).deserializeNBT(tag);((BlockEntity)links.get(0)).setChanged();check(endpoint.insert(new ItemStack(Items.GOLD_INGOT,2),false).isEmpty()&&amount(barrels.get(0),Items.GOLD_INGOT)==1&&amount(barrels.get(1),Items.GOLD_INGOT)==2,"Read-only native port accepted an insertion or swallowed its remainder");
            var gold=ItemKey.of(new ItemStack(Items.GOLD_INGOT));check(endpoint.extract(gold,1).stream().mapToInt(ItemStack::getCount).sum()==1&&amount(barrels.get(0),Items.GOLD_INGOT)==0,"Read-only native port did not allow the physical request direction");
            tag=capability(0).serializeNBT();tag.putString("way","BOTH");capability(0).deserializeNBT(tag);capability(0).getFilter().setIsAllowlist(false);capability(0).setFilter(0,ItemStack.EMPTY);((BlockEntity)links.get(0)).setChanged();clearStores();
            var left=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT);if(ChestBlock.getConnectedDirection(left)!=Direction.EAST)left=left.setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT);
            var right=left.setValue(ChestBlock.TYPE,left.getValue(ChestBlock.TYPE)==net.minecraft.world.level.block.state.properties.ChestType.LEFT?net.minecraft.world.level.block.state.properties.ChestType.RIGHT:net.minecraft.world.level.block.state.properties.ChestType.LEFT);
            level.setBlock(barrelPos(0),Blocks.AIR.defaultBlockState(),3);level.setBlock(barrelPos(1),Blocks.AIR.defaultBlockState(),3);
            // Place both matching states before neighbor shape updates can split the unfinished pair.
            level.setBlock(barrelPos(0),left,18);level.setBlock(barrelPos(1),right,18);firstHalf=(ChestBlockEntity)level.getBlockEntity(barrelPos(0));secondHalf=(ChestBlockEntity)level.getBlockEntity(barrelPos(1));firstHalf.setBlockState(left);secondHalf.setBlockState(right);
            var combined=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,level.getBlockState(barrelPos(0)),level,barrelPos(0),true);check(combined!=null&&combined.getContainerSize()==54,"Fixture did not create a real 54-slot vanilla double chest: "+level.getBlockState(barrelPos(0))+" / "+level.getBlockState(barrelPos(1)));
            firstHalf.setItem(0,new ItemStack(Items.QUARTZ,7));secondHalf.setItem(0,new ItemStack(Items.QUARTZ,5));firstHalf.setChanged();secondHalf.setChanged();refresh();phase=5;
            UnusualCopper.LOGGER.info("CuE SSN BRIDGE FILTER PASS: actual native allowlist, exact filtered stock, OUT extraction and insertion routed only to a writable native port");
        }else if(phase==5){
            var count=endpoint.count(ItemKey.of(new ItemStack(Items.QUARTZ)));if(!count.complete())return;check(count.amount()==12,"Two native links counted a double chest more than once: "+count);secondHalf.setItem(0,new ItemStack(Items.QUARTZ,2));secondHalf.setChanged();check(endpoint.count(ItemKey.of(new ItemStack(Items.QUARTZ))).amount()!=12||!endpoint.count(ItemKey.of(new ItemStack(Items.QUARTZ))).complete(),"Partner-half mutation did not invalidate the native cached count");phase=6;
        }else if(phase==6){
            var count=endpoint.count(ItemKey.of(new ItemStack(Items.QUARTZ)));if(!count.complete())return;check(count.amount()==9,"Native double-chest partner mutation was lost");for(int i=0;i<2;i++){replace(barrelPos(i),Blocks.BARREL);barrels.set(i,(BarrelBlockEntity)level.getBlockEntity(barrelPos(i)));}refresh();clearStores();
            var bridge=(SsnBridgeBlockEntity)level.getBlockEntity(BRIDGE);bridge.redstoneMode=1;check(!endpoint.isAvailable()&&endpoint.identity().sameInventory(second.identity()),"Redstone pause lost the native shared stock identity");bridge.redstoneMode=0;
            barrels.get(0).setItem(0,new ItemStack(Items.GLASS_BOTTLE,16));barrels.get(0).setItem(1,new ItemStack(Items.NETHER_WART,6));barrels.get(0).setItem(2,new ItemStack(Items.MAGMA_CREAM,6));
            brewerA=brewer(MAIN.west(4),BRIDGE);brewerB=brewer(MAIN.west(5).south(2),SECOND);stockBegan=level.getGameTime();phase=7;
            UnusualCopper.LOGGER.info("CuE SSN BRIDGE DOUBLE CHEST PASS: duplicate native links count twelve once; partner mutation rebuilds nine; paused bridge preserves shared master identity");
        }else if(phase==7){
            var potion=net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION),net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE);var count=endpoint.count(ItemKey.of(potion));
            if(level.getGameTime()%100==0)UnusualCopper.LOGGER.info("CuE SSN STOCK PROGRESS: real={} counted={} A={}/{}/bottles{}/req{}/job{} B={}/{}/bottles{}/req{}/job{} sourceBottles={}",master.getNetwork().getAmount(new com.lothrazar.storagenetwork.capability.handler.ItemStackMatcher(potion,false,true)),count,brewerA.status(),brewerA.energy.stored(),brewerA.inventory.getStackInSlot(0).getCount(),brewerA.brewer.requestedBatch,brewerA.brewer.job==null?-1:brewerA.brewer.job.stage,brewerB.status(),brewerB.energy.stored(),brewerB.inventory.getStackInSlot(0).getCount(),brewerB.brewer.requestedBatch,brewerB.brewer.job==null?-1:brewerB.brewer.job.stage,amount(barrels.get(0),Items.GLASS_BOTTLE));
            if(!count.complete())return;check(count.amount()<=16,"Two native bridge brewers overproduced shared potion stock: "+count.amount());
            if(count.amount()!=16||brewerA.brewer.job!=null||brewerB.brewer.job!=null||brewerA.brewer.recovery.count()!=0||brewerB.brewer.recovery.count()!=0||brewerA.boundCargoCount()!=0||brewerB.boundCargoCount()!=0)return;phaseAt=level.getGameTime();phase=8;
        }else if(phase==8){
            if(level.getGameTime()-phaseAt<45)return;var potion=net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION),net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE);var count=endpoint.count(ItemKey.of(potion));if(!count.complete())return;
            check(count.amount()==16&&brewerA.energy.stored()+brewerB.energy.stored()==120000&&amount(barrels.get(0),Items.GLASS_BOTTLE)==0&&amount(barrels.get(0),Items.NETHER_WART)==0&&amount(barrels.get(0),Items.MAGMA_CREAM)==0,"Native shared stock, exact eighty-thousand fee or six real reagent batches mismatch");
            UnusualCopper.LOGGER.info("CuE SSN BRIDGE STOCK PASS: two brewers on distinct bridges, nineteen native storages, exactly sixteen physical fire-resistance potions, six batches of real reagents, 80000 CuE and stable shared stock");
            UnusualCopper.LOGGER.info("CuE SSN STOCK TIMING: {} world ticks including the final stability wait",level.getGameTime()-stockBegan);
            brewerA.redstoneMode=1;brewerB.redstoneMode=1;
            if(ModList.get().isLoaded("openpartiesandclaims")){clearStores();ClaimsProbe.start(level,MAIN,BRIDGE,OWNER,endpoint);phase=10;}
            else nextProfile();
        }else if(phase==9){
            if(ProgressiveProbe.tick()){UnusualCopper.LOGGER.info("CuE SSN PROGRESSIVE PROFILE PASS: bridge suite and actual Progressive crafting completed together");finish();}
        }else if(phase==10){
            if(ClaimsProbe.tick())nextProfile();
        }
    }
    private static void nextProfile(){if(ModList.get().isLoaded("ssn_progressive")){clearStores();ProgressiveProbe.start(level,master,MAIN,BRIDGE,OWNER,endpoint);phase=9;}else{UnusualCopper.LOGGER.info("CuE SSN VANILLA PROFILE PASS: real SSN bridge suite completed without Progressive");finish();}}
    private static void finish(){clearStores();NormalCheckpoint.write(level,MAIN,BRIDGE,OWNER);level.getServer().halt(false);phase=11;}
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){if(level!=null)for(long key:forced){var chunk=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}forced.clear();}
}
