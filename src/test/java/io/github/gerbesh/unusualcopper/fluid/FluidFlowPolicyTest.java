package io.github.gerbesh.unusualcopper.fluid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidFlowPolicyTest {
    @Test void oneBucketAlwaysPaysFourBatches() {
        assertEquals(250,FluidFlowPolicy.BATCH_MB);
        assertEquals(160,FluidFlowPolicy.pumpCuE(true)*(1000/FluidFlowPolicy.BATCH_MB));
        assertEquals(480,FluidFlowPolicy.pumpCuE(false)*(1000/FluidFlowPolicy.BATCH_MB));
    }
    @Test void rateAndReceiverCapacityPreventOverflow() {
        assertEquals(0,FluidFlowPolicy.budgetedTransfer(1000,0));
        assertEquals(125,FluidFlowPolicy.budgetedTransfer(1000,125));
        assertEquals(250,FluidFlowPolicy.budgetedTransfer(1000,5000));
        assertEquals(50,FluidFlowPolicy.budgetedTransfer(50,100));
        assertEquals(0,FluidFlowPolicy.budgetedTransfer(-1,100));
        assertEquals(0,FluidFlowPolicy.budgetedTransfer(100,-1));
    }
    @Test void lakeMinimumAndSearchRadiusAreStable() {
        assertEquals(15,FluidFlowPolicy.MIN_SURFACE_SOURCES);
        assertEquals(15,FluidFlowPolicy.HARVEST_RADIUS);
        assertEquals(5,FluidFlowPolicy.TICKS_PER_BATCH);
    }
}
