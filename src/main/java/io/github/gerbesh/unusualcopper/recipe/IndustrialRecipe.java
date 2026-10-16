package io.github.gerbesh.unusualcopper.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

/** Datapack-controlled, server-side industrial recipe. Never accepts NBT-bearing inputs. */
public final class IndustrialRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final MachineKind machine;
    private final Ingredient ingredient;
    private final Ingredient mold;
    private final int inputCount;
    private final ItemStack result;
    private final int ticks;
    private final int cost;

    public IndustrialRecipe(ResourceLocation id, MachineKind machine, Ingredient ingredient, Ingredient mold,
                            int inputCount, ItemStack result, int ticks, int cost) {
        if (machine != MachineKind.RESONANCE_CRUSHER || inputCount < 1 || inputCount > 64
                || result.isEmpty() || result.getCount() > 64 || ticks < 1 || ticks > 12000
                || cost < 0 || cost > 1_000_000 || ingredient.isEmpty()) {
            throw new IllegalArgumentException("Invalid industrial processing recipe: " + id);
        }
        this.id=id; this.machine=machine; this.ingredient=ingredient; this.mold=mold;
        this.inputCount=inputCount; this.result=result.copy(); this.ticks=ticks; this.cost=cost;
    }
    public MachineKind machine() { return machine; }
    public int inputCount() { return inputCount; }
    public int ticks() { return ticks; }
    public int cost() { return cost; }
    @Override public boolean matches(Container items, Level level) {
        ItemStack in=items.getItem(0);
        ItemStack form=items.getContainerSize()>1 ? items.getItem(1) : ItemStack.EMPTY;
        return !in.isEmpty() && !in.hasTag() && in.getCount() >= inputCount && ingredient.test(in)
                && (mold.isEmpty() || !form.isEmpty() && !form.hasTag() && mold.test(form));
    }
    @Override public ItemStack assemble(Container items, RegistryAccess registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width>=1 && height>=1; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list=NonNullList.create();
        list.add(ingredient);
        if (!mold.isEmpty()) list.add(mold);
        return list;
    }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.INDUSTRIAL_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.INDUSTRIAL_RECIPE.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<IndustrialRecipe> {
        @Override public IndustrialRecipe fromJson(ResourceLocation id, JsonObject json) {
            String name=GsonHelper.getAsString(json,"machine");
            MachineKind machine=null;
            for (var kind : MachineKind.values()) if (kind.id.equals(name)) { machine=kind; break; }
            if (machine==null) throw new JsonParseException("Unknown industrial machine: " + name);
            Ingredient input=Ingredient.fromJson(GsonHelper.getAsJsonObject(json,"ingredient"));
            Ingredient mold=json.has("mold") ? Ingredient.fromJson(json.get("mold")) : Ingredient.EMPTY;
            ItemStack result=ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json,"result"));
            try {
                return new IndustrialRecipe(id,machine,input,mold,GsonHelper.getAsInt(json,"input_count",1),
                        result,GsonHelper.getAsInt(json,"ticks"),GsonHelper.getAsInt(json,"cue"));
            } catch (IllegalArgumentException ex) { throw new JsonParseException(ex.getMessage(),ex); }
        }
        @Override public @Nullable IndustrialRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var machine=buf.readEnum(MachineKind.class);
            Ingredient ingredient=Ingredient.fromNetwork(buf);
            Ingredient mold=Ingredient.fromNetwork(buf);
            int count=buf.readVarInt();
            ItemStack result=buf.readItem();
            int ticks=buf.readVarInt(), cost=buf.readVarInt();
            return new IndustrialRecipe(id,machine,ingredient,mold,count,result,ticks,cost);
        }
        @Override public void toNetwork(FriendlyByteBuf buf, IndustrialRecipe recipe) {
            buf.writeEnum(recipe.machine);
            recipe.ingredient.toNetwork(buf);
            recipe.mold.toNetwork(buf);
            buf.writeVarInt(recipe.inputCount);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.ticks);
            buf.writeVarInt(recipe.cost);
        }
    }
}
