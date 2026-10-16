package io.github.gerbesh.unusualcopper.client;

import java.util.*;

/** Cached unit sphere: the renderer scales it without allocating a new mesh every frame. */
public final class MiningSphereMesh {
    public static final float RADIUS=2.5F;
    public record Vertex(float x,float y,float z,float u,float v){}
    public static final List<Vertex> VERTICES=build();
    private static Vertex vertex(int lat,int lon){double a=-Math.PI/2+lat*Math.PI/8,b=lon*Math.PI/8;return new Vertex((float)(Math.cos(a)*Math.cos(b)),(float)Math.sin(a),(float)(Math.cos(a)*Math.sin(b)),lon/16F,lat/8F);}
    private static List<Vertex> build(){var result=new ArrayList<Vertex>(512);for(int lat=0;lat<8;lat++)for(int lon=0;lon<16;lon++){result.add(vertex(lat,lon));result.add(vertex(lat+1,lon));result.add(vertex(lat+1,lon+1));result.add(vertex(lat,lon+1));}return List.copyOf(result);}
    private MiningSphereMesh(){}
}
