package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.compat.quark.QuarkTroughGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="org.violetmoon.quark.content.automation.block.be.FeedingTroughBlockEntity",remap=false)
public abstract class QuarkFeedingTroughMixin extends BlockEntity {
    protected QuarkFeedingTroughMixin(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }

    @Inject(method="tryFeedingAnimal(Lnet/minecraft/world/entity/animal/Animal;)Lorg/violetmoon/quark/content/automation/block/be/FeedingTroughBlockEntity$FeedResult;",
            at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void unusualcopper$beforeFeeding(Animal animal,CallbackInfoReturnable<Object> callback) {
        var denied=QuarkTroughGuard.deniedResult(this,animal);
        if(denied!=null)callback.setReturnValue(denied);
    }
}
