package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionOrderPolicyTest {
    @Test void fixedRecipesAndPaymentMustBeBounded() {
        for(int type=0;type<2;type++) {
            var plan=ProductionOrderPolicy.plan(type);
            int paid=0;
            for(int tick=1;tick<=plan.ticks();tick++)
                paid+=OperationMath.payment(plan.cue(),tick,plan.ticks());
            assertEquals(plan.cue(),paid);
            assertTrue(plan.ticks()>=40 && plan.ticks()<=200);
            assertTrue(plan.cue()>0);
        }
        assertEquals("resonant_coil",ProductionOrderPolicy.plan(0).recipe());
        assertEquals("reinforced_frame",ProductionOrderPolicy.plan(1).recipe());
        assertThrows(IllegalArgumentException.class,()->ProductionOrderPolicy.plan(-1));
        assertThrows(IllegalArgumentException.class,()->ProductionOrderPolicy.plan(2));
    }
    @Test void queuesAreFiniteAndOverflowSafe() {
        assertEquals(16,ProductionOrderPolicy.queue(0,16));
        assertEquals(4096,ProductionOrderPolicy.queue(4090,Integer.MAX_VALUE));
        assertEquals(4096,ProductionOrderPolicy.queue(4096,16));
        assertEquals(0,ProductionOrderPolicy.queue(0,0));
        assertThrows(IllegalArgumentException.class,()->ProductionOrderPolicy.queue(-1,1));
        assertThrows(IllegalArgumentException.class,()->ProductionOrderPolicy.queue(0,-1));
        assertThrows(IllegalArgumentException.class,()->ProductionOrderPolicy.queue(4097,1));
    }
}
