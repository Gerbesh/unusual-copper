package io.github.gerbesh.unusualcopper.machine;

/** Read-only conservative selection of one already placed E2 press operation. */
public final class ProductionPressPolicy {
    private ProductionPressPolicy() {}
    /**
     * Inputs are true stock census counts; -1 means incomplete/unknown.
     * A nonzero quantity of desired output must be consumed from storage before
     * requesting another production batch. Intermediate output is never invented.
     */
    public static int stage(int pattern,int missingSlots,long finishedStock,long plateStock) {
        if((pattern!=0&&pattern!=1) || missingSlots<=0 || finishedStock!=0)return 0;
        if(pattern==1)return ProductionPressTask.PLATE;
        if(plateStock<0)return 0;
        return plateStock>=2?ProductionPressTask.WIRE:ProductionPressTask.PLATE;
    }
}
