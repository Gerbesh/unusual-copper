package io.github.gerbesh.unusualcopper;
import io.github.gerbesh.unusualcopper.gameplay.MiningTiming;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningTimingTest {
    @Test void flightAlwaysTakesSixtyTicksRegardlessOfDistance(){
        for(double distance:new double[]{2,32,128,384,512}){
            assertEquals(distance/2,distance*MiningTiming.fraction(30),.0001);
            assertTrue(distance*MiningTiming.fraction(59)<distance);
            assertEquals(distance,distance*MiningTiming.fraction(60),.0001);
        }
    }
    @Test void pickaxePreparationDoesNotStretchFlight(){
        for(int work:new int[]{20,60,400}){int progress=MiningTiming.preparation(work);assertEquals(0,MiningTiming.advance(progress,work));assertEquals(59,MiningTiming.advance(0,59));assertEquals(60,MiningTiming.advance(0,60));}
    }
    @Test void missedSchedulerVisitsCatchUpWithoutOvershooting(){assertEquals(50,MiningTiming.advance(10,40));assertEquals(60,MiningTiming.advance(10,Long.MAX_VALUE));assertEquals(10,MiningTiming.advance(10,-5));assertEquals(10,MiningTiming.advance(10,0));assertEquals(0,MiningTiming.fraction(-20));assertEquals(1,MiningTiming.fraction(200));}
}
