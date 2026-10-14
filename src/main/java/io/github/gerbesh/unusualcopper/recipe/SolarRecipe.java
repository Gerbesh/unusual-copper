package io.github.gerbesh.unusualcopper.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import io.github.gerbesh.unusualcopper.registry.ModContent;

public record SolarRecipe(ResourceLocation id, Ingredient ingredient, int multiplier) implements Recipe<Container> {
    public SolarRecipe {
        if (multiplier < 1 || multiplier > 2) throw new IllegalArgumentException("Infusion multiplier must be 1 or 2");
    }
    @Override public boolean matches(Container container, Level level) { return ingredient.test(container.getItem(0)); }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return getResultItem(access).copy(); }
    @Override public ItemStack getResultItem(RegistryAccess access) { return new ItemStack(ModContent.ESSENCE.get(), multiplier); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.SOLAR_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.SOLAR_RECIPE.get(); }

    public static final class Serializer implements RecipeSerializer<SolarRecipe> {
        @Override public SolarRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new SolarRecipe(id, Ingredient.fromJson(json.get("ingredient")), GsonHelper.getAsInt(json, "essence_per_item"));
        }
        @Override public SolarRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new SolarRecipe(id, Ingredient.fromNetwork(buf), buf.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buf, SolarRecipe recipe) {
            recipe.ingredient.toNetwork(buf); buf.writeVarInt(recipe.multiplier);
        }
    }
}
