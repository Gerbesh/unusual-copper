package io.github.gerbesh.unusualcopper.armor;

/** Pure balance invariants for modular armor capacity and paid module scheduling. */
public final class ArmorProgression {
    private ArmorProgression(){}
    public static int sockets(int tier,boolean chest) {
        if(tier<1||tier>3)throw new IllegalArgumentException("Invalid chassis tier");
        return switch(tier){
            case 1 -> 1;
            case 2 -> chest?3:2;
            default -> chest?4:3;
        };
    }
    public static int pieceBudget(boolean chest,boolean boots,boolean helmet) {
        return chest?650:boots?200:helmet?150:150;
    }
    public static int totalBudget(){return 900;}
    public static boolean validPayment(int charge) {return charge>0 && charge<=totalBudget();}
}
