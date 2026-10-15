package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.world.entity.player.Player;

/** Current spendable XP comes from level and bar, never the historical totalExperience field. */
public final class XpWallet {
    public record State(int level,float progress,int total) {
        public boolean matches(Player p){return p.experienceLevel==level&&Float.floatToIntBits(p.experienceProgress)==Float.floatToIntBits(progress)&&p.totalExperience==total;}
        public void apply(Player p){p.experienceLevel=level;p.experienceProgress=progress;p.totalExperience=total;}
    }
    public static long base(int level){
        if(level<0||level>1_000_000)throw new IllegalArgumentException("Unsupported XP level");long l=level;
        return level<=16?l*l+6*l:level<=31?(5*l*l-81*l+720)/2:(9*l*l-325*l+4440)/2;
    }
    public static int span(int level){return(int)(base(level+1)-base(level));}
    public static State capture(Player p){if(p.experienceLevel<0||p.experienceLevel>=1_000_000||!Float.isFinite(p.experienceProgress)||p.experienceProgress<0||p.experienceProgress>=1)throw new IllegalArgumentException("Invalid XP state");return new State(p.experienceLevel,p.experienceProgress,p.totalExperience);}
    public static double points(State s){if(!Float.isFinite(s.progress)||s.progress<0||s.progress>=1)throw new IllegalArgumentException("Invalid XP progress");return base(s.level)+(double)s.progress*span(s.level);}
    public static double points(Player p){return points(capture(p));}
    public static State spend(State before,int price){
        double available=points(before);if(price<0||available+.0001<price)throw new IllegalArgumentException("Not enough XP points");double remaining=Math.max(0,available-price);
        int low=0,high=before.level;while(low<high){int middle=low+(high-low+1)/2;if(base(middle)<=remaining+.00001)low=middle;else high=middle-1;}
        float bar=(float)Math.max(0,(remaining-base(low))/span(low));bar=Math.min(Math.nextDown(1F),bar);
        return new State(low,bar,(int)Math.max(0L,(long)before.total-price));
    }
    private XpWallet(){}
}
