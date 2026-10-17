package io.github.gerbesh.unusualcopper.registry;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.energy.EnergyPipeBlock;
import io.github.gerbesh.unusualcopper.energy.EnergyPipeBlockEntity;
import io.github.gerbesh.unusualcopper.item.SolarLensItem;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.recipe.SolarRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.EnumMap;

public final class ModContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UnusualCopper.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UnusualCopper.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, UnusualCopper.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, UnusualCopper.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE, UnusualCopper.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, UnusualCopper.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UnusualCopper.MOD_ID);
    public static final EnumMap<MachineKind, RegistryObject<Block>> MACHINES = new EnumMap<>(MachineKind.class);
    static {
        for (MachineKind kind : MachineKind.values()) {
            RegistryObject<Block> block = BLOCKS.register(kind.id, () -> new CopperMachineBlock(kind));
            MACHINES.put(kind, block); ITEMS.register(kind.id, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }
    public static final RegistryObject<Block> ENERGY_PIPE = BLOCKS.register("copper_energy_pipe", EnergyPipeBlock::new);
    public static final RegistryObject<Item> ENERGY_PIPE_ITEM = ITEMS.register("copper_energy_pipe", () -> new BlockItem(ENERGY_PIPE.get(), new Item.Properties()));
    public static final RegistryObject<Block> REINFORCED_ENERGY_PIPE = BLOCKS.register("reinforced_energy_pipe", () -> new EnergyPipeBlock(1));
    public static final RegistryObject<Item> REINFORCED_ENERGY_PIPE_ITEM = ITEMS.register("reinforced_energy_pipe", () -> new BlockItem(REINFORCED_ENERGY_PIPE.get(), new Item.Properties()));
    public static final RegistryObject<Block> CRYSTALLINE_ENERGY_PIPE = BLOCKS.register("crystalline_energy_pipe", () -> new EnergyPipeBlock(2));
    public static final RegistryObject<Item> CRYSTALLINE_ENERGY_PIPE_ITEM = ITEMS.register("crystalline_energy_pipe", () -> new BlockItem(CRYSTALLINE_ENERGY_PIPE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ESSENCE = ITEMS.register("saturated_copper_essence", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SOLAR_LENS = ITEMS.register("diamond_solar_lens", SolarLensItem::new);
    public static final RegistryObject<Item> SENTINEL_FOCUS = ITEMS.register("sentinel_range_focus", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FRAME = ITEMS.register("copper_frame", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Block> SSN_BRIDGE=BLOCKS.register("ssn_bridge",io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlock::new);
    public static final RegistryObject<Item> SSN_BRIDGE_ITEM=ITEMS.register("ssn_bridge",()->new BlockItem(SSN_BRIDGE.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlockEntity>> SSN_BRIDGE_ENTITY=ENTITIES.register("ssn_bridge",()->BlockEntityType.Builder.of(io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlockEntity::new,SSN_BRIDGE.get()).build(null));
    public static final RegistryObject<Item> CORE = ITEMS.register("copper_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COPPER_BATTERY=ITEMS.register("copper_battery",()->new io.github.gerbesh.unusualcopper.item.CopperBatteryItem(0));
    public static final RegistryObject<Item> REINFORCED_BATTERY=ITEMS.register("reinforced_battery",()->new io.github.gerbesh.unusualcopper.item.CopperBatteryItem(1));
    public static final RegistryObject<Item> RESONANT_BATTERY=ITEMS.register("resonant_battery",()->new io.github.gerbesh.unusualcopper.item.CopperBatteryItem(2));
    public static final RegistryObject<Item> AETHERIC_BATTERY=ITEMS.register("aetheric_battery",()->new io.github.gerbesh.unusualcopper.item.CopperBatteryItem(3));
    public static final RegistryObject<RecipeSerializer<io.github.gerbesh.unusualcopper.recipe.BatteryUpgradeRecipe>> BATTERY_UPGRADE_SERIALIZER=SERIALIZERS.register("battery_upgrade",io.github.gerbesh.unusualcopper.recipe.BatteryUpgradeRecipe.Serializer::new);
    public static final RegistryObject<Item> COPPER_GRIT = ITEMS.register("copper_grit", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> IRON_GRIT = ITEMS.register("iron_grit", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD_GRIT = ITEMS.register("gold_grit", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COPPER_DUST = ITEMS.register("washed_copper_dust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> IRON_DUST = ITEMS.register("washed_iron_dust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD_DUST = ITEMS.register("washed_gold_dust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INERT_TAILINGS = ITEMS.register("inert_tailings", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COPPER_PLATE = ITEMS.register("copper_plate", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> COPPER_WIRE = ITEMS.register("copper_wire_spool", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLATE_DIE = ITEMS.register("plate_die", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIRE_DIE = ITEMS.register("wire_die", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RESONANT_COIL = ITEMS.register("resonant_coil", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> REINFORCED_FRAME = ITEMS.register("reinforced_frame", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CIRCUIT_BLANK = ITEMS.register("circuit_blank", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PATTERN_CARD = ITEMS.register("pattern_card", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PRECISION_PROCESSOR = ITEMS.register("precision_processor", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MODULE_BLANK_I = ITEMS.register("module_blank_i", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> RESONANT_CRYSTAL = ITEMS.register("resonant_crystal", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ETCHING_REAGENT = ITEMS.register("etching_reagent", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ETCHED_CIRCUIT = ITEMS.register("etched_circuit", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ENCHANTMENT_CORE = ITEMS.register("resonant_enchantment_core",()->new Item(new Item.Properties()));
    public static final RegistryObject<Block> FLUID_PIPE = BLOCKS.register("copper_fluid_pipe", io.github.gerbesh.unusualcopper.fluid.FluidPipeBlock::new);
    public static final RegistryObject<Item> FLUID_PIPE_ITEM = ITEMS.register("copper_fluid_pipe",()->new BlockItem(FLUID_PIPE.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<io.github.gerbesh.unusualcopper.fluid.FluidPipeBlockEntity>> FLUID_PIPE_ENTITY =
            ENTITIES.register("copper_fluid_pipe",()->BlockEntityType.Builder.of(io.github.gerbesh.unusualcopper.fluid.FluidPipeBlockEntity::new,FLUID_PIPE.get()).build(null));
    public static final RegistryObject<Block> ITEM_PIPE = BLOCKS.register("copper_item_pipe", io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlock::new);
    public static final RegistryObject<Item> ITEM_PIPE_ITEM = ITEMS.register("copper_item_pipe", () -> new BlockItem(ITEM_PIPE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FACE_SELECTOR = ITEMS.register("face_selector", io.github.gerbesh.unusualcopper.item.FaceSelectorItem::new);
    public static final RegistryObject<Item> AREA_WAND = ITEMS.register("area_wand", io.github.gerbesh.unusualcopper.item.AreaWandItem::new);
    public static final RegistryObject<Item> CARGO_BUNDLE = ITEMS.register("cargo_bundle", io.github.gerbesh.unusualcopper.item.CargoBundleItem::new);
    public static final RegistryObject<BlockEntityType<CopperMachineBlockEntity>> MACHINE_ENTITY = ENTITIES.register("copper_machine", () -> BlockEntityType.Builder.of(CopperMachineBlockEntity::new, MACHINES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<EnergyPipeBlockEntity>> PIPE_ENTITY = ENTITIES.register("energy_pipe", () -> BlockEntityType.Builder.of(EnergyPipeBlockEntity::new, ENERGY_PIPE.get(), REINFORCED_ENERGY_PIPE.get(), CRYSTALLINE_ENERGY_PIPE.get()).build(null));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.MiningMenu>> MINING_MENU=MENUS.register("mining",()->IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.MiningMenu::new));
    public static final RegistryObject<MenuType<CopperMachineMenu>> MACHINE_MENU = MENUS.register("copper_machine", () -> IForgeMenuType.create(CopperMachineMenu::new));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.EnergyControlMenu>> ENERGY_MENU=MENUS.register("energy_control",()->IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.EnergyControlMenu::new));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.ReaperMenu>> REAPER_MENU = MENUS.register("reaper_settings", () -> IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.ReaperMenu::new));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.BrewerMenu>> BREWER_MENU = MENUS.register("brewer_settings",()->IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.BrewerMenu::new));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.EnchanterMenu>> ENCHANTER_MENU = MENUS.register("enchantment_resonator",()->IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.EnchanterMenu::new));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.RepairMenu>> REPAIR_MENU=MENUS.register("repair_crystal",()->IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.RepairMenu::new));
    public static final RegistryObject<BlockEntityType<io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity>> ITEM_PIPE_ENTITY = ENTITIES.register("item_pipe", () -> BlockEntityType.Builder.of(io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity::new, ITEM_PIPE.get()).build(null));
    public static final RegistryObject<MenuType<io.github.gerbesh.unusualcopper.menu.ItemPipeMenu>> PIPE_MENU = MENUS.register("item_pipe", () -> IForgeMenuType.create(io.github.gerbesh.unusualcopper.menu.ItemPipeMenu::new));
    public static final RegistryObject<RecipeType<io.github.gerbesh.unusualcopper.recipe.PrecisionAssemblyRecipe>> ASSEMBLY_RECIPE =
            RECIPES.register("precision_assembly", () -> new RecipeType<>() {
                @Override public String toString() { return "unusualcopper:precision_assembly"; }
            });
    public static final RegistryObject<RecipeSerializer<io.github.gerbesh.unusualcopper.recipe.PrecisionAssemblyRecipe>> ASSEMBLY_SERIALIZER =
            SERIALIZERS.register("precision_assembly", io.github.gerbesh.unusualcopper.recipe.PrecisionAssemblyRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe>> INDUSTRIAL_RECIPE =
            RECIPES.register("industrial_processing", () -> new RecipeType<>() {
                @Override public String toString() { return "unusualcopper:industrial_processing"; }
            });
    public static final RegistryObject<RecipeSerializer<io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe>> INDUSTRIAL_SERIALIZER =
            SERIALIZERS.register("industrial_processing", io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<SolarRecipe>> SOLAR_RECIPE = RECIPES.register("solar_infusion", () -> new RecipeType<>() { @Override public String toString() { return "unusualcopper:solar_infusion"; } });
    public static final RegistryObject<RecipeSerializer<SolarRecipe>> SOLAR_SERIALIZER = SERIALIZERS.register("solar_infusion", SolarRecipe.Serializer::new);
    public static final TagKey<Item> SMELTABLE_ORES = TagKey.create(Registries.ITEM, new ResourceLocation(UnusualCopper.MOD_ID, "smeltable_ores"));
    public static final TagKey<Item> SMELTING_BLACKLIST = TagKey.create(Registries.ITEM, new ResourceLocation(UnusualCopper.MOD_ID, "smelting_blacklist"));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("machines", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.unusualcopper"))
            .icon(() -> new ItemStack(MACHINES.get(MachineKind.SOLAR_INFUSER).get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get()))).build());
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); RECIPES.register(bus); SERIALIZERS.register(bus); TABS.register(bus); }
}
