package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.gameplay.MobPopulations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractHorse.class)
public abstract class HorsePopulationMixin {
    @Inject(method = {"setOwnerUUID(Ljava/util/UUID;)V", "m_30586_(Ljava/util/UUID;)V"}, at = @At("TAIL"), remap = false)
    private void unusualcopper$owner(CallbackInfo callback) { MobPopulations.changed((Entity)(Object)this); }
}
