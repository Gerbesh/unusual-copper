package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.network.MachineEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.*;

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ArboristEffects {
    private record Active(MachineEffects.Forestry event,long start) {}
    private static final ArrayDeque<Active> ACTIVE=new ArrayDeque<>();
    private static ClientLevel world;
    private static long messageTick=Long.MIN_VALUE,cuts,plants,particles;
    private static int messages;
    private static final DustParticleOptions AQUA=new DustParticleOptions(new Vector3f(.25F,1F,.8F),.8F);
    public record Statistics(long cuts,long plants,long particles,int active) {}
    public static Statistics statistics() { return new Statistics(cuts,plants,particles,ACTIVE.size()); }
    private static ClientLevel world() {
        var current=Minecraft.getInstance().level;
        if(current!=world) { world=current; ACTIVE.clear(); messageTick=Long.MIN_VALUE; messages=0; cuts=plants=particles=0; } return current;
    }
    public static int youngest(boolean planted) { var level=world(); return level==null?-1:ACTIVE.stream().filter(a->a.event.planted()==planted).mapToInt(a->(int)(level.getGameTime()-a.start)).min().orElse(-1); }
    public static void accept(MachineEffects.Forestry event) {
        var level=world(); if(level==null||!level.dimension().location().equals(event.dimension())) return;
        long now=level.getGameTime(); if(now!=messageTick) { messageTick=now; messages=0; } if(messages>=8) return; messages++;
        while(ACTIVE.size()>=8) ACTIVE.removeFirst(); ACTIVE.addLast(new Active(event,now)); if(event.planted()) plants++; else cuts++;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return; var level=world(); if(level==null) return;
        ACTIVE.removeIf(a->{long age=level.getGameTime()-a.start; return age<0||age>=16;}); int budget=48;
        for(var active:ACTIVE) {
            double age=level.getGameTime()-active.start, progress=age/16;
            for(var pos:active.event.positions()) for(int arm=0;arm<2 && budget>0;arm++,budget--) {
                double angle=age*.65+arm*Math.PI, radius=.55*(1-progress)+.1;
                double x=pos.getX()+.5+Math.cos(angle)*radius,y=pos.getY()+.1+progress*1.4,z=pos.getZ()+.5+Math.sin(angle)*radius;
                level.addParticle(arm==0?AQUA:ParticleTypes.END_ROD,x,y,z,0,.01,0); particles++;
            }
        }
    }
    private ArboristEffects() {}
}
