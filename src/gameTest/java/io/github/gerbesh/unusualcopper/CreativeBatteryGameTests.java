package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.armor.ArmorData;
import io.github.gerbesh.unusualcopper.item.*;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;

/** Compile-only until the user runs gameplay acceptance. */
@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeBatteryGameTests {
    @GameTest(template="empty",timeoutTicks=30)
    public static void infiniteSourceKeepsChargeAndChargingItDoesNotConsumeCellEnergy(GameTestHelper h) {
        var pos=new BlockPos(1,2,1);h.setBlock(pos,ModContent.MACHINES.get(MachineKind.COPPER_CELL_III).get());
        var machine=(CopperMachineBlockEntity)h.getBlockEntity(pos);var source=new ItemStack(ModContent.CREATIVE_BATTERY.get());
        machine.battery.setStackInSlot(0,source);machine.batteryMode=1;
        h.runAtTickTime(2,()->{
            h.assertTrue(machine.energy.stored()==2*machine.energyTransferRate(),"Creative source bypassed the cell rate or failed to feed it");
            h.assertTrue(CopperBatteryItem.stored(source)==CopperBatteryItem.capacity(source),"Creative source drained");
            machine.energy.restore(12345);machine.batteryMode=0;
        });
        h.runAtTickTime(5,()->{
            h.assertTrue(machine.energy.stored()==12345,"Cell charged an infinite source and lost energy");
            var loaded=new CopperMachineBlockEntity(BlockPos.ZERO,machine.getBlockState());loaded.load(machine.saveWithoutMetadata());
            h.assertTrue(CopperBatteryItem.infinite(loaded.battery.getStackInSlot(0))&&CopperBatteryItem.stored(loaded.battery.getStackInSlot(0))>0,"Source lost identity on reload");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void embeddedSourceSurvivesReservationSaveSettlementAndExtraction(GameTestHelper h) {
        var chest=new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_chestplate_tier_3").get());
        h.assertTrue(ArmorData.installBattery(chest,new ItemStack(ModContent.CREATIVE_BATTERY.get())),"Armor rejected creative battery");
        int full=CopperBatteryItem.stored(ArmorData.battery(chest));h.assertTrue(ArmorData.reserve(chest,2000),"Reservation failed");
        var batch=ArmorData.reservationBatch(chest);var restored=ItemStack.of(chest.save(new CompoundTag()));
        h.assertTrue(CopperBatteryItem.stored(ArmorData.battery(restored))==full-2000,"Reload lost a hold");
        h.assertTrue(ArmorData.settle(restored,batch)&&CopperBatteryItem.stored(ArmorData.battery(restored))==full,"Settlement did not replenish source");
        h.assertTrue(!ArmorData.settle(restored,batch),"Receipt settled twice");
        var recovered=ArmorData.takeBattery(restored);
        h.assertTrue(CopperBatteryItem.infinite(recovered)&&CopperBatteryItem.stored(recovered)==full&&ArmorData.battery(restored).isEmpty(),"Extraction lost or duplicated the source");
        var ordinary=new ItemStack(ModContent.COPPER_BATTERY.get());ordinary.getOrCreateTag().putBoolean("Infinite",true);
        h.assertTrue(!CopperBatteryItem.infinite(ordinary)&&CopperBatteryItem.stored(ordinary)==0,"NBT made a survival battery infinite");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=10)
    public static void wandSharesOnlyModeAndOpaquePreviewToken(GameTestHelper h) {
        var wand=new ItemStack(ModContent.AREA_WAND.get());var tag=wand.getOrCreateTag();
        tag.putLong("Machine",BlockPos.ZERO.asLong());tag.putLong("First",new BlockPos(1,2,3).asLong());
        tag.putString("Dimension","minecraft:overworld");tag.putUUID("Instance",java.util.UUID.randomUUID());
        tag.putUUID(AreaWandItem.PREVIEW_SESSION,java.util.UUID.randomUUID());
        var shared=((AreaWandItem)wand.getItem()).getShareTag(wand);
        h.assertTrue(shared.getAllKeys().size()==2&&shared.hasUUID(AreaWandItem.PREVIEW_SESSION)&&!shared.contains("Machine")&&!shared.contains("First"),"Private wand state leaked into shared item NBT");
        tag.putInt("Mode",1);shared=((AreaWandItem)wand.getItem()).getShareTag(wand);
        h.assertTrue(!shared.contains(AreaWandItem.PREVIEW_SESSION),"Storage-binding mode retained a public preview token");h.succeed();
    }
}
