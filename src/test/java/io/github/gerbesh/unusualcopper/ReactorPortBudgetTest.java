package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.reactor.ReactorPortBudget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactorPortBudgetTest {
    @Test void eightSeparateNetworksShare32000AndEachSocketHas4096(){
        var ledger=new ReactorPortBudget();int total=0;
        for(int socket=0;socket<8;socket++){
            assertTrue(ledger.transfer(100,socket,100000,true)<=4096);
            total+=ledger.transfer(100,socket,100000,false);
            assertEquals(0,ledger.transfer(100,socket,1,false));
        }
        assertEquals(32000,total);assertEquals(32000,ledger.delivered(100));assertEquals(4096,ledger.transfer(101,0,10000,false));
    }
    @Test void simulationsAndRejectedReceiverRefundsDoNotConsumeTheBudget(){
        var ledger=new ReactorPortBudget();for(int i=0;i<50;i++)assertEquals(4096,ledger.transfer(10,0,9999,true));
        assertEquals(1000,ledger.transfer(10,0,1000,false));assertEquals(600,ledger.refund(10,0,600));
        assertEquals(3696,ledger.transfer(10,0,9999,false));assertEquals(4096,ledger.delivered(10));
        assertEquals(0,ledger.refund(9,0,500));assertEquals(4096,ledger.refund(10,0,99999));assertEquals(0,ledger.delivered(10));
    }
    @Test void invalidPortsAndAmountsNeverDebitOrCreateNegativeFlow(){
        var ledger=new ReactorPortBudget();assertEquals(0,ledger.transfer(0,-1,5,false));assertEquals(0,ledger.transfer(0,8,5,false));assertEquals(0,ledger.transfer(0,0,-1,false));assertEquals(0,ledger.refund(0,0,1));
        assertEquals(0,ledger.delivered(0));assertEquals(4096,ledger.transfer(Long.MAX_VALUE,0,Integer.MAX_VALUE,false));
    }
}
