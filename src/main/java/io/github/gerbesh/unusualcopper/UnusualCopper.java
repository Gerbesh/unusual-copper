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
        io.github.gerbesh.unusualcopper.network.AreaNetwork.register();
        io.github.gerbesh.unusualcopper.network.MiningNetwork.register();
        io.github.gerbesh.unusualcopper.network.ReaperNetwork.register();
        io.github.gerbesh.unusualcopper.network.BrewerNetwork.register();
        io.github.gerbesh.unusualcopper.network.EnchanterNetwork.register();
        io.github.gerbesh.unusualcopper.network.RepairNetwork.register();
        io.github.gerbesh.unusualcopper.network.ArmorNetwork.register();
        io.github.gerbesh.unusualcopper.network.ItemTransitNetwork.register();
        io.github.gerbesh.unusualcopper.network.EnergyControlNetwork.register();
        io.github.gerbesh.unusualcopper.network.MachineEffects.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, io.github.gerbesh.unusualcopper.config.ArmorSenseConfig.SPEC,"unusualcopper-armor-senses.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, io.github.gerbesh.unusualcopper.config.ArmorClientConfig.SPEC);
        bus.addListener(CopperCapabilities::register);
        MinecraftForge.EVENT_BUS.register(EnergyNetworks.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.MiningScheduler.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.itempipe.ItemNetworks.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.fluid.FluidNetworks.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.armor.ArmorRuntime.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.armor.ArmorPayments.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.armor.ArmorControlServer.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.armor.ArmorSensors.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.AreaWorkScheduler.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.EntityIndexes.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.SentinelScheduler.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.BrewerScheduler.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.PotionPlans.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.MobPopulations.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.gameplay.ReaperLoot.class);
        MinecraftForge.EVENT_BUS.register(io.github.gerbesh.unusualcopper.diagnostics.ServerLifecycleDiagnostics.class);
        LOGGER.info("Unusual Copper: server-authoritative CuE machinery initialized");
    }
}
