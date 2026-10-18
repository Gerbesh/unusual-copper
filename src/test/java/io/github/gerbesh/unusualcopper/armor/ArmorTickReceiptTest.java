package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ArmorTickReceiptTest {
    @Test void earlyEventAndContinuousWorkShareOneCharge(){
        var receipt=new ArmorTickReceipt();var canceled=new AtomicBoolean(false);var effects=new AtomicInteger();
        assertTrue(receipt.add(()->!canceled.get(),effects::incrementAndGet));
        assertEquals(10,receipt.confirmed(10));receipt.applied();
        assertEquals(10,receipt.confirmed(10));canceled.set(true);
        assertEquals(10,receipt.confirmed(10));receipt.after();assertEquals(0,effects.get());
    }
    @Test void canceledQueriesRefundAndNeverEmitVisuals(){
        var receipt=new ArmorTickReceipt();var effects=new AtomicInteger();
        assertTrue(receipt.untouched());
        for(int i=0;i<64;i++)assertTrue(receipt.add(()->false,effects::incrementAndGet));
        assertFalse(receipt.add(()->true,effects::incrementAndGet));
        assertFalse(receipt.untouched());assertEquals(0,receipt.confirmed(60));receipt.after();assertEquals(0,effects.get());
    }
    @Test void onlyAppliedContributionsEmitAndInvalidAmountsCannotMintCharge(){
        var receipt=new ArmorTickReceipt();var effects=new AtomicInteger();
        receipt.add(()->false,()->effects.addAndGet(100));receipt.add(()->true,effects::incrementAndGet);
        assertEquals(36,receipt.confirmed(36));receipt.after();assertEquals(1,effects.get());
        assertEquals(1000,ArmorReceiptPolicy.charged(1000,-1));
        assertEquals(1000,ArmorReceiptPolicy.charged(1000,1001));
        assertEquals(123,ArmorReceiptPolicy.charged(1000,123));
        assertEquals(0,ArmorReceiptPolicy.charged(1000,0));
    }
}
