package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.config.ArmorEffectsConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArmorSupportPricingTest {
    @Test void serverDefaultsPreserveEveryExistingLevelAndFitTheirBus(){
        for(var module:ArmorModuleType.values())for(int level=1;level<=module.maxLevel();level++){
            assertEquals(module.baseCue(level),module.cue(level));
            assertEquals(List.of(module),ArmorPowerScheduler.plan(List.of(new ArmorPowerScheduler.Demand(module,
                    ArmorPowerScheduler.defaultPriority(module),module.cue(level))),10000).accepted());
        }
        assertEquals(500,ArmorEffectsConfig.healPrice());assertEquals(40,ArmorEffectsConfig.healInterval());assertFalse(ArmorEffectsConfig.pvp());
    }
    @Test void reactiveWorkStillOccupiesItsPieceWhenContinuousWorkIsPlanned(){
        var strike=new ArmorPowerScheduler.Demand(ArmorModuleType.STRENGTH_SERVO,5,36);
        var thermal=new ArmorPowerScheduler.Demand(ArmorModuleType.THERMAL_SHELL,3,620);
        var plan=ArmorPowerScheduler.plan(List.of(thermal),10000,List.of(strike));
        assertTrue(plan.accepted().isEmpty());assertEquals(List.of(ArmorModuleType.THERMAL_SHELL),plan.limited());
        assertEquals(0,plan.cost()); // The already reserved strike is not billed a second time.
    }
}
