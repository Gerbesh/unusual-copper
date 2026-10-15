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
    private static final ForgeConfigSpec.IntValue NETWORK_LIMIT;
    private static final ForgeConfigSpec.IntValue TRANSFER_BUDGET;
    private static final ForgeConfigSpec.IntValue ITEM_RATE, ITEM_INTERVAL, ITEM_BUDGET, ITEM_REBUILD;
    private static final ForgeConfigSpec.IntValue CULTIVATOR_COST, CULTIVATOR_INTERVAL, CULTIVATOR_CHECKS, AREA_BUDGET;
    private static final ForgeConfigSpec.IntValue COLLECTOR_BASE_COST, COLLECTOR_DISTANCE_COST, COLLECTOR_CANDIDATES, ENTITY_BUDGET;
    private static final ForgeConfigSpec.IntValue SHEPHERD_COST, SHEPHERD_INTERVAL, SHEPHERD_ADVANCE, SHEPHERD_CANDIDATES;
    private static final ForgeConfigSpec.IntValue REAPER_COST, REAPER_INTERVAL, REAPER_CANDIDATES;
    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("copper_economy");
        SOLAR_TICKS = builder.comment("Clear-day ticks to infuse a full stack of 64 copper inputs.").defineInRange("solar_batch_ticks", 800, 64, 96_000);
        ESSENCE_ENERGY = builder.comment("CuE produced by consuming one essence item.").defineInRange("cue_per_essence", 500, 1, 50_000);
        SPLITTER_RATE = builder.defineInRange("splitter_items_per_tick", 4, 1, 64);
        SMELT_COST = builder.comment("CuE cost per smelting operation. An active job retains its quoted cost.").defineInRange("smelting_cue_cost", 240, 1, 24_000);
        SMELT_DIVISOR = builder.comment("Divide the original recipe duration by this number, rounding up.").defineInRange("smelting_speed_divisor", 10, 1, 100);
        builder.pop().push("network");
        PIPE_RATE = builder.comment("Maximum total CuE through one basic pipe or machine port per tick.").defineInRange("basic_transfer_rate", 256, 1, 4096);
        NETWORK_LIMIT = builder.comment("Oversized connected components stop transferring until split or the limit is raised.").defineInRange("maximum_component_nodes", 1024, 16, 16_384);
        TRANSFER_BUDGET = builder.comment("Maximum source/target transfer attempts per dimension per tick.").defineInRange("transfer_attempts_per_tick", 128, 1, 4096);
        builder.pop().push("item_transport");
        ITEM_RATE = builder.comment("Maximum items through each basic item pipe per transport interval.").defineInRange("items_per_interval", 8, 1, 64);
        ITEM_INTERVAL = builder.defineInRange("interval_ticks", 5, 1, 100);
        ITEM_BUDGET = builder.defineInRange("attempts_per_dimension", 64, 1, 4096);
        ITEM_REBUILD = builder.comment("Graph nodes visited per tick while rebuilding item networks.").defineInRange("rebuild_nodes_per_tick", 128, 8, 4096);
        builder.pop().push("area_machines");
        CULTIVATOR_COST = builder.defineInRange("cultivator_cue_per_growth", 140, 1, 50_000);
        CULTIVATOR_INTERVAL = builder.defineInRange("cultivator_interval_ticks", 20, 20, 200);
        CULTIVATOR_CHECKS = builder.defineInRange("cultivator_checks_per_wave", 8, 1, 8);
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
        builder.pop(); SPEC = builder.build();
    }
    private static int value(ForgeConfigSpec.IntValue entry) { return SPEC.isLoaded() ? entry.get() : entry.getDefault(); }
    public static int solarTicks() { return value(SOLAR_TICKS); }
    public static int essenceEnergy() { return value(ESSENCE_ENERGY); }
    public static int splitterRate() { return value(SPLITTER_RATE); }
    public static int smeltCost() { return value(SMELT_COST); }
    public static int smeltDivisor() { return value(SMELT_DIVISOR); }
    public static int pipeRate() { return value(PIPE_RATE); }
    public static int networkLimit() { return value(NETWORK_LIMIT); }
    public static int transferBudget() { return value(TRANSFER_BUDGET); }
    public static int itemRate() { return value(ITEM_RATE); }
    public static int itemInterval() { return value(ITEM_INTERVAL); }
    public static int itemTransferBudget() { return value(ITEM_BUDGET); }
    public static int itemRebuildBudget() { return value(ITEM_REBUILD); }
    public static int cultivatorCost() { return value(CULTIVATOR_COST); }
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
}
