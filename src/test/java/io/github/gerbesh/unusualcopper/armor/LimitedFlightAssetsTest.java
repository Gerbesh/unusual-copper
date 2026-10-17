package io.github.gerbesh.unusualcopper.armor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LimitedFlightAssetsTest {
    @Test void survivalFlightModuleIsShippedWithMatchingDatagenAndClientAssets() throws Exception {
        var recipe=JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/data/unusualcopper/recipes/flight_core_ii_module.json"))).getAsJsonObject();
        assertEquals("minecraft:crafting_shapeless",recipe.get("type").getAsString());
        assertEquals("unusualcopper:flight_core_ii_module",recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals("unusualcopper:module_blank_i",
                recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:nether_star",
                recipe.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/unusualcopper/models/item/flight_core_ii_module.json")));
        for(String language:new String[]{"en_us","ru_ru"}) {
            var localization=JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/unusualcopper/lang/"+language+".json"))).getAsJsonObject();
            assertTrue(localization.has("item.unusualcopper.flight_core_ii_module"));
            assertTrue(localization.has("armor.unusualcopper.flight_ii_hint"));
        }
    }
}
