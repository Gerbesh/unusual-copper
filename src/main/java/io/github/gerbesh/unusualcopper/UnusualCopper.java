package io.github.gerbesh.unusualcopper;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.energy.CopperCapabilities;
import io.github.gerbesh.unusualcopper.energy.EnergyNetworks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import io.github.gerbesh.unusualcopper.config.ServerConfig;

@Mod(UnusualCopper.MOD_ID)
public final class UnusualCopper {
    public static final String MOD_ID = "unusualcopper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UnusualCopper() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContent.register(bus);
        io.github.gerbesh.unusualcopper.network.PipeNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        bus.addListener(CopperCapabilities::register);
        MinecraftForge.EVENT_BUS.register(EnergyNetworks.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.itempipe.ItemNetworks.class);
        LOGGER.info("Unusual Copper: server-authoritative CuE machinery initialized");
    }
}
