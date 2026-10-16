package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.diagnostics.ServerLifecycleDiagnostics;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerLifecycleMixin {
    // Official/SRG targets are pinned in unusualcopper.refmap.json for MC 1.20.1.
    @Inject(method = "halt(Z)V", at = @At("HEAD"))
    private void unusualcopper$halt(boolean waitForCompletion, CallbackInfo callback) {
        ServerLifecycleDiagnostics.shutdown((MinecraftServer) (Object) this,
                "halt(waitForCompletion=" + waitForCompletion + ")");
    }

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void unusualcopper$stopServer(CallbackInfo callback) {
        ServerLifecycleDiagnostics.shutdown((MinecraftServer) (Object) this, "stopServer()");
    }
}
