package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.network.AreaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, value = Dist.CLIENT)
public final class AreaPreviewRenderer {
    private static AreaNetwork.Preview preview;
    private static long expires;
    public static void accept(AreaNetwork.Preview value) {
        var level = Minecraft.getInstance().level; if (level == null || !level.dimension().location().equals(value.dimension())) return;
        preview = value; expires = level.getGameTime() + 240;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (preview == null || mc.level == null || mc.level.getGameTime() >= expires || !mc.level.dimension().location().equals(preview.dimension())) { preview = null; return; }
        var min = preview.min(); var max = preview.max();
        var box = new AABB(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()),
                (double)Math.max(min.getX(), max.getX()) + 1, (double)Math.max(min.getY(), max.getY()) + 1, (double)Math.max(min.getZ(), max.getZ()) + 1);
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition(); var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z); RenderSystem.enableBlend(); RenderSystem.disableDepthTest();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, preview.valid() ? .2F : 1F, preview.valid() ? 1F : .2F, preview.valid() ? .8F : .2F, .8F);
        buffers.endBatch(RenderType.lines()); RenderSystem.enableDepthTest(); RenderSystem.disableBlend(); pose.popPose();
    }
}
