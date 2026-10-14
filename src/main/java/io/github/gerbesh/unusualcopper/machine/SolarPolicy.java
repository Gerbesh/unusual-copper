package io.github.gerbesh.unusualcopper.machine;

public final class SolarPolicy {
    private SolarPolicy() {}
    /** Weather changes the rate, never turns daytime into nighttime. */
    public static int rate(long time, boolean hasSkylight, boolean seesSky, boolean rain, boolean thunder) {
        if (!hasSkylight || !seesSky || Math.floorMod(time, 24_000L) >= 12_000L) return 0;
        return thunder ? 1 : rain ? 2 : 4;
    }
}
