package io.github.gerbesh.unusualcopper.item;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class FaceSelectorItem extends Item {
    public FaceSelectorItem() { super(new Properties().stacksTo(1)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var player = context.getPlayer(); var pos = context.getClickedPos(); var side = context.getClickedFace();
        if (player == null || !level.hasChunkAt(pos)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !ProtectionPolicy.allowed(server, pos, player.getUUID())) return InteractionResult.FAIL;
        var entity = level.getBlockEntity(pos);
        if(entity instanceof io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlockEntity bridge&&bridge.canAccess(player)){
            level.setBlock(pos,bridge.getBlockState().setValue(io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlock.FACING,side),3);bridge.invalidateStorage();bridge.setChanged();return InteractionResult.SUCCESS;
        }
        if (entity instanceof CopperMachineBlockEntity machine && machine.canAccess(player)) {
            machine.cycleSide(side); player.displayClientMessage(Component.translatable(machine.sideMode(side).translationKey()), true); return InteractionResult.SUCCESS;
        }
        if (entity instanceof ItemPipeBlockEntity pipe && pipe.canAccess(player)) {
            pipe.rule(side).mode = pipe.rule(side).mode.next(); pipe.changed();
            player.displayClientMessage(Component.translatable(pipe.rule(side).mode.translationKey()), true); return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
