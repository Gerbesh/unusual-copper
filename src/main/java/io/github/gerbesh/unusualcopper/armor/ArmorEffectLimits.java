package io.github.gerbesh.unusualcopper.armor;

/** Bounded numeric quotes, shared by server effects and their regression checks. */
public final class ArmorEffectLimits {
    private ArmorEffectLimits() {}
    public record Shield(float prevented,int cue) {}
    public static Shield shield(float incoming,int level,int available) {
        if(level<1||level>3)throw new IllegalArgumentException("Shield level");
        if(!Float.isFinite(incoming)||incoming<=0||available<=0)return new Shield(0,0);
        int perHp=switch(level){case 1->300;case 2->600;default->1200;};
        float prevented=(float)Math.min(Math.min(incoming,2D*level),available/(double)perHp);
        int cost=(int)Math.ceil(prevented*(double)perHp);
        if(cost>available){prevented=Math.nextDown(prevented);cost=(int)Math.ceil(prevented*(double)perHp);}
        return prevented>0?new Shield(prevented,cost):new Shield(0,0);
    }
    public static float mining(float current,int level) {
        if(level<1||level>3)throw new IllegalArgumentException("Mining level");
        if(!Float.isFinite(current)||current<=0)return current;
        double factor=switch(level){case 1->1.4D;case 2->1.8D;default->2.4D;};
        return current>=64F?current:(float)Math.min(64D,current*factor);
    }
    /** Apply the cap after foreign attribute multipliers, without lowering foreign bonuses. */
    public static double addition(double current,double requested,double cap,double multiplier) {
        if(!Double.isFinite(current)||!Double.isFinite(requested)||!Double.isFinite(cap)
                ||!Double.isFinite(multiplier)||requested<=0||multiplier<=0)return 0;
        return Math.max(0,Math.min(requested,(cap-current)/multiplier));
    }
}
