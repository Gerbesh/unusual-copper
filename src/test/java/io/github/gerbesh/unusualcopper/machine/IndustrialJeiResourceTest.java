package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** The JEI categories must be backed by the recipes actually shipped in the mod jar. */
class IndustrialJeiResourceTest {
    @Test void industrialCategoryHasAllFiveMachineKinds() throws Exception {
        Path dir=Path.of("src/main/resources/data/unusualcopper/recipes/industrial");
        var machines=new HashSet<String>();
        try(var paths=Files.list(dir)) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertEquals("unusualcopper:industrial_processing",json.get("type").getAsString());
                assertTrue(json.has("result"));
                assertTrue(json.has("ticks"));
                assertTrue(json.has("cue"));
                machines.add(json.get("machine").getAsString());
            }
        }
        assertEquals(java.util.Set.of("resonance_crusher","copper_press","vortex_purifier",
                "resonance_crystallizer","circuit_etcher"),machines);
    }

    @Test void precisionJeiCategoryCanRenderSparseRecipes() throws Exception {
        Path dir=Path.of("src/main/resources/data/unusualcopper/recipes/precision");
        int count=0,sparse=0;
        try(var paths=Files.list(dir)) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertEquals("unusualcopper:precision_assembly",json.get("type").getAsString());
                var ingredients=json.getAsJsonArray("ingredients");
                assertEquals(9,ingredients.size());
                for(var input:ingredients)if(input.isJsonNull())sparse++;
                count++;
            }
        }
        assertTrue(count>=3);
        assertTrue(sparse>0);
        for(String lang:new String[]{"en_us","ru_ru"}) {
            var json=JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/unusualcopper/lang/"+lang+".json"))).getAsJsonObject();
            assertTrue(json.has("jei.unusualcopper.industrial_processing"));
            assertTrue(json.has("jei.unusualcopper.precision_assembly"));
        }
    }
}
