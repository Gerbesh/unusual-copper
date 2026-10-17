package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** No synthetic finished materials: each E5B dispatch must use an actual E2 batch. */
class ProductionPressPolicyTest {
    @Test void coilOrdersNeedPhysicallyPressedWireAndPlate() {
        assertEquals(ProductionPressTask.PLATE,ProductionPressPolicy.stage(0,4,0,0));
        assertEquals(ProductionPressTask.PLATE,ProductionPressPolicy.stage(0,4,0,1));
        assertEquals(ProductionPressTask.WIRE,ProductionPressPolicy.stage(0,4,0,2));
        assertEquals(ProductionPressTask.WIRE,ProductionPressPolicy.stage(0,4,0,128));
        assertEquals(0,ProductionPressPolicy.stage(0,4,1,0));
        assertEquals(0,ProductionPressPolicy.stage(0,4,-1,32));
        assertEquals(0,ProductionPressPolicy.stage(0,4,0,-1));
        assertEquals(0,ProductionPressPolicy.stage(0,0,0,3));
    }

    @Test void framesNeedPlateAndRefuseUnknownCensus() {
        assertEquals(ProductionPressTask.PLATE,ProductionPressPolicy.stage(1,4,0,0));
        assertEquals(0,ProductionPressPolicy.stage(1,4,1,0));
        assertEquals(0,ProductionPressPolicy.stage(1,4,-1,0));
        assertEquals(0,ProductionPressPolicy.stage(-1,4,0,0));
        assertEquals(0,ProductionPressPolicy.stage(2,4,0,0));
    }
}
