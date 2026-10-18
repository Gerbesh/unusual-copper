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
    private final Ingredient additive;
    private final int inputCount;
    private final ItemStack result;
    private final int ticks;
    private final int cost;
    private final int water;
    private final ItemStack secondary;
    private final int fluidOutput;

    public IndustrialRecipe(ResourceLocation id, MachineKind machine, Ingredient ingredient, Ingredient mold, Ingredient additive,
                            int inputCount, ItemStack result, int ticks, int cost, int water, ItemStack secondary) {
        this(id,machine,ingredient,mold,additive,inputCount,result,ticks,cost,water,secondary,0);
    }
    public IndustrialRecipe(ResourceLocation id,MachineKind machine,Ingredient ingredient,Ingredient mold,Ingredient additive,
                            int inputCount,ItemStack result,int ticks,int cost,int water,ItemStack secondary,int fluidOutput) {
        if(fluidOutput!=0&&(fluidOutput!=1000||machine!=MachineKind.VORTEX_PURIFIER||result.getCount()!=1||!result.is(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.BUCKET.get())||!secondary.isEmpty()||water!=1000||!mold.isEmpty()||!additive.isEmpty()))throw new IllegalArgumentException("Invalid coolant output");
        if ((machine != MachineKind.RESONANCE_CRUSHER && machine != MachineKind.COPPER_PRESS && machine != MachineKind.VORTEX_PURIFIER && machine != MachineKind.RESONANCE_CRYSTALLIZER && machine != MachineKind.CIRCUIT_ETCHER) || inputCount < 1 || inputCount > 64
                || result.isEmpty() || result.getCount() > 64 || ticks < 1 || ticks > 12000
                || cost < 0 || cost > 1_000_000 || water < 0 || water > 4000 || ingredient.isEmpty()
                || (machine != MachineKind.VORTEX_PURIFIER && !secondary.isEmpty())
                || (machine != MachineKind.VORTEX_PURIFIER && machine != MachineKind.CIRCUIT_ETCHER && water>0)
                || (machine == MachineKind.CIRCUIT_ETCHER && (water==0 || mold.isEmpty() || !additive.isEmpty()))
                || (machine == MachineKind.RESONANCE_CRYSTALLIZER && (mold.isEmpty() || additive.isEmpty()))
                || (machine != MachineKind.RESONANCE_CRYSTALLIZER && !additive.isEmpty())
                || (!secondary.isEmpty() && secondary.getCount()>64)) {
            throw new IllegalArgumentException("Invalid industrial processing recipe: " + id);
        }
        this.id=id; this.machine=machine; this.ingredient=ingredient; this.mold=mold; this.additive=additive;
        this.inputCount=inputCount; this.result=result.copy(); this.ticks=ticks; this.cost=cost;
        this.water=water; this.secondary=secondary.copy();this.fluidOutput=fluidOutput;
    }
    public int fluidOutput(){return fluidOutput;}
    public MachineKind machine() { return machine; }
    public int inputCount() { return inputCount; }
    public int ticks() { return ticks; }
    public int cost() { return cost; }
    public int waterMb() { return water; }
    public ItemStack secondary() { return secondary.copy(); }
    @Override public boolean matches(Container items, Level level) {
        ItemStack in=items.getItem(0);
        ItemStack form=items.getContainerSize()>1 ? items.getItem(1) : ItemStack.EMPTY;
        ItemStack third=items.getContainerSize()>2 ? items.getItem(2) : ItemStack.EMPTY;
        return !in.isEmpty() && !in.hasTag() && in.getCount() >= inputCount && ingredient.test(in)
                && (mold.isEmpty() || !form.isEmpty() && !form.hasTag() && mold.test(form))
                && (additive.isEmpty() || !third.isEmpty() && !third.hasTag() && additive.test(third));
    }
    @Override public ItemStack assemble(Container items, RegistryAccess registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width>=1 && height>=1; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list=NonNullList.create();
        list.add(ingredient);
        if (!mold.isEmpty()) list.add(mold);
        if (!additive.isEmpty()) list.add(additive);
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
            Ingredient additive=json.has("additive") ? Ingredient.fromJson(json.get("additive")) : Ingredient.EMPTY;
            ItemStack result=ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json,"result"));
            ItemStack byproduct=json.has("secondary") ? ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json,"secondary")) : ItemStack.EMPTY;
            try {
                return new IndustrialRecipe(id,machine,input,mold,additive,GsonHelper.getAsInt(json,"input_count",1),
                        result,GsonHelper.getAsInt(json,"ticks"),GsonHelper.getAsInt(json,"cue"),
                        GsonHelper.getAsInt(json,"water_mb",0),byproduct,GsonHelper.getAsInt(json,"coolant_mb",0));
            } catch (IllegalArgumentException ex) { throw new JsonParseException(ex.getMessage(),ex); }
        }
        @Override public @Nullable IndustrialRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var machine=buf.readEnum(MachineKind.class);
            Ingredient ingredient=Ingredient.fromNetwork(buf);
            Ingredient mold=Ingredient.fromNetwork(buf);
            Ingredient additive=Ingredient.fromNetwork(buf);
            int count=buf.readVarInt();
            ItemStack result=buf.readItem();
            int ticks=buf.readVarInt(), cost=buf.readVarInt();
            int water=buf.readVarInt();
            ItemStack secondary=buf.readItem();
            return new IndustrialRecipe(id,machine,ingredient,mold,additive,count,result,ticks,cost,water,secondary,buf.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buf, IndustrialRecipe recipe) {
            buf.writeEnum(recipe.machine);
            recipe.ingredient.toNetwork(buf);
            recipe.mold.toNetwork(buf);
            recipe.additive.toNetwork(buf);
            buf.writeVarInt(recipe.inputCount);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.ticks);
            buf.writeVarInt(recipe.cost);
            buf.writeVarInt(recipe.water);
            buf.writeItem(recipe.secondary);
            buf.writeVarInt(recipe.fluidOutput);
        }
    }
}
