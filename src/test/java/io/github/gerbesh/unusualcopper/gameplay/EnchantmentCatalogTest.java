package io.github.gerbesh.unusualcopper.gameplay;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class EnchantmentCatalogTest {
    @Test void thousandsOfEnchantmentsSurviveChunkingAndRepeatedStatusHeaders(){
        var values=IntStream.range(0,4097).boxed().toList();var token=UUID.randomUUID();var catalog=new EnchantmentCatalog<Integer>();
        assertTrue(catalog.begin(token,values.size(),5000));
        for(int start=0;start<values.size();start+=EnchantmentCatalog.PAGE_SIZE){
            assertTrue(catalog.append(token,start,values.subList(start,Math.min(values.size(),start+EnchantmentCatalog.PAGE_SIZE))));
            assertFalse(catalog.begin(token,values.size(),5000));
            assertTrue(catalog.append(token,0,values.subList(0,EnchantmentCatalog.PAGE_SIZE)));
        }
        assertTrue(catalog.complete());assertEquals(values,catalog.entries());
        assertThrows(UnsupportedOperationException.class,()->catalog.entries().clear());
    }
    @Test void replacedInputCannotInheritDelayedPagesFromThePreviousItem(){
        var catalog=new EnchantmentCatalog<String>();var old=UUID.randomUUID();var current=UUID.randomUUID();
        catalog.begin(old,2,10);catalog.append(old,0,List.of("helmet-only"));
        assertTrue(catalog.begin(current,1,10));assertFalse(catalog.append(old,1,List.of("stale curse")));
        assertEquals(0,catalog.loaded());catalog.append(current,0,List.of("glove reach"));
        assertEquals(List.of("glove reach"),catalog.entries());assertTrue(catalog.complete());
    }
    @Test void malformedCountsGapsAndChangedDuplicatePagesAreRejected(){
        var catalog=new EnchantmentCatalog<Integer>();var token=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->catalog.begin(token,101,100));
        catalog.begin(token,100,100);catalog.append(token,0,List.of(1,2));
        assertThrows(IllegalArgumentException.class,()->catalog.begin(token,99,100));
        assertThrows(IllegalArgumentException.class,()->catalog.append(token,4,List.of(5)));
        assertThrows(IllegalArgumentException.class,()->catalog.append(token,0,List.of(1,3)));
        assertThrows(IllegalArgumentException.class,()->catalog.append(token,2,IntStream.range(0,65).boxed().toList()));
        assertEquals(List.of(1,2),catalog.entries());
    }
    @Test void anEmptyCatalogIsACompleteDistinctInput(){
        var catalog=new EnchantmentCatalog<Integer>();catalog.begin(UUID.randomUUID(),0,500);
        assertTrue(catalog.complete());assertEquals(0,catalog.total());
        assertThrows(IllegalArgumentException.class,()->catalog.begin(null,0,500));
    }
}
