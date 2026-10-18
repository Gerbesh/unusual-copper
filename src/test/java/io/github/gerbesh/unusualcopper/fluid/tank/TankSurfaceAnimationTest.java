package io.github.gerbesh.unusualcopper.fluid.tank;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TankSurfaceAnimationTest {
    @Test void lateMemberPacketsCannotRewindTheCommonPlaneOrFluid(){
        var animation=new TankSurfaceAnimation<String>(150,0);animation.accept(10,150.5,"water");animation.advance(1);
        animation.accept(11,152,"lava");double previous=animation.value();
        for(int frame=2;frame<40;frame++){
            animation.accept(10,150.5,"water");animation.advance(frame);
            assertTrue(animation.value()>=previous&&animation.value()<=152);assertEquals("lava",animation.material());previous=animation.value();
        }
        assertEquals(152,animation.value(),.0001);
    }
    @Test void equalElapsedTimeGivesEqualHeightAtDifferentFrameRates(){
        var slow=new TankSurfaceAnimation<String>(-64,0);var fast=new TankSurfaceAnimation<String>(-64,0);
        slow.accept(1,-60,"water");fast.accept(1,-60,"water");
        for(int i=1;i<=15;i++)slow.advance(i*20D/15);for(int i=1;i<=240;i++)fast.advance(i*20D/240);
        assertEquals(slow.value(),fast.value(),1E-10);
    }
    @Test void drainingRetainsTheMaterialUntilTheSurfaceRecedesAndBadTimeCannotJumpIt(){
        var animation=new TankSurfaceAnimation<String>(150.8,0);animation.accept(1,150.8,"water");animation.accept(2,150,null);
        animation.advance(1);assertTrue(animation.value()>150&&animation.value()<150.8);assertEquals("water",animation.material());
        double value=animation.value();animation.advance(Double.NaN);animation.advance(-1);assertEquals(value,animation.value());
        animation.advance(50);assertEquals(150,animation.value(),1E-6);
    }
}
