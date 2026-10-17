package io.github.gerbesh.unusualcopper.armor;

/**
 * F2 is paid vertical stabilization, never a flight permission.
 * A 30-second sustained session requires a five-second enforced recovery.
 */
public final class HoverPolicy {
    public static final int REQUIRED_TIER=2;
    public static final int CUE_PER_TICK=80;
    public static final int MAX_SESSION_TICKS=600;
    public static final int REST_TICKS=100;
    private HoverPolicy(){}

    public static boolean canRun(int tier,long now,long started,long restUntil) {
        return tier>=REQUIRED_TIER && now>=restUntil && now>=started
                && now-started<MAX_SESSION_TICKS;
    }

    public static long restUntil(long start) {
        if(start<0 || start>Long.MAX_VALUE-(MAX_SESSION_TICKS+REST_TICKS))
            return Long.MAX_VALUE;
        return start+MAX_SESSION_TICKS+REST_TICKS;
    }
}
