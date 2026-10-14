package io.github.gerbesh.unusualcopper.machine;

public final class OperationMath {
    private OperationMath() {}
    public static int smeltTicks(int vanillaTicks) { return smeltTicks(vanillaTicks, 10); }
    public static int smeltTicks(int vanillaTicks, int divisor) {
        if (divisor < 1) throw new IllegalArgumentException("Invalid speed divisor");
        return (int)Math.max(1L, ((long)vanillaTicks + divisor - 1L) / divisor);
    }
    public static int payment(int totalCost, int nextTick, int totalTicks) {
        if (totalCost < 0 || totalTicks < 1 || nextTick < 1 || nextTick > totalTicks) throw new IllegalArgumentException("Invalid operation step");
        return (int)((long)totalCost * nextTick / totalTicks - (long)totalCost * (nextTick - 1) / totalTicks);
    }
}
