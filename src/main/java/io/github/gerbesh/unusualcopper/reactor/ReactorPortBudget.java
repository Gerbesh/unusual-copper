package io.github.gerbesh.unusualcopper.reactor;

import java.util.Arrays;

/** One ledger per controller, shared by every physical socket, network and retained capability. */
public final class ReactorPortBudget {
    private long tick=Long.MIN_VALUE;
    private int used;
    private final int[] sockets=new int[8];
    public int transfer(long now,int socket,int requested,boolean simulate){
        if(socket<0||socket>=8||requested<=0)return 0;
        if(tick!=now){tick=now;used=0;Arrays.fill(sockets,0);}
        int amount=Math.min(requested,Math.min(4096-sockets[socket],ReactorEngine.MAX_OUTPUT-used));
        if(!simulate){sockets[socket]+=amount;used+=amount;}return amount;
    }
    public int refund(long now,int socket,int requested){
        if(now!=tick||socket<0||socket>=8||requested<=0)return 0;
        int amount=Math.min(requested,sockets[socket]);sockets[socket]-=amount;used-=amount;return amount;
    }
    public int delivered(long now){return now==tick?used:0;}
}
