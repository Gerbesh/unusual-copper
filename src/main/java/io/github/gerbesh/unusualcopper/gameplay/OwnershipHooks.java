package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID)
public final class OwnershipHooks {
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        var entity = event.getLevel().getBlockEntity(event.getPos());
        if ((entity instanceof CopperMachineBlockEntity machine && !machine.canAccess(event.getPlayer()))
                || (entity instanceof ItemPipeBlockEntity pipe && !pipe.canAccess(event.getPlayer()))) event.setCanceled(true);
    }
}
