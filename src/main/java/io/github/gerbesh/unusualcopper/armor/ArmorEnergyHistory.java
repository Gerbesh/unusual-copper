package io.github.gerbesh.unusualcopper.armor;

/** Pure rolling one-second meter of actual successful debits, never an energy authority. */
public final class ArmorEnergyHistory {
    private final int[] spent=new int[20];private final long[] masks=new long[20];private long last=Long.MIN_VALUE;
    private void advance(long tick){
        if(last==Long.MIN_VALUE||tick<last||tick-last>=20){java.util.Arrays.fill(spent,0);java.util.Arrays.fill(masks,0);}
        else for(long t=last+1;t<=tick;t++){int i=(int)Math.floorMod(t,20);spent[i]=0;masks[i]=0;}
        last=tick;
    }
    public void record(long tick,int amount,int module){
        if(amount<=0||module<0||module>=63)throw new IllegalArgumentException("Debit sample");
        advance(tick);int i=(int)Math.floorMod(tick,20);spent[i]=(int)Math.min(Integer.MAX_VALUE,(long)spent[i]+amount);masks[i]|=1L<<module;
    }
    public int perSecond(long tick){advance(tick);long sum=0;for(int n:spent)sum+=n;return (int)Math.min(Integer.MAX_VALUE,sum);}
    public long active(long tick){advance(tick);long value=0;for(long n:masks)value|=n;return value;}
}
