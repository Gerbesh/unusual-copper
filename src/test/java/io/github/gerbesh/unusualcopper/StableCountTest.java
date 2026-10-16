package io.github.gerbesh.unusualcopper;
import io.github.gerbesh.unusualcopper.storage.StableCount;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StableCountTest {
    @Test void pagesNeverReplaceAnObservedTotal(){var c=new StableCount();c.accept(74,false);assertEquals(-1,c.amount());c.accept(1024,true);c.accept(0,false);assertEquals(1024,c.amount());assertFalse(c.current());c.accept(74,false);assertEquals(1024,c.amount());c.accept(960,true);assertEquals(960,c.amount());assertTrue(c.current());}
    @Test void completedZeroIsARealChange(){var c=new StableCount();c.accept(64,true);c.accept(0,true);assertEquals(0,c.amount());assertTrue(c.current());}
}
