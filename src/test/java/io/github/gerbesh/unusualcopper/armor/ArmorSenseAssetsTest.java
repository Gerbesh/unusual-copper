package io.github.gerbesh.unusualcopper.armor;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A dev client can hide missing SRG references; inspect the compiled injection metadata too. */
class ArmorSenseAssetsTest {
    private static final String MIXIN="io/github/gerbesh/unusualcopper/mixin/ArmorLightTextureMixin";
    @Test void clientLightMixinIsNeverDeclaredForTheDedicatedServer() throws Exception {
        var config=JsonParser.parseString(Files.readString(Path.of("src/main/resources/unusualcopper.mixins.json"))).getAsJsonObject();
        for(String mixin:new String[]{"ArmorLightTextureMixin","ArmorIrisUniformMixin"}){
            assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(v->v.getAsString().equals(mixin)));
            assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(v->v.getAsString().equals(mixin)));
        }
    }
    @Test void everyCompiledRedirectReferenceHasThePinnedMc1201SrgMapping() throws Exception {
        var refmap=JsonParser.parseString(Files.readString(Path.of("src/main/resources/unusualcopper.refmap.json"))).getAsJsonObject();
        var mappings=refmap.getAsJsonObject("mappings").getAsJsonObject(MIXIN);assertNotNull(mappings);
        assertEquals(mappings,refmap.getAsJsonObject("data").getAsJsonObject("searge").getAsJsonObject(MIXIN));
        var bytecode=new ClassNode();
        try(var in=getClass().getClassLoader().getResourceAsStream(MIXIN+".class")){
            assertNotNull(in);new ClassReader(in).accept(bytecode,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        var refs=new HashSet<String>();int redirects=0;
        for(var method:bytecode.methods)if(method.visibleAnnotations!=null)for(var annotation:method.visibleAnnotations)
            if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")){
                redirects++;
                for(int i=0;i<annotation.values.size();i+=2){
                    String key=(String)annotation.values.get(i);Object value=annotation.values.get(i+1);
                    if(key.equals("method"))for(var target:(List<?>)value)refs.add((String)target);
                    if(key.equals("at")){
                        var at=(AnnotationNode)value;
                        for(int j=0;j<at.values.size();j+=2)if(at.values.get(j).equals("target"))refs.add((String)at.values.get(j+1));
                    }
                }
            }
        assertEquals(2,redirects);assertEquals(3,refs.size());for(var ref:refs)assertTrue(mappings.has(ref),"Unmapped production target: "+ref);
        assertEquals("Lnet/minecraft/client/renderer/LightTexture;m_109881_(F)V",mappings.get("updateLightTexture(F)V").getAsString());
        assertTrue(mappings.get("Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z").getAsString().contains(";m_21023_"));
        assertTrue(mappings.get("Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F").getAsString().contains(";m_109108_"));
    }
}
