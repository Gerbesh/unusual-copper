package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArmorKeys {
    public static final KeyMapping OPEN=new KeyMapping("key.unusualcopper.armor_open",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.unusualcopper");
    public static final KeyMapping PROFILE=new KeyMapping("key.unusualcopper.armor_profile",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.unusualcopper");
    public static final KeyMapping HUD=new KeyMapping("key.unusualcopper.armor_hud",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"key.categories.unusualcopper");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(OPEN);event.register(PROFILE);event.register(HUD);}
    private ArmorKeys(){}
}
