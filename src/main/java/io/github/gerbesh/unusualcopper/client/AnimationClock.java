package io.github.gerbesh.unusualcopper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Shared by disposable effects; pause and world changes cannot produce a visual time jump. */
final class AnimationClock {
    private static ClientLevel world;
    private static FrameClock clock=new FrameClock();
    static double now(){
        var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;clock=new FrameClock();}
        return clock.sample(System.nanoTime(),mc.isPaused()||mc.level==null);
    }
    private AnimationClock(){}
}
