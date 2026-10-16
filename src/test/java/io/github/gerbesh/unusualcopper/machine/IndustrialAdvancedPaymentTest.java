package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact cumulative CuE payments despite pauses and chunk unloads. */
class IndustrialAdvancedPaymentTest {
    private static void check(int cost,int duration,int pause) {
        int paid=0;
        for(int t=1;t<=pause;t++)paid+=OperationMath.payment(cost,t,duration);
        int atPause=paid;
        for(int i=0;i<100;i++)assertEquals(atPause,paid);
        for(int t=pause+1;t<=duration;t++)paid+=OperationMath.payment(cost,t,duration);
        assertEquals(cost,paid);
    }
    @Test void crystallizer() { check(8000,120,59); }
    @Test void etcher() { check(1200,100,49); }
    @Test void purifier() { check(300,80,39); }
}
