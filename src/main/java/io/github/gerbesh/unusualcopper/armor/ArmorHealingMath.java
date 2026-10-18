package io.github.gerbesh.unusualcopper.armor;

/** Quotes the actual representable health target, not an ideal decimal HP delta. */
public final class ArmorHealingMath {
    public record Quote(float target,float hp,int cue){}
    private static final Quote EMPTY=new Quote(0,0,0);
    private ArmorHealingMath(){}
    public static Quote quote(float before,float maximum,float limit,int cuePerHp,int available){
        if(cuePerHp<1||cuePerHp>2000)throw new IllegalArgumentException("Healing HP price");
        if(!Float.isFinite(before)||!Float.isFinite(maximum)||!Float.isFinite(limit)
                ||before<0||maximum<=before||limit<=0||available<=0)return EMPTY;
        int budget=Math.min(10_000,available);
        double delta=Math.min(Math.min((double)maximum-before,limit),budget/(double)cuePerHp);
        float target=(float)Math.min(maximum,before+delta);
        for(int adjustment=0;adjustment<4&&target>before;adjustment++){
            float actual=target-before;double price=Math.ceil(actual*(double)cuePerHp);
            if(price<=budget&&actual<=limit)return new Quote(target,actual,(int)price);
            target=Math.nextDown(target);
        }
        return EMPTY;
    }
    public static int confirmed(float actual,float ownLimit,int cuePerHp,int authorized){
        if(!Float.isFinite(actual)||!Float.isFinite(ownLimit)||ownLimit<0)return authorized;
        if(actual<=0)return 0;
        double cost=Math.ceil(Math.min(actual,ownLimit)*(double)cuePerHp);
        return (int)Math.min(authorized,cost);
    }
}
