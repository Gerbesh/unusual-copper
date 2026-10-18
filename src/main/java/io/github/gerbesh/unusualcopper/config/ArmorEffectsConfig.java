package io.github.gerbesh.unusualcopper.config;

import io.github.gerbesh.unusualcopper.armor.ArmorModuleType;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.EnumMap;
import java.util.Map;

/** Server-owned pricing. Event prices and passive bus allocations are separate. */
public final class ArmorEffectsConfig {
    public static final ForgeConfigSpec SPEC;
    private static final Map<ArmorModuleType,ForgeConfigSpec.IntValue[]> COSTS=new EnumMap<>(ArmorModuleType.class);
    private static final ForgeConfigSpec.IntValue HEAL_PRICE,HEAL_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue PVP;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("CuE per funded tick. Equipment and whole-set power caps still apply.").push("tick_prices");
        for(var module:ArmorModuleType.values()){
            var levels=new ForgeConfigSpec.IntValue[module.maxLevel()];
            for(int i=0;i<levels.length;i++)levels[i]=b.defineInRange(module.id+"_"+(i+1),module.baseCue(i+1),1,900);
            COSTS.put(module,levels);
        }
        b.pop().push("support");
        HEAL_PRICE=b.comment("Additional CuE per HP actually restored by the core.").defineInRange("healing_cue_per_hp",500,1,2000);
        HEAL_INTERVAL=b.comment("Minimum server ticks between healing attempts, including cancelled attempts.").defineInRange("healing_interval",40,10,1200);
        PVP=b.comment("Allow strength servo bonus damage against other players. Base attacks are unchanged.").define("strength_in_pvp",false);
        b.pop();SPEC=b.build();
    }
    private static int get(ForgeConfigSpec.IntValue v){return SPEC.isLoaded()?v.get():v.getDefault();}
    public static int cue(ArmorModuleType module,int level){
        if(level<1||level>module.maxLevel())throw new IllegalArgumentException("Module level");
        return get(COSTS.get(module)[level-1]);
    }
    public static int healPrice(){return get(HEAL_PRICE);}
    public static int healInterval(){return get(HEAL_INTERVAL);}
    public static boolean pvp(){return SPEC.isLoaded()?PVP.get():PVP.getDefault();}
    private ArmorEffectsConfig(){}
}
