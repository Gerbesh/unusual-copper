package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.gameplay.MobPopulations;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explicit official/SRG pairs for the pinned Minecraft 1.20.1 runtime. No obfuscated client names. */
@Mixin(Entity.class)
public abstract class EntityPopulationMixin {
    @Inject(method = {"setPosRaw(DDD)V", "m_20343_(DDD)V"}, at = @At("TAIL"), remap = false)
    private void unusualcopper$position(CallbackInfo callback) { MobPopulations.changed((Entity)(Object)this); }
    @Inject(method = "onAddedToWorld()V", at = @At("TAIL"), remap = false)
    private void unusualcopper$added(CallbackInfo callback) { MobPopulations.added((Entity)(Object)this); io.github.gerbesh.unusualcopper.gameplay.ReaperLoot.accepted((Entity)(Object)this); }
    @Inject(method = "onRemovedFromWorld()V", at = @At("TAIL"), remap = false)
    private void unusualcopper$removed(CallbackInfo callback) { MobPopulations.removed((Entity)(Object)this); }
}
