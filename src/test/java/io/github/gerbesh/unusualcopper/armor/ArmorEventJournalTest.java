package io.github.gerbesh.unusualcopper.armor;

import io.github.gerbesh.unusualcopper.item.BatteryChargeLedger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorEventJournalTest {
    @Test void oneEventReservesOnlyOnceEvenAcrossBatchOrOwnerChanges(){
        var journal=new ArmorEventJournal();var cell=new CompoundTag();cell.putInt("CuE",5000);
        var owner=new Object();var hit=new Object();
        java.util.function.IntPredicate payment=n->BatteryChargeLedger.reserve(cell,10000,n);
        assertTrue(journal.reserve(owner,hit,600,payment));
        assertFalse(journal.reserve(owner,hit,600,payment));journal.endBatch();
        assertFalse(journal.reserve(new Object(),hit,600,payment));assertEquals(4400,BatteryChargeLedger.available(cell,10000));
    }
    @Test void failedPaymentCanRetryButReentrantPaymentCannotDuplicate(){
        var journal=new ArmorEventJournal();var owner=new Object();var event=new Object();
        assertFalse(journal.reserve(owner,event,2000,n->false));
        assertTrue(journal.reserve(owner,event,2000,n->{assertFalse(journal.reserve(owner,event,2000,other->true));return true;}));
        var retry=new Object();assertThrows(IllegalStateException.class,()->journal.reserve(owner,retry,8,n->{throw new IllegalStateException();}));
        assertTrue(journal.reserve(owner,retry,8,n->true));
    }
    @Test void perWearerBatchIsBoundedAndOnlyRealSuccessConsumesTheQuota(){
        var journal=new ArmorEventJournal();var owner=new Object();var keep=new java.util.ArrayList<Object>();
        for(int i=0;i<64;i++){var event=new Object();keep.add(event);assertTrue(journal.reserve(owner,event,1,n->true));}
        assertFalse(journal.reserve(owner,new Object(),1,n->true));
        assertTrue(journal.reserve(new Object(),new Object(),1,n->true));
        journal.endBatch();assertTrue(journal.reserve(owner,new Object(),1,n->true));
        assertFalse(journal.reserve(owner,new Object(),10001,n->true));assertFalse(journal.reserve(owner,new Object(),0,n->true));
    }
}
