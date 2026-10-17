package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimitedFlightPolicyTest {
    @Test void paidF3NeedsTierThreeAndSixtySecondSession() {
        assertTrue(LimitedFlightPolicy.canThrust(3,3,1199,0,true,true));
        assertFalse(LimitedFlightPolicy.canThrust(3,3,1201,0,true,true));
        assertFalse(LimitedFlightPolicy.canThrust(3,2,40,0,true,true));
        assertFalse(LimitedFlightPolicy.canThrust(2,3,40,0,true,true));
        assertFalse(LimitedFlightPolicy.canThrust(3,3,40,0,false,true));
        assertFalse(LimitedFlightPolicy.canThrust(3,3,40,0,true,false));
        assertFalse(LimitedFlightPolicy.canThrust(3,3,0,100,true,true));
        assertEquals(200,LimitedFlightPolicy.CUE_PER_TICK);
        assertEquals(64D,LimitedFlightPolicy.MAX_HEIGHT);
    }
    @Test void f3HudReportsRemainingSecondsAndHeadroomWithoutResettingOnKeyRelease() {
        assertEquals(1200,LimitedFlightPolicy.remainingTicks(1200,1200,true));
        assertEquals(920,LimitedFlightPolicy.remainingTicks(1280,1000,true));
        assertEquals(0,LimitedFlightPolicy.remainingTicks(2200,1000,true));
        assertEquals(0,LimitedFlightPolicy.remainingTicks(999,1000,true));
        assertEquals(0,LimitedFlightPolicy.remainingTicks(1300,1000,false));
        assertEquals(64,LimitedFlightPolicy.headroom(80,80,true));
        assertEquals(29,LimitedFlightPolicy.headroom(80,115,true));
        assertEquals(0,LimitedFlightPolicy.headroom(80,160,true));
        assertEquals(0,LimitedFlightPolicy.headroom(80,90,false));
        assertEquals(0,LimitedFlightPolicy.headroom(Double.NaN,90,true));
        assertThrows(IllegalArgumentException.class,()->new ArmorRuntime.FlightStatus(true,1201,10));
        assertThrows(IllegalArgumentException.class,()->new ArmorRuntime.FlightStatus(false,20,10));
        assertThrows(IllegalArgumentException.class,()->new ArmorRuntime.FlightStatus(true,120,65));
        assertThrows(IllegalArgumentException.class,()->new ArmorRuntime.FlightStatus(true,-1,20));
    }

    @Test void directionChangesVerticalThrustButCannotPassTakeoffCeiling() {
        assertTrue(LimitedFlightPolicy.vertical(1,64)>LimitedFlightPolicy.vertical(0,64));
        assertTrue(LimitedFlightPolicy.vertical(0,64)>LimitedFlightPolicy.vertical(-1,64));
        assertEquals(0D,LimitedFlightPolicy.vertical(1,0),1e-9);
        assertEquals(-0.2D,LimitedFlightPolicy.vertical(1,-0.2),1e-9);
        assertThrows(IllegalArgumentException.class,()->LimitedFlightPolicy.vertical(Double.NaN,12));
    }
    @Test void diagonalSteeringIsNeverFasterThanStraight() {
        var diagonal=LimitedFlightPolicy.horizontal(1,1);
        assertEquals(LimitedFlightPolicy.MAX_HORIZONTAL,Math.hypot(diagonal[0],diagonal[1]),1e-9);
        var nominal=LimitedFlightPolicy.horizontal(.1,.2);
        assertArrayEquals(new double[]{.1,.2},nominal,1e-9);
        assertThrows(IllegalArgumentException.class,()->LimitedFlightPolicy.horizontal(0,Double.POSITIVE_INFINITY));
    }
}
