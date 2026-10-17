package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSubcraftAssetsTest {
    @Test void shippedCopperFrameRecipeIsExactlyEightCopperAndOneIronNugget() throws Exception {
        var path=Path.of("src/generated/resources/data/unusualcopper/recipes/copper_frame.json");
        var recipe=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());
        assertEquals("unusualcopper:copper_frame",recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals("CCC",recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("CIC",recipe.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("CCC",recipe.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("minecraft:copper_ingot",recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        assertEquals("minecraft:iron_nugget",recipe.getAsJsonObject("key").getAsJsonObject("I").get("item").getAsString());
    }

    @Test void sixteenRealCopperFramesNeedExactly128IngotsAnd16IronNuggets() throws Exception {
        var recipe=new ProductionDependencyPlanner.RecipeSpec("copper_frame","frame",1,
                "crafting_table",0,1,Map.of("copper_ingot",8,"iron_nugget",1),java.util.Set.of());
        var plan=ProductionDependencyPlanner.plan(java.util.List.of(recipe),"frame",16);
        assertEquals(Map.of("copper_ingot",128L,"iron_nugget",16L),plan.rawMaterials());
        assertEquals(16,plan.stages().get(0).operations());
        assertEquals(0,plan.totalCue());
    }
}
