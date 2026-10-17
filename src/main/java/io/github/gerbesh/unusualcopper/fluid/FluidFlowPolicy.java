package io.github.gerbesh.unusualcopper.fluid;

/** Independent numbers kept small enough for pure-Java JUnit verification. */
public final class FluidFlowPolicy {
    public static final int MIN_SURFACE_SOURCES=15;
    public static final int HARVEST_RADIUS=15;
    public static final int BATCH_MB=250;
    public static final int TICKS_PER_BATCH=5;
    public static final int WATER_CUE=40;
    public static final int OTHER_CUE=120;
    private FluidFlowPolicy(){}
    public static int pumpCuE(boolean water){return water?WATER_CUE:OTHER_CUE;}
    public static int budgetedTransfer(int requested,int freeCapacity){
        return Math.max(0,Math.min(BATCH_MB,Math.min(requested,freeCapacity)));
    }
}
