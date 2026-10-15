package io.github.gerbesh.unusualcopper.compat.jei;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.recipe.SolarRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Display the server recipe multiplier and the reusable, wearing lens. */
final class SolarInfusionCategory implements IRecipeCategory<SolarRecipe> {
    static final RecipeType<SolarRecipe> TYPE = new RecipeType<>(
            new ResourceLocation(UnusualCopper.MOD_ID, "solar_infusion"), SolarRecipe.class);
    private final IDrawable background;
    private final IDrawable icon;

    SolarInfusionCategory(IGuiHelper helper) {
        background = helper.createBlankDrawable(118, 43);
        icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModContent.MACHINES.get(io.github.gerbesh.unusualcopper.machine.MachineKind.SOLAR_INFUSER).get()));
    }

    @Override public RecipeType<SolarRecipe> getRecipeType() { return TYPE; }

    @Override public Component getTitle() {
        return Component.translatable("jei.unusualcopper.solar_infusion");
    }

    @Override public IDrawable getBackground() { return background; }

    @Override public IDrawable getIcon() { return icon; }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, SolarRecipe recipe, IFocusGroup focuses) {
        layout.addSlot(RecipeIngredientRole.INPUT, 8, 15).addIngredients(recipe.ingredient());
        layout.addSlot(RecipeIngredientRole.CATALYST, 51, 15)
                .addItemStack(new ItemStack(ModContent.SOLAR_LENS.get()));
        layout.addSlot(RecipeIngredientRole.OUTPUT, 94, 15)
                .addItemStack(new ItemStack(ModContent.ESSENCE.get(), recipe.multiplier()));
    }
}
