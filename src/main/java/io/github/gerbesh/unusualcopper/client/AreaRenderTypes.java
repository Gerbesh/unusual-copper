package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;

/** An outlined selection stays legible through terrain without changing the world depth buffer. */
final class AreaRenderTypes extends RenderType {
    private static final DepthTestStateShard DEPTH=new DepthTestStateShard("cue_area_overlay",519){
        @Override public void setupRenderState(){RenderSystem.disableDepthTest();}
        @Override public void clearRenderState(){RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);}
    };
    static final RenderType LINES=create("unusualcopper_area",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.empty()))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(DEPTH).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private AreaRenderTypes(){super("unused",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,()->{},()->{});}
}
