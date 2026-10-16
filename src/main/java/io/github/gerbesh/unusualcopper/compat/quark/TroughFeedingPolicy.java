package io.github.gerbesh.unusualcopper.compat.quark;

/** The population limit is checked before feeding, including babies and other nearby species. */
public final class TroughFeedingPolicy {
    public static boolean permits(int animals,int limit,int age,boolean canFallInLove) {
        return animals>=0 && animals<limit && age==0 && canFallInLove;
    }
    private TroughFeedingPolicy() {}
}
