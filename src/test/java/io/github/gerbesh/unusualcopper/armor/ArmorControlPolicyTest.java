package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ArmorControlPolicyTest {
    @Test void staleAndReplacedEquipmentSessionsCannotCommit(){
        var nonce=UUID.randomUUID();assertTrue(ArmorControlPolicy.current(nonce,7,nonce,7));
        assertFalse(ArmorControlPolicy.current(nonce,8,nonce,7));assertFalse(ArmorControlPolicy.current(nonce,7,UUID.randomUUID(),7));
        assertFalse(ArmorControlPolicy.current(nonce,-1,nonce,-1));assertFalse(ArmorControlPolicy.current(null,0,nonce,0));
    }
    @Test void noUnknownSlotModuleProfileOrUnboundedSettings(){
        var op=ArmorControlPolicy.Operation.ENABLE;
        assertTrue(ArmorControlPolicy.valid(op,0,0,1,9));
        assertFalse(ArmorControlPolicy.valid(op,4,0,1,9));assertFalse(ArmorControlPolicy.valid(op,0,9,1,9));assertFalse(ArmorControlPolicy.valid(op,0,0,900,9));
        assertFalse(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PROFILE,0,-1,2,9));
        assertTrue(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PROFILE,1,-1,3,9));
        assertFalse(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PROFILE,1,-1,4,9));
        assertFalse(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.MINIMUM,1,4,96,9));
        assertFalse(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.TRIGGER,1,4,Integer.MAX_VALUE,9));
        assertTrue(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PRIORITY,1,4,2,9));
        assertTrue(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PRIORITY,1,4,6,9));
        assertFalse(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PRIORITY,1,4,1,9));
        assertFalse(ArmorControlPolicy.valid(ArmorControlPolicy.Operation.PRIORITY,1,4,7,9));
    }
}
