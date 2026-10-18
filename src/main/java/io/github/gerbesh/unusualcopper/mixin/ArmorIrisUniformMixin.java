package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.client.ArmorSenseClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Oculus 1.8/Iris uniform; no foreign classes are linked into the mod. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.uniforms.CommonUniforms",remap=false)
public abstract class ArmorIrisUniformMixin {
    @Inject(method="getNightVision()F",at=@At("RETURN"),cancellable=true,require=0,remap=false)
    private static void unusualcopper$paidShaderVision(CallbackInfoReturnable<Float> callback){
        if(ArmorSenseClient.nightVision())callback.setReturnValue(Math.max(callback.getReturnValueF(),1F));
    }
}
