package io.github.gerbesh.unusualcopper.probe.mixin;

import io.github.gerbesh.unusualcopper.probe.PerformanceRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="io.github.gerbesh.unusualcopper.gameplay.AreaWorkScheduler",remap=false)
public abstract class AreaTimer {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void unusualcopperProbe$begin(CallbackInfo ci){PerformanceRecorder.begin(3);}
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void unusualcopperProbe$end(CallbackInfo ci){PerformanceRecorder.end(3);}
}
