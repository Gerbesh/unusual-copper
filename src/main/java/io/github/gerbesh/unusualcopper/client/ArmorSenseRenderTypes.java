package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;

/** Scoped draw state: only server-approved ore boxes use depth-free lines. */
final class ArmorSenseRenderTypes extends RenderType {
    // Vanilla's NO_DEPTH_TEST shard is a no-op (GL_ALWAYS), so it retains the
    // world pass's enabled depth test. Explicitly bracket this overlay instead.
    private static final DepthTestStateShard ORE_DEPTH=new DepthTestStateShard("cue_ore_overlay",519){
        @Override public void setupRenderState(){RenderSystem.disableDepthTest();}
        @Override public void clearRenderState(){RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);}
    };
    static final RenderType ORE_LINES=create("unusualcopper_ore_senses",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.empty()))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(ORE_DEPTH).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private ArmorSenseRenderTypes(){super("unused",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,()->{},()->{});}
}
