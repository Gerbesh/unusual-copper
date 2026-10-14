package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.machine.logic.SmelterLogic;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

/** Test-only items and live RecipeManager fixtures; never packaged in the production jar. */
@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmeltingIntegrationGameTests {
    @SubscribeEvent public static void registerFixtures(RegisterEvent event) {
        event.register(Registries.ITEM, id("fixture_ore"), () -> new Item(new Item.Properties()));
        event.register(Registries.ITEM, id("fixture_reload_ore"), () -> new Item(new Item.Properties()));
    }
    private static ResourceLocation id(String name) { return new ResourceLocation(UnusualCopper.MOD_ID, name); }
    private static CopperMachineBlockEntity machine(GameTestHelper h, String name) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModContent.MACHINES.get(MachineKind.RESONANCE_SMELTER).get());
        var machine = (CopperMachineBlockEntity)h.getBlockEntity(pos);
        machine.inventory.setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(id(name))));
        machine.energy.add(240, false); machine.redstoneMode = 1;
        return machine;
    }
    private static void addRecipe(GameTestHelper h, String name, int duration, ItemStack result) {
        var recipes = new ArrayList<Recipe<?>>(h.getLevel().getRecipeManager().getRecipes());
        recipes.add(new SmeltingRecipe(id(name), "", CookingBookCategory.MISC,
                Ingredient.of(BuiltInRegistries.ITEM.get(id(name))), result, 0, duration));
        h.getLevel().getRecipeManager().replaceRecipes(recipes);
    }

    @GameTest(template = "empty")
    public static void nonstandardCookingTimeAndResultCount(GameTestHelper h) {
        addRecipe(h, "fixture_ore", 300, new ItemStack(Items.IRON_INGOT, 2));
        var machine = machine(h, "fixture_ore"); var logic = new SmelterLogic();
        for (int tick = 0; tick < 29; tick++) logic.tick(machine, h.getLevel());
        h.assertTrue(machine.inventory.getStackInSlot(2).isEmpty() && machine.progress == 29, "300-tick recipe has not completed at 29 accelerated ticks");
        logic.tick(machine, h.getLevel());
        h.assertTrue(machine.inventory.getStackInSlot(2).is(Items.IRON_INGOT) && machine.inventory.getStackInSlot(2).getCount() == 2, "Exact RecipeManager result count is preserved");
        h.assertTrue(machine.energy.stored() == 0 && machine.inventory.getStackInSlot(0).isEmpty(), "Exactly 240 CuE and one input paid in 30 ticks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void actualDatapackReloadPausesRemovedRecipe(GameTestHelper h) {
        addRecipe(h, "fixture_reload_ore", 200, new ItemStack(Items.IRON_INGOT));
        var machine = machine(h, "fixture_reload_ore"); var logic = new SmelterLogic();
        for (int tick = 0; tick < 10; tick++) logic.tick(machine, h.getLevel());
        h.assertTrue(machine.progress == 10 && machine.paid == 120, "Half-paid recipe exists before the real reload");
        var server = h.getLevel().getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
            if (error != null) { h.fail("Resource reload failed: " + error); return; }
            logic.tick(machine, h.getLevel());
            h.assertTrue(machine.status() == MachineStatus.RECIPE_CHANGED && machine.progress == 10 && machine.paid == 120, "Removed recipe pauses with quoted progress after actual server resource reload");
            h.assertTrue(machine.inventory.getStackInSlot(0).getCount() == 1 && machine.inventory.getStackInSlot(2).isEmpty() && machine.energy.stored() == 120, "Reload neither spends the remaining CuE nor removes the input nor issues a result");
            h.succeed();
        }));
    }
}
