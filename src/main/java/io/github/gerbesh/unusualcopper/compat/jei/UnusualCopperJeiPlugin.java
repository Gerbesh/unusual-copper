package io.github.gerbesh.unusualcopper.compat.jei;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional client-only JEI integration. Discovered by JEI, never registered by the mod constructor. */
@JeiPlugin
public final class UnusualCopperJeiPlugin implements IModPlugin {
    /** Read-only smoke-test handle, cleared whenever JEI recreates its runtime. */
    private static volatile mezz.jei.api.runtime.IJeiRuntime activeRuntime;
    public static mezz.jei.api.runtime.IJeiRuntime activeRuntime() { return activeRuntime; }
    private static final ResourceLocation ID = new ResourceLocation(UnusualCopper.MOD_ID, "jei_plugin");

    @Override public ResourceLocation getPluginUid() { return ID; }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SolarInfusionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ModContent.ESSENCE.get()),
                Component.translatable("jei.unusualcopper.essence_info"));
        registration.addItemStackInfo(new ItemStack(ModContent.SOLAR_LENS.get()),
                Component.translatable("jei.unusualcopper.lens_info"));
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // Read data-pack recipes, never maintain an independent hardcoded list.
        var recipes = level.getRecipeManager().getAllRecipesFor(ModContent.SOLAR_RECIPE.get());
        registration.addRecipes(SolarInfusionCategory.TYPE, recipes);
        UnusualCopper.LOGGER.info("CuE JEI SOLAR RECIPES REGISTERED: {} recipes from the active world", recipes.size());
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModContent.MACHINES.get(MachineKind.SOLAR_INFUSER).get()),
                SolarInfusionCategory.TYPE);
    }

    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        activeRuntime = runtime;
        UnusualCopper.LOGGER.info("CuE JEI CLIENT LOADED: optional JEI 15 integration active");
    }

    @Override public void onRuntimeUnavailable() { activeRuntime = null; }
}
