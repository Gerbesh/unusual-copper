package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.storage.InventoryChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both normal container changes and vanilla static furnace-style dirty notifications. */
@Mixin(BlockEntity.class)
public abstract class InventoryChangesMixin {
    @Inject(method={"setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V","m_155232_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"},at=@At("HEAD"),remap=false)
    private static void unusualcopper$dirty(Level level,BlockPos pos,BlockState state,CallbackInfo callback){InventoryChanges.changed(level,pos);}
}
