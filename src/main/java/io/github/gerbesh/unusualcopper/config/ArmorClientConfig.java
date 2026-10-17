package io.github.gerbesh.unusualcopper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation preferences only. No client-side prices, eligibility or authority. */
public final class ArmorClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HUD;
    static{var builder=new ForgeConfigSpec.Builder();HUD=builder.comment("Show your confirmed armor charge and paid consumption").define("armor_hud",true);SPEC=builder.build();}
    private ArmorClientConfig(){}
}
