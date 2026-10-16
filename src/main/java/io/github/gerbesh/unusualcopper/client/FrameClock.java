package io.github.gerbesh.unusualcopper.client;

/** Monotonic cosmetic time in 20 Hz units. Independent of server clock corrections. */
public final class FrameClock {
    private long last=Long.MIN_VALUE;
    private double ticks;
    public double sample(long nanos,boolean paused){
        if(last!=Long.MIN_VALUE&&!paused)ticks+=Math.max(0,Math.min(250_000_000L,nanos-last))/50_000_000.0;
        last=nanos;return ticks;
    }
}
