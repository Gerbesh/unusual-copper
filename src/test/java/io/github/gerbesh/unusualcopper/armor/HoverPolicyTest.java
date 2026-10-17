package io.github.gerbesh.unusualcopper.armor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** F2 limits and shipped assets tested without launching a Minecraft client/server. */
class HoverPolicyTest {
    @Test void validatesTierDurationAndCooldown() {
        assertEquals(2,HoverPolicy.REQUIRED_TIER);
        assertEquals(80,HoverPolicy.CUE_PER_TICK);
        assertEquals(600,HoverPolicy.MAX_SESSION_TICKS);
        assertEquals(100,HoverPolicy.REST_TICKS);
        assertFalse(HoverPolicy.canRun(1,100,100,0));
        assertTrue(HoverPolicy.canRun(2,100,100,0));
        assertTrue(HoverPolicy.canRun(3,699,100,0));
        assertFalse(HoverPolicy.canRun(2,700,100,0));
        long rest=HoverPolicy.restUntil(100);
        assertEquals(800,rest);
        assertFalse(HoverPolicy.canRun(2,750,750,rest));
        assertTrue(HoverPolicy.canRun(2,800,800,rest));
        assertFalse(HoverPolicy.canRun(2,99,100,0));
        assertEquals(Long.MAX_VALUE,HoverPolicy.restUntil(Long.MAX_VALUE));
        assertTrue(ArmorProgression.validPayment(HoverPolicy.CUE_PER_TICK));
    }

    @Test void shippableModuleRecipeModelAndLocales() throws Exception {
        Path root=Path.of("src/generated/resources/data/unusualcopper/recipes/hover_drive_module.json");
        var recipe=JsonParser.parseString(Files.readString(root)).getAsJsonObject();
        assertEquals("minecraft:crafting_shapeless",recipe.get("type").getAsString());
        assertEquals("unusualcopper:hover_drive_module",
                recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(3,recipe.getAsJsonArray("ingredients").size());
        assertTrue(recipe.getAsJsonArray("ingredients").toString().contains("shulker_shell"));
        assertTrue(Files.exists(Path.of("src/main/resources/assets/unusualcopper/models/item/hover_drive_module.json")));
        for(String locale:new String[]{"en_us","ru_ru"}) {
            var translations=JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/unusualcopper/lang/"+locale+".json"))).getAsJsonObject();
            assertTrue(translations.has("item.unusualcopper.hover_drive_module"));
            assertTrue(translations.has("armor.unusualcopper.hover_hint"));
        }
    }
}
