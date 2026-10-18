package io.github.gerbesh.unusualcopper.armor;

/** A callback can confirm less than reserved, but cannot mint charge with an invalid quote. */
public final class ArmorReceiptPolicy {
    private ArmorReceiptPolicy(){}
    public static int charged(int authorized,int confirmed){
        if(authorized<1||authorized>10_000)throw new IllegalArgumentException("Authorized armor receipt");
        return confirmed<0||confirmed>authorized?authorized:confirmed;
    }
}
