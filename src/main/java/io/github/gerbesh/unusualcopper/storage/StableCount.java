package io.github.gerbesh.unusualcopper.storage;

/** UI observation only. A partial page is never promoted to a total. */
public final class StableCount {
    private long amount=-1;
    private boolean current;
    public void accept(long observed,boolean complete){current=complete;if(complete)amount=Math.max(0,observed);}
    public long amount(){return amount;}
    public boolean current(){return current;}
}
