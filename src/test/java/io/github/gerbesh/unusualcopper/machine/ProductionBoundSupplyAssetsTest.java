package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Ensure the E5B feeder only addresses the two whitelisted 3x3 production recipes. */
class ProductionBoundSupplyAssetsTest {
    @Test void bothSupplyRecipesAreTrueThreeByThree() throws Exception {
        for(var name:new String[]{"resonant_coil","reinforced_frame"}) {
            var file=Path.of("src/generated/resources/data/unusualcopper/recipes/"+name+".json");
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals("minecraft:crafting_shaped",json.get("type").getAsString());
            var pattern=json.getAsJsonArray("pattern");
            assertEquals(3,pattern.size(),"Grid slots must not be shifted for "+name);
            for(var row:pattern)assertEquals(3,row.getAsString().length(),name);
            assertEquals("unusualcopper:"+name,json.getAsJsonObject("result").get("item").getAsString());
            var result=json.getAsJsonObject("result");
            assertEquals(1,result.has("count")?result.get("count").getAsInt():1);
        }
    }
    @Test void bothLanguagesExplainPhysicalEscrow() throws Exception {
        for(var language:new String[]{"en_us","ru_ru"}) {
            var json=JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/unusualcopper/lang/"+language+".json"))).getAsJsonObject();
            assertTrue(json.has("status.unusualcopper.waiting_materials"));
            assertTrue(json.has("production.unusualcopper.pattern_hint"));
        }
    }
}
