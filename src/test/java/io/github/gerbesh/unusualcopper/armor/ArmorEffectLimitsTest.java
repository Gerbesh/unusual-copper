package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorEffectLimitsTest {
    @Test void shieldQuotesCannotOverdrawEvenAtFractionalFloatBoundaries(){
        for(int level=1;level<=3;level++)for(int available:new int[]{0,1,299,300,301,599,7200,10000})
            for(float damage:new float[]{Float.MIN_VALUE,.1F,1F,1.001F,12F,Float.MAX_VALUE}){
                var quote=ArmorEffectLimits.shield(damage,level,available);
                assertTrue(quote.cue()>=0&&quote.cue()<=available);
                assertTrue(quote.prevented()>=0&&quote.prevented()<=damage&&quote.prevented()<=2*level);
                int perHp=level==1?300:level==2?600:1200;
                assertEquals((int)Math.ceil(quote.prevented()*(double)perHp),quote.cue());
            }
        assertEquals(new ArmorEffectLimits.Shield(0,0),ArmorEffectLimits.shield(Float.NaN,1,1000));
        assertEquals(new ArmorEffectLimits.Shield(0,0),ArmorEffectLimits.shield(Float.POSITIVE_INFINITY,1,1000));
    }
    @Test void miningCapsOnlyItsOwnIncreaseAndNeverLowersForeignSpeed(){
        assertEquals(14F,ArmorEffectLimits.mining(10F,1));assertEquals(18F,ArmorEffectLimits.mining(10F,2));
        assertEquals(24F,ArmorEffectLimits.mining(10F,3));assertEquals(64F,ArmorEffectLimits.mining(60F,3));
        assertEquals(100F,ArmorEffectLimits.mining(100F,3));assertEquals(.009F,ArmorEffectLimits.mining(.005F,2),.0000001F);
        assertEquals(-1F,ArmorEffectLimits.mining(-1F,3));
    }
    @Test void foreignAttributeMultipliersAreIncludedInTheLimit(){
        assertEquals(.025D,ArmorEffectLimits.addition(.25D,.10D,.30D,2D),1E-8);
        assertEquals(0D,ArmorEffectLimits.addition(.4D,.10D,.30D,1D));
        assertEquals(.05D,ArmorEffectLimits.addition(.10D,.05D,.30D,1D));
        assertEquals(0D,ArmorEffectLimits.addition(.1D,.1D,.3D,Double.POSITIVE_INFINITY));
    }
}
