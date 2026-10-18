package io.github.gerbesh.unusualcopper.reactor;

/** Server-owned accounting. Fuel is committed before begin(); output is never recreated on reload. */
public final class ReactorEngine {
    public static final int CAPACITY=16_000_000,BATCH=512_000,COOLANT=16_000,MAX_OUTPUT=32_000;
    public static final int[] RATES={2000,8000,32000};
    public int stored,remaining,heat,heatRemainder,mode=1;
    public long produced;
    public boolean cooling;
    public boolean canBegin(){return remaining==0&&CAPACITY-stored>=BATCH;}
    public boolean begin(){if(!canBegin())return false;remaining=BATCH;return true;}
    public int tick(int coolant,boolean enabled){
        if(heat>=9000)cooling=true;
        int removedHeat=1+(int)((long)Math.max(0,Math.min(COOLANT,coolant))*(100+heat/12)/COOLANT);
        heat=Math.max(0,heat-removedHeat);
        if(this.cooling&&heat<=7000)this.cooling=false;
        if(!enabled||coolant<1000||this.cooling)return 0;
        int made=Math.min(RATES[Math.floorMod(mode,3)],Math.min(remaining,CAPACITY-stored));
        made=Math.min(made,Math.max(0,(9000-heat)*64-heatRemainder));
        stored+=made;remaining-=made;produced+=made;
        int thermal=heatRemainder+made;heat+=thermal/64;heatRemainder=thermal%64;
        if(heat>=9000)this.cooling=true;
        return made;
    }
    public int extract(int amount,boolean simulate){int taken=Math.max(0,Math.min(amount,stored));if(!simulate)stored-=taken;return taken;}
    public void restore(int energy,int batch,int temperature,int remainder,int setting,long generated){
        stored=Math.max(0,Math.min(CAPACITY,energy));remaining=Math.max(0,Math.min(BATCH,batch));
        heat=Math.max(0,Math.min(12000,temperature));cooling=heat>=9000;heatRemainder=Math.floorMod(remainder,64);mode=Math.floorMod(setting,3);produced=Math.max(0,generated);
    }
}
