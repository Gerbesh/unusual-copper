package io.github.gerbesh.unusualcopper.gameplay;

/** Integer prices for actual restored durability; XP means points, never levels. */
public record RepairPrice(int points,int cue,int xp) {
    public RepairPrice{if(points<1||points>4096||cue<1||xp<1)throw new IllegalArgumentException("Invalid repair payment");}
    public static RepairPrice of(int damage,int requested,int cuePerPoint,int durabilityPerXp){
        if(damage<=0||requested<1||requested>4096||cuePerPoint<1||durabilityPerXp<1)throw new IllegalArgumentException("Invalid repair quote");
        int points=Math.min(damage,requested);long cue=(long)points*cuePerPoint;
        if(cue>Integer.MAX_VALUE)throw new IllegalArgumentException("Repair price overflow");
        return new RepairPrice(points,(int)cue,(int)(((long)points+durabilityPerXp-1)/durabilityPerXp));
    }
}
