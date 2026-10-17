package io.github.gerbesh.unusualcopper.machine;

/** Bounded physical transfers into E5's persistent nine-slot owned grid. */
public final class ProductionSupplyPolicy {
    public static final int GRID_SIZE=9;
    public static final int FIRST_ESCROW_SLOT=4;
    public static final int MAX_WITHDRAW_PER_STEP=8;
    private ProductionSupplyPolicy() {}

    public static int needed(int queued,int held,int slotLimit,int sourceOffered) {
        if(queued<=0 || held<0 || slotLimit<0 || sourceOffered<=0)return 0;
        int target=Math.min(slotLimit,Math.min(ProductionOrderPolicy.MAX_QUEUE,queued));
        return Math.min(MAX_WITHDRAW_PER_STEP,
                Math.min(sourceOffered,Math.max(0,target-held)));
    }

    public static int cargoSlot(int gridSlot) {
        if(gridSlot<0 || gridSlot>=GRID_SIZE)throw new IllegalArgumentException("Invalid production grid slot");
        return FIRST_ESCROW_SLOT+gridSlot;
    }

    public static boolean gridCargoSlot(int slot) {
        return slot>=FIRST_ESCROW_SLOT && slot<FIRST_ESCROW_SLOT+GRID_SIZE;
    }
}
