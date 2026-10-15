package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModContent.MACHINE_MENU.get(), CopperMachineScreen::new);
            MenuScreens.register(ModContent.PIPE_MENU.get(), ItemPipeScreen::new);
            io.github.gerbesh.unusualcopper.network.AreaNetwork.clientReceiver(AreaPreviewRenderer::accept);
            io.github.gerbesh.unusualcopper.network.MachineEffects.clientReceiver(ShepherdEffects::accept);
            io.github.gerbesh.unusualcopper.network.PipeNetwork.clientReceiver(message -> {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.ItemPipeMenu menu && menu.containerId == message.menu() && menu.pos.equals(message.pos())) menu.tags(message.face(), message.tags());
            });
        });
    }
}
