package io.github.gerbesh.unusualcopper.armor;

/** Pure upgrade policy: paid conversion of two physical chassis into one higher tier. */
public final class ArmorUpgradePolicy {
    private ArmorUpgradePolicy() {}

    public static int energyCost(int sourceTier) {
        return switch(sourceTier) {
            case 1 -> 24_000;
            case 2 -> 60_000;
            default -> 0;
        };
    }

    /** Retain the fraction of remaining durability when the maximum increases. */
    public static int damageAfterUpgrade(int damage, int previousMax, int upgradedMax) {
        if(previousMax <= 0 || upgradedMax <= 0 || damage < 0 || damage >= previousMax)
            throw new IllegalArgumentException("Invalid chassis durability");
        return (int)Math.min(upgradedMax-1,
                ((long)damage*upgradedMax+previousMax/2L)/previousMax);
    }
}
