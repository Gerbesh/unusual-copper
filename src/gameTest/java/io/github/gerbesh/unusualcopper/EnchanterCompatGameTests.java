package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnchanterCompatGameTests {
    @GameTest(template="empty",timeoutTicks=40)
    public static void zeroEnchantabilityModClothingUsesItsForgeHook(GameTestHelper h){
        var gloves=new ItemStack(ForgeRegistries.ITEMS.getValue(EnchanterCompatFixtures.id("gloves")));
        h.assertTrue(!gloves.isEnchantable()&&!(gloves.getItem() instanceof ArmorItem),"Witness is a mod item with no vanilla armor category or enchantability");
        var offer=EnchantmentPolicy.offer(gloves,EnchanterCompatFixtures.id("reach"));
        h.assertTrue(EnchantmentPolicy.target(gloves)&&offer!=null&&offer.level()==3,"Registry-wide policy honors the item's table hook and treasure maximum");
        gloves.getOrCreateTag().putString("CustomClothing","preserve");var result=EnchantmentPolicy.apply(gloves,offer);
        h.assertTrue(result.getEnchantmentLevel(ForgeRegistries.ENCHANTMENTS.getValue(offer.enchantment()))==3&&result.getOrCreateTag().getString("CustomClothing").equals("preserve")&&!gloves.isEnchanted(),"A copied result preserves custom clothing data and exposes its real Forge enchantment level");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void virtualCurseCannotBeBypassedByMissingVanillaNbt(GameTestHelper h){
        var gloves=new ItemStack(ForgeRegistries.ITEMS.getValue(EnchanterCompatFixtures.id("sealed_gloves")));
        h.assertTrue(!gloves.isEnchanted()&&EnchantmentPolicy.existing(gloves).get(Enchantments.BINDING_CURSE)==1,"Item hook supplies the pre-existing curse without vanilla enchantment NBT");
        h.assertTrue(EnchantmentPolicy.offer(gloves,EnchanterCompatFixtures.id("reach"))==null,"Incompatible implicit enchantment blocks purchase");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void booksKeepCatalogsLargerThan256(GameTestHelper h){
        var book=new ItemStack(Items.BOOK);h.assertTrue(EnchantmentPolicy.offers(book).size()>300,"Every compatible registered entry remains available beyond the former packet cap");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void itemIgnoringStandardEnchantmentsCannotSellADeadResult(GameTestHelper h){
        var gloves=new ItemStack(ForgeRegistries.ITEMS.getValue(EnchanterCompatFixtures.id("opaque_gloves")));
        h.assertTrue(EnchantmentPolicy.target(gloves),"Special item can be placed in the target slot");
        h.assertTrue(EnchantmentPolicy.offer(gloves,EnchanterCompatFixtures.id("reach"))==null,"An item hook that ignores the written enchantment cannot yield a purchasable result");h.succeed();
    }
}
