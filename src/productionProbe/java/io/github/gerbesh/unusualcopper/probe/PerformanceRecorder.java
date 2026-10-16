package io.github.gerbesh.unusualcopper.probe;

/** Probe-only clocks around the actual ordinary-JAR entry points. Never shipped in the mod. */
public final class PerformanceRecorder {
    public static final String[] NAMES={"machines","energy","items","area","sentinel","brewer","population"};
    public static boolean recording;
    private static final long[] START=new long[NAMES.length],TOTAL=new long[NAMES.length],CALLS=new long[NAMES.length];
    private PerformanceRecorder(){}
    public static void begin(int category){if(recording)START[category]=System.nanoTime();}
    public static void end(int category){if(recording){TOTAL[category]+=System.nanoTime()-START[category];CALLS[category]++;}}
    public static long[] take(){long[] result=new long[NAMES.length*2];for(int i=0;i<NAMES.length;i++){result[i]=TOTAL[i];result[i+NAMES.length]=CALLS[i];TOTAL[i]=CALLS[i]=0;}return result;}
}
