package io.github.gerbesh.unusualcopper.machine;

/** Advisory storage census, never a reservation, lease or promise of future availability. */
public final class ProductionStockPolicy {
    private ProductionStockPolicy() {}
    public record Estimate(boolean complete,long available,long missing) {}
    public static Estimate estimate(long required,long counted,boolean complete) {
        if(required<0)throw new IllegalArgumentException("Invalid material requirement");
        if(!complete || counted<0)return new Estimate(false,0L,0L);
        return new Estimate(true,Math.max(0L,counted),Math.max(0L,required-counted));
    }
}
