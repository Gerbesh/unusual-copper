package io.github.gerbesh.unusualcopper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned permissions and hard, bounded scanning costs. */
public final class ArmorSenseConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ORES,DIAGNOSTICS;
    private static final ForgeConfigSpec.IntValue RADIUS,PERSONAL,BUDGET;
    static {
        var b=new ForgeConfigSpec.Builder();
        ORES=b.comment("Allow the paid tier II visor to reveal nearby ores in loaded, permitted chunks.").define("allow_ore_highlights",true);
        DIAGNOSTICS=b.comment("Allow the paid field scanner. Private machine data is owner-only.").define("allow_diagnostics",true);
        RADIUS=b.defineInRange("ore_radius",6,1,8);
        PERSONAL=b.comment("Maximum candidate coordinates per wearer and tick, including diagnostic ray reads.").defineInRange("wearer_checks_per_tick",192,64,512);
        BUDGET=b.comment("Shared maximum across every wearer and dimension per server tick.").defineInRange("total_checks_per_tick",512,64,4096);
        SPEC=b.build();
    }
    public static boolean ores(){return SPEC.isLoaded()?ORES.get():ORES.getDefault();}
    public static boolean diagnostics(){return SPEC.isLoaded()?DIAGNOSTICS.get():DIAGNOSTICS.getDefault();}
    public static int radius(){return SPEC.isLoaded()?RADIUS.get():RADIUS.getDefault();}
    public static int personalBudget(){return SPEC.isLoaded()?PERSONAL.get():PERSONAL.getDefault();}
    public static int totalBudget(){return SPEC.isLoaded()?BUDGET.get():BUDGET.getDefault();}
    private ArmorSenseConfig(){}
}
