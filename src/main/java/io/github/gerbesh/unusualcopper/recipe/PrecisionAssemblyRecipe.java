package io.github.gerbesh.unusualcopper.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

/** Whitelisted datapack assembly recipes; nine physical one-item grid inputs. */
public final class PrecisionAssemblyRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final NonNullList<Ingredient> inputs;
    private final ItemStack result;
    private final int ticks;
    private final int cue;

    public PrecisionAssemblyRecipe(ResourceLocation id, NonNullList<Ingredient> inputs, ItemStack result, int ticks, int cue) {
        if (inputs.size()!=9 || inputs.stream().allMatch(Ingredient::isEmpty) || result.isEmpty()
                || result.hasTag() || result.getCount()>64 || ticks<40 || ticks>200
                || cue<400 || cue>4000)
            throw new IllegalArgumentException("Invalid precision assembly recipe: "+id);
        this.id=id; this.inputs=inputs; this.result=result.copy(); this.ticks=ticks; this.cue=cue;
    }
    public int ticks() { return ticks; }
    public int cue() { return cue; }
    @Override public boolean matches(Container items, Level level) {
        if(items.getContainerSize()<9)return false;
        for(int i=0;i<9;i++) {
            ItemStack item=items.getItem(i);
            if(inputs.get(i).isEmpty()) {
                if(!item.isEmpty())return false;
            } else if(item.isEmpty() || item.hasTag() || !inputs.get(i).test(item))return false;
        }
        return true;
    }
    /** Null signals a rejected container-return recipe, never a silently lost item. */
    @Nullable public ItemStack containerReturns(Container items) {
        ItemStack combined=ItemStack.EMPTY;
        for(int i=0;i<9;i++) {
            ItemStack input=items.getItem(i);
            if(input.isEmpty())continue;
            ItemStack returned=input.getCraftingRemainingItem();
            if(returned.isEmpty())continue;
            // A single output slot can safely return only vanilla, stateless containers.
            // Tools, batteries, custom NBT and mixed remainder types are intentionally refused.
            if(returned.hasTag() || returned.getCount()!=1
                    || !(returned.is(net.minecraft.world.item.Items.BUCKET)
                    || returned.is(net.minecraft.world.item.Items.GLASS_BOTTLE)))return null;
            if(combined.isEmpty())combined=returned.copy();
            else if(ItemStack.isSameItemSameTags(combined,returned)
                    && combined.getCount()<combined.getMaxStackSize())combined.grow(1);
            else return null;
        }
        return combined;
    }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width,int height) { return width>=3&&height>=3; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }
    @Override public NonNullList<Ingredient> getIngredients() { return inputs; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.ASSEMBLY_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.ASSEMBLY_RECIPE.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<PrecisionAssemblyRecipe> {
        @Override public PrecisionAssemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
            var array=GsonHelper.getAsJsonArray(json,"ingredients");
            if(array.size()!=9)throw new JsonParseException("Precision assembly requires exactly 9 inputs: "+id);
            NonNullList<Ingredient> ingredients=NonNullList.withSize(9,Ingredient.EMPTY);
            for(int i=0;i<9;i++)ingredients.set(i,array.get(i).isJsonNull()?Ingredient.EMPTY:Ingredient.fromJson(array.get(i)));
            ItemStack output=ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json,"result"));
            try {
                return new PrecisionAssemblyRecipe(id,ingredients,output,GsonHelper.getAsInt(json,"ticks"),GsonHelper.getAsInt(json,"cue"));
            }catch(IllegalArgumentException ex){throw new JsonParseException(ex.getMessage(),ex);}
        }
        @Override public @Nullable PrecisionAssemblyRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buf) {
            NonNullList<Ingredient> ingredients=NonNullList.withSize(9,Ingredient.EMPTY);
            for(int i=0;i<9;i++)ingredients.set(i,Ingredient.fromNetwork(buf));
            ItemStack result=buf.readItem();
            return new PrecisionAssemblyRecipe(id,ingredients,result,buf.readVarInt(),buf.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buf,PrecisionAssemblyRecipe recipe) {
            for(var ingredient:recipe.inputs)ingredient.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.ticks);
            buf.writeVarInt(recipe.cue);
        }
    }
}
