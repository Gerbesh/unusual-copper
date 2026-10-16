package io.github.gerbesh.unusualcopper.gameplay;

/** Negative progress prepares a target; 0..60 is the distance-independent beam flight. */
public final class MiningTiming {
    public static final int FLIGHT_TICKS=60;
    public static int preparation(int workTicks){return -Math.max(0,Math.min(404,workTicks));}
    public static int advance(int progress,long elapsed){return (int)Math.min(FLIGHT_TICKS,(long)progress+Math.max(0,Math.min(464,elapsed)));}
    public static float fraction(float progress){return Math.max(0,Math.min(1,progress/FLIGHT_TICKS));}
    private MiningTiming(){}
}
