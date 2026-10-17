package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.armor.*;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;

/** Future functional acceptance scenarios. Compilation does not count as execution. */
@GameTestHolder(UnusualCopper.MOD_ID)
public final class ArmorConfigurationGameTests {
    @GameTest(template="empty",timeoutTicks=40)
    public static void preferencesRoundTripWithoutChangingBattery(GameTestHelper h){
        var chest=new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_chestplate_tier_3").get());
        var battery=new ItemStack(ModContent.AETHERIC_BATTERY.get());CopperBatteryItem.stored(battery,123456);
        h.assertTrue(ArmorData.installBattery(chest,battery)&&ArmorData.install(chest,ArmorModuleType.SHIELD_FIELD),"Fixture install");
        var preferences=new ArmorModuleSettings(false,ArmorModuleSettings.Trigger.LOW_HEALTH,25,false);
        h.assertTrue(ArmorData.settings(chest,ArmorModuleType.SHIELD_FIELD,1,preferences),"Settings rejected");
        var restored=ItemStack.of(chest.save(new CompoundTag()));
        h.assertTrue(ArmorData.settings(restored,ArmorModuleType.SHIELD_FIELD,1).equals(preferences),"Preset lost in NBT");
        h.assertTrue(ArmorData.settings(restored,ArmorModuleType.SHIELD_FIELD,2).enabled(),"Different preset overwritten");
        h.assertTrue(ArmorData.upgradeSafe(restored),"Valid data rejected");
        h.assertTrue(ItemStack.isSameItemSameTags(battery,ArmorData.battery(restored)),"Battery changed by configuration");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void priorityMigrationIsBackwardCompatibleAndMalformedValuesFailClosed(GameTestHelper h){
        var helmet=new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_helmet_tier_3").get());
        h.assertTrue(ArmorData.install(helmet,ArmorModuleType.AQUA_LUNGS),"Module setup failed");
        var old=new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.UNDERWATER,0,false);
        h.assertTrue(ArmorData.settings(helmet,ArmorModuleType.AQUA_LUNGS,0,old),"Old preset setup");
        var row=helmet.getOrCreateTagElement("UCProgrammableArmor").getCompound("Presets")
                .getCompound("0").getCompound("aqua_lungs");
        row.remove("Priority"); // Simulate an item saved before priority was introduced.
        h.assertTrue(ArmorData.upgradeSafe(helmet),"Legacy preset rejected");
        h.assertTrue(ArmorData.settings(helmet,ArmorModuleType.AQUA_LUNGS,0).priority()==3,"Legacy fallback priority");
        h.assertTrue(ArmorData.settings(helmet,ArmorModuleType.AQUA_LUNGS,0,
                new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.UNDERWATER,0,false,2)),"Priority write");
        var restored=ItemStack.of(helmet.save(new CompoundTag()));
        h.assertTrue(ArmorData.settings(restored,ArmorModuleType.AQUA_LUNGS,0).priority()==2,"Priority lost after reload");
        h.assertTrue(ArmorData.upgradeSafe(restored),"Valid priority rejected");
        restored.getOrCreateTagElement("UCProgrammableArmor").getCompound("Presets")
                .getCompound("0").getCompound("aqua_lungs").putInt("Priority",99);
        h.assertTrue(!ArmorData.upgradeSafe(restored),"Invalid priority accepted");
        h.assertTrue(!ArmorData.settings(restored,ArmorModuleType.AQUA_LUNGS,0).enabled(),"Invalid priority activated module");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void malformedPreferencesFailClosed(GameTestHelper h){
        var helmet=new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_helmet_tier_3").get());
        ArmorData.install(helmet,ArmorModuleType.AQUA_LUNGS);
        ArmorData.settings(helmet,ArmorModuleType.AQUA_LUNGS,0,new ArmorModuleSettings(true,ArmorModuleSettings.Trigger.AUTO,0,false));
        helmet.getOrCreateTagElement("UCProgrammableArmor").getCompound("Presets").getCompound("0").getCompound("aqua_lungs").putInt("Trigger",999);
        h.assertTrue(!ArmorData.upgradeSafe(helmet),"Malformed data accepted for upgrade");
        h.assertTrue(!ArmorData.settings(helmet,ArmorModuleType.AQUA_LUNGS,0).enabled(),"Malformed trigger activated module");h.succeed();
    }
}
