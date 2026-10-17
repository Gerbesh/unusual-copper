package io.github.gerbesh.unusualcopper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduces BufferSource's shared-builder aliasing without a GPU or a game client. */
class VisualMaterialBindingTest {
    @Test void alternatingMaterialsBindWhenGeometryIsWritten(){
        var selected=new String[1];var vertices=new ArrayList<String>();
        VertexConsumer shared=new VertexConsumer(){
            @Override public VertexConsumer vertex(double x,double y,double z){vertices.add(selected[0]);return this;}
            @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
            @Override public VertexConsumer uv(float u,float v){return this;}
            @Override public VertexConsumer overlayCoords(int u,int v){return this;}
            @Override public VertexConsumer uv2(int u,int v){return this;}
            @Override public VertexConsumer normal(float x,float y,float z){return this;}
            @Override public void endVertex(){}
            @Override public void defaultColor(int r,int g,int b,int a){}
            @Override public void unsetDefaultColor(){}
        };
        var copper=VisualGeometry.deferred(()->{selected[0]="copper";return shared;});
        var aqua=VisualGeometry.deferred(()->{selected[0]="aqua";return shared;});
        var pose=new PoseStack();
        for(var material:new VertexConsumer[]{copper,aqua,copper})
            VisualGeometry.box(pose,material,0,0,0,1,1,1,1,1,1,1,0xf000f0);
        assertEquals(72,vertices.size());
        for(int i=0;i<vertices.size();i++)assertEquals(i>=24&&i<48?"aqua":"copper",vertices.get(i),"vertex "+i);
    }
}
