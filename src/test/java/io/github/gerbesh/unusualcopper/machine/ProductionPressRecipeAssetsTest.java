package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Real E2 stage resources, not unregistered fantasy manufacturing. */
class ProductionPressRecipeAssetsTest {
    @Test void theTwoE2PressJobsHaveKnownPhysicalYieldsAndInputs() throws Exception {
        String[] recipes={"press_copper_plate","press_copper_wire"};
        String[] inputs={"minecraft:copper_ingot","unusualcopper:copper_plate"};
        String[] outputs={"unusualcopper:copper_plate","unusualcopper:copper_wire_spool"};
        String[] molds={"unusualcopper:plate_die","unusualcopper:wire_die"};
        int[] inputCounts={1,2},yields={2,4};
        for(int i=0;i<recipes.length;i++) {
            var json=JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/data/unusualcopper/recipes/industrial/"+recipes[i]+".json"))).getAsJsonObject();
            assertEquals("unusualcopper:industrial_processing",json.get("type").getAsString());
            assertEquals("copper_press",json.get("machine").getAsString());
            assertEquals(inputs[i],json.getAsJsonObject("ingredient").get("item").getAsString());
            assertEquals(molds[i],json.getAsJsonObject("mold").get("item").getAsString());
            assertEquals(outputs[i],json.getAsJsonObject("result").get("item").getAsString());
            assertEquals(inputCounts[i],json.get("input_count").getAsInt());
            assertEquals(yields[i],json.getAsJsonObject("result").get("count").getAsInt());
        }
    }
    @Test void bothLanguagesExplainTheRealPhysicalPressStage() throws Exception {
        for(String language:new String[]{"en_us","ru_ru"}) {
            var json=JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/unusualcopper/lang/"+language+".json"))).getAsJsonObject();
            assertTrue(json.has("status.unusualcopper.waiting_upstream"));
            assertTrue(json.has("production.unusualcopper.preview.warning"));
        }
    }
}
