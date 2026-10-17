package io.github.gerbesh.unusualcopper.compat.jei;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.recipe.PrecisionAssemblyRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Full server-datapack 3x3 grid, including explicit empty cells and a reusable card. */
final class PrecisionAssemblyCategory implements IRecipeCategory<PrecisionAssemblyRecipe> {
    static final RecipeType<PrecisionAssemblyRecipe> TYPE=new RecipeType<>(
            new ResourceLocation(UnusualCopper.MOD_ID,"precision_assembly"),PrecisionAssemblyRecipe.class);
    private final IDrawable background,icon;
    PrecisionAssemblyCategory(IGuiHelper helper) {
        background=helper.createBlankDrawable(160,91);
        icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModContent.MACHINES.get(MachineKind.PRECISION_ASSEMBLER).get()));
    }
    @Override public RecipeType<PrecisionAssemblyRecipe> getRecipeType(){return TYPE;}
    @Override public Component getTitle(){return Component.translatable("jei.unusualcopper.precision_assembly");}
    @Override public IDrawable getBackground(){return background;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public void setRecipe(IRecipeLayoutBuilder layout,PrecisionAssemblyRecipe recipe,IFocusGroup focuses) {
        var ingredients=recipe.getIngredients();
        for(int i=0;i<9;i++)
            if(!ingredients.get(i).isEmpty())
                layout.addSlot(RecipeIngredientRole.INPUT,7+(i%3)*18,9+(i/3)*18)
                        .addIngredients(ingredients.get(i));
        layout.addSlot(RecipeIngredientRole.CATALYST,88,27)
                .addItemStack(new ItemStack(ModContent.PATTERN_CARD.get()));
        layout.addSlot(RecipeIngredientRole.OUTPUT,134,27)
                .addItemStack(recipe.getResultItem(RegistryAccess.EMPTY));
    }
    @Override public void draw(PrecisionAssemblyRecipe recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY) {
        graphics.drawString(Minecraft.getInstance().font,recipe.cue()+" CuE / "+recipe.ticks()+" t",7,75,0xff52625c,false);
    }
}
