package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verify the actual shipped datapack and both locale entries without starting Minecraft. */
class PrecisionAssemblyResourcesTest {
    @Test void cooledCoilsUseHollowPatternAndPhysicalWaterBucket() throws Exception {
        Path path=Path.of("src/main/resources/data/unusualcopper/recipes/precision/cooled_coil_pair.json");
        JsonObject recipe=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("unusualcopper:precision_assembly",recipe.get("type").getAsString());
        var grid=recipe.getAsJsonArray("ingredients");
        assertEquals(9,grid.size());
        assertTrue(grid.get(3).isJsonNull());
        assertTrue(grid.get(5).isJsonNull());
        assertEquals("minecraft:water_bucket",grid.get(4).getAsJsonObject().get("item").getAsString());
        assertEquals("unusualcopper:resonant_coil",recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(2,recipe.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(1600,recipe.get("cue").getAsInt());
        assertEquals(100,recipe.get("ticks").getAsInt());
    }

    @Test void patternButtonHasBothTranslations() throws Exception {
        for(String language:new String[]{"en_us","ru_ru"}) {
            Path path=Path.of("src/main/resources/assets/unusualcopper/lang/"+language+".json");
            JsonObject translations=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for(String key:new String[]{"assembly.unusualcopper.engrave",
                    "assembly.unusualcopper.engrave_hint","assembly.unusualcopper.blank",
                    "assembly.unusualcopper.bound"})
                assertTrue(translations.has(key),language+": "+key);
        }
    }
}
