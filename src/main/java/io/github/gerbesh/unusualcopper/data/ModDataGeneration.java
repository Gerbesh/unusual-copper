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
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.COPPER_BATTERY.get()).pattern("CGC").pattern("RKR").pattern("CCC")
                    .define('C',Items.COPPER_INGOT).define('G',Items.GLASS).define('R',Items.REDSTONE).define('K',ModContent.CORE.get()).unlockedBy("has_core",has(ModContent.CORE.get())).save(out);
            Consumer<FinishedRecipe> batteryUpgrade=recipe->out.accept(new FinishedRecipe() {
                @Override public void serializeRecipeData(JsonObject json) { recipe.serializeRecipeData(json); }
                @Override public ResourceLocation getId() { return recipe.getId(); }
                @Override public RecipeSerializer<?> getType() { return ModContent.BATTERY_UPGRADE_SERIALIZER.get(); }
                @Override public JsonObject serializeAdvancement() { return recipe.serializeAdvancement(); }
                @Override public ResourceLocation getAdvancementId() { return recipe.getAdvancementId(); }
            });
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.REINFORCED_BATTERY.get()).pattern("CMC").pattern("RBR").pattern("CMC")
                    .define('C',Items.COPPER_BLOCK).define('M',Items.GLASS).define('R',Items.REDSTONE_BLOCK).define('B',ModContent.COPPER_BATTERY.get()).unlockedBy("has_battery",has(ModContent.COPPER_BATTERY.get())).save(batteryUpgrade);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.RESONANT_BATTERY.get()).pattern("CMC").pattern("RBR").pattern("CMC")
                    .define('C',Items.AMETHYST_BLOCK).define('M',Items.DIAMOND).define('R',Items.GOLD_BLOCK).define('B',ModContent.REINFORCED_BATTERY.get()).unlockedBy("has_battery",has(ModContent.REINFORCED_BATTERY.get())).save(batteryUpgrade);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.AETHERIC_BATTERY.get()).pattern("CMC").pattern("RBR").pattern("CMC")
                    .define('C',Items.OBSIDIAN).define('M',Items.NETHERITE_INGOT).define('R',Items.NETHER_STAR).define('B',ModContent.RESONANT_BATTERY.get()).unlockedBy("has_battery",has(ModContent.RESONANT_BATTERY.get())).save(batteryUpgrade);
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
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModContent.COPPER_PLATE.get())
                    .requires(Items.COPPER_INGOT).unlockedBy("has_copper",has(Items.COPPER_INGOT)).save(out,"unusualcopper:copper_plate_bootstrap");
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModContent.RESONANT_COIL.get())
                    .requires(ModContent.COPPER_PLATE.get()).requires(Items.COPPER_INGOT,2)
                    .requires(Items.REDSTONE).requires(Items.QUARTZ)
                    .unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out,"unusualcopper:resonant_coil_bootstrap");
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.RESONANT_COIL.get()).pattern(" W ").pattern("WRW").pattern(" W ")
                    .define('W',ModContent.COPPER_WIRE.get()).define('R',Items.REDSTONE)
                    .unlockedBy("has_wire",has(ModContent.COPPER_WIRE.get())).save(out,"unusualcopper:resonant_coil");
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.PLATE_DIE.get()).pattern("III").pattern("ISI").pattern("III")
                    .define('I',Items.IRON_INGOT).define('S',Items.SMOOTH_STONE)
                    .unlockedBy("has_iron",has(Items.IRON_INGOT)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.WIRE_DIE.get()).pattern("III").pattern("SPS").pattern("III")
                    .define('I',Items.IRON_INGOT).define('P',ModContent.COPPER_PLATE.get()).define('S',Items.SMOOTH_STONE)
                    .unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.PATTERN_CARD.get())
                    .pattern("PRP").pattern("CQC").pattern("PRP")
                    .define('P',Items.PAPER).define('R',Items.REDSTONE)
                    .define('C',ModContent.COPPER_PLATE.get()).define('Q',Items.QUARTZ)
                    .unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.FLUID_PIPE.get(),8)
                    .pattern("PPP").pattern("G G").pattern("PPP")
                    .define('P',ModContent.COPPER_PLATE.get()).define('G',Items.GLASS)
                    .unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.COPPER_FLUID_PUMP).get())
                    .pattern("GCG").pattern("PFP").pattern("IBI")
                    .define('G',Items.GLASS).define('C',ModContent.RESONANT_COIL.get())
                    .define('P',ModContent.COPPER_PLATE.get()).define('F',ModContent.FRAME.get())
                    .define('I',Items.IRON_INGOT).define('B',Items.BUCKET)
                    .unlockedBy("has_coil",has(ModContent.RESONANT_COIL.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.COPPER_FLUID_TANK).get())
                    .pattern("PGP").pattern("GCG").pattern("PGP")
                    .define('P',ModContent.COPPER_PLATE.get()).define('G',Items.GLASS)
                    .define('C',ModContent.RESONANT_COIL.get())
                    .unlockedBy("has_coil",has(ModContent.RESONANT_COIL.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.ARMOR_WORKBENCH).get())
                    .pattern("PCP").pattern("FIF").pattern("PBP")
                    .define('P',ModContent.COPPER_PLATE.get()).define('C',ModContent.RESONANT_COIL.get())
                    .define('F',ModContent.REINFORCED_FRAME.get()).define('I',ModContent.PRECISION_PROCESSOR.get())
                    .define('B',Items.IRON_BLOCK)
                    .unlockedBy("has_processor",has(ModContent.PRECISION_PROCESSOR.get())).save(out);
            for(int tier=1;tier<=3;tier++) {
                for(var type:new net.minecraft.world.item.ArmorItem.Type[]{
                        net.minecraft.world.item.ArmorItem.Type.HELMET,
                        net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
                        net.minecraft.world.item.ArmorItem.Type.LEGGINGS,
                        net.minecraft.world.item.ArmorItem.Type.BOOTS}) {
                    String[] pattern=switch(tier) {
                        case 1 -> switch(type) {
                            case HELMET -> new String[]{"PPP","P P"};
                            case CHESTPLATE -> new String[]{"P P","PFP","PPP"};
                            case LEGGINGS -> new String[]{"PPP","P P","P P"};
                            case BOOTS -> new String[]{"P P","P P"};
                        };
                        case 2 -> switch(type) {
                            case HELMET -> new String[]{"RCR","F F"};
                            case CHESTPLATE -> new String[]{"R R","FCF","RRR"};
                            case LEGGINGS -> new String[]{"RCR","F F","F F"};
                            case BOOTS -> new String[]{"R R","FCF"};
                        };
                        default -> switch(type) {
                            case HELMET -> new String[]{"NCN","P P"};
                            case CHESTPLATE -> new String[]{"N N","PCP","NFN"};
                            case LEGGINGS -> new String[]{"NCN","P P","P P"};
                            case BOOTS -> new String[]{"N N","PCP"};
                        };
                    };
                    var item=ModContent.PROGRAMMABLE_ARMOR.get("programmable_"+type.getName()+"_tier_"+tier).get();
                    var recipe=ShapedRecipeBuilder.shaped(RecipeCategory.MISC,item);
                    for(var row:pattern)recipe.pattern(row);
                    String symbols=String.join("",pattern);
                    if(symbols.indexOf('P')>=0)recipe.define('P',ModContent.COPPER_PLATE.get());
                    if(symbols.indexOf('F')>=0)recipe.define('F',ModContent.FRAME.get());
                    if(symbols.indexOf('R')>=0)recipe.define('R',ModContent.REINFORCED_FRAME.get());
                    if(symbols.indexOf('C')>=0)recipe.define('C',ModContent.RESONANT_CRYSTAL.get());
                    if(symbols.indexOf('N')>=0)recipe.define('N',Items.NETHERITE_INGOT);
                    recipe.unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out);
                }
            }
            for(var type:io.github.gerbesh.unusualcopper.armor.ArmorModuleType.values()) {
                var catalyst=switch(type){
                    case OPTIC_VISOR -> Items.AMETHYST_SHARD;
                    case AQUA_LUNGS -> Items.PRISMARINE_CRYSTALS;
                    case STRIDE_DRIVE -> Items.SUGAR;
                    case MINING_SERVO -> Items.DIAMOND;
                    case SHIELD_FIELD -> Items.IRON_BLOCK;
                    case FALL_DAMPER -> Items.PHANTOM_MEMBRANE;
                    case JUMP_DRIVE -> Items.RABBIT_FOOT;
                };
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModContent.ARMOR_MODULES.get(type).get())
                        .requires(ModContent.MODULE_BLANK_I.get()).requires(catalyst).requires(Items.REDSTONE)
                        .unlockedBy("has_module_blank",has(ModContent.MODULE_BLANK_I.get())).save(out);
            }
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.PRODUCTION_CONTROLLER).get())
                    .pattern("RCR").pattern("PEP").pattern("RFR")
                    .define('R',ModContent.RESONANT_COIL.get()).define('C',ModContent.RESONANT_CRYSTAL.get())
                    .define('P',ModContent.COPPER_PLATE.get()).define('E',ModContent.ETCHED_CIRCUIT.get())
                    .define('F',ModContent.REINFORCED_FRAME.get())
                    .unlockedBy("has_circuit",has(ModContent.ETCHED_CIRCUIT.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.PRECISION_ASSEMBLER).get())
                    .pattern("RCR").pattern("PEP").pattern("RFR")
                    .define('R',ModContent.RESONANT_COIL.get()).define('C',ModContent.RESONANT_CRYSTAL.get())
                    .define('P',ModContent.COPPER_PLATE.get()).define('E',ModContent.ETCHED_CIRCUIT.get())
                    .define('F',ModContent.REINFORCED_FRAME.get())
                    .unlockedBy("has_circuit",has(ModContent.ETCHED_CIRCUIT.get())).save(out);
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModContent.ETCHING_REAGENT.get())
                    .requires(Items.FERMENTED_SPIDER_EYE).requires(Items.BLAZE_POWDER).requires(Items.GUNPOWDER)
                    .requires(Items.GLASS_BOTTLE).unlockedBy("has_blaze",has(Items.BLAZE_POWDER)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.RESONANCE_CRYSTALLIZER).get())
                    .pattern("ACA").pattern("RFR").pattern("PQP")
                    .define('A',Items.AMETHYST_BLOCK).define('C',ModContent.RESONANT_COIL.get())
                    .define('R',Items.REDSTONE_BLOCK).define('F',ModContent.REINFORCED_FRAME.get())
                    .define('P',ModContent.COPPER_PLATE.get()).define('Q',Items.QUARTZ)
                    .unlockedBy("has_frame",has(ModContent.REINFORCED_FRAME.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.CIRCUIT_ETCHER).get())
                    .pattern("GCG").pattern("PFP").pattern("RBR")
                    .define('G',Items.GLASS).define('C',ModContent.RESONANT_COIL.get())
                    .define('P',ModContent.COPPER_PLATE.get()).define('F',ModContent.REINFORCED_FRAME.get())
                    .define('R',Items.REDSTONE).define('B',Items.BREWING_STAND)
                    .unlockedBy("has_frame",has(ModContent.REINFORCED_FRAME.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.VORTEX_PURIFIER).get())
                    .pattern("GCG").pattern("PFP").pattern("GQG")
                    .define('G',Items.GLASS).define('C',ModContent.RESONANT_COIL.get())
                    .define('P',ModContent.COPPER_PLATE.get()).define('F',ModContent.FRAME.get()).define('Q',Items.QUARTZ)
                    .unlockedBy("has_coil",has(ModContent.RESONANT_COIL.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.COPPER_PRESS).get())
                    .pattern("PCP").pattern("AFS").pattern("PRP")
                    .define('P',ModContent.COPPER_PLATE.get()).define('C',ModContent.RESONANT_COIL.get())
                    .define('A',Items.ANVIL).define('F',ModContent.FRAME.get()).define('S',Items.PISTON).define('R',Items.REDSTONE)
                    .unlockedBy("has_coil",has(ModContent.RESONANT_COIL.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.REINFORCED_FRAME.get()).pattern("PIP").pattern("OFO").pattern("PIP")
                    .define('P',ModContent.COPPER_PLATE.get()).define('I',Items.IRON_INGOT)
                    .define('O',Items.OBSIDIAN).define('F',ModContent.FRAME.get())
                    .unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.CIRCUIT_BLANK.get()).pattern("PRP").pattern("PQP").pattern("PRP")
                    .define('P',ModContent.COPPER_PLATE.get()).define('R',Items.REDSTONE).define('Q',Items.QUARTZ)
                    .unlockedBy("has_plate",has(ModContent.COPPER_PLATE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.RESONANCE_CRUSHER).get())
                    .pattern("IFI").pattern("RCR").pattern("IDI")
                    .define('I', Items.IRON_BLOCK).define('F', ModContent.FRAME.get())
                    .define('R', Items.REDSTONE).define('C', ModContent.CORE.get())
                    .define('D', Items.DIAMOND).unlockedBy("has_core", has(ModContent.CORE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.RESONANCE_SMELTER).get()).pattern("FQF").pattern("IPI").pattern("RFR")
                    .define('F', ModContent.FRAME.get()).define('Q', Items.QUARTZ).define('I', Items.IRON_INGOT).define('P', Items.FURNACE).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.COPPER_CELL).get()).pattern("FQF").pattern("QKQ").pattern("RRR")
                    .define('F', ModContent.FRAME.get()).define('Q', Items.QUARTZ).define('K', ModContent.CORE.get()).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.ENERGY_PIPE.get(), 8).pattern("CRC").pattern("CRC").pattern("CC ")
                    .define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE).unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.REINFORCED_ENERGY_PIPE.get(), 8).pattern("PPP").pattern("PIP").pattern("PPP")
                    .define('I', Items.IRON_INGOT).define('P', ModContent.ENERGY_PIPE.get()).unlockedBy("has_pipe", has(ModContent.ENERGY_PIPE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.CRYSTALLINE_ENERGY_PIPE.get(), 6).pattern("ADA").pattern("PPP").pattern("PPP")
                    .define('A', Items.AMETHYST_SHARD).define('D', Items.DIAMOND).define('P', ModContent.REINFORCED_ENERGY_PIPE.get()).unlockedBy("has_pipe", has(ModContent.REINFORCED_ENERGY_PIPE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.COPPER_CELL_II).get()).pattern("FRF").pattern("RDR").pattern("FRF")
                    .define('F', ModContent.FRAME.get()).define('R', Items.REDSTONE_BLOCK).define('D', Items.DIAMOND).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.COPPER_CELL_III).get()).pattern("FDF").pattern("DSD").pattern("FDF")
                    .define('F', ModContent.FRAME.get()).define('D', Items.DIAMOND_BLOCK).define('S', Items.NETHER_STAR).unlockedBy("has_star", has(Items.NETHER_STAR)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.ENERGY_DISTRIBUTOR).get()).pattern("FPF").pattern("RKR").pattern("FPF")
                    .define('F',ModContent.FRAME.get()).define('P',ModContent.REINFORCED_ENERGY_PIPE.get()).define('R',Items.REDSTONE).define('K',ModContent.CORE.get()).unlockedBy("has_pipe",has(ModContent.REINFORCED_ENERGY_PIPE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.ENERGY_MONITOR).get()).pattern("FGF").pattern("RCR").pattern("FFF")
                    .define('F',ModContent.FRAME.get()).define('G',Items.GLASS).define('R',Items.REDSTONE).define('C',Items.COMPARATOR).unlockedBy("has_core",has(ModContent.CORE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.ITEM_PIPE.get(), 8).pattern("CIC").pattern("CIC").pattern("CC ")
                    .define('C', Items.COPPER_INGOT).define('I', Items.IRON_NUGGET).unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModContent.FACE_SELECTOR.get()).pattern(" C").pattern("IR")
                    .define('C', Items.COPPER_INGOT).define('I', Items.IRON_INGOT).define('R', Items.REDSTONE).unlockedBy("has_copper", has(Items.COPPER_INGOT)).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModContent.AREA_WAND.get()).pattern(" Q").pattern("C ").pattern("S ")
                    .define('Q', Items.QUARTZ).define('C', Items.COPPER_INGOT).define('S', Items.STICK).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.AURA_CULTIVATOR).get()).pattern(" Q ").pattern("FEF").pattern(" R ")
                    .define('Q', Items.QUARTZ).define('F', ModContent.FRAME.get()).define('E', Items.EMERALD).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.MAGNETIC_COLLECTOR).get()).pattern("IRI").pattern("FHF").pattern(" Q ")
                    .define('I', Items.IRON_INGOT).define('R', Items.REDSTONE).define('F', ModContent.FRAME.get()).define('H', Items.HOPPER).define('Q', Items.QUARTZ).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.GROWTH_SHEPHERD).get()).pattern(" G ").pattern("FAF").pattern(" R ")
                    .define('G', Items.GLASS).define('F', ModContent.FRAME.get()).define('A', Items.GOLDEN_APPLE).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModContent.MACHINES.get(MachineKind.MOB_REAPER).get()).pattern("FOF").pattern("OEO").pattern("FRF")
                    .define('F', ModContent.FRAME.get()).define('O', Items.OBSIDIAN).define('E', Items.ENDER_PEARL).define('R', Items.REDSTONE).unlockedBy("has_essence", has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.ARBORIST).get()).pattern("FIF").pattern("RAR").pattern("FIF")
                    .define('F',ModContent.FRAME.get()).define('I',Items.IRON_INGOT).define('R',Items.REDSTONE).define('A',Items.DIAMOND_AXE).unlockedBy("has_essence",has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.COPPER_SENTINEL).get()).pattern("FFF").pattern("OQC").pattern("FFF")
                    .define('F',ModContent.FRAME.get()).define('Q',Items.QUARTZ).define('O',Items.OBSIDIAN).define('C',ModContent.SENTINEL_FOCUS.get()).unlockedBy("has_essence",has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.SENTINEL_FOCUS.get()).pattern("DQD").pattern("QEQ").pattern("DQD")
                    .define('D',Items.DIAMOND).define('Q',Items.QUARTZ).define('E',Items.ENDER_PEARL).unlockedBy("has_essence",has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.AETHERIC_BREWER).get()).pattern("FGF").pattern("GCG").pattern("FBF")
                    .define('F',ModContent.FRAME.get()).define('G',Items.GLASS).define('C',Items.CAULDRON).define('B',Items.BREWING_STAND).unlockedBy("has_essence",has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.ENCHANTMENT_CORE.get()).pattern("OLO").pattern("ATA").pattern("OLO")
                    .define('O',Items.OBSIDIAN).define('L',Items.LAPIS_LAZULI).define('A',Items.AMETHYST_SHARD).define('T',Items.ENCHANTING_TABLE).unlockedBy("has_essence",has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.SSN_BRIDGE.get()).pattern("FQF").pattern("RER").pattern("FQF")
                    .define('F',ModContent.FRAME.get()).define('Q',Items.QUARTZ).define('R',Items.REDSTONE).define('E',Items.ENDER_PEARL).unlockedBy("has_essence",has(ModContent.ESSENCE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.ENCHANTMENT_RESONATOR).get()).pattern("FFF").pattern("FCF").pattern("FFF")
                    .define('F',ModContent.FRAME.get()).define('C',ModContent.ENCHANTMENT_CORE.get()).unlockedBy("has_core",has(ModContent.ENCHANTMENT_CORE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.RESONANT_EXTRACTOR).get()).pattern("FAF").pattern("NCN").pattern("FPF")
                    .define('F',ModContent.FRAME.get()).define('A',Items.AMETHYST_BLOCK).define('N',Items.NETHERITE_INGOT).define('C',ModContent.CORE.get()).define('P',Items.DIAMOND_PICKAXE).unlockedBy("has_core",has(ModContent.CORE.get())).save(out);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModContent.MACHINES.get(MachineKind.REPAIR_CRYSTAL).get()).pattern("FAF").pattern("GNG").pattern("FCF")
                    .define('F',ModContent.FRAME.get()).define('A',Items.AMETHYST_SHARD).define('G',Items.GLASS).define('N',Items.ANVIL).define('C',ModContent.CORE.get()).unlockedBy("has_core",has(ModContent.CORE.get())).save(out);
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
