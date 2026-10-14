package io.github.gerbesh.unusualcopper.energy;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public final class CopperCapabilities {
    public static final Capability<CopperEnergyHandler> ENERGY = CapabilityManager.get(new CapabilityToken<>() {});
    private CopperCapabilities() {}
    public static void register(RegisterCapabilitiesEvent event) { event.register(CopperEnergyHandler.class); }
}
