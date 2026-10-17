package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Unfinished SSN/handler census can never be reported as a proven material shortage. */
class ProductionStockPolicyTest {
    @Test void completedCountReportsTrueShortfallOnlyAsAnEstimate() {
        var partial=ProductionStockPolicy.estimate(16,9,false);
        assertFalse(partial.complete());
        assertEquals(0,partial.missing());
        assertEquals(0,partial.available());
        var counted=ProductionStockPolicy.estimate(16,9,true);
        assertTrue(counted.complete());
        assertEquals(7,counted.missing());
        assertEquals(9,counted.available());
        assertEquals(0,ProductionStockPolicy.estimate(16,128,true).missing());
        assertEquals(0,ProductionStockPolicy.estimate(0,0,true).missing());
    }
    @Test void corruptCountNeverBecomesAReservation() {
        assertFalse(ProductionStockPolicy.estimate(4096,-1,true).complete());
        assertThrows(IllegalArgumentException.class,()->ProductionStockPolicy.estimate(-1,100,true));
    }
}
