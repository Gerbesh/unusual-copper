package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** CI verifies the actual bundled survival assets, not only their datagen authoring code. */
class IndustrialSurvivalAssetsTest {
    @Test void newMachinesShipLootCraftingAndPickaxeTags() throws Exception {
        String[] ids={"resonance_crystallizer","circuit_etcher","precision_assembler",
                "copper_fluid_pipe","copper_fluid_pump","copper_fluid_tank"};
        String tag=Files.readString(Path.of("src/generated/resources/data/minecraft/tags/blocks/mineable/pickaxe.json"));
        for(String id:ids){
            Path crafting=Path.of("src/generated/resources/data/unusualcopper/recipes/"+id+".json");
            Path loot=Path.of("src/generated/resources/data/unusualcopper/loot_tables/blocks/"+id+".json");
            assertTrue(Files.isRegularFile(crafting),"Recipe missing: "+id);
            assertTrue(Files.isRegularFile(loot),"Loot table missing: "+id);
            assertTrue(Files.readString(crafting).contains("unusualcopper:"+id),"Wrong crafting output: "+id);
            assertTrue(Files.readString(loot).contains("unusualcopper:"+id),"Wrong loot item: "+id);
            assertTrue(tag.contains("unusualcopper:"+id),"Not pickaxe-mineable: "+id);
        }
        assertTrue(Files.isRegularFile(Path.of("src/generated/resources/data/unusualcopper/recipes/etching_reagent.json")));
        assertTrue(Files.isRegularFile(Path.of("src/generated/resources/data/unusualcopper/recipes/pattern_card.json")));
    }
}
