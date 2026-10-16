package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Operation payments are exact even when the server pauses and resumes. */
class IndustrialPaymentTest {
    @Test void crusherIsExactly180CuEAcross60Ticks() {
        int total=0;
        for (int tick=1;tick<=60;tick++) total+=OperationMath.payment(180,tick,60);
        assertEquals(180,total);
    }
    @Test void pausedOperationDoesNotBillRepeatedSteps() {
        int paid=0, progress=0;
        for(int i=0;i<35;i++) { progress++; paid+=OperationMath.payment(180,progress,60); }
        assertEquals(105,paid);
        for(int i=0;i<120;i++) assertEquals(paid,105);
        for(int i=35;i<60;i++) { progress++; paid+=OperationMath.payment(180,progress,60); }
        assertEquals(180,paid);
    }
}
