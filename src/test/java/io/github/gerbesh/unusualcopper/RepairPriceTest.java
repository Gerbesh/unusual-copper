package io.github.gerbesh.unusualcopper;
import io.github.gerbesh.unusualcopper.gameplay.RepairPrice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepairPriceTest {
    @Test void onlyActualRestoredDamageIsBilled(){assertEquals(new RepairPrice(31,3100,8),RepairPrice.of(31,4096,100,4));assertEquals(new RepairPrice(128,12800,32),RepairPrice.of(1000,128,100,4));}
    @Test void fractionalXpRoundsUpAndCannotBecomeFree(){for(int points=1;points<1000;points++){var q=RepairPrice.of(points,4096,100,4);assertEquals((points+3)/4,q.xp());assertTrue(q.xp()>=1);assertEquals(points*100,q.cue());}}
    @Test void largeModdedDurabilityUsesABoundedPortionWithoutOverflow(){assertEquals(new RepairPrice(4096,409600,1024),RepairPrice.of(Integer.MAX_VALUE,4096,100,4));assertThrows(IllegalArgumentException.class,()->RepairPrice.of(Integer.MAX_VALUE,4096,Integer.MAX_VALUE,4));}
    @Test void invalidQuantitiesCannotProduceAPayment(){for(int n:new int[]{-1,0,4097,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->RepairPrice.of(100,n,100,4));assertThrows(IllegalArgumentException.class,()->RepairPrice.of(0,128,100,4));assertThrows(IllegalArgumentException.class,()->RepairPrice.of(1,128,0,4));assertThrows(IllegalArgumentException.class,()->RepairPrice.of(1,128,100,0));}
}
