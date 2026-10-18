package io.github.gerbesh.unusualcopper.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue SOLAR_TICKS;
    private static final ForgeConfigSpec.IntValue ESSENCE_ENERGY;
    private static final ForgeConfigSpec.IntValue SPLITTER_RATE;
    private static final ForgeConfigSpec.IntValue SMELT_COST;
    private static final ForgeConfigSpec.IntValue SMELT_DIVISOR;
    private static final ForgeConfigSpec.IntValue PIPE_RATE;
    private static final ForgeConfigSpec.IntValue REINFORCED_PIPE_RATE, CRYSTALLINE_PIPE_RATE;
    private static final ForgeConfigSpec.IntValue NETWORK_LIMIT;
    private static final ForgeConfigSpec.IntValue TRANSFER_BUDGET;
    private static final ForgeConfigSpec.IntValue ITEM_RATE, ITEM_INTERVAL, ITEM_BUDGET, ITEM_REBUILD;
    private static final ForgeConfigSpec.IntValue CULTIVATOR_COST, CULTIVATOR_HARVEST_COST, CULTIVATOR_INTERVAL, CULTIVATOR_CHECKS, AREA_BUDGET;
    private static final ForgeConfigSpec.IntValue COLLECTOR_BASE_COST, COLLECTOR_DISTANCE_COST, COLLECTOR_CANDIDATES, ENTITY_BUDGET;
    private static final ForgeConfigSpec.IntValue SHEPHERD_COST, SHEPHERD_INTERVAL, SHEPHERD_ADVANCE, SHEPHERD_CANDIDATES;
    private static final ForgeConfigSpec.IntValue REAPER_COST, REAPER_INTERVAL, REAPER_CANDIDATES;
    private static final ForgeConfigSpec.IntValue ARBORIST_COST, ARBORIST_PLANT, ARBORIST_NODES, ARBORIST_LOGS, ARBORIST_TREE;
    private static final ForgeConfigSpec.BooleanValue ARBORIST_BUILDINGS;
    private static final ForgeConfigSpec.IntValue SENTINEL_COST,SENTINEL_UPKEEP,SENTINEL_RANGE,SENTINEL_FOCUSED_RANGE,SENTINEL_HEIGHT,SENTINEL_INTERVAL,SENTINEL_SCAN,SENTINEL_CHUNK,SENTINEL_CANDIDATES,SENTINEL_BUDGET;
    private static final ForgeConfigSpec.DoubleValue SENTINEL_DAMAGE;
    private static final ForgeConfigSpec.BooleanValue SENTINEL_LOS;
    private static final ForgeConfigSpec.IntValue BREWER_COST,BREWER_EXTRA,BREWER_INTERVAL,BREWER_BUDGET;
    private static final ForgeConfigSpec.IntValue ENCHANT_LEVEL,ENCHANT_XP_BASE,ENCHANT_CUE_BASE,ENCHANT_CUE_MIN;
    private static final ForgeConfigSpec.IntValue MINING_COST;
    private static final ForgeConfigSpec.IntValue REPAIR_CUE,REPAIR_PER_XP;
    private static final ForgeConfigSpec.DoubleValue ENCHANT_XP_MULT,ENCHANT_ADDITIONAL;
    private static final ForgeConfigSpec.DoubleValue[] ENCHANT_RARITY=new ForgeConfigSpec.DoubleValue[4];
    private static final ForgeConfigSpec.BooleanValue ENCHANT_TREASURE,ENCHANT_CURSES,ENCHANT_AUTOMATIC;
    private static final ForgeConfigSpec.ConfigValue<java.util.List<String>> ENCHANT_ITEMS,ENCHANT_ENCHANTMENTS;
    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("copper_economy");
        SOLAR_TICKS = builder.comment("Clear-day ticks to infuse a full stack of 64 copper inputs.").defineInRange("solar_batch_ticks", 800, 64, 96_000);
        ESSENCE_ENERGY = builder.comment("CuE produced by consuming one essence item.").defineInRange("cue_per_essence", 500, 1, 50_000);
        SPLITTER_RATE = builder.defineInRange("splitter_items_per_tick", 4, 1, 64);
        SMELT_COST = builder.comment("CuE cost per smelting operation. An active job retains its quoted cost.").defineInRange("smelting_cue_cost", 240, 1, 24_000);
        SMELT_DIVISOR = builder.comment("Divide the original recipe duration by this number, rounding up.").defineInRange("smelting_speed_divisor", 10, 1, 100);
        builder.pop().push("network");
        PIPE_RATE = builder.comment("Maximum total CuE through one basic pipe or cell I per network tick.").defineInRange("basic_transfer_rate", 256, 1, 4096);
        REINFORCED_PIPE_RATE = builder.comment("Maximum total CuE per reinforced pipe or cell II port per tick.").defineInRange("reinforced_transfer_rate", 1024, 1, 16_384);
        CRYSTALLINE_PIPE_RATE = builder.comment("Maximum total CuE per crystalline pipe, cell III or non-cell machine per network tick.").defineInRange("crystalline_transfer_rate", 4096, 1, 65_536);
        NETWORK_LIMIT = builder.comment("Oversized connected components stop transferring until split or the limit is raised.").defineInRange("maximum_component_nodes", 1024, 16, 16_384);
        TRANSFER_BUDGET = builder.comment("Maximum source/target transfer attempts per dimension per tick.").defineInRange("transfer_attempts_per_tick", 128, 1, 4096);
        builder.pop().push("item_transport");
        ITEM_RATE = builder.comment("Maximum items through each basic item pipe per transport interval.").defineInRange("items_per_interval", 8, 1, 64);
        ITEM_INTERVAL = builder.defineInRange("interval_ticks", 5, 1, 100);
        ITEM_BUDGET = builder.defineInRange("attempts_per_dimension", 64, 1, 4096);
        ITEM_REBUILD = builder.comment("Graph nodes visited per tick while rebuilding item networks.").defineInRange("rebuild_nodes_per_tick", 128, 8, 4096);
        builder.pop().push("area_machines");
        CULTIVATOR_COST = builder.defineInRange("cultivator_cue_per_growth", 140, 1, 50_000);
        CULTIVATOR_HARVEST_COST = builder.defineInRange("cultivator_cue_per_harvest",140,1,50_000);
        CULTIVATOR_INTERVAL = builder.comment("Cooldown after a complete cultivation sweep, or before retrying a blocked sweep.").defineInRange("cultivator_interval_ticks", 20, 20, 200);
        CULTIVATOR_CHECKS = builder.comment("Coordinates per scheduled tick of an outward sweep; also bounded by the shared dimension budget. Legacy key retained.").defineInRange("cultivator_checks_per_wave", 8, 1, 8);
        AREA_BUDGET = builder.comment("Global coordinate-check budget per dimension and tick, shared by queued machines.").defineInRange("checks_per_dimension_tick", 128, 8, 4096);
        COLLECTOR_BASE_COST = builder.defineInRange("collector_stack_base_cue", 30, 1, 1000);
        COLLECTOR_DISTANCE_COST = builder.comment("Additional CuE at distance 32; linear and rounded upward.").defineInRange("collector_distance_cue", 70, 0, 1000);
        COLLECTOR_CANDIDATES = builder.defineInRange("collector_candidates_per_wave", 16, 1, 64);
        ENTITY_BUDGET = builder.defineInRange("entity_candidates_per_dimension_tick", 256, 64, 4096);
        SHEPHERD_COST = builder.defineInRange("shepherd_cue_per_animal", 400, 1, 50_000);
        SHEPHERD_INTERVAL = builder.defineInRange("shepherd_interval_ticks", 200, 200, 24_000);
        SHEPHERD_ADVANCE = builder.defineInRange("shepherd_age_ticks", 2400, 1, 24_000);
        SHEPHERD_CANDIDATES = builder.defineInRange("shepherd_candidates_per_wave", 16, 1, 64);
        REAPER_COST = builder.defineInRange("reaper_cue_per_kill", 1500, 1, 50_000);
        REAPER_INTERVAL = builder.defineInRange("reaper_interval_ticks", 20, 20, 200);
        REAPER_CANDIDATES = builder.defineInRange("reaper_candidates_per_step", 16, 2, 64);
        ARBORIST_COST=builder.comment("An active forestry job retains its quoted price.").defineInRange("arborist_cue_per_log",250,1,1000);
        ARBORIST_PLANT=builder.defineInRange("arborist_cue_per_sapling",120,1,1000);
        ARBORIST_NODES=builder.defineInRange("arborist_nodes_per_step",32,1,32);
        ARBORIST_LOGS=builder.defineInRange("arborist_logs_per_step",4,1,4);
        ARBORIST_TREE=builder.comment("Includes both logs and naturally generated leaves.").defineInRange("arborist_maximum_tree_blocks",256,16,256);
        ARBORIST_BUILDINGS=builder.define("arborist_reject_construction",true);
        builder.pop().push("sentinel");
        SENTINEL_COST=builder.defineInRange("cue_per_shot",2500,1,100000);
        SENTINEL_UPKEEP=builder.defineInRange("cue_per_active_tick",1,0,1000);
        SENTINEL_DAMAGE=builder.defineInRange("damage_hp",10.0,0.1,1000.0);
        SENTINEL_RANGE=builder.defineInRange("base_horizontal_range",24,8,64);
        SENTINEL_FOCUSED_RANGE=builder.defineInRange("focused_horizontal_range",64,8,64);
        SENTINEL_HEIGHT=builder.defineInRange("vertical_range",32,4,32);
        SENTINEL_INTERVAL=builder.defineInRange("shot_interval_ticks",20,20,1200);
        SENTINEL_SCAN=builder.defineInRange("scan_interval_ticks",10,10,200);
        SENTINEL_CHUNK=builder.defineInRange("machines_per_chunk",2,1,16);
        SENTINEL_CANDIDATES=builder.defineInRange("candidates_per_scan",32,1,64);
        SENTINEL_BUDGET=builder.defineInRange("candidates_per_dimension_tick",256,64,4096);
        SENTINEL_LOS=builder.define("require_line_of_sight",true);
        builder.pop().push("brewer");
        BREWER_COST=builder.defineInRange("cue_per_bottle",5000,1,25000);
        BREWER_EXTRA=builder.comment("Extra price per bottle for every recipe stage beyond two.").defineInRange("cue_per_extra_stage",1000,0,5000);
        BREWER_INTERVAL=builder.defineInRange("stage_interval_ticks",20,20,1200);
        BREWER_BUDGET=builder.defineInRange("machines_per_dimension_tick",8,1,64);
        builder.pop().push("enchanter");
        ENCHANT_LEVEL=builder.defineInRange("maximum_level",32767,1,32767);
        ENCHANT_XP_BASE=builder.defineInRange("xp_base",25,1,1000);
        ENCHANT_XP_MULT=builder.defineInRange("xp_multiplier",1.5,.1,10.0);
        ENCHANT_CUE_BASE=builder.defineInRange("cue_base",25000,1000,100000);
        ENCHANT_CUE_MIN=builder.defineInRange("cue_minimum",80000,1,2000000);
        ENCHANT_ADDITIONAL=builder.defineInRange("additional_enchantment_multiplier",.25,0,10.0);
        for(int i=0;i<4;i++)ENCHANT_RARITY[i]=builder.defineInRange("rarity_"+new String[]{"common","uncommon","rare","very_rare"}[i],new double[]{1,1.5,2,2.5}[i],.1,10.0);
        ENCHANT_AUTOMATIC=builder.comment("Use registered items/enchantments from every mod. False restores the explicit legacy mod ID allowlists.").define("automatic_mod_compatibility",true);
        ENCHANT_TREASURE=builder.define("allow_treasure",true);ENCHANT_CURSES=builder.define("allow_curses",true);
        java.util.function.Predicate<Object> ids=value->value instanceof java.util.List<?> list&&list.size()<=128&&list.stream().allMatch(raw->raw instanceof String s&&net.minecraft.resources.ResourceLocation.tryParse(s)!=null);
        ENCHANT_ITEMS=builder.define("allowed_mod_items",java.util.List.<String>of(),ids);
        ENCHANT_ENCHANTMENTS=builder.define("allowed_mod_enchantments",java.util.List.<String>of(),ids);
        builder.pop().push("repair");
        REPAIR_CUE=builder.comment("CuE per restored durability point. No copper items are consumed.").defineInRange("cue_per_durability",100,1,10000);
        REPAIR_PER_XP=builder.comment("Restored durability per player XP point; cost rounds up per confirmed repair.").defineInRange("durability_per_xp",4,1,128);
        builder.pop().push("mining");MINING_COST=builder.comment("Legacy base CuE per ore before the global 0.25 mining discount; footprint, depth and hardness multiply this cost.").defineInRange("ore_base_cue",1200,1,100000);builder.pop(); SPEC = builder.build();
    }
    private static int value(ForgeConfigSpec.IntValue entry) { return SPEC.isLoaded() ? entry.get() : entry.getDefault(); }
    public static int solarTicks() { return value(SOLAR_TICKS); }
    public static int essenceEnergy() { return value(ESSENCE_ENERGY); }
    public static int splitterRate() { return value(SPLITTER_RATE); }
    public static int smeltCost() { return value(SMELT_COST); }
    public static int smeltDivisor() { return value(SMELT_DIVISOR); }
    public static int pipeRate() { return value(PIPE_RATE); }
    public static int pipeRate(int tier) { return tier == 2 ? value(CRYSTALLINE_PIPE_RATE) : tier == 1 ? value(REINFORCED_PIPE_RATE) : pipeRate(); }
    public static int networkLimit() { return value(NETWORK_LIMIT); }
    public static int transferBudget() { return value(TRANSFER_BUDGET); }
    public static int itemRate() { return value(ITEM_RATE); }
    public static int itemInterval() { return value(ITEM_INTERVAL); }
    public static int itemTransferBudget() { return value(ITEM_BUDGET); }
    public static int itemRebuildBudget() { return value(ITEM_REBUILD); }
    public static int cultivatorCost() { return value(CULTIVATOR_COST); }
    public static int cultivatorHarvestCost() { return value(CULTIVATOR_HARVEST_COST); }
    public static int cultivatorInterval() { return value(CULTIVATOR_INTERVAL); }
    public static int cultivatorChecks() { return value(CULTIVATOR_CHECKS); }
    public static int areaBudget() { return value(AREA_BUDGET); }
    public static int collectorBaseCost() { return value(COLLECTOR_BASE_COST); }
    public static int collectorDistanceCost() { return value(COLLECTOR_DISTANCE_COST); }
    public static int collectorCandidates() { return value(COLLECTOR_CANDIDATES); }
    public static int entityBudget() { return value(ENTITY_BUDGET); }
    public static int shepherdCost() { return value(SHEPHERD_COST); }
    public static int shepherdInterval() { return value(SHEPHERD_INTERVAL); }
    public static int shepherdAdvance() { return value(SHEPHERD_ADVANCE); }
    public static int shepherdCandidates() { return value(SHEPHERD_CANDIDATES); }
    public static int reaperCost() { return value(REAPER_COST); }
    public static int reaperInterval() { return value(REAPER_INTERVAL); }
    public static int reaperCandidates() { return value(REAPER_CANDIDATES); }
    public static int arboristCost() { return value(ARBORIST_COST); }
    public static int arboristPlantCost() { return value(ARBORIST_PLANT); }
    public static int arboristNodes() { return value(ARBORIST_NODES); }
    public static int arboristLogs() { return value(ARBORIST_LOGS); }
    public static int arboristTreeBlocks() { return value(ARBORIST_TREE); }
    public static boolean arboristBuildingGuard() { return SPEC.isLoaded()?ARBORIST_BUILDINGS.get():ARBORIST_BUILDINGS.getDefault(); }
    public static int sentinelCost() { return value(SENTINEL_COST); }
    public static int brewerCost(){return value(BREWER_COST);}
    public static int miningCost(){return value(MINING_COST);}
    public static int repairCue(){return value(REPAIR_CUE);}
    public static int repairDurabilityPerXp(){return value(REPAIR_PER_XP);}
    public static int enchanterMaxLevel(){return value(ENCHANT_LEVEL);}
    public static int enchanterXpBase(){return value(ENCHANT_XP_BASE);}
    public static int enchanterCueBase(){return value(ENCHANT_CUE_BASE);}
    public static int enchanterCueMinimum(){return value(ENCHANT_CUE_MIN);}
    public static double enchanterXpMultiplier(){return SPEC.isLoaded()?ENCHANT_XP_MULT.get():ENCHANT_XP_MULT.getDefault();}
    public static double enchanterAdditional(){return SPEC.isLoaded()?ENCHANT_ADDITIONAL.get():ENCHANT_ADDITIONAL.getDefault();}
    public static double enchanterRarity(int index){var v=ENCHANT_RARITY[index];return SPEC.isLoaded()?v.get():v.getDefault();}
    public static boolean enchanterTreasure(){return SPEC.isLoaded()?ENCHANT_TREASURE.get():ENCHANT_TREASURE.getDefault();}
    public static boolean enchanterAutomatic(){return SPEC.isLoaded()?ENCHANT_AUTOMATIC.get():ENCHANT_AUTOMATIC.getDefault();}
    public static boolean enchanterCurses(){return SPEC.isLoaded()?ENCHANT_CURSES.get():ENCHANT_CURSES.getDefault();}
    public static java.util.List<String> enchanterItems(){return SPEC.isLoaded()?ENCHANT_ITEMS.get():ENCHANT_ITEMS.getDefault();}
    public static java.util.List<String> enchanterEnchantments(){return SPEC.isLoaded()?ENCHANT_ENCHANTMENTS.get():ENCHANT_ENCHANTMENTS.getDefault();}
    public static int brewerExtra(){return value(BREWER_EXTRA);}
    public static int brewerInterval(){return value(BREWER_INTERVAL);}
    public static int brewerBudget(){return value(BREWER_BUDGET);}
    public static int sentinelUpkeep() { return value(SENTINEL_UPKEEP); }
    public static int sentinelRange() { return value(SENTINEL_RANGE); }
    public static int sentinelFocusedRange() { return value(SENTINEL_FOCUSED_RANGE); }
    public static int sentinelHeight() { return value(SENTINEL_HEIGHT); }
    public static int sentinelInterval() { return value(SENTINEL_INTERVAL); }
    public static int sentinelScan() { return value(SENTINEL_SCAN); }
    public static int sentinelChunkLimit() { return value(SENTINEL_CHUNK); }
    public static int sentinelCandidates() { return value(SENTINEL_CANDIDATES); }
    public static int sentinelBudget() { return value(SENTINEL_BUDGET); }
    public static boolean sentinelLos() { return SPEC.isLoaded()?SENTINEL_LOS.get():SENTINEL_LOS.getDefault(); }
    public static float sentinelDamage() { return (float)(SPEC.isLoaded()?SENTINEL_DAMAGE.get():SENTINEL_DAMAGE.getDefault()).doubleValue(); }
}
