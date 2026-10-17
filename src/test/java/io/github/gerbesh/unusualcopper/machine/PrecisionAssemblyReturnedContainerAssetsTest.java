package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PrecisionAssemblyReturnedContainerAssetsTest {
    @Test void cooledCoilPairShipsPhysicalWaterBucketRecipe() throws Exception {
        var source=Path.of("src/main/resources/data/unusualcopper/recipes/precision/cooled_coil_pair.json");
        var json=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        assertEquals("unusualcopper:precision_assembly",json.get("type").getAsString());
        var inputs=json.getAsJsonArray("ingredients");
        assertEquals(9,inputs.size());
        assertEquals("minecraft:water_bucket",inputs.get(4).getAsJsonObject().get("item").getAsString());
        assertEquals("unusualcopper:resonant_coil",json.getAsJsonObject("result").get("item").getAsString());
        assertEquals(2,json.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(1600,json.get("cue").getAsInt());
    }
}
