package io.github.gerbesh.unusualcopper.armor;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorProgressionTest {
    @Test void socketLimitsAreTieredAndBounded() {
        for(int tier=1;tier<=3;tier++){
            assertEquals(new int[]{1,3,4}[tier-1],ArmorProgression.sockets(tier,true));
            assertEquals(new int[]{1,2,3}[tier-1],ArmorProgression.sockets(tier,false));
        }
        assertThrows(IllegalArgumentException.class,()->ArmorProgression.sockets(0,true));
        assertThrows(IllegalArgumentException.class,()->ArmorProgression.sockets(4,false));
    }
    @Test void cueBudgetsCannotAuthorizeFreeOrNegativePowers() {
        assertEquals(900,ArmorProgression.totalBudget());
        assertEquals(650,ArmorProgression.pieceBudget(true,false,false));
        assertEquals(150,ArmorProgression.pieceBudget(false,false,true));
        assertFalse(ArmorProgression.validPayment(0));
        assertFalse(ArmorProgression.validPayment(-1));
        assertFalse(ArmorProgression.validPayment(901));
        assertTrue(ArmorProgression.validPayment(120));
    }
    @Test void fullSurvivalAssetsArePresent() throws Exception {
        Path recipes=Path.of("src/generated/resources/data/unusualcopper/recipes");
        Path models=Path.of("src/main/resources/assets/unusualcopper/models/item");
        for(int tier=1;tier<=3;tier++)for(String piece:new String[]{"helmet","chestplate","leggings","boots"}){
            String id="programmable_"+piece+"_tier_"+tier;
            assertTrue(Files.isRegularFile(recipes.resolve(id+".json")),"Recipe: "+id);
            assertTrue(Files.isRegularFile(models.resolve(id+".json")),"Model: "+id);
            assertTrue(Files.readString(recipes.resolve(id+".json")).contains("unusualcopper:"+id));
        }
        for(var kind:ArmorModuleType.values()){
            String id=kind.id+"_module";
            assertTrue(Files.isRegularFile(recipes.resolve(id+".json")),"Recipe: "+id);
            assertTrue(Files.isRegularFile(models.resolve(id+".json")),"Model: "+id);
        }
        assertTrue(Files.isRegularFile(recipes.resolve("armor_workbench.json")));
        assertTrue(Files.isRegularFile(Path.of("src/generated/resources/data/unusualcopper/loot_tables/blocks/armor_workbench.json")));
    }
}
