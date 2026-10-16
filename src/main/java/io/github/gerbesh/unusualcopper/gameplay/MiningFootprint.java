package io.github.gerbesh.unusualcopper.gameplay;

/** Horizontal inclusive footprint; height is intentionally independent of the area wand's two clicks. */
public record MiningFootprint(int minX,int minZ,int maxX,int maxZ) {
    public static final int MAX_SIDE=128,REACH=128;
    public MiningFootprint {long w=(long)maxX-minX+1,d=(long)maxZ-minZ+1;if(w<1||d<1||w>MAX_SIDE||d>MAX_SIDE)throw new IllegalArgumentException("Mining footprint exceeds 128x128");}
    /** Per-axis reach supports a full-size footprint with the station at a corner as well as its centre. */
    public static boolean reachable(int originX,int originZ,int x,int z){return Math.abs((long)x-originX)<=REACH&&Math.abs((long)z-originZ)<=REACH;}
    public int width(){return maxX-minX+1;}public int depth(){return maxZ-minZ+1;}public int columns(){return width()*depth();}
    public boolean contains(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
    public int x(int column){return minX+column%width();}public int z(int column){return minZ+column/width();}
    /** Quarter of the legacy price, including old configs and the old cap; round up whole CuE. */
    public static int price(int base,int columns,int depth,float hardness){
        double scaled=Math.max(1,base)*(1+Math.max(0,columns-1)/256.0)*(1+Math.max(0,depth)/128.0)*Math.max(1,hardness/3.0);
        int legacy=(int)Math.min(1_000_000,Math.ceil(scaled));return Math.max(1,(legacy+3)/4);
    }
    public static int duration(float hardness,float speed){return Math.max(20,Math.min(400,(int)Math.ceil(60*Math.max(.25,hardness)/Math.max(.1,speed))));}
    public record Holding(int cost,int quarters){}
    public static Holding holding(int columns,int carried){int quarters=(Math.max(1,Math.min(16384,columns))+31)/32+Math.floorMod(carried,4);return new Holding(quarters/4,quarters%4);}
}
