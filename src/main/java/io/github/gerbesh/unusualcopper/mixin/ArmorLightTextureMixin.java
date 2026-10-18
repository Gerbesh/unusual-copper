package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.client.ArmorSenseClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Render-only night vision. Never creates, refreshes or removes a MobEffectInstance. */
@Mixin(LightTexture.class)
public abstract class ArmorLightTextureMixin {
    // MC 1.20.1 official/SRG references are pinned in unusualcopper.refmap.json.
    @Redirect(method="updateLightTexture(F)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z"))
    private boolean unusualcopper$paidVision(LocalPlayer player,MobEffect effect){
        return player.hasEffect(effect)||effect==MobEffects.NIGHT_VISION&&ArmorSenseClient.nightVision();
    }
    @Redirect(method="updateLightTexture(F)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float unusualcopper$visionScale(LivingEntity entity,float partial){
        float external=entity.hasEffect(MobEffects.NIGHT_VISION)?GameRenderer.getNightVisionScale(entity,partial):0F;
        return Math.max(external,ArmorSenseClient.nightVision()?1F:0F);
    }
}
