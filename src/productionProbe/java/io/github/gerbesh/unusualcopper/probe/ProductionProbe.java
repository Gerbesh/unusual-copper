package io.github.gerbesh.unusualcopper.probe;

import io.github.gerbesh.unusualcopper.gameplay.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Separate, reobfuscated test mod. Never packaged into the production artifact. */
@Mod("unusualcopper_probe")
public final class ProductionProbe {
    private static ServerLevel level; private static long started; private static boolean done;
    private static UUID cancelledVictim;
    private static io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity phaseMachine;
    private static Cow phaseVictim;
    private static long phaseStarted, phaseDeadline;
    private static final Set<Long> EXTRA_CHUNKS=new HashSet<>();
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) { if (event.getEntity().getUUID().equals(cancelledVictim)) event.setCanceled(true); }
    private static final BlockPos TARGET = new BlockPos(130, 70, 130);
    private static final class Checkpoint extends net.minecraft.world.level.saveddata.SavedData {
        UUID animal; int round, emergency;
        static Checkpoint load(net.minecraft.nbt.CompoundTag tag) { var value = new Checkpoint(); value.animal = tag.hasUUID("Animal") ? tag.getUUID("Animal") : null; value.round = tag.getInt("Round"); value.emergency=tag.getInt("Emergency"); return value; }
        @Override public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) { if (animal != null) tag.putUUID("Animal", animal); tag.putInt("Round",round); tag.putInt("Emergency",emergency); return tag; }
    }
    public ProductionProbe() { MinecraftForge.EVENT_BUS.register(ProductionProbe.class); }
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private static <T extends Mob> T spawn(EntityType<T> type, BlockPos pos) {
        var mob = type.create(level); mob.setPos(pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5); mob.setNoAi(true); mob.setNoGravity(true); mob.setInvulnerable(true); check(level.addFreshEntity(mob), "Probe mob spawn rejected"); return mob;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("unusualcopper.productionProbe")) return;
        level = event.getServer().overworld(); started = level.getGameTime(); level.setChunkForced(TARGET.getX() >> 4, TARGET.getZ() >> 4, true); level.getChunkAt(TARGET);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (level == null || event.level != level || event.phase != TickEvent.Phase.END) return;
        if(done) { phaseTick(); return; }
        if (level.getGameTime() - started > 400) throw new IllegalStateException("Production probe could not load entity region");
        if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(TARGET.getX() >> 4, TARGET.getZ() >> 4)) || !level.isPositionEntityTicking(TARGET)) return;
        // Epoch 2 runs multiple real ticks with animals enabled. The old single-tick fixture
        // disabled animals globally, so extending it could discard its persisted UUID witness.
        var checkpoint = level.getDataStorage().computeIfAbsent(Checkpoint::load, Checkpoint::new, "unusualcopper_production_population_probe_v2");
        if (checkpoint.animal != null && level.getEntity(checkpoint.animal) == null) return;
        done = true;
        var area = WorkArea.between(TARGET, TARGET.east(2));
        for (var mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(area.min(), area.max().offset(1,1,1)).inflate(2))) mob.discard();
        var cows = new ArrayList<Cow>(); for (int i = 0; i < 21; i++) cows.add(spawn(EntityType.COW, TARGET));
        Horse horse = spawn(EntityType.HORSE, TARGET.east()); Wolf wolf = spawn(EntityType.WOLF, TARGET.east(2));
        var rule = new ReaperRule(true, true, Set.of(), Set.of(), EnumSet.allOf(MobCategory.class));
        var ledger = MobPopulations.ledger(level, UUID.randomUUID(), TARGET.west(2), area, rule);
        check(ledger.step(16) <= 16 && !ledger.ready(), "Production cold scan did not respect its budget"); ledger.step(16);
        check(ledger.ready() && ledger.count() == 23, "Production population count differs: " + ledger.count());
        horse.setOwnerUUID(UUID.randomUUID()); check(ledger.count() == 22, "SRG horse owner hook failed"); horse.setOwnerUUID(null); check(ledger.count() == 23, "SRG horse owner removal failed");
        wolf.setOwnerUUID(UUID.randomUUID()); check(ledger.count() == 22, "SRG wolf data hook failed"); wolf.setOwnerUUID(null); check(ledger.count() == 23, "SRG wolf owner removal failed");
        var cow = cows.get(0); cow.setCustomName(Component.literal("Protected")); check(ledger.count() == 22, "SRG custom name hook failed"); cow.setCustomName(null);
        cow.setCustomName(Component.literal("Bulk protected")); var snapshot = cow.getEntityData().getNonDefaultValues(); cow.setCustomName(null); cow.getEntityData().assignValues(snapshot); check(ledger.count() == 22, "SRG bulk metadata hook failed"); cow.setCustomName(null);
        cow.setLeashedTo(cows.get(1), false); check(ledger.count() == 22, "SRG leash hook failed"); cow.dropLeash(false, false); check(ledger.count() == 23, "SRG leash removal failed");
        cow.setPos(TARGET.getX() + .5, TARGET.getY() + .1, TARGET.getZ() + 1.5); check(ledger.count() == 22, "SRG same-section position hook failed");
        cow.setPos(TARGET.getX() + .5, TARGET.getY() + .1, TARGET.getZ() + .5); cow.setAge(-5000); check(ledger.count() == 22, "SRG age hook failed"); cow.setAge(0); check(ledger.count() == 23, "SRG adulthood hook failed");
        cow.setHealth(0); check(ledger.count() == 22, "SRG health hook failed");
        cows.forEach(Mob::discard); horse.discard(); wolf.discard(); check(ledger.count() == 0, "SRG entity removal hook failed");
        var machinePos=TARGET.west(2);
        if (checkpoint.emergency>0) {
            var restored=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(machinePos);
            check(restored!=null && restored.reaper.lootCount()==checkpoint.emergency && restored.reaper.loot().get(0).getHoverName().getString().equals("Production emergency loot"),"Emergency loot lost count or NBT on real process restart");
        }
        level.setBlock(machinePos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TARGET).inflate(4))) item.discard();
        level.setBlock(machinePos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.MOB_REAPER).get().defaultBlockState(),3);
        var machine=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(machinePos); machine.energy.add(3000,false);
        var previousPlayer=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ProductionAttacker"));
        var cancelled=spawn(EntityType.COW,TARGET); cancelled.setInvulnerable(false); cancelled.setLastHurtByPlayer(previousPlayer); cancelledVictim=cancelled.getUUID();
        check(!ReaperLoot.kill(machine,cancelled,level,1500) && cancelled.isAlive() && cancelled.getHealth()==cancelled.getMaxHealth() && cancelled.getKillCredit()==previousPlayer && machine.energy.stored()==3000,"SRG cancellation failed health/credit/energy restore"); cancelledVictim=null; cancelled.discard();
        var victim=spawn(EntityType.COW,TARGET); victim.setInvulnerable(false); victim.setLastHurtByPlayer(previousPlayer);
        check(ReaperLoot.kill(machine,victim,level,1500) && ((ReaperVictim)victim).unusualcopper$deathCommitted() && victim.getKillCredit()==null && machine.energy.stored()==1500,"SRG real death or player credit suppression failed");
        check(!machine.inventory.getStackInSlot(2).isEmpty() && level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,new net.minecraft.world.phys.AABB(TARGET).inflate(4)).isEmpty(),"SRG real loot capture or zero XP failed"); victim.discard();
        var emergency=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,5); emergency.setHoverName(Component.literal("Production emergency loot")); machine.reaper.hold(emergency); ((net.minecraft.world.level.block.entity.BlockEntity)machine).setChanged(); checkpoint.emergency=5;
        var savedPos = TARGET.east(8);
        if (checkpoint.animal == null) checkpoint.animal = spawn(EntityType.COW, savedPos).getUUID();
        var savedArea = new WorkArea(savedPos,savedPos); var loaded = MobPopulations.ledger(level, UUID.randomUUID(), TARGET, savedArea, rule); loaded.step(16);
        check(loaded.ready() && loaded.count() == 1 && loaded.target().getUUID().equals(checkpoint.animal), "Original persisted cow is not indexed exactly once");
        checkpoint.round++; checkpoint.setDirty();
        LogUtils.getLogger().info("CuE PRODUCTION REAPER PASS: real paid death, no player credit/XP, accepted loot, cancelled death restores health/credit/energy, emergency loot checkpoint round={}",checkpoint.round);
        LogUtils.getLogger().info("CuE PRODUCTION POPULATION PASS: exact 23-mob count; bounded bootstrap; SRG position, name, age, health, wolf/horse ownership, leash and removal hooks; persisted round={}", checkpoint.round);
        verifyArborist();
        verifySentinel();
        verifyBrewer();
        verifyEnchanter();
        verifyInventoryChanges();
        EnergyTierProbe.start(level, TARGET.offset(0, 10, 5));
        var phasePos=TARGET.west(2).south(3); var phaseTarget=TARGET.south(3);
        for(var old:level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(phaseTarget).inflate(1))) old.discard();
        level.setBlock(phasePos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        level.setBlock(phasePos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.MOB_REAPER).get().defaultBlockState(),3);
        phaseMachine=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(phasePos);
        previousPlayer.setPos(phasePos.getX()+.5,phasePos.getY()+.5,phasePos.getZ()+.5); phaseMachine.setOwner(previousPlayer.getUUID());
        check(phaseMachine.setWorkArea(new WorkArea(phaseTarget,phaseTarget),previousPlayer),"Production windup area rejected");
        phaseMachine.reaper.settings(new ReaperRule(true,true,Set.of(new net.minecraft.resources.ResourceLocation("minecraft","cow")),Set.of(),Set.of()),0,0); phaseMachine.energy.add(1500,false);
        phaseVictim=spawn(EntityType.COW,phaseTarget); phaseVictim.setInvulnerable(false); phaseStarted=level.getGameTime();
    }
    private static void phaseTick() {
        EnergyTierProbe.tick();
        if(phaseMachine==null) return;
        long now=level.getGameTime(); check(now-phaseStarted<120,"Production windup ticker timed out");
        var pending=phaseMachine.reaper.windup;
        if(pending!=null && phaseDeadline==0) { phaseDeadline=pending.strikeAt(); check(phaseDeadline-now>=10,"Production preparation was shorter than twelve ticks"); }
        if(phaseDeadline>0 && now<phaseDeadline) check(phaseVictim.isAlive() && phaseMachine.energy.stored()==1500,"Production preparation damaged or charged early");
        if(phaseVictim.isAlive()) return;
        check(phaseDeadline>0 && now>=phaseDeadline && phaseMachine.energy.stored()==0 && !phaseMachine.inventory.getStackInSlot(2).isEmpty(),"Production prepared strike did not commit real paid loot: deadline="+phaseDeadline+", now="+now+", removed="+phaseVictim.isRemoved()+", hp="+phaseVictim.getHealth()+", energy="+phaseMachine.energy.stored());
        check(EnergyTierProbe.ready(),"Production energy tier probe did not finish");
        phaseVictim.discard(); phaseMachine.redstoneMode=1; phaseMachine=null;
        LogUtils.getLogger().info("CuE PRODUCTION WINDUP PASS: actual twelve-tick preparation, no early damage/fee, real paid strike and loot");
        level.setChunkForced(TARGET.getX()>>4,TARGET.getZ()>>4,false); level.getServer().halt(false);
    }
    private static void verifyInventoryChanges(){
        var pos=TARGET.above(12);level.setBlock(pos,net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(),3);
        var barrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(pos);
        var token=io.github.gerbesh.unusualcopper.storage.InventoryChanges.watch(level,barrel);long before=token.revision();
        barrel.setItem(0,new ItemStack(Items.DIAMOND,7));barrel.setChanged();check(token.revision()>before,"SRG vanilla inventory dirty notification did not invalidate the watched revision");
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(),3);
        var replacement=io.github.gerbesh.unusualcopper.storage.InventoryChanges.watch(level,level.getBlockEntity(pos));check(replacement!=token&&!token.valid(),"Replacement reused a previous inventory revision token");
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        LogUtils.getLogger().info("CuE PRODUCTION INVENTORY INDEX PASS: SRG vanilla dirty hook invalidates watched revisions; replacement invalidates old identity");
    }
    private static void verifyEnchanter(){
        var pos=TARGET.west(8).north(10);var owner=UUID.fromString("f78455cc-b7e7-488c-bf47-e47f38410be4");level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);EXTRA_CHUNKS.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
        net.minecraft.server.level.ServerPlayer player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"CuEResXP"));player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        var receipt=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("cue-enchanter-player.nbt").toFile();
        if(level.getBlockEntity(pos)instanceof io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity saved&&saved.kind==io.github.gerbesh.unusualcopper.machine.MachineKind.ENCHANTMENT_RESONATOR&&receipt.isFile()){
            try{player.load(net.minecraft.nbt.NbtIo.readCompressed(receipt));}catch(java.io.IOException error){throw new IllegalStateException("Cannot read fixture player XP checkpoint",error);}
            var item=saved.inventory.getStackInSlot(0);check(saved.owner().equals(owner)&&saved.energy.stored()==343750&&saved.inventory.getStackInSlot(1).isEmpty()&&Math.abs(XpWallet.points(player)-765)<.0001&&net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY,item)==5&&item.getDamageValue()==31&&item.getHoverName().getString().equals("Production keeper")&&net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,item)==1&&player.totalExperience==765&&item.getOrCreateTag().getString("EnchantmentWitness").equals("preserve"),"Saved enchanted item/XP/CuE did not survive the two-file normal checkpoint");
            LogUtils.getLogger().info("CuE PRODUCTION ENCHANTER DISK RESTORE PASS: machine region plus vanilla player NBT; exact enchanted item, original owner, 765 XP points, 343750 CuE and no lapis");
        }
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.ENCHANTMENT_RESONATOR).get().defaultBlockState(),3);
        var m=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(pos);m.setOwner(owner);m.energy.add(500000,false);m.inventory.setStackInSlot(1,new ItemStack(Items.LAPIS_LAZULI,64));var target=new ItemStack(Items.DIAMOND_PICKAXE);target.setDamageValue(31);target.setHoverName(Component.literal("Production keeper"));target.getOrCreateTag().putString("EnchantmentWitness","preserve");target.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,1);m.inventory.setStackInSlot(0,target);
        player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);player.experienceLevel=0;player.experienceProgress=0;player.totalExperience=0;player.giveExperiencePoints(1000);var menu=new io.github.gerbesh.unusualcopper.menu.EnchanterMenu(79,player.getInventory(),m);player.containerMenu=menu;
        check(menu.select(m.runtimeIdentity,new net.minecraft.resources.ResourceLocation("minecraft","efficiency"),player),"Production enchantment quote rejected");var quote=menu.quote();check(quote.offer().xp()==235&&quote.offer().cue()==156250&&m.energy.stored()==500000&&Math.abs(XpWallet.points(player)-1000)<.0001,"Production preview changed resources or price");check(menu.confirm(m.runtimeIdentity,quote.nonce(),player),"Production enchantment confirmation rejected");
        check(m.energy.stored()==343750&&m.inventory.getStackInSlot(1).isEmpty()&&Math.abs(XpWallet.points(player)-765)<.0001&&net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY,m.inventory.getStackInSlot(0))==5,"Production enchanting fee/result mismatch");check(!menu.confirm(m.runtimeIdentity,quote.nonce(),player)&&m.energy.stored()==343750&&Math.abs(XpWallet.points(player)-765)<.0001,"Production confirmation replay spent resources");
        try{net.minecraft.nbt.NbtIo.writeCompressed(player.saveWithoutId(new net.minecraft.nbt.CompoundTag()),receipt);}catch(java.io.IOException error){throw new IllegalStateException("Cannot save fixture player XP checkpoint",error);}((net.minecraft.world.level.block.entity.BlockEntity)m).setChanged();
        LogUtils.getLogger().info("CuE PRODUCTION ENCHANTER PASS: real quote and one-shot confirmation; Efficiency V, original NBT, 235 XP points, 156250 CuE and exactly 64 lapis; vanilla player NBT checkpoint written");
    }
    private static void brewerStep(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity m){m.brewer.nextWork=0;io.github.gerbesh.unusualcopper.machine.logic.BrewerLogic.process(m,level);}
    private static int brewerProducts(net.minecraft.world.level.block.entity.BarrelBlockEntity c){int n=0;for(int i=0;i<c.getContainerSize();i++){var stack=c.getItem(i);if(stack.is(Items.POTION)&&net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack)==net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE)n+=stack.getCount();}return n;}
    private static void verifyBrewer(){
        var pos=TARGET.north(10);var storage=pos.east(4);var owner=UUID.fromString("57cb2ebd-e127-4c45-80ea-92134e85041e");
        for(var p:java.util.List.of(pos,storage)){level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);EXTRA_CHUNKS.add(net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));level.getChunkAt(p);}
        var previous=level.getBlockEntity(pos);
        if(previous instanceof io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity restored&&restored.brewer!=null&&restored.brewer.job!=null){
            var c=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(storage);
            check(owner.equals(restored.owner())&&restored.brewer.job.stage==1&&restored.brewer.job.batch==3&&net.minecraft.world.item.alchemy.PotionUtils.getPotion(restored.brewer.job.current)==net.minecraft.world.item.alchemy.Potions.AWKWARD&&restored.energy.stored()==92500,"Production brewer region checkpoint changed its owner, intermediate or fee");
            restored.redstoneMode=0;restored.inventory.setStackInSlot(1,new ItemStack(Items.MAGMA_CREAM));for(int i=0;i<30;i++){brewerStep(restored);io.github.gerbesh.unusualcopper.machine.logic.BoundStorageLogic.tick(restored,level);}
            check(brewerProducts(c)==3&&restored.energy.stored()==85000&&restored.brewer.job==null,"Production brewer disk continuation duplicated or lost a real bottle/fee");
            LogUtils.getLogger().info("CuE PRODUCTION BREWER DISK RESTORE PASS: actual region-file awkward batch, original owner, three exact final potions and total 15000 CuE");
        }
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(storage,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        level.setBlock(pos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.AETHERIC_BREWER).get().defaultBlockState(),3);level.setBlock(storage,net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(),3);
        var m=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(pos);m.setOwner(owner);m.brewer.pending=16;m.energy.add(100000,false);
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"CuEBrewer"));check(m.bind(storage,net.minecraft.core.Direction.UP,actor),"Production brewer source binding failed");
        var c=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(storage);c.setItem(0,new ItemStack(Items.GLASS_BOTTLE,16));c.setItem(1,new ItemStack(Items.NETHER_WART,6));c.setItem(2,new ItemStack(Items.MAGMA_CREAM,6));
        for(int i=0;i<300;i++){brewerStep(m);io.github.gerbesh.unusualcopper.machine.logic.BoundStorageLogic.tick(m,level);}
        check(brewerProducts(c)==16&&m.energy.stored()==20000&&m.brewer.pending==0&&m.brewer.job==null&&m.brewer.recovery.empty()&&m.boundCargoCount()==0,"Production brewer did not conserve sixteen physical bottles and 80000 CuE");
        LogUtils.getLogger().info("CuE PRODUCTION BREWER PASS: actual Forge brewing registry, physical barrel, sixteen exact potions, six batches of real reagents and 80000 CuE");
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(storage,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.AETHERIC_BREWER).get().defaultBlockState(),3);level.setBlock(storage,net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(),3);
        var checkpoint=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(pos);checkpoint.setOwner(owner);checkpoint.brewer.pending=3;checkpoint.energy.add(100000,false);check(checkpoint.bind(storage,net.minecraft.core.Direction.UP,actor),"Production brewer checkpoint binding failed");checkpoint.inventory.setStackInSlot(0,new ItemStack(Items.GLASS_BOTTLE,3));checkpoint.inventory.setStackInSlot(1,new ItemStack(Items.NETHER_WART));brewerStep(checkpoint);checkpoint.redstoneMode=1;((net.minecraft.world.level.block.entity.BlockEntity)checkpoint).setChanged();
        check(checkpoint.brewer.job!=null&&checkpoint.brewer.job.stage==1&&checkpoint.energy.stored()==92500,"Production brewer partial checkpoint failed");LogUtils.getLogger().info("CuE PRODUCTION BREWER DISK CHECKPOINT: real three-bottle awkward batch, one paid wart stage, 92500 CuE and redstone pause");
    }
    private static void verifySentinel() {
        var pos=TARGET.east(6); var targetPos=pos.east(6);
        for(int x=pos.getX();x<=targetPos.getX();x++) for(int z=pos.getZ()-1;z<=pos.getZ()+1;z++) { for(int y=0;y<5;y++) level.setBlock(new BlockPos(x,pos.getY()+y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3); level.setBlock(new BlockPos(x,pos.getY()-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3); }
        level.setBlock(pos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.COPPER_SENTINEL).get().defaultBlockState(),3);
        var m=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(pos); m.setOwner(UUID.randomUUID()); m.energy.add(7500,false); m.sentinel.active=true;
        var target=spawn(EntityType.HUSK,targetPos); target.setInvulnerable(false); target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        for(int y=0;y<4;y++) level.setBlock(pos.offset(3,y,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        io.github.gerbesh.unusualcopper.machine.logic.SentinelLogic.process(m,level); check(target.getHealth()==20 && m.energy.stored()==7500,"Production sentinel fired through a real collision wall");
        for(int y=0;y<4;y++) level.setBlock(pos.offset(3,y,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        io.github.gerbesh.unusualcopper.machine.logic.SentinelLogic.process(m,level); check(target.getHealth()==10 && m.energy.stored()==5000,"Production sentinel real damage or custom type/fee failed: status="+m.status()+", hp="+target.getHealth());
        target.invulnerableTime=0; io.github.gerbesh.unusualcopper.machine.logic.SentinelLogic.process(m,level); check(target.getHealth()==10 && m.energy.stored()==5000,"Production sentinel bypassed its paid cooldown");
        target.discard(); m.redstoneMode=1;
        LogUtils.getLogger().info("CuE PRODUCTION SENTINEL PASS: real collision wall denied; custom damage type dealt ten HP for 2500 CuE; paid cooldown blocked a duplicate hit");
    }
    private static void verifyArborist() {
        var root=TARGET.south(10); var pos=root.west(5);
        for(int x=(root.getX()-8)>>4;x<=(root.getX()+8)>>4;x++) for(int z=(root.getZ()-8)>>4;z<=(root.getZ()+8)>>4;z++) { level.setChunkForced(x,z,true); EXTRA_CHUNKS.add(net.minecraft.world.level.ChunkPos.asLong(x,z)); level.getChunk(x,z); }
        level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true); EXTRA_CHUNKS.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4)); level.getChunkAt(pos);
        for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) {
            for(int y=0;y<=7;y++) level.setBlock(root.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            level.setBlock(root.offset(x,-1,z),net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),2);
        }
        for(int y=0;y<4;y++) level.setBlock(root.above(y),net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(),2);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0||z!=0) level.setBlock(root.offset(x,3,z),net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1),2);
        level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3); level.setBlock(pos,io.github.gerbesh.unusualcopper.registry.ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.ARBORIST).get().defaultBlockState(),3);
        var m=(io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity)level.getBlockEntity(pos);
        var owner=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ForestryOwner")); owner.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        check(m.togglePlantation(root,owner),"Production plantation marker rejected");
        m.inventory.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_SAPLING,4)); m.inventory.setStackInSlot(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE)); m.energy.add(1120,false);
        var previousSpawn=level.getSharedSpawnPos(); float angle=level.getSharedSpawnAngle();
        level.getServer().getPlayerList().op(new com.mojang.authlib.GameProfile(UUID.fromString("ffab1381-f9a9-4a1b-b7b9-57ac86f07dab"),"SpawnAdmin")); level.setDefaultSpawnPos(root,0);
        try {
            for(int i=0;i<300;i++) { io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic.process(m,level); if(m.status()!=io.github.gerbesh.unusualcopper.machine.MachineStatus.TREE_SCANNING) break; }
            check(m.status()==io.github.gerbesh.unusualcopper.machine.MachineStatus.PROTECTED && m.arborist.job==null && m.energy.stored()==1120 && level.getBlockState(root).is(net.minecraft.world.level.block.Blocks.OAK_LOG),"Vanilla spawn protection did not stop the first cut");
        } finally { level.setDefaultSpawnPos(previousSpawn,angle); }
        m.arborist.retryAt=0;
        for(int i=0;i<400 && m.grown==0;i++) io.github.gerbesh.unusualcopper.machine.logic.ArboristLogic.process(m,level);
        check(m.grown==1 && m.arborist.job==null && m.energy.stored()==0 && level.getBlockState(root).is(net.minecraft.world.level.block.Blocks.OAK_SAPLING) && m.inventory.getStackInSlot(2).getCount()==4 && m.inventory.getStackInSlot(1).getDamageValue()==4 && m.inventory.getStackInSlot(0).getCount()==3,"Production forestry conservation/replant failed: "+m.status()); m.redstoneMode=1;
        LogUtils.getLogger().info("CuE PRODUCTION ARBORIST PASS: vanilla spawn protection denied; four actual logs, exact 1120 CuE, four axe wear and one real replant after permission returned");
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { if (level != null) { level.setChunkForced(TARGET.getX() >> 4, TARGET.getZ() >> 4, false); for(long key:EXTRA_CHUNKS) { var p=new net.minecraft.world.level.ChunkPos(key); level.setChunkForced(p.x,p.z,false); } EXTRA_CHUNKS.clear(); } }
}
