package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.armor.ArmorEffectEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorEffectPacketTest {
    @Test void bodyEffectHasOnlyDimensionEntityKindAndLevel(){
        for(var kind:ArmorEffectEvents.Kind.values()){
            var value=new ArmorEffectEvents.Burst(new ResourceLocation("minecraft:overworld"),7,kind,1);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{ArmorEffectEvents.Burst.encode(value,buffer);assertTrue(buffer.readableBytes()<35);
                assertEquals(value,ArmorEffectEvents.Burst.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally{buffer.release();}
        }
    }
    @Test void corruptedEntityAndUnsupportedLevelsCannotReachRenderer(){
        var dimension=new ResourceLocation("minecraft:overworld");
        assertThrows(IllegalArgumentException.class,()->new ArmorEffectEvents.Burst(dimension,-1,ArmorEffectEvents.Kind.HEAL,1));
        for(int level:new int[]{0,3,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,
                ()->new ArmorEffectEvents.Burst(dimension,1,ArmorEffectEvents.Kind.STRENGTH,level));
        assertThrows(IllegalArgumentException.class,()->new ArmorEffectEvents.Burst(dimension,1,ArmorEffectEvents.Kind.THERMAL,2));
    }
}
