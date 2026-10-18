package io.github.gerbesh.unusualcopper.fluid.tank;

/** Shared, frame-rate independent plane. A late member packet cannot rewind a newer snapshot. */
public final class TankSurfaceAnimation<T> {
    private long revision=-1;private double value,target,last,touched;private T material;
    public TankSurfaceAnimation(double height,double now){
        if(!Double.isFinite(height)||!Double.isFinite(now))throw new IllegalArgumentException("Tank visual clock");
        value=target=height;last=touched=now;
    }
    public void advance(double now){
        if(!Double.isFinite(now)||now<last)return;
        double dt=now-last;last=now;value+=(target-value)*-Math.expm1(-dt/2.5D);touched=now;
    }
    public void accept(long nextRevision,double height,T nextMaterial){
        if(nextRevision<0||!Double.isFinite(height))throw new IllegalArgumentException("Tank visual snapshot");
        if(nextRevision<=revision)return;revision=nextRevision;target=height;if(nextMaterial!=null)material=nextMaterial;
    }
    public double value(){return value;}
    public double touched(){return touched;}
    public T material(){return material;}
}
