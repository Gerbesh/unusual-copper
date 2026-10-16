package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CultivationSweepTest {
    @Test void fullVolumeIsCoveredOnceInOutwardColumnOrder() {
        var area=new WorkArea(new BlockPos(-7,-3,-7),new BlockPos(8,12,8));
        var wave=new CultivationSweep();wave.prepare(area,BlockPos.ZERO);
        var seen=new HashSet<BlockPos>();float previous=-1;
        for(int i=0;i<area.volume();i++) {
            assertTrue(seen.add(wave.at(i)));assertTrue(area.contains(wave.at(i)));
            assertTrue(wave.radius(i)>=previous);previous=wave.radius(i);
        }
        assertEquals(4096,seen.size());assertEquals(wave.at(0),wave.at(area.volume()));
    }
    @Test void savedCursorResumesTheSameOrderAndEveryBatchCanCoverMultiplePlants() {
        var area=new WorkArea(new BlockPos(10,4,-5),new BlockPos(13,4,-2));var origin=new BlockPos(9,4,-6);
        var wave=new CultivationSweep();wave.prepare(area,origin);
        var restored=new CultivationSweep();restored.prepare(area,origin);
        var first=new HashSet<BlockPos>();for(int i=0;i<8;i++)first.add(wave.at(i));assertEquals(8,first.size());
        for(int i=8;i<16;i++){assertFalse(first.contains(restored.at(i)));assertEquals(wave.at(i),restored.at(i));}
    }
    @Test void unchangedSelectionKeepsProgressButNewSelectionResetsTransientVisualState() {
        var area=new WorkArea(BlockPos.ZERO,new BlockPos(2,0,2));var wave=new CultivationSweep();wave.prepare(area,BlockPos.ZERO);
        wave.active=true;wave.front=2;wave.changed(BlockPos.ZERO,3);wave.prepare(area,BlockPos.ZERO);
        assertTrue(wave.active);assertEquals(2,wave.front);assertEquals(1,wave.changes().size());
        wave.prepare(new WorkArea(new BlockPos(4,0,4),new BlockPos(5,0,5)),BlockPos.ZERO);
        assertFalse(wave.active);assertEquals(0,wave.front);assertTrue(wave.changes().isEmpty());
    }
}
