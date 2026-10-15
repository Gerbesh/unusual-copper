package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.gameplay.MobPopulations;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the data store rather than subclass callbacks which mods can override. */
@Mixin(SynchedEntityData.class)
public abstract class EntityDataPopulationMixin {
    @Shadow(aliases = "f_135344_", remap = false) @Final private Entity entity;
    @Inject(method = {"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", "m_276349_(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V"}, at = @At("TAIL"), remap = false)
    private void unusualcopper$data(CallbackInfo callback) { MobPopulations.changed(entity); }
    @Inject(method = {"assignValues(Ljava/util/List;)V", "m_135356_(Ljava/util/List;)V"}, at = @At("TAIL"), remap = false)
    private void unusualcopper$bulkData(CallbackInfo callback) { MobPopulations.changed(entity); }
}
