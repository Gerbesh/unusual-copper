package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionInputDemandPolicyTest {
    @Test void redstoneAlreadyInCorrectCoilSlotDoesNotBlockWireManufacture() {
        boolean[] occupied={false,true,false,true,true,true,false,true,false};
        boolean[] matching={true,true,true,true,true,true,true,true,true};
        boolean[] wire={false,true,false,true,false,true,false,true,false};
        int[] held={0,0,0,0,1,0,0,0,0}; // physically present redstone in middle
        assertEquals(4,ProductionInputDemandPolicy.missing(16,occupied,matching,wire,held));
        held[1]=16;
        assertEquals(3,ProductionInputDemandPolicy.missing(16,occupied,matching,wire,held));
    }

    @Test void existingCorrectIronObsidianAndFrameDoNotBlockPlatePress() {
        boolean[] occupied={true,true,true,true,true,true,true,true,true};
        boolean[] matching={true,true,true,true,true,true,true,true,true};
        boolean[] plates={true,false,true,false,false,false,true,false,true};
        int[] held={0,16,0,16,1,16,0,16,0}; // all non-press ingredients
        assertEquals(4,ProductionInputDemandPolicy.missing(16,occupied,matching,plates,held));
        held[0]=16;held[2]=16;held[6]=16;held[8]=16;
        assertEquals(0,ProductionInputDemandPolicy.missing(16,occupied,matching,plates,held));
    }

    @Test void invalidGridOrUnexpectedItemsBlockDispatch() {
        boolean[] occupied={false,true,false,true,true,true,false,true,false};
        boolean[] matching={true,true,true,true,true,true,true,true,true};
        boolean[] wire={false,true,false,true,false,true,false,true,false};
        int[] held=new int[9];
        held[0]=1; // extra physical item in intentionally empty recipe position
        assertEquals(-1,ProductionInputDemandPolicy.missing(1,occupied,matching,wire,held));
        held[0]=0;matching[4]=false;held[4]=1;
        assertEquals(-1,ProductionInputDemandPolicy.missing(1,occupied,matching,wire,held));
        held[4]=0;
        assertEquals(4,ProductionInputDemandPolicy.missing(1,occupied,matching,wire,held));
        held[1]=65;
        assertEquals(-1,ProductionInputDemandPolicy.missing(1,occupied,matching,wire,held));
    }
}
