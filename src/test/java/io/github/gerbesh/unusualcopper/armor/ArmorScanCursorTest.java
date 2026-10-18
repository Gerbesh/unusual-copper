package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorScanCursorTest {
    @Test void everyPassIsFiniteUniqueBoundedAndStartsAtTheWearer(){
        for(int radius=1;radius<=8;radius++){
            var center=new BlockPos(-17,-50,31);var cursor=new ArmorScanCursor(center,radius);
            assertEquals(center,cursor.next());var seen=new HashSet<BlockPos>();seen.add(center);double previous=0;
            while(!cursor.complete()){
                var p=cursor.next();assertTrue(seen.add(p));double distance=p.distSqr(center);
                assertTrue(distance>=previous&&distance<=radius*radius);previous=distance;
            }
            assertEquals(cursor.checked(),seen.size());assertEquals(cursor.total(),seen.size());assertTrue(seen.size()<=2200);
            assertNull(cursor.next());
        }
    }
    @Test void mutableOriginDoesNotMoveAnInProgressPass(){
        var center=new BlockPos.MutableBlockPos(10,20,30);var cursor=new ArmorScanCursor(center,1);center.set(0,0,0);
        assertEquals(new BlockPos(10,20,30),cursor.next());assertEquals(7,cursor.total());
        assertThrows(IllegalArgumentException.class,()->new ArmorScanCursor(BlockPos.ZERO,9));
    }
}
