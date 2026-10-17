package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;

class IndustrialPurificationYieldTest {
    @Test void allThreePurifiersProduceTripleDust() throws Exception {
        for(String metal:new String[]{"copper","iron","gold"}) {
            String recipe=Files.readString(Path.of("src/main/resources/data/unusualcopper/recipes/industrial/purify_"+metal+".json"));
            assertTrue(recipe.contains("\"count\": 3"),"Missing x3 yield for "+metal);
            assertTrue(recipe.contains("\"water_mb\": 250"),"Missing water cost "+metal);
            assertTrue(recipe.contains("\"cue\": 300"),"Missing CuE cost "+metal);
        }
    }
}
