package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.gameplay.MobPopulations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobLeashPopulationMixin {
    @Inject(method = {"setLeashedTo(Lnet/minecraft/world/entity/Entity;Z)V", "m_21463_(Lnet/minecraft/world/entity/Entity;Z)V", "dropLeash(ZZ)V", "m_21455_(ZZ)V"}, at = @At("TAIL"), remap = false, require = 2)
    private void unusualcopper$leash(CallbackInfo callback) { MobPopulations.changed((Entity)(Object)this); }
}
