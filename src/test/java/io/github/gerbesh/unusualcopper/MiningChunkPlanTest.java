package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MiningChunkPlanTest {
    @Test void alignedMaximumCoversExactlyEightByEightChunks() {
        var plan=MiningChunkPlan.create(new MiningFootprint(0,0,127,127),8,8,10,10);
        assertEquals(64,plan.chunks().size());
        assertEquals(1,plan.chunks().values().stream().filter(Boolean::booleanValue).count());
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)assertTrue(plan.chunks().containsKey(MiningChunkPlan.key(x,z)));
    }
    @Test void unalignedMaximumCoversAllEightyOneChunks() {
        var plan=MiningChunkPlan.create(new MiningFootprint(1,1,128,128),8,8,10,10);
        assertEquals(81,plan.chunks().size());
        for(int x=0;x<=8;x++)for(int z=0;z<=8;z++)assertTrue(plan.chunks().containsKey(MiningChunkPlan.key(x,z)));
        assertFalse(plan.chunks().get(MiningChunkPlan.key(8,8)));
    }
    @Test void negativeChunksUseFloorDivisionAndDoNotAlias() {
        var plan=MiningChunkPlan.create(new MiningFootprint(-17,-17,-1,-1),-8,-8,-5,-5);
        assertEquals(4,plan.chunks().size());
        assertTrue(plan.chunks().containsKey(MiningChunkPlan.key(-2,-2)));
        assertFalse(plan.chunks().containsKey(MiningChunkPlan.key(0,0)));
        assertNotEquals(MiningChunkPlan.key(-1,0),MiningChunkPlan.key(0,-1));
    }
    @Test void controllerAndStoragePromoteAreaChunksToTicking() {
        var plan=MiningChunkPlan.create(new MiningFootprint(0,0,63,63),8,8,40,40);
        assertTrue(plan.chunks().get(MiningChunkPlan.key(0,0)));
        assertTrue(plan.chunks().get(MiningChunkPlan.key(2,2)));
        assertFalse(plan.chunks().get(MiningChunkPlan.key(1,1)));
        assertEquals(16,plan.chunks().size());
    }
    @Test void storageAtCornerKeepsOnlyTheActualChestPartnerChunk() {
        var plan=MiningChunkPlan.create(new MiningFootprint(40,40,40,40),40,40,-16,-16).withStoragePartner(-17,-16);
        assertTrue(plan.chunks().get(MiningChunkPlan.key(-1,-1)));
        assertTrue(plan.chunks().get(MiningChunkPlan.key(-2,-1)));
        assertFalse(plan.chunks().containsKey(MiningChunkPlan.key(-1,-2)));
        assertFalse(plan.chunks().containsKey(MiningChunkPlan.key(-2,-2)));
        assertEquals(3,plan.chunks().size());
    }
    @Test void plansAreImmutableAndControllerOnlyIsOneTicket() {
        var plan=MiningChunkPlan.controller(-1,16);
        assertEquals(java.util.Map.of(MiningChunkPlan.key(-1,1),true),plan.chunks());
        assertThrows(UnsupportedOperationException.class,()->plan.chunks().clear());
    }
    @Test void ordinaryContainerOnChunkCornerDoesNotLoadUnrelatedNeighbours() {
        var plan=MiningChunkPlan.create(new MiningFootprint(40,40,40,40),40,40,-16,-16);
        assertEquals(2,plan.chunks().size());
        assertFalse(plan.chunks().containsKey(MiningChunkPlan.key(-2,-1)));
        assertFalse(plan.chunks().containsKey(MiningChunkPlan.key(-1,-2)));
    }
    @Test void maximumHorizontalFootprintAndRemoteServicesStayBounded() {
        var plan=MiningChunkPlan.create(new MiningFootprint(1,1,128,128),200,200,-16,-16).withStoragePartner(-17,-16);
        assertEquals(MiningChunkPlan.MAX_TICKETS,plan.chunks().size());
    }
    @Test void quotaDenialIsAtomicAndReplacementReusesItsReservation() {
        var budget=new MiningChunkReservations<String>();var owner=UUID.randomUUID();
        assertTrue(budget.reserve("a",owner,81,100,200));
        assertFalse(budget.reserve("b",owner,20,100,200));assertEquals(81,budget.total());
        assertTrue(budget.reserve("a",owner,84,100,200));assertEquals(84,budget.total());
        assertFalse(budget.reserve("a",owner,84,83,200));assertEquals(84,budget.total());
    }
    @Test void ownersShareDimensionBudgetAndReleaseRestoresAdmission() {
        var budget=new MiningChunkReservations<String>();var a=UUID.randomUUID();var b=UUID.randomUUID();
        assertTrue(budget.reserve("a",a,81,100,150));assertFalse(budget.reserve("b",b,81,100,150));
        budget.release("a");assertTrue(budget.reserve("b",b,81,100,150));
        assertTrue(budget.reserve("b",b,0,100,150));assertEquals(0,budget.total());
    }
    @Test void ownerChangesAndBadRequestsCannotLeaveFreeOrNegativeReservations() {
        var budget=new MiningChunkReservations<String>();var a=UUID.randomUUID();var b=UUID.randomUUID();
        assertTrue(budget.reserve("a",a,81,100,200));assertTrue(budget.reserve("a",b,81,100,200));
        assertTrue(budget.reserve("b",a,81,100,200));assertEquals(162,budget.total());
        assertFalse(budget.reserve("x",null,1,100,200));assertFalse(budget.reserve("x",a,-1,100,200));
        assertFalse(budget.reserve("x",a,86,1000,1000));assertEquals(162,budget.total());
    }
}
