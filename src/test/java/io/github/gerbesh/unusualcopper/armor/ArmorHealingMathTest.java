package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorHealingMathTest {
    @Test void floatRoundingCannotHealMoreThanTheReservedPrice(){
        for(float before:new float[]{0F,.1F,1.3F,12.345F,19.99999F,1023.9F,1E11F})
            for(int price:new int[]{1,500,2000})for(int available:new int[]{0,1,17,499,500,999,10000}){
                var q=ArmorHealingMath.quote(before,before+20F,2F,price,available);
                assertTrue(q.cue()>=0&&q.cue()<=available&&q.hp()>=0&&q.hp()<=2F);
                if(q.cue()>0){assertEquals(q.hp(),q.target()-before);assertEquals((int)Math.ceil(q.hp()*(double)price),q.cue());}
            }
    }
    @Test void postHookBaselineExcludesForeignNestedHealing(){
        var reserved=ArmorHealingMath.quote(10F,20F,2F,500,5000);
        assertEquals(1000,reserved.cue());
        // A foreign hook heals to 19 before our setter. Only the last own HP is billed.
        var actual=ArmorHealingMath.quote(19F,20F,reserved.hp(),500,reserved.cue());
        assertEquals(1F,actual.hp());assertEquals(500,actual.cue());
        assertEquals(0,ArmorHealingMath.quote(20F,20F,reserved.hp(),500,reserved.cue()).cue());
        // Hook reductions and cancellation preserve the unused portion of the reservation.
        assertEquals(125,ArmorHealingMath.quote(10F,20F,.25F,500,reserved.cue()).cue());
        assertEquals(0,ArmorHealingMath.confirmed(0F,reserved.hp(),500,reserved.cue()));
    }
    @Test void malformedHealthCannotCreateHealingOrAnUnboundedRefund(){
        assertEquals(0,ArmorHealingMath.quote(Float.NaN,20,1,500,5000).cue());
        assertEquals(0,ArmorHealingMath.quote(10,Float.POSITIVE_INFINITY,1,500,5000).cue());
        assertEquals(500,ArmorHealingMath.confirmed(Float.NaN,1,500,500));
        assertEquals(500,ArmorHealingMath.confirmed(5,1,500,500));
        assertEquals(0,ArmorReceiptPolicy.charged(500,0));assertEquals(125,ArmorReceiptPolicy.charged(500,125));
        assertEquals(500,ArmorReceiptPolicy.charged(500,-1));assertEquals(500,ArmorReceiptPolicy.charged(500,501));
    }
}
