package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonParser;
import io.github.gerbesh.unusualcopper.item.BatteryTierPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** E8 add-on batteries preserve old tier sizes and use a charged physical predecessor. */
class PortableBatteryTierAssetsTest {
    @Test void capacitiesStayWithinPersistentSignedInt() {
        int[] expected={10_000,100_000,1_000_000,10_000_000,50_000_000,250_000_000};
        for(int i=0;i<expected.length;i++)assertEquals(expected[i],BatteryTierPolicy.capacityForTier(i));
        assertThrows(IllegalArgumentException.class,()->BatteryTierPolicy.capacityForTier(-1));
        assertThrows(IllegalArgumentException.class,()->BatteryTierPolicy.capacityForTier(6));
        assertEquals(250_000L,expected[5]/1000L); // paid solar copper equivalents, not a crafting refund
    }

    @Test void expensiveTierUpgradesRequireTheirPhysicalPredecessor() throws Exception {
        String[] ids={"prismatic_battery","sovereign_battery"};
        String[] inputs={"aetheric_battery","prismatic_battery"};
        for(int i=0;i<ids.length;i++) {
            var path=Path.of("src/generated/resources/data/unusualcopper/recipes/"+ids[i]+".json");
            var recipe=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals("unusualcopper:battery_upgrade",recipe.get("type").getAsString());
            assertEquals("unusualcopper:"+inputs[i],
                    recipe.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString());
            assertEquals("unusualcopper:"+ids[i],recipe.getAsJsonObject("result").get("item").getAsString());
            assertFalse(recipe.getAsJsonObject("result").has("nbt"));
            assertEquals("RBR",recipe.getAsJsonArray("pattern").get(1).getAsString());
            assertTrue(Files.isRegularFile(
                    Path.of("src/main/resources/assets/unusualcopper/models/item/"+ids[i]+".json")));
            for(String lang:new String[]{"en_us","ru_ru"}) {
                var translations=JsonParser.parseString(Files.readString(
                        Path.of("src/main/resources/assets/unusualcopper/lang/"+lang+".json"))).getAsJsonObject();
                assertTrue(translations.has("item.unusualcopper."+ids[i]));
            }
        }
    }
}
