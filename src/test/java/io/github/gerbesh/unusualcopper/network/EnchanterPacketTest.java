package io.github.gerbesh.unusualcopper.network;

import io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy.Offer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnchanterPacketTest {
    private static Offer offer(){return new Offer(new ResourceLocation("fixture","glove_reach"),3,282,187500);}
    private static EnchanterNetwork.Reply reply(){return new EnchanterNetwork.Reply(5,new BlockPos(0,80,0),UUID.randomUUID(),List.of(offer()),null,2000,2000000,64,EnchanterNetwork.Result.NONE,0,true,UUID.randomUUID(),4097);}
    @Test void statusHasSmallChunksAndFullCatalogCount(){
        var value=reply();var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{EnchanterNetwork.Codec.encode(value,buffer);assertEquals(value,EnchanterNetwork.Codec.decode(buffer,5000));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
    }
    @Test void pageAndStaleIdentityRoundTripWithoutAnyItemOrPrivateNbt(){
        var value=new EnchanterNetwork.Page(5,BlockPos.ZERO,UUID.randomUUID(),UUID.randomUUID(),4096,4097,List.of(offer()));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{EnchanterNetwork.Codec.encodePage(value,buffer);assertEquals(value,EnchanterNetwork.Codec.decodePage(buffer,5000));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
    }
    @Test void catalogCannotClaimMoreThanTheSynchronizedRegistry(){
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{EnchanterNetwork.Codec.encode(reply(),buffer);assertThrows(IllegalArgumentException.class,()->EnchanterNetwork.Codec.decode(buffer,4096));}finally{buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new EnchanterNetwork.Page(1,BlockPos.ZERO,UUID.randomUUID(),UUID.randomUUID(),0,100,Collections.nCopies(65,offer())));
        assertThrows(IllegalArgumentException.class,()->new EnchanterNetwork.Page(1,BlockPos.ZERO,UUID.randomUUID(),UUID.randomUUID(),Integer.MAX_VALUE,100,List.of(offer())));
    }
    @Test void quotesHaveRepresentableLevelsAndBoundedPositivePrices(){
        assertThrows(IllegalArgumentException.class,()->new Offer(offer().enchantment(),32768,1,1));
        assertThrows(IllegalArgumentException.class,()->new Offer(offer().enchantment(),1,0,1));
        assertThrows(IllegalArgumentException.class,()->new Offer(offer().enchantment(),1,1,2000001));
    }
    @Test void longRegisteredIdsAreSplitByBytesWithoutLosingChoices(){
        var values=java.util.stream.IntStream.range(0,5).mapToObj(i->new Offer(new ResourceLocation("fixture","a".repeat(32000)+i),1,1,1)).toList();
        int offset=0;var received=new ArrayList<Offer>();
        while(offset<values.size()){
            var page=io.github.gerbesh.unusualcopper.gameplay.EnchantmentPolicy.page(values,offset);assertFalse(page.isEmpty());assertTrue(page.size()<=2);
            var message=new EnchanterNetwork.Page(1,BlockPos.ZERO,UUID.randomUUID(),UUID.randomUUID(),offset,values.size(),page);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{EnchanterNetwork.Codec.encodePage(message,buffer);assertTrue(buffer.readableBytes()<66000);received.addAll(EnchanterNetwork.Codec.decodePage(buffer,5).offers());}finally{buffer.release();}
            offset+=page.size();
        }
        assertEquals(values,received);
    }
}
