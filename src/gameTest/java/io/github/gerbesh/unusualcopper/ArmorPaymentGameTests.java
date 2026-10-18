package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.armor.*;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;

/** Event-hook acceptance scenarios, compiled only unless explicitly authorized to execute. */
@GameTestHolder(UnusualCopper.MOD_ID)
public final class ArmorPaymentGameTests {
    private static ServerPlayer wearer(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CuEPaymentTest"));
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            String part=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";default->"boots";};
            p.setItemSlot(slot,new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_"+part+"_tier_3").get()));
        }
        var battery=new ItemStack(ModContent.AETHERIC_BATTERY.get());CopperBatteryItem.stored(battery,10000);
        ArmorData.installBattery(p.getItemBySlot(EquipmentSlot.CHEST),battery);return p;
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void repeatedJumpCallbackCannotPayOrBoostTwice(GameTestHelper h){
        var p=wearer(h);var chest=p.getItemBySlot(EquipmentSlot.CHEST);
        ArmorData.profile(chest,1);ArmorData.install(p.getItemBySlot(EquipmentSlot.FEET),ArmorModuleType.JUMP_DRIVE);
        var event=new LivingEvent.LivingJumpEvent(p);ArmorRuntime.jump(event);ArmorRuntime.jump(event);
        h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(chest))==8000,"Jump did not reserve exactly one event");
        h.runAfterDelay(1,()->{h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(chest))==8000&&ArmorTelemetry.perSecond(p)==2000,"Jump batch mismatch");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void lateCanceledDamageReturnsItsReservedCost(GameTestHelper h){
        var p=wearer(h);var chest=p.getItemBySlot(EquipmentSlot.CHEST);ArmorData.profile(chest,2);
        ArmorData.install(chest,ArmorModuleType.SHIELD_FIELD);
        var event=new LivingDamageEvent(p,p.damageSources().generic(),2F);ArmorRuntime.shield(event);event.setCanceled(true);
        h.runAfterDelay(1,()->{h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(chest))==10000&&ArmorTelemetry.perSecond(p)==0,"Cancelled shield was charged");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void multipleMiningQueriesShareOneTickAllocation(GameTestHelper h){
        var p=wearer(h);var chest=p.getItemBySlot(EquipmentSlot.CHEST);ArmorData.profile(chest,1);
        ArmorData.install(p.getItemBySlot(EquipmentSlot.LEGS),ArmorModuleType.MINING_SERVO);
        p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        var pos=h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        var first=new PlayerEvent.BreakSpeed(p,Blocks.STONE.defaultBlockState(),1F,pos);
        var second=new PlayerEvent.BreakSpeed(p,Blocks.STONE.defaultBlockState(),1F,pos);
        ArmorRuntime.mining(first);ArmorRuntime.mining(second);ArmorRuntime.mining(second);
        h.assertTrue(first.getNewSpeed()==1.4F&&second.getNewSpeed()==1.4F,"Repeated callback multiplied mining twice");
        h.runAfterDelay(1,()->{h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(chest))==9992&&ArmorTelemetry.perSecond(p)==8,"Mining was billed per query");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void earlyFireAndContinuousShellShareTheirTickCharge(GameTestHelper h){
        var p=wearer(h);var chest=p.getItemBySlot(EquipmentSlot.CHEST);ArmorData.profile(chest,2);
        ArmorData.install(chest,ArmorModuleType.THERMAL_SHELL);p.setRemainingFireTicks(100);
        var hit=new LivingDamageEvent(p,p.damageSources().onFire(),4F);ArmorSupport.thermal(hit);
        ArmorRuntime.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(hit.getAmount()==0&&CopperBatteryItem.stored(ArmorData.battery(chest))==9990,"Fire applied without one shared authorization");
        h.runAfterDelay(1,()->{h.assertTrue(ArmorTelemetry.perSecond(p)==10,"Shell charged twice");p.clearFire();h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void regenerationPaysForPartialActualHpAndPreservesPotions(GameTestHelper h){
        var p=wearer(h);var chest=p.getItemBySlot(EquipmentSlot.CHEST);ArmorData.profile(chest,2);
        ArmorData.install(chest,ArmorModuleType.REGEN_CORE);p.setHealth(19.5F);
        ArmorRuntime.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(p.getHealth()==20F&&p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION)==null,"Core did not heal directly");
        h.runAfterDelay(1,()->{h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(chest))==9735,"Core did not bill 15 upkeep + 250 for half HP");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void paidScannerDoesNotRewriteOrChargeForExternalNightVision(GameTestHelper h){
        var p=wearer(h);var chest=p.getItemBySlot(EquipmentSlot.CHEST);ArmorData.profile(chest,3);
        var helmet=p.getItemBySlot(EquipmentSlot.HEAD);
        ArmorData.install(helmet,ArmorModuleType.OPTIC_VISOR);ArmorData.install(helmet,ArmorModuleType.FIELD_SCANNER,2);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,600,0));
        var external=p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        var event=new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p);
        try{
            ArmorRuntime.tick(event);ArmorRuntime.tick(event);
            h.assertTrue(p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)==external&&external.getDuration()==600,"External potion rewritten");
            h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(chest))==9994,"Scanner not reserved exactly once, or redundant visor charged");
        }finally{
            // No live client exists for a FakePlayer. Verify payment here, then close its
            // sensing session before the normal owner-packet publishing phase.
            ArmorSensors.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
        }
        h.succeed();
    }
}
