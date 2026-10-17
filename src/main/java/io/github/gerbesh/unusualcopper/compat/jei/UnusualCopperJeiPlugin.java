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
        var helper=registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SolarInfusionCategory(helper),
                new IndustrialProcessingCategory(helper),new PrecisionAssemblyCategory(helper));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ModContent.MACHINES.get(MachineKind.REPAIR_CRYSTAL).get()),Component.translatable("jei.unusualcopper.repair_info"));
        registration.addItemStackInfo(new ItemStack(ModContent.ESSENCE.get()),
                Component.translatable("jei.unusualcopper.essence_info"));
        registration.addItemStackInfo(new ItemStack(ModContent.SOLAR_LENS.get()),
                Component.translatable("jei.unusualcopper.lens_info"));
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // Read data-pack recipes, never maintain an independent hardcoded list.
        var recipes = level.getRecipeManager().getAllRecipesFor(ModContent.SOLAR_RECIPE.get());
        registration.addRecipes(SolarInfusionCategory.TYPE, recipes);
        var industrial=level.getRecipeManager().getAllRecipesFor(ModContent.INDUSTRIAL_RECIPE.get());
        registration.addRecipes(IndustrialProcessingCategory.TYPE,industrial);
        var assembly=level.getRecipeManager().getAllRecipesFor(ModContent.ASSEMBLY_RECIPE.get());
        registration.addRecipes(PrecisionAssemblyCategory.TYPE,assembly);
        UnusualCopper.LOGGER.info("CuE JEI data-driven recipes: solar={}, industrial={}, assembly={}",
                recipes.size(),industrial.size(),assembly.size());
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModContent.MACHINES.get(MachineKind.SOLAR_INFUSER).get()),
                SolarInfusionCategory.TYPE);
        for(var kind:new MachineKind[]{MachineKind.RESONANCE_CRUSHER,MachineKind.COPPER_PRESS,
                MachineKind.VORTEX_PURIFIER,MachineKind.RESONANCE_CRYSTALLIZER,MachineKind.CIRCUIT_ETCHER})
            registration.addRecipeCatalyst(new ItemStack(ModContent.MACHINES.get(kind).get()),
                    IndustrialProcessingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModContent.MACHINES.get(MachineKind.PRECISION_ASSEMBLER).get()),
                PrecisionAssemblyCategory.TYPE);
    }

    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        activeRuntime = runtime;
        UnusualCopper.LOGGER.info("CuE JEI CLIENT LOADED: optional JEI 15 integration active");
    }

    @Override public void onRuntimeUnavailable() { activeRuntime = null; }
}
