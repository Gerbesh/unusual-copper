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
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.REACTOR_ENTITY.get(),ReactorRenderer::new);
        event.registerBlockEntityRenderer(ModContent.MACHINE_ENTITY.get(),CopperMachineRenderer::new);
        event.registerBlockEntityRenderer(ModContent.PIPE_ENTITY.get(),EnergyPipeRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModContent.REACTOR_MENU.get(),ReactorScreen::new);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.STILL.get(),net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(io.github.gerbesh.unusualcopper.reactor.ReactorFluids.FLOWING.get(),net.minecraft.client.renderer.RenderType.translucent());
            io.github.gerbesh.unusualcopper.network.ArmorNetwork.receiver(ArmorClientState::receive);
            io.github.gerbesh.unusualcopper.network.ArmorNetwork.senseReceiver(ArmorSenseClient::receive);
            io.github.gerbesh.unusualcopper.network.ArmorNetwork.effectReceiver(ArmorSupportEffects::accept);
            io.github.gerbesh.unusualcopper.fluid.tank.TankNetworks.visualReceiver(FluidTankRenderer::receive);
            MenuScreens.register(ModContent.REAPER_MENU.get(), ReaperScreen::new);
            MenuScreens.register(ModContent.MINING_MENU.get(),MiningScreen::new);
            io.github.gerbesh.unusualcopper.network.MiningNetwork.clientReceiver(r->{var p=net.minecraft.client.Minecraft.getInstance().player;if(p!=null&&p.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.MiningMenu menu)menu.receive(r);});
            io.github.gerbesh.unusualcopper.network.MiningNetwork.effectsReceiver(MiningEffects::accept);
            io.github.gerbesh.unusualcopper.network.MiningNetwork.fieldReceiver(MiningEffects::track);
            MenuScreens.register(ModContent.BREWER_MENU.get(),BrewerScreen::new);
            MenuScreens.register(ModContent.ENCHANTER_MENU.get(),EnchanterScreen::new);
            MenuScreens.register(ModContent.REPAIR_MENU.get(),RepairScreen::new);
            io.github.gerbesh.unusualcopper.network.ItemTransitNetwork.clientReceiver(ItemTransitEffects::accept);
            io.github.gerbesh.unusualcopper.network.RepairNetwork.clientReceiver(message->{var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.RepairMenu menu)menu.receive(message);});
            MenuScreens.register(ModContent.ENERGY_MENU.get(),EnergyControlScreen::new);
            io.github.gerbesh.unusualcopper.network.EnergyControlNetwork.clientReceiver(message->{var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.EnergyControlMenu menu)menu.receive(message);});
            io.github.gerbesh.unusualcopper.network.EnchanterNetwork.clientReceiver(message->{var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.EnchanterMenu menu)menu.receive(message);});
            io.github.gerbesh.unusualcopper.network.EnchanterNetwork.pageReceiver(message->{var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.EnchanterMenu menu)menu.receivePage(message);});
            io.github.gerbesh.unusualcopper.network.BrewerNetwork.clientReceiver(message->{var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.BrewerMenu menu)menu.receive(message);});
            io.github.gerbesh.unusualcopper.network.ReaperNetwork.clientReceiver(message -> {
                var player=net.minecraft.client.Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.ReaperMenu menu) menu.receive(message);
            });
            MenuScreens.register(ModContent.MACHINE_MENU.get(), CopperMachineScreen::new);
            MenuScreens.register(ModContent.PIPE_MENU.get(), ItemPipeScreen::new);
            io.github.gerbesh.unusualcopper.network.AreaNetwork.clientReceiver(AreaPreviewRenderer::accept);
            io.github.gerbesh.unusualcopper.network.AreaNetwork.selectionReceiver(AreaPreviewRenderer::selection);
            io.github.gerbesh.unusualcopper.network.MachineEffects.clientReceiver(ShepherdEffects::accept);
            io.github.gerbesh.unusualcopper.network.MachineEffects.reaperReceiver(ReaperEffects::accept);
            io.github.gerbesh.unusualcopper.network.MachineEffects.forestryReceiver(ArboristEffects::accept);
            io.github.gerbesh.unusualcopper.network.MachineEffects.beamReceiver(SentinelEffects::accept);
            io.github.gerbesh.unusualcopper.network.MachineEffects.cultivatorReceiver(CultivatorEffects::accept);
            io.github.gerbesh.unusualcopper.network.MachineEffects.cultivationDeliveryReceiver(CultivatorEffects::delivery);
            io.github.gerbesh.unusualcopper.network.PipeNetwork.clientReceiver(message -> {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.ItemPipeMenu menu && menu.containerId == message.menu() && menu.pos.equals(message.pos())) menu.tags(message.face(), message.tags());
            });
        });
    }
}
