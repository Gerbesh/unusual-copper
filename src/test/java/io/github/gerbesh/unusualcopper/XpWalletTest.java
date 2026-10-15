package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.XpWallet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class XpWalletTest {
    @Test void vanillaLandmarksAndTransitions(){int[] levels={0,5,15,16,17,20,30,31,32,40,50,100};long[] points={0,55,315,352,394,550,1395,1507,1628,2920,5345,30970};for(int i=0;i<levels.length;i++)assertEquals(points[i],XpWallet.base(levels[i]));assertEquals(37,XpWallet.span(15));assertEquals(42,XpWallet.span(16));assertEquals(112,XpWallet.span(30));assertEquals(121,XpWallet.span(31));}
    @Test void fractionalBarAndHistoricalCounterAreIndependent(){var before=new XpWallet.State(20,.35F,1_000_000);var after=XpWallet.spend(before,188);assertEquals(188,XpWallet.points(before)-XpWallet.points(after),.00001);assertEquals(16,after.level());assertEquals(999812,after.total());assertTrue(after.progress()>0);}
    @Test void exactBoundaryAndInsufficientFunds(){var state=new XpWallet.State(16,0,352);assertEquals(351,XpWallet.points(XpWallet.spend(state,1)),.00001);assertEquals(0,XpWallet.points(XpWallet.spend(state,352)),.00001);assertThrows(IllegalArgumentException.class,()->XpWallet.spend(state,353));assertThrows(IllegalArgumentException.class,()->XpWallet.spend(state,-1));}
    @Test void highLevelsKeepVanillaFloatPrecisionWithoutIntegerOverflow(){var state=new XpWallet.State(20000,.75F,Integer.MAX_VALUE);var after=XpWallet.spend(state,100000);assertEquals(100000,XpWallet.points(state)-XpWallet.points(after),.02);assertEquals(Integer.MAX_VALUE-100000,after.total());assertEquals(0,XpWallet.spend(new XpWallet.State(20,.5F,Integer.MIN_VALUE),1).total());}
    @Test void malformedXpIsNotSpendable(){assertThrows(IllegalArgumentException.class,()->XpWallet.points(new XpWallet.State(20,Float.NaN,100)));assertThrows(IllegalArgumentException.class,()->XpWallet.points(new XpWallet.State(20,1F,100)));assertThrows(IllegalArgumentException.class,()->XpWallet.base(-1));assertThrows(IllegalArgumentException.class,()->XpWallet.base(1000001));}
}
