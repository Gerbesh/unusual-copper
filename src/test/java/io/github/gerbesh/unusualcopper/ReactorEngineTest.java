package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.reactor.*;
import net.minecraft.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactorEngineTest {
    @Test void recommendedCoolantChargesSustainEachPowerMode(){
        int[] charges={1000,4000,16000};
        for(int mode=0;mode<3;mode++){
            var e=new ReactorEngine();e.mode=mode;
            for(int tick=0;tick<1024;tick++){if(e.remaining==0)assertTrue(e.begin());assertEquals(ReactorEngine.RATES[mode],e.tick(charges[mode],true));e.extract(Integer.MAX_VALUE,false);assertFalse(e.cooling);}
            assertEquals(0,e.heatRemainder);
        }
    }
    @Test void paidBatchReleasesExactly512000AndCannotBeRestartedMidway(){
        var e=new ReactorEngine();e.mode=2;assertTrue(e.begin());assertFalse(e.begin());
        for(int i=0;i<16;i++)assertEquals(32000,e.tick(16000,true));
        assertEquals(512000,e.stored);assertEquals(0,e.remaining);assertEquals(512000,e.produced);assertEquals(0,e.tick(16000,true));
    }
    @Test void pauseReloadAndBlockedOutputPreservePaidRemainder(){
        var e=new ReactorEngine();e.mode=2;e.begin();e.tick(16000,true);int left=e.remaining;
        for(int i=0;i<30;i++)assertEquals(0,e.tick(16000,false));assertEquals(left,e.remaining);
        var restored=new ReactorEngine();restored.restore(e.stored,e.remaining,e.heat,e.heatRemainder,e.mode,e.produced);
        while(restored.remaining>0)restored.tick(16000,true);
        assertEquals(512000,restored.stored);assertEquals(512000,restored.produced);
        restored.stored=ReactorEngine.CAPACITY;assertFalse(restored.begin());
    }
    @Test void fullCoolingSustainsTurboAndTheSameCoolantIsNotConsumed(){
        var e=new ReactorEngine();e.mode=2;long out=0;
        for(int i=0;i<5000;i++){
            if(e.remaining==0)assertTrue(e.begin());assertEquals(32000,e.tick(16000,true));
            out+=e.extract(32000,false);assertTrue(e.heat<9000);
        }
        assertEquals(160_000_000L,out);assertEquals(out,e.produced);assertEquals(0,e.stored);
    }
    @Test void missingCoolantStopsFuelReleaseAndSimulationsDoNotSpend(){
        var e=new ReactorEngine();e.mode=2;e.begin();assertEquals(0,e.tick(0,true));assertEquals(512000,e.remaining);
        e.tick(16000,true);assertEquals(32000,e.extract(50000,true));assertEquals(32000,e.stored);
        assertEquals(32000,e.extract(50000,false));assertEquals(0,e.stored);assertEquals(0,e.extract(-1,false));
    }
    @Test void heatAndSignedPersistentValuesAreBounded(){
        var e=new ReactorEngine();e.restore(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,-1,Long.MIN_VALUE);
        assertEquals(ReactorEngine.CAPACITY,e.stored);assertEquals(ReactorEngine.BATCH,e.remaining);assertEquals(12000,e.heat);assertEquals(63,e.heatRemainder);assertEquals(2,e.mode);assertEquals(0,e.produced);
        assertEquals(0,e.tick(0,true));assertTrue(e.heat>9000);
    }
    @Test void geometryHasExactly36MembersAndEightUniqueExternalPowerFaces(){
        var origin=new BlockPos(-4,70,9);var positions=ReactorGeometry.positions(origin);
        assertEquals(36,positions.size());assertEquals(36,new java.util.HashSet<>(positions).size());
        var found=new java.util.HashSet<Integer>();int count=0;
        for(var pos:positions)for(var face:Direction.values()){
            int port=ReactorGeometry.outlet(origin,pos,face);if(port<0)continue;
            assertTrue(ReactorGeometry.outside(origin,pos,face));assertTrue(found.add(port));count++;
        }
        assertEquals(8,count);assertEquals(java.util.Set.of(0,1,2,3,4,5,6,7),found);
        assertTrue(positions.contains(origin.offset(2,3,2)));
    }
}
