package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorScanBudgetTest {
    @Test void repeatedAndCompetingWearersShareOneHardLimit(){
        var budget=new ArmorScanBudget(512,192);var first=new UUID(0,1);var second=new UUID(0,2);var third=new UUID(0,3);
        for(int i=0;i<192;i++)assertTrue(budget.take(first));
        assertFalse(budget.take(first));for(int i=0;i<192;i++)assertTrue(budget.take(second));
        for(int i=0;i<128;i++)assertTrue(budget.take(third));
        assertFalse(budget.take(second));assertFalse(budget.take(third));assertFalse(budget.remaining(new UUID(0,4)));assertEquals(512,budget.used());
    }
    @Test void oneSmallTotalCannotBeBypassedWithManyIdentities(){
        var budget=new ArmorScanBudget(7,192);for(int i=0;i<7;i++)assertTrue(budget.take(new UUID(0,i)));
        assertFalse(budget.take(new UUID(1,1)));assertEquals(7,budget.used());
        assertThrows(IllegalArgumentException.class,()->new ArmorScanBudget(0,192));
    }
    @Test void crowdedWorldsKeepAtomicRaySlicesAndSmallGroupsShareTheBudget(){
        assertEquals(192,ArmorScanBudget.fairSlice(512,192,1));
        assertEquals(128,ArmorScanBudget.fairSlice(512,192,4));
        assertEquals(64,ArmorScanBudget.fairSlice(512,192,20));
        assertEquals(64,ArmorScanBudget.fairSlice(64,192,20));
        assertThrows(IllegalArgumentException.class,()->ArmorScanBudget.fairSlice(32,192,20));
    }
    @Test void twentyFullRayWearersAreAllServedWithinThreeTicks(){
        int cursor=0;var seen=new HashSet<Integer>();
        for(int tick=0;tick<3;tick++){
            var rotation=new ArmorScanBudget.Rotation(cursor,20);var budget=new ArmorScanBudget(512,64);
            for(int i=0;i<20;i++){
                int person=rotation.at(i);var owner=new UUID(0,person);
                if(budget.remainingFor(owner)<64){rotation.deferred(i);continue;}
                for(int j=0;j<64;j++)assertTrue(budget.take(owner));rotation.worked(i);seen.add(person);
            }
            assertEquals(512,budget.used());cursor=rotation.next();
        }
        assertEquals(20,seen.size());
    }
    @Test void anAtomicRayDeferredByCheapReadersCannotStayAtTheTailForever(){
        int cursor=0,services=0;
        for(int tick=0;tick<20;tick++){
            var rotation=new ArmorScanBudget.Rotation(cursor,17);var budget=new ArmorScanBudget(512,192);
            for(int i=0;i<17;i++){
                int person=rotation.at(i);var owner=new UUID(0,person);int need=person==16?64:31;
                if(budget.remainingFor(owner)<64)rotation.deferred(i);
                if(budget.remainingFor(owner)<need)continue;
                for(int j=0;j<need;j++)assertTrue(budget.take(owner));rotation.worked(i);if(person==16)services++;
            }
            assertTrue(budget.used()<=512);cursor=rotation.next();
        }
        assertTrue(services>=10,"The atomic ray was repeatedly skipped after small reads");
    }
}
