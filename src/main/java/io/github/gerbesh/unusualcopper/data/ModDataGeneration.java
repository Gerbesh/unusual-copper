package io.github.gerbesh.unusualcopper.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModDataGeneration {
    @SubscribeEvent public static void gather(GatherDataEvent event) {
        var output = event.getGenerator().getPackOutput();
        event.getGenerator().addProvider(event.includeServer(), new Recipes(output));
        event.getGenerator().addProvider(event.includeServer(), new BlockData(output));
    }
    private static final class Recipes extends RecipeProvider {
        Recipes(PackOutput output) { super(output); }
        @Override protected void buildRecipes(Consumer<FinishedRecipe> out) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.SOLAR_LENS.get()).pattern("IDI").pattern("DGD").pattern("IDI")
                    .define('I', Items.IRON_NUGGET).define('D', Items.DIAMOND).define('G', Items.GLASS).unlockedBy("has_diamond", has(Items.DIAMOND)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.FRAME.get()).pattern("CCC").pattern("CIC").pattern("CCC")
                    .define('C', Items.COPPER_INGOT).define('I', Items.IRON_NUGGET).unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.CORE.get()).pattern("CRC").pattern("RQR").pattern("CRC")
                    .define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE).define('Q', Items.QUARTZ).unlockedBy("has_frame", has(ModContent.FRAME.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.SOLAR_INFUSER).get()).pattern("GFG").pattern("GRG")
                    .define('G', Items.GLASS).define('F', ModContent.FRAME.get()).define('R', Items.REDSTONE).unlockedBy("has_frame", has(ModContent.FRAME.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.ESSENCE_SPLITTER).get()).pattern("FRF").pattern("IEI").pattern("RRR")
                    .define('F', ModContent.FRAME.get()).define('R', Items.REDSTONE).define('I', Items.IRON_INGOT).define('E', ModContent.ESSENCE.get()).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.RESONANCE_SMELTER).get()).pattern("FQF").pattern("IPI").pattern("RFR")
                    .define('F', ModContent.FRAME.get()).define('Q', Items.QUARTZ).define('I', Items.IRON_INGOT).define('P', Items.FURNACE).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.COPPER_CELL).get()).pattern("FQF").pattern("QKQ").pattern("RRR")
                    .define('F', ModContent.FRAME.get()).define('Q', Items.QUARTZ).define('K', ModContent.CORE.get()).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.ENERGY_PIPE.get(), 8).pattern("CRC").pattern("CRC").pattern("CC ")
                    .define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE).unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out);
            for (var item : new net.minecraft.world.item.Item[]{Items.COPPER_INGOT, Items.RAW_COPPER, Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE}) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                int multiplier = item == Items.COPPER_INGOT ? 2 : 1;
                out.accept(new FinishedRecipe() {
                    @Override public void serializeRecipeData(JsonObject json) { json.add("ingredient", Ingredient.of(item).toJson()); json.addProperty("essence_per_item", multiplier); }
                    @Override public ResourceLocation getId() { return new ResourceLocation(UnusualCopper.MOD_ID, "infuse_" + id.getPath()); }
                    @Override public RecipeSerializer<?> getType() { return ModContent.SOLAR_SERIALIZER.get(); }
                    @Override public JsonObject serializeAdvancement() { return null; }
                    @Override public ResourceLocation getAdvancementId() { return null; }
                });
            }
        }
    }
    private record BlockData(PackOutput output) implements DataProvider {
        @Override public CompletableFuture<?> run(CachedOutput cache) {
            var writes = new ArrayList<CompletableFuture<?>>(); var mineable = new JsonArray();
            for (var entry : ModContent.BLOCKS.getEntries()) {
                String id = entry.getId().toString(); mineable.add(id);
                JsonObject drop = new JsonObject(); drop.addProperty("type", "minecraft:item"); drop.addProperty("name", id);
                JsonObject condition = new JsonObject(); condition.addProperty("condition", "minecraft:survives_explosion");
                JsonArray entries = new JsonArray(); entries.add(drop); JsonArray conditions = new JsonArray(); conditions.add(condition);
                JsonObject pool = new JsonObject(); pool.addProperty("rolls", 1); pool.add("entries", entries); pool.add("conditions", conditions);
                JsonArray pools = new JsonArray(); pools.add(pool); JsonObject loot = new JsonObject(); loot.addProperty("type", "minecraft:block"); loot.add("pools", pools);
                writes.add(DataProvider.saveStable(cache, loot, output.getOutputFolder().resolve("data/unusualcopper/loot_tables/blocks/" + entry.getId().getPath() + ".json")));
            }
            JsonObject tag = new JsonObject(); tag.addProperty("replace", false); tag.add("values", mineable);
            writes.add(DataProvider.saveStable(cache, tag, output.getOutputFolder().resolve("data/minecraft/tags/blocks/mineable/pickaxe.json")));
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }
        @Override public String getName() { return "Unusual Copper block drops and mining tags"; }
    }
}
