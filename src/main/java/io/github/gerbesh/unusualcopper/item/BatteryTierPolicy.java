package io.github.gerbesh.unusualcopper.item;

/** Persistent portable CuE capacity policy. Signed-int NBT format remains unchanged. */
public final class BatteryTierPolicy {
    private BatteryTierPolicy() {}
    public static int capacityForTier(int tier) {
        return switch(tier) {
            case 0 -> 10_000;
            case 1 -> 100_000;
            case 2 -> 1_000_000;
            case 3 -> 10_000_000;
            case 4 -> 50_000_000;
            case 5 -> 250_000_000;
            default -> throw new IllegalArgumentException("Battery tier");
        };
    }
}
