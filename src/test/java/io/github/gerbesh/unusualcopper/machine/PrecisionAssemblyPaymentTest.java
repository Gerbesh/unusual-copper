package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** E4 machine resumes without charging duplicate CuE for paid ticks. */
class PrecisionAssemblyPaymentTest {
    @Test void twoStagePayment() {
        for(int[] job:new int[][]{{2500,120},{3200,160}}){
            int cost=job[0],ticks=job[1],paid=0;
            for(int tick=1;tick<=ticks;tick++){
                if(tick==61){int snapshot=paid;for(int pause=0;pause<200;pause++)assertEquals(snapshot,paid);}
                paid+=OperationMath.payment(cost,tick,ticks);
            }
            assertEquals(cost,paid);
        }
    }
}
