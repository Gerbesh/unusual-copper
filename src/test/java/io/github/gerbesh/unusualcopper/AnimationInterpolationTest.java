package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationInterpolationTest {
    @Test void snapshotsNeverTeleportOrReverseTheCube(){
        var motion=new InterpolatedProgress();double prior=motion.sample(0,0,1,true,60);
        for(int frame=1;frame<=600;frame++){
            double now=frame/10.0;int packet=frame/50;double server=Math.min(60,packet*5+(packet%2==0?0:3));
            double value=motion.sample(now,server,packet+2,true,60);
            assertTrue(value>=prior);assertTrue(value-prior<.3,"packet correction must be spread over frames");assertTrue(value<=60);prior=value;
        }
    }
    @Test void hugeForwardCorrectionStartsAtCurrentPositionAndConverges(){
        var motion=new InterpolatedProgress();assertEquals(10,motion.sample(0,10,1,false,60));
        assertEquals(10,motion.sample(1,50,2,false,60));
        double smooth=motion.sample(1.1,50,2,false,60);assertTrue(smooth>10&&smooth<12);
        assertTrue(motion.sample(25,50,2,false,60)>49.9);
    }
    @Test void lostUpdatesCannotSendCubePastPredictionWindow(){
        var motion=new InterpolatedProgress();motion.sample(0,20,1,true,60);
        assertEquals(30,motion.sample(1000,20,1,true,60));
        assertEquals(30,motion.sample(1001,25,2,false,60));
        assertEquals(30,motion.sample(1100,25,2,false,60));
    }
    @Test void steadyFlightStaysWithinOneFrameOfThreeSeconds(){
        for(int fps:new int[]{30,60,144}){
            var motion=new InterpolatedProgress();motion.sample(0,0,1,true,60);double value=0;
            for(int frame=1;frame<=fps*3;frame++){double ticks=frame*20.0/fps;long packet=(long)(ticks/5);value=motion.sample(ticks,packet*5,packet+1,true,60);}
            assertTrue(value>=60-20.0/fps,"snapshot arrival may trail by at most one frame");
            assertEquals(60,motion.sample(60+20.0/fps,60,13,true,60),1e-6);
        }
    }
    @Test void cosmeticClockIsFrameRateIndependentPauseAwareAndBoundsStalls(){
        var clock=new FrameClock();assertEquals(0,clock.sample(0,false));
        assertEquals(.1,clock.sample(5_000_000,false),1e-9);
        assertEquals(.1,clock.sample(1_000_000_000,true),1e-9);
        assertEquals(.2,clock.sample(1_005_000_000,false),1e-9);
        assertEquals(5.2,clock.sample(10_000_000_000L,false),1e-9);
    }
}
