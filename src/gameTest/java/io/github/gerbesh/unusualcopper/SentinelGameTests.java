package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.SentinelLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SentinelGameTests {
    private static void loaded(GameTestHelper h,int id,java.util.function.Consumer<BlockPos> test) {
        var base=new BlockPos(100008+id*128,70,100008); PopulationGameTests.loaded(h,new WorkArea(base.offset(-7,0,-7),base.offset(8,15,8)),()->{prepare(h.getLevel(),base);test.accept(base);});
    }
    static void prepare(ServerLevel level,BlockPos p) {
        for(int x=-7;x<=8;x++) for(int z=-7;z<=8;z++) { for(int y=0;y<8;y++) level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),3); level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3); }
        for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p).inflate(12))) item.discard();
    }
    static CopperMachineBlockEntity machine(ServerLevel level,BlockPos pos) {
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3); level.setBlock(pos,ModContent.MACHINES.get(MachineKind.COPPER_SENTINEL).get().defaultBlockState(),3);
        var m=(CopperMachineBlockEntity)level.getBlockEntity(pos); m.setOwner(UUID.randomUUID()); m.energy.add(10000,false); m.sentinel.active=true; return m;
    }
    static Husk hostile(ServerLevel level,BlockPos pos) {
        var mob=EntityType.HUSK.create(level); mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); mob.setNoAi(true); mob.setPersistenceRequired(); mob.getAttribute(Attributes.ARMOR).setBaseValue(0); level.addFreshEntity(mob); return mob;
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void tenHpRealDamageExactFeeAndPaidCooldownSurviveNbt(GameTestHelper h) {
        loaded(h,0,p->{ var level=h.getLevel(); var m=machine(level,p); var mob=hostile(level,p.east(5));
            h.assertTrue(SentinelLogic.valid(m,level,mob),"Fixture hostile is valid; world identity="+(level.getEntity(mob.getUUID())==mob));
            h.assertTrue(SentinelLogic.visible(m,level,mob),"Fixture target has a clear loaded collision ray");
            SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==10 && m.energy.stored()==7500 && m.grown==1,"A real unarmored hostile loses exactly ten HP for 2500 CuE: status="+m.status()+", candidates="+m.inspected+", hp="+mob.getHealth()+", active="+m.sentinel.active+", quota="+SentinelScheduler.permitted(level,m));
            mob.invulnerableTime=0; SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==10 && m.energy.stored()==7500,"A repeat call before twenty ticks cannot fire or charge");
            var saved=m.saveWithFullMetadata(); m.load(saved); m.sentinel.active=true; SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==10 && m.energy.stored()==7500 && m.sentinel.target==null,"Saved cooldown survives; transient target does not");
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void armorAndAbsorptionUseNormalDamageRules(GameTestHelper h) {
        loaded(h,1,p->{ var level=h.getLevel(); var m=machine(level,p); var mob=hostile(level,p.east(5)); mob.getAttribute(Attributes.ARMOR).setBaseValue(20); mob.setAbsorptionAmount(2);
            SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()>10 && mob.getHealth()<20 && mob.getAbsorptionAmount()<2 && m.energy.stored()==7500,"Custom beam does not bypass armor or absorption");
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void wallRangeHeightAndNamedNeutralPetNpcBossAreExcluded(GameTestHelper h) {
        loaded(h,2,p->{ var level=h.getLevel(); var m=machine(level,p); var mob=hostile(level,p.east(5));
            for(int y=0;y<4;y++) for(int z=-1;z<=1;z++) level.setBlock(p.offset(2,y,z),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(!SentinelLogic.visible(m,level,mob),"A solid wall blocks the actual block collision ray"); SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==20 && m.energy.stored()==10000,"No through-wall shot or fee");
            mob.setCustomName(Component.literal("Protected")); h.assertTrue(!SentinelLogic.valid(m,level,mob),"Named hostiles are excluded"); mob.setCustomName(null);
            mob.setPos(p.getX()+25.5,p.getY(),p.getZ()+.5); h.assertTrue(!SentinelLogic.valid(m,level,mob),"Horizontal range is circular"); mob.setPos(p.getX()+.5,p.getY()+36,p.getZ()+.5); h.assertTrue(!SentinelLogic.valid(m,level,mob),"Vertical range is bounded");
            for(var type:java.util.List.of(EntityType.COW,EntityType.WOLF,EntityType.VILLAGER,EntityType.ENDERMAN,EntityType.ZOMBIFIED_PIGLIN,EntityType.WITHER,EntityType.WARDEN)) {
                var other=(Mob)type.create(level); other.moveTo(p.getX()+3.5,p.getY(),p.getZ()+.5,0,0); other.setNoAi(true); level.addFreshEntity(other); h.assertTrue(!SentinelLogic.valid(m,level,other),"Excluded target type: "+type); other.discard();
            }
        });
    }
    public static final class Cancel {
        final UUID target; Cancel(UUID target) {this.target=target;}
        @SubscribeEvent public void attack(LivingAttackEvent event) { if(event.getEntity().getUUID().equals(target)) event.setCanceled(true); }
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void cancelledForgeAttackAndMissingEnergyDoNotSpendShotPrice(GameTestHelper h) {
        loaded(h,3,p->{ var level=h.getLevel(); var m=machine(level,p); var mob=hostile(level,p.east(5)); var cancel=new Cancel(mob.getUUID()); MinecraftForge.EVENT_BUS.register(cancel);
            try { SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==20 && m.energy.stored()==10000 && m.grown==0 && m.status()==MachineStatus.SHOT_DENIED,"Cancelled Forge attack keeps HP, shot fee and count"); }
            finally {MinecraftForge.EVENT_BUS.unregister(cancel);}
            m.energy.restore(2499); SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==20 && m.energy.stored()==2499,"Insufficient complete fee never fires");
            m.energy.restore(2500); SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==10 && m.energy.stored()==0,"Exactly the full shot price succeeds");
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void adjacentSentinelsCoordinateTheSameTarget(GameTestHelper h) {
        loaded(h,4,p->{ var level=h.getLevel(); var first=machine(level,p); var second=machine(level,p.south(3)); var mob=hostile(level,p.east(5));
            SentinelLogic.process(first,level); SentinelLogic.process(second,level); h.assertTrue(mob.getHealth()==10 && first.energy.stored()==7500 && second.energy.stored()==10000,"The second sentinel does not pay or shoot at a target locked by its neighbor");
            var other=hostile(level,p.east(6).south(3)); SentinelLogic.process(second,level); h.assertTrue(other.getHealth()==10 && second.energy.stored()==7500,"It can choose another valid hostile instead");
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void chunkQuotaFocusOwnershipAndRemovalPreserveInstalledItem(GameTestHelper h) {
        loaded(h,5,p->{ var level=h.getLevel(); var first=machine(level,p); var second=machine(level,p.south()); var third=machine(level,p.south(2));
            h.assertTrue(SentinelScheduler.permitted(level,first)&&SentinelScheduler.permitted(level,second)&&!SentinelScheduler.permitted(level,third),"Only two installed machines are active in the same chunk");
            var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(first.owner(),"SentinelOwner")); player.getAbilities().instabuild=false; player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModContent.SENTINEL_FOCUS.get()));
            h.assertTrue(first.installSentinelFocus(player,net.minecraft.world.InteractionHand.MAIN_HAND)&&player.getMainHandItem().isEmpty()&&SentinelLogic.radius(first)==64,"Owner physically installs one paid focus for 64 blocks");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModContent.SENTINEL_FOCUS.get())); h.assertTrue(!first.installSentinelFocus(player,net.minecraft.world.InteractionHand.MAIN_HAND)&&player.getMainHandItem().getCount()==1,"Duplicate upgrade cannot consume an item");
            var privateTag=first.saveWithFullMetadata(); first.load(privateTag); h.assertTrue(first.sentinel.focused&&!first.sentinel.active,"Upgrade survives NBT, active visual does not");
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3); h.assertTrue(SentinelScheduler.permitted(level,third),"Removing one machine releases chunk quota");
            int focus=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p).inflate(2)).stream().filter(e->e.getItem().is(ModContent.SENTINEL_FOCUS.get())).mapToInt(e->e.getItem().getCount()).sum(); h.assertTrue(focus==1,"Installed upgrade is returned exactly once on removal");
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void physicalFocusExtendsARealLoadedTargetPastBaseRange(GameTestHelper h) {
        loaded(h,7,p->{var level=h.getLevel();
            for(int x=-7;x<=18;x++) { for(int y=0;y<5;y++) level.setBlock(p.offset(x,y,0),Blocks.AIR.defaultBlockState(),3); level.setBlock(p.offset(x,-1,0),Blocks.STONE.defaultBlockState(),3); }
            var m=machine(level,p.west(7)); var mob=hostile(level,p.east(18));
            try {
                h.assertTrue(!SentinelLogic.valid(m,level,mob),"A real loaded target 25 blocks away is outside the base radius"); SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==20&&m.energy.stored()==10000,"Base machine cannot pay for an out of range hit");
                var foreign=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ForeignFocus")); foreign.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModContent.SENTINEL_FOCUS.get())); h.assertTrue(!m.installSentinelFocus(foreign,net.minecraft.world.InteractionHand.MAIN_HAND)&&foreign.getMainHandItem().getCount()==1,"A stranger cannot install or consume the upgrade");
                var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(m.owner(),"FocusOwner")); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModContent.SENTINEL_FOCUS.get())); player.getAbilities().instabuild=false;
                h.assertTrue(m.installSentinelFocus(player,net.minecraft.world.InteractionHand.MAIN_HAND)&&SentinelLogic.valid(m,level,mob),"An actually consumed owner focus admits that same real target"); SentinelLogic.process(m,level); h.assertTrue(mob.getHealth()==10&&m.energy.stored()==7500,"Focused machine hits beyond base range for the same exact fee");
                var tag=m.getUpdateTag(); h.assertTrue(!tag.contains("Sentinel")&&!tag.contains("Owner")&&!tag.contains("Target"),"Ordinary update tag only exposes public visual state");
            } finally {mob.discard();m.redstoneMode=1;}
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void upkeepAndRedstoneRunOnActualMachineTicker(GameTestHelper h) {
        loaded(h,6,p->{ var level=h.getLevel(); var m=machine(level,p); m.energy.restore(1);
            CopperMachineBlockEntity.tick(level,p,m.getBlockState(),m); h.assertTrue(m.energy.stored()==0&&m.sentinel.active,"One CuE runs one active idle tick");
            CopperMachineBlockEntity.tick(level,p,m.getBlockState(),m); h.assertTrue(!m.sentinel.active&&m.status()==MachineStatus.NO_ENERGY,"Empty machine lowers and extinguishes its crystal");
            m.energy.restore(20); m.redstoneMode=1; CopperMachineBlockEntity.tick(level,p,m.getBlockState(),m); SentinelLogic.process(m,level); h.assertTrue(m.energy.stored()==20&&!m.sentinel.active&&m.status()==MachineStatus.REDSTONE_PAUSED,"Redstone pause costs neither upkeep nor shots");
        });
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void brightNeutralSpiderIsSparedButDarkHostileSpiderIsHit(GameTestHelper h) {
        var p=new BlockPos(101288,70,100008); var state=new Object(){CopperMachineBlockEntity machine; net.minecraft.world.entity.monster.Spider spider;};
        PopulationGameTests.loadedSequence(h,new WorkArea(p.offset(-7,0,-7),p.offset(8,15,8)),seq->seq.thenExecute(()->{
            var level=h.getLevel(); prepare(level,p);
            for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++) { level.setBlock(p.offset(x,4,z),Blocks.STONE.defaultBlockState(),3); if(Math.abs(x)==6||Math.abs(z)==6) for(int y=0;y<4;y++) level.setBlock(p.offset(x,y,z),Blocks.STONE.defaultBlockState(),3); }
            state.machine=machine(level,p); state.machine.redstoneMode=1; level.setBlock(p.offset(3,-1,0),Blocks.SEA_LANTERN.defaultBlockState(),3);
            state.spider=EntityType.SPIDER.create(level); state.spider.setPos(p.getX()+3.5,p.getY(),p.getZ()+.5); state.spider.setNoAi(true); state.spider.setNoGravity(true); state.spider.setPersistenceRequired(); level.addFreshEntity(state.spider);
        }).thenWaitUntil(()->h.assertTrue(state.spider.getLightLevelDependentMagicValue()>=.5F,"Actual local light makes the spider neutral")).thenExecute(()->{
            h.assertTrue(!SentinelLogic.valid(state.machine,h.getLevel(),state.spider),"A bright non-aggressive spider is not treated as hostile just because of MONSTER category"); h.getLevel().setBlock(p.offset(3,-1,0),Blocks.STONE.defaultBlockState(),3);
        }).thenWaitUntil(()->h.assertTrue(state.spider.getLightLevelDependentMagicValue()<.5F,"Actual light propagation leaves a dark room")).thenExecute(()->{
            state.machine.redstoneMode=0; state.machine.sentinel.active=true;
            h.assertTrue(SentinelLogic.valid(state.machine,h.getLevel(),state.spider),"That same spider is hostile in the dark"); SentinelLogic.process(state.machine,h.getLevel());
            h.assertTrue(state.spider.getHealth()==6 && state.machine.energy.stored()==7500,"Dark hostile spider receives ten real HP for exactly one shot"); state.machine.redstoneMode=1;
        }));
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void realSchedulerEnforcesTwentyTickFireIntervalAndUpkeep(GameTestHelper h) {
        var p=new BlockPos(101032,70,100008); var state=new Object(){CopperMachineBlockEntity machine; Husk target; long start;};
        PopulationGameTests.loadedSequence(h,new WorkArea(p.offset(-7,0,-7),p.offset(8,15,8)),seq->seq.thenExecute(()->{
            prepare(h.getLevel(),p); state.machine=machine(h.getLevel(),p); state.target=hostile(h.getLevel(),p.east(5)); state.start=h.getLevel().getGameTime();
        }).thenWaitUntil(()->h.assertTrue(state.machine.grown==1,"Real queue has fired once: status="+state.machine.status()+", candidates="+state.machine.inspected+", energy="+state.machine.energy.stored())).thenExecute(()->{
            h.assertTrue(state.target.getHealth()==10,"First real queued shot deals ten HP");
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(state.machine.grown==1&&state.target.getHealth()==10,"Five ticking frames cannot bypass the cooldown");
        }).thenWaitUntil(()->h.assertTrue(state.machine.grown==2,"Second real shot has fired")).thenExecute(()->{
            long elapsed=h.getLevel().getGameTime()-state.start; h.assertTrue(elapsed>=21&&state.target.isDeadOrDying()&&state.machine.energy.stored()==10000-5000-(int)elapsed,"Two real shots and every active tick are paid exactly"); state.machine.redstoneMode=1;
        }));
    }
}
