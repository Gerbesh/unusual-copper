package io.github.gerbesh.unusualcopper.armor;

/**
 * F3A: bounded, paid *kinematic* thrust. This never grants mayfly or changes
 * PlayerAbilities, so vanilla, creative and third-party flight rights stay owned
 * by their providers. Not a replacement for F4's future genuine flight grant.
 */
public final class LimitedFlightPolicy {
    public static final int REQUIRED_TIER=3;
    public static final int CUE_PER_TICK=200;
    public static final long MAX_SESSION_TICKS=1200;
    public static final double MAX_HEIGHT=64.0D;
    public static final double MAX_HORIZONTAL=0.42D;
    private LimitedFlightPolicy(){}

    public static boolean canThrust(int chestTier,int bootsTier,long now,long sessionStart,
                                     boolean sameDimension,boolean inWorldBorder) {
        return chestTier>=REQUIRED_TIER && bootsTier>=REQUIRED_TIER
                && sameDimension && inWorldBorder && now>=sessionStart
                && now-sessionStart<MAX_SESSION_TICKS;
    }

    /** Remains locked until physical landing, even if the player releases the key. */
    public static int remainingTicks(long now,long started,boolean sameDimension) {
        if(!sameDimension||started<0||now<started)return 0;
        return (int)Math.max(0L,MAX_SESSION_TICKS-Math.min(MAX_SESSION_TICKS,now-started));
    }

    /** HUD only: clamp to a bounded, conservative displayed height allowance. */
    public static int headroom(double anchorY,double currentY,boolean sameDimension) {
        if(!sameDimension||!Double.isFinite(anchorY)||!Double.isFinite(currentY))return 0;
        double remaining=anchorY+MAX_HEIGHT-currentY;
        return (int)Math.floor(Math.max(0D,Math.min(MAX_HEIGHT,remaining)));
    }

    /** Vanilla gravity compensation, with upward/downward steering using look pitch. */
    public static double vertical(double lookY,double remainingHeight) {
        if(!Double.isFinite(lookY)||!Double.isFinite(remainingHeight))
            throw new IllegalArgumentException("Invalid flight geometry");
        double requested=Math.max(-0.13D,Math.min(0.28D,0.075D+Math.max(-1,Math.min(1,lookY))*0.22D));
        return Math.max(-0.28D,Math.min(requested,remainingHeight));
    }

    /** Bound both horizontal components together: diagonal steering is not faster. */
    public static double[] horizontal(double x,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(z))
            throw new IllegalArgumentException("Invalid horizontal thrust");
        double length=Math.hypot(x,z);
        if(length<=MAX_HORIZONTAL)return new double[]{x,z};
        double scale=MAX_HORIZONTAL/length;
        return new double[]{x*scale,z*scale};
    }
}
