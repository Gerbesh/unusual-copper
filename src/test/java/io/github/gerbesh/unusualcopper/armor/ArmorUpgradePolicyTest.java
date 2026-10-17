package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Server upgrade energy and durability cannot be changed by client arithmetic. */
class ArmorUpgradePolicyTest {
    @Test void workbenchUpgradeHasBothTranslations() throws Exception {
        for(String language:new String[]{"en_us","ru_ru"}) {
            var path=Path.of("src/main/resources/assets/unusualcopper/lang/"+language+".json");
            assertTrue(JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                    .has("armor.unusualcopper.upgrade"));
        }
    }

    @Test void requiresRealPaidNextTier() {
        assertEquals(24_000, ArmorUpgradePolicy.energyCost(1));
        assertEquals(60_000, ArmorUpgradePolicy.energyCost(2));
        assertEquals(0, ArmorUpgradePolicy.energyCost(3));
        assertEquals(0, ArmorUpgradePolicy.energyCost(0));
    }

    @Test void preservesDamageRatioAndNeverCreatesBrokenChassis() {
        assertEquals(0,ArmorUpgradePolicy.damageAfterUpgrade(0,240,480));
        assertEquals(240,ArmorUpgradePolicy.damageAfterUpgrade(120,240,480));
        assertEquals(320,ArmorUpgradePolicy.damageAfterUpgrade(160,240,480));
        assertEquals(478,ArmorUpgradePolicy.damageAfterUpgrade(239,240,480));
        assertThrows(IllegalArgumentException.class,()->ArmorUpgradePolicy.damageAfterUpgrade(-1,240,480));
        assertThrows(IllegalArgumentException.class,()->ArmorUpgradePolicy.damageAfterUpgrade(240,240,480));
        assertThrows(IllegalArgumentException.class,()->ArmorUpgradePolicy.damageAfterUpgrade(1,0,480));
    }
}
