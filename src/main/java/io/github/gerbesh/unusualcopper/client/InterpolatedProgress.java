package io.github.gerbesh.unusualcopper.client;

/** Snapshot reconciliation without position snaps. Values are ticks, not world coordinates. */
public final class InterpolatedProgress {
    private double received,anchor,correction,value;
    private long revision=Long.MIN_VALUE;
    private boolean initialized,wasRunning;
    public double sample(double now,double observed,long update,boolean running,double duration){
        if(!initialized){initialized=true;received=now;anchor=value=clamp(observed,duration);revision=update;}
        else if(update!=revision){
            advance(now,wasRunning,duration);
            revision=update;received=now;anchor=clamp(observed,duration);correction=value-anchor;
        }
        wasRunning=running;advance(now,running,duration);return value;
    }
    private void advance(double now,boolean running,double duration){
        double age=Math.max(0,now-received);
        // At most half a second of prediction, then hold until the next authoritative snapshot.
        double target=anchor+(running?Math.min(10,age):0);
        value=Math.max(value,clamp(target+correction*Math.exp(-age/3),duration));
    }
    private static double clamp(double value,double duration){return Math.max(0,Math.min(duration,value));}
}
