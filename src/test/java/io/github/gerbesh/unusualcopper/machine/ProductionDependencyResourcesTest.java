package io.github.gerbesh.unusualcopper.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** E5B planner contract against the actual shipped datapack recipes, not mock constants. */
class ProductionDependencyResourcesTest {
    private static JsonObject recipe(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }
    private static String item(JsonObject ingredient){return ingredient.get("item").getAsString();}
    private static ProductionDependencyPlanner.RecipeSpec shaped(String name,int cue,int ticks) throws Exception {
        var json=recipe("src/generated/resources/data/unusualcopper/recipes/"+name+".json");
        assertEquals("minecraft:crafting_shaped",json.get("type").getAsString());
        var pattern=json.getAsJsonArray("pattern");
        assertEquals(3,pattern.size());
        var key=json.getAsJsonObject("key");
        var inputs=new TreeMap<String,Integer>();
        for(var line:pattern) {
            String chars=line.getAsString();
            assertEquals(3,chars.length());
            for(int i=0;i<3;i++) {
                String keyChar=String.valueOf(chars.charAt(i));
                if(keyChar.equals(" "))continue;
                inputs.merge(item(key.getAsJsonObject(keyChar)),1,Integer::sum);
            }
        }
        var output=json.getAsJsonObject("result");
        return new ProductionDependencyPlanner.RecipeSpec(name,item(output),
                output.has("count")?output.get("count").getAsInt():1,
                "crafting",cue,ticks,inputs,Set.of());
    }
    private static ProductionDependencyPlanner.RecipeSpec press(String id) throws Exception {
        var json=recipe("src/main/resources/data/unusualcopper/recipes/industrial/"+id+".json");
        assertEquals("unusualcopper:industrial_processing",json.get("type").getAsString());
        assertEquals("copper_press",json.get("machine").getAsString());
        var output=json.getAsJsonObject("result");
        return new ProductionDependencyPlanner.RecipeSpec("industrial/"+id,item(output),
                output.has("count")?output.get("count").getAsInt():1,
                json.get("machine").getAsString(),json.get("cue").getAsInt(),json.get("ticks").getAsInt(),
                Map.of(item(json.getAsJsonObject("ingredient")),json.get("input_count").getAsInt()),
                Set.of(item(json.getAsJsonObject("mold"))));
    }
    @Test void realCoilAndReinforcedFrameBillOfMaterials() throws Exception {
        var recipes=List.of(
                shaped("resonant_coil",240,80),
                shaped("reinforced_frame",1800,120),
                shaped("copper_frame",0,1),
                press("press_copper_wire"),
                press("press_copper_plate")
        );
        var coil=ProductionDependencyPlanner.plan(recipes,"unusualcopper:resonant_coil",16);
        assertEquals(Map.of("minecraft:copper_ingot",16L,"minecraft:redstone",16L),coil.rawMaterials());
        assertEquals(7680L,coil.totalCue());
        assertEquals(List.of("industrial/press_copper_plate","industrial/press_copper_wire","resonant_coil"),
                coil.stages().stream().map(ProductionDependencyPlanner.Stage::recipe).toList());

        var frame=ProductionDependencyPlanner.plan(recipes,"unusualcopper:reinforced_frame",16);
        assertEquals(Map.of("minecraft:copper_ingot",160L,"minecraft:iron_nugget",16L,
                "minecraft:iron_ingot",32L,"minecraft:obsidian",32L),frame.rawMaterials());
        assertEquals(32640L,frame.totalCue());
        assertEquals(Set.of("unusualcopper:plate_die"),frame.reusableTools());
        assertEquals(Set.of("unusualcopper:plate_die","unusualcopper:wire_die"),coil.reusableTools());
    }

    @Test void localizedPreviewMessagesAndNotAClientSideOrder() throws Exception {
        for(String lang:new String[]{"ru_ru","en_us"}) {
            var json=recipe("src/main/resources/assets/unusualcopper/lang/"+lang+".json");
            for(String key:new String[]{"hint","header","stage","material","tool","warning","failed"})
                assertTrue(json.has("production.unusualcopper.preview."+key),lang+"/"+key);
        }
    }
}
