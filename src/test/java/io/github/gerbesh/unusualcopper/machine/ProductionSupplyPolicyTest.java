package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** E5B: never claim a reservation before actual, bounded server-side extraction. */
class ProductionSupplyPolicyTest {
    @Test void noWithdrawalWithoutOutstandingOrdersOrRoom() {
        assertEquals(0, ProductionSupplyPolicy.needed(0,0,64,64));
        assertEquals(0, ProductionSupplyPolicy.needed(-1,0,64,64));
        assertEquals(0, ProductionSupplyPolicy.needed(16,16,64,64));
        assertEquals(0, ProductionSupplyPolicy.needed(4096,64,64,64));
        assertEquals(0, ProductionSupplyPolicy.needed(1,1,64,64));
        assertEquals(0, ProductionSupplyPolicy.needed(1,0,64,0));
    }

    @Test void reserveOnlyOneRealBatchAndNoMoreThanTarget() {
        assertEquals(1,ProductionSupplyPolicy.needed(1,0,64,64));
        assertEquals(8,ProductionSupplyPolicy.needed(4096,0,64,64));
        assertEquals(5,ProductionSupplyPolicy.needed(16,11,64,64));
        assertEquals(3,ProductionSupplyPolicy.needed(16,0,64,3));
        assertEquals(4,ProductionSupplyPolicy.needed(4096,60,64,64));
        assertEquals(0,ProductionSupplyPolicy.needed(16,-1,64,64));
        assertEquals(0,ProductionSupplyPolicy.needed(16,0,-1,64));
    }

    @Test void persistAndRestoreAllNineGridEscrowSlots() {
        for(int i=0;i<9;i++) {
            int saved=ProductionSupplyPolicy.cargoSlot(i);
            assertEquals(4+i,saved);
            assertTrue(ProductionSupplyPolicy.gridCargoSlot(saved));
        }
        for(int i:new int[]{-1,0,1,2,3,13,99})assertFalse(ProductionSupplyPolicy.gridCargoSlot(i));
        assertThrows(IllegalArgumentException.class,()->ProductionSupplyPolicy.cargoSlot(-1));
        assertThrows(IllegalArgumentException.class,()->ProductionSupplyPolicy.cargoSlot(9));
    }
}
