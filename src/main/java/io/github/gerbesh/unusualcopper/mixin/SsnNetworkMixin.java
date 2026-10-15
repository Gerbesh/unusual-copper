package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.compat.ssn.SsnNetworkRevision;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.lothrazar.storagenetwork.block.main.NetworkModule",remap=false)
public abstract class SsnNetworkMixin implements SsnNetworkRevision {
    @Unique private long unusualcopper$revision;
    @Override public long unusualcopper$networkRevision(){return unusualcopper$revision;}
    // Optional versions may lack this API. The runtime adapter separately requires the pinned API and this interface.
    @Inject(method="doRefresh(Lcom/lothrazar/storagenetwork/api/DimPos;)V",at=@At("TAIL"),require=0,remap=false)
    private void unusualcopper$refreshed(CallbackInfo callback){unusualcopper$revision++;}
}
