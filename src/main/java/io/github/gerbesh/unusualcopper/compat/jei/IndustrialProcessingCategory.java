package io.github.gerbesh.unusualcopper.compat.jei;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe;
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
import net.minecraft.world.item.Items;

/** Client-only JEI view of the real server-synced industrial processing recipes. */
final class IndustrialProcessingCategory implements IRecipeCategory<IndustrialRecipe> {
    static final RecipeType<IndustrialRecipe> TYPE=new RecipeType<>(
            new ResourceLocation(UnusualCopper.MOD_ID,"industrial_processing"),IndustrialRecipe.class);
    private final IDrawable background,icon;
    IndustrialProcessingCategory(IGuiHelper helper) {
        background=helper.createBlankDrawable(170,78);
        icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModContent.MACHINES.get(MachineKind.RESONANCE_CRUSHER).get()));
    }
    @Override public RecipeType<IndustrialRecipe> getRecipeType(){return TYPE;}
    @Override public Component getTitle(){return Component.translatable("jei.unusualcopper.industrial_processing");}
    @Override public IDrawable getBackground(){return background;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public void setRecipe(IRecipeLayoutBuilder layout,IndustrialRecipe recipe,IFocusGroup focuses) {
        layout.addSlot(RecipeIngredientRole.CATALYST,6,17)
                .addItemStack(new ItemStack(ModContent.MACHINES.get(recipe.machine()).get()));
        var ingredients=recipe.getIngredients();
        if(!ingredients.isEmpty()) {
            var variants=java.util.Arrays.stream(ingredients.get(0).getItems())
                    .map(stack->stack.copyWithCount(recipe.inputCount())).toList();
            layout.addSlot(RecipeIngredientRole.INPUT,37,17).addItemStacks(variants);
        }
        if(ingredients.size()>1)
            layout.addSlot(RecipeIngredientRole.INPUT,63,17).addIngredients(ingredients.get(1));
        if(ingredients.size()>2)
            layout.addSlot(RecipeIngredientRole.INPUT,89,17).addIngredients(ingredients.get(2));
        if(recipe.waterMb()>0)layout.addSlot(RecipeIngredientRole.CATALYST,89,45).addItemStack(new ItemStack(Items.WATER_BUCKET));
        if(recipe.fluidOutput()>0)layout.addSlot(RecipeIngredientRole.OUTPUT,139,17)
                .addIngredients(mezz.jei.api.forge.ForgeTypes.FLUID_STACK,java.util.List.of(new net.minecraftforge.fluids.FluidStack(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.STILL.get(),recipe.fluidOutput())));
        else layout.addSlot(RecipeIngredientRole.OUTPUT,139,17).addItemStack(recipe.getResultItem(RegistryAccess.EMPTY));
        if(!recipe.secondary().isEmpty())
            layout.addSlot(RecipeIngredientRole.OUTPUT,139,45).addItemStack(recipe.secondary());
    }
    @Override public void draw(IndustrialRecipe recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY) {
        graphics.drawString(Minecraft.getInstance().font,recipe.cost()+" CuE / "+recipe.ticks()+" t",6,59,0xff52625c,false);
        if(recipe.waterMb()>0)
            graphics.drawString(Minecraft.getInstance().font,recipe.waterMb()+" mB",6,69,0xff52625c,false);
    }
}
