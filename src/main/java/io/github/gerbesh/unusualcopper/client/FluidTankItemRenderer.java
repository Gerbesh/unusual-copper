package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

/** Native cube frame plus the actual fluid carried by this stack. No temporary world BE. */
public final class FluidTankItemRenderer extends BlockEntityWithoutLevelRenderer {
    public FluidTankItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){FluidTankRenderer.item(stack,pose,buffers,light);}
}
