package io.github.gerbesh.unusualcopper;
import io.github.gerbesh.unusualcopper.gameplay.MiningFootprint;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MiningFootprintTest {
    @Test void walksEveryColumnExactlyOnceIncludingNegativeCoordinates(){var a=new MiningFootprint(-63,-63,64,64);var visited=new HashSet<String>();for(int i=0;i<a.columns();i++){assertTrue(a.contains(a.x(i),a.z(i)));assertTrue(visited.add(a.x(i)+":"+a.z(i)));}assertEquals(16384,visited.size());assertFalse(a.contains(65,0));}
    @Test void footprintBoundsRejectReversedAndOverflowedInput(){assertThrows(IllegalArgumentException.class,()->new MiningFootprint(0,0,128,0));assertThrows(IllegalArgumentException.class,()->new MiningFootprint(2,0,1,0));assertThrows(IllegalArgumentException.class,()->new MiningFootprint(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0));assertEquals(1,new MiningFootprint(0,0,0,0).columns());}
    @Test void fullSquareCanBeSelectedFromCornerOrCentreWithoutDistanceOverflow(){assertTrue(MiningFootprint.reachable(0,0,128,128));assertTrue(MiningFootprint.reachable(0,0,-128,-128));assertFalse(MiningFootprint.reachable(0,0,129,0));assertFalse(MiningFootprint.reachable(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0));var a=new MiningFootprint(0,0,127,127);assertEquals(16384,a.columns());assertTrue(MiningFootprint.reachable(64,64,a.minX(),a.minZ()));assertTrue(MiningFootprint.reachable(64,64,a.maxX(),a.maxZ()));}
    @Test void largerAreaDepthAndHardnessIncreaseEnergyWithoutOverflow(){int base=MiningFootprint.price(1200,1,0,3);assertEquals(300,base);assertTrue(MiningFootprint.price(1200,256,0,3)>base);assertTrue(MiningFootprint.price(1200,1,128,3)>base);assertTrue(MiningFootprint.price(1200,1,0,6)>base);assertEquals(250_000,MiningFootprint.price(Integer.MAX_VALUE,1024,1000000,50));}
    @Test void fasterPickaxeReducesDurationWithinAnimationLimits(){assertTrue(MiningFootprint.duration(3,8)<MiningFootprint.duration(3,2));assertEquals(20,MiningFootprint.duration(1,10000));assertEquals(400,MiningFootprint.duration(50,0));}
    @Test void discountAlsoAppliesToExistingCustomBasesAndRoundedPrices(){
        assertEquals(1469,MiningFootprint.price(1200,23*26,60,3));
        assertEquals(2500,MiningFootprint.price(10000,1,0,3));
        assertEquals(1,MiningFootprint.price(1,1,0,3));
    }
    @Test void holdingDiscountAccumulatesFractionsInsteadOfRoundingUpEverySecond(){
        int quarters=0,total=0;for(int second=0;second<4;second++){var fee=MiningFootprint.holding(1,quarters);total+=fee.cost();quarters=fee.quarters();}
        assertEquals(1,total);assertEquals(0,quarters);
        assertEquals(128,MiningFootprint.holding(128*128,0).cost());
    }
}
