package io.github.gerbesh.unusualcopper.recipe;

import com.google.gson.JsonObject;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Ordinary shaped crafting/recipe book, with the input battery's charge retained. */
public final class BatteryUpgradeRecipe extends ShapedRecipe {
    public BatteryUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(),recipe.getGroup(),recipe.category(),recipe.getWidth(),recipe.getHeight(),recipe.getIngredients(),recipe.getResultItem(RegistryAccess.EMPTY),recipe.showNotification());
    }
    @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess registries) {
        var result=super.assemble(grid,registries);
        for(int i=0;i<grid.getContainerSize();i++)if(grid.getItem(i).getItem() instanceof CopperBatteryItem) {
            var input=grid.getItem(i);
            if(input.hasTag())result.setTag(input.getTag().copy());
            CopperBatteryItem.stored(result,CopperBatteryItem.stored(input));
            break;
        }
        return result;
    }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.BATTERY_UPGRADE_SERIALIZER.get(); }
    public static final class Serializer implements RecipeSerializer<BatteryUpgradeRecipe> {
        @Override public BatteryUpgradeRecipe fromJson(ResourceLocation id,JsonObject json) { return new BatteryUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id,json)); }
        @Override public BatteryUpgradeRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer) { return new BatteryUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id,buffer)); }
        @Override public void toNetwork(FriendlyByteBuf buffer,BatteryUpgradeRecipe recipe) { RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer,recipe); }
    }
}
