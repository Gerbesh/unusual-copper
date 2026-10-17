package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArmorPowerSchedulerTest {
    @Test void earlierMiningOccupiesPowerButIsNotChargedTwice(){
        var mining=new ArmorPowerScheduler.Demand(ArmorModuleType.MINING_SERVO,5,8);
        var stride=new ArmorPowerScheduler.Demand(ArmorModuleType.STRIDE_DRIVE,4,145);
        var breath=new ArmorPowerScheduler.Demand(ArmorModuleType.AQUA_LUNGS,3,3);
        var result=ArmorPowerScheduler.plan(List.of(stride,breath),3,List.of(mining));
        assertEquals(List.of(ArmorModuleType.AQUA_LUNGS),result.accepted());assertEquals(3,result.cost());
        assertEquals(3,result.charges().get(ArmorModuleType.AQUA_LUNGS));
        assertEquals(List.of(ArmorModuleType.STRIDE_DRIVE),result.limited());
        assertThrows(IllegalArgumentException.class,()->ArmorPowerScheduler.plan(List.of(mining),20,List.of(mining)));
    }
    private static ArmorPowerScheduler.Demand demand(ArmorModuleType module,int priority,int charge) {
        return new ArmorPowerScheduler.Demand(module,priority,charge);
    }

    @Test void prioritizesPaidFlightOverLowPriorityChestProtection() {
        var flight=demand(ArmorModuleType.FLIGHT_CORE_II,2,480);
        var shield=demand(ArmorModuleType.SHIELD_FIELD,3,300);
        var plan=ArmorPowerScheduler.plan(List.of(shield,flight),1000);
        assertEquals(List.of(ArmorModuleType.FLIGHT_CORE_II),plan.accepted());
        assertEquals(List.of(ArmorModuleType.SHIELD_FIELD),plan.limited());
        assertEquals(480,plan.cost()); // 650 CuE/t chest limit, never 780
    }

    @Test void powerOnlyCoversAcceptedModulesAndDoesNotOverdrawBattery() {
        var requests=List.of(demand(ArmorModuleType.STRIDE_DRIVE,4,5),
                demand(ArmorModuleType.AQUA_LUNGS,3,3),
                demand(ArmorModuleType.HOVER_DRIVE,2,80));
        var plan=ArmorPowerScheduler.plan(requests,83);
        assertEquals(List.of(ArmorModuleType.HOVER_DRIVE,ArmorModuleType.AQUA_LUNGS),plan.accepted());
        assertEquals(List.of(ArmorModuleType.STRIDE_DRIVE),plan.limited());
        assertEquals(83,plan.cost());
        assertEquals(0,ArmorPowerScheduler.plan(requests,0).cost());
    }

    @Test void aggregateLimitAppliesAcrossFourSeparatePieces() {
        var requests=List.of(demand(ArmorModuleType.FLIGHT_CORE_II,2,600),
                demand(ArmorModuleType.HOVER_DRIVE,2,200),
                demand(ArmorModuleType.AQUA_LUNGS,3,150),
                demand(ArmorModuleType.STRIDE_DRIVE,4,150));
        var plan=ArmorPowerScheduler.plan(requests,1200);
        assertEquals(800,plan.cost());
        assertEquals(List.of(ArmorModuleType.FLIGHT_CORE_II,ArmorModuleType.HOVER_DRIVE),plan.accepted());
        assertEquals(List.of(ArmorModuleType.AQUA_LUNGS,ArmorModuleType.STRIDE_DRIVE),plan.limited());
    }

    @Test void perPieceBudgetsAndCallerOrderAreNotBypassable() {
        var low=demand(ArmorModuleType.HOVER_DRIVE,6,150);
        var high=demand(ArmorModuleType.FALL_DAMPER,2,100);
        var first=ArmorPowerScheduler.plan(List.of(low,high),900);
        var reversed=ArmorPowerScheduler.plan(List.of(high,low),900);
        assertEquals(first,reversed);
        assertEquals(List.of(ArmorModuleType.FALL_DAMPER),first.accepted());
        assertEquals(List.of(ArmorModuleType.HOVER_DRIVE),first.limited());
    }

    @Test void rejectsDuplicateAndInvalidRequests() {
        var one=demand(ArmorModuleType.AQUA_LUNGS,3,3);
        assertThrows(IllegalArgumentException.class,()->ArmorPowerScheduler.plan(List.of(one,one),900));
        assertThrows(IllegalArgumentException.class,()->ArmorPowerScheduler.plan(List.of(one),-1));
        assertThrows(IllegalArgumentException.class,()->demand(ArmorModuleType.AQUA_LUNGS,1,3));
        assertThrows(IllegalArgumentException.class,()->demand(ArmorModuleType.AQUA_LUNGS,7,3));
        assertThrows(IllegalArgumentException.class,()->demand(ArmorModuleType.AQUA_LUNGS,3,0));
        assertThrows(IllegalArgumentException.class,()->demand(ArmorModuleType.AQUA_LUNGS,3,901));
    }

    @Test void configuredPriorityCanPutDefenseBeforeFlight() {
        var requests=List.of(demand(ArmorModuleType.SHIELD_FIELD,2,550),
                demand(ArmorModuleType.FLIGHT_CORE_II,6,200));
        var plan=ArmorPowerScheduler.plan(requests,900);
        assertEquals(List.of(ArmorModuleType.SHIELD_FIELD),plan.accepted());
        assertEquals(List.of(ArmorModuleType.FLIGHT_CORE_II),plan.limited());
        assertEquals(550,plan.cost());
    }

    @Test void prioritiesRespectDocumentedFlightProtectionMovementOrder() {
        assertEquals(2,ArmorPowerScheduler.defaultPriority(ArmorModuleType.FLIGHT_CORE_II));
        assertEquals(3,ArmorPowerScheduler.defaultPriority(ArmorModuleType.SHIELD_FIELD));
        assertEquals(4,ArmorPowerScheduler.defaultPriority(ArmorModuleType.STRIDE_DRIVE));
        assertEquals(5,ArmorPowerScheduler.defaultPriority(ArmorModuleType.MINING_SERVO));
        assertEquals(6,ArmorPowerScheduler.defaultPriority(ArmorModuleType.OPTIC_VISOR));
    }
}
