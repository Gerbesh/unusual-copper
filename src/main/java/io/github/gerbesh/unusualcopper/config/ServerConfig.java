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
}
