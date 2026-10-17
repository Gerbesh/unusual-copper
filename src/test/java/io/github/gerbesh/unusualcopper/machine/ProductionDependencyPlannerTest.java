package io.github.gerbesh.unusualcopper.machine;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionDependencyPlannerTest {
    private static ProductionDependencyPlanner.RecipeSpec recipe(String id,String output,int yield,int cue,int ticks,
                                                                   Map<String,Integer> consumed,String... tools) {
        return new ProductionDependencyPlanner.RecipeSpec(id,output,yield,id,cue,ticks,consumed,Set.of(tools));
    }
    private static List<ProductionDependencyPlanner.RecipeSpec> liveWhitelistedChain() {
        return List.of(
                recipe("resonant_coil","coil",1,240,80,Map.of("wire",4,"redstone",1)),
                recipe("reinforced_frame","reinforced",1,1800,120,Map.of("plate",4,"iron_ingot",2,"obsidian",2,"frame",1)),
                recipe("copper_frame","frame",1,0,1,Map.of("copper",8,"iron_nugget",1)),
                recipe("press_copper_wire","wire",4,120,40,Map.of("plate",2),"wire_die"),
                recipe("press_copper_plate","plate",2,120,40,Map.of("copper",1),"plate_die")
        );
    }

    @Test void sixteenCoilsExpandIntoThirtyTwoPhysicalPressJobs() {
        var plan=ProductionDependencyPlanner.plan(liveWhitelistedChain(),"coil",16);
        assertEquals(Map.of("copper",16L,"redstone",16L),plan.rawMaterials());
        assertEquals(Set.of("wire_die","plate_die"),plan.reusableTools());
        assertEquals(7680L,plan.totalCue());
        assertEquals(2560L,plan.totalTicks());
        assertEquals(List.of("plate","wire","coil"),plan.stages().stream().map(ProductionDependencyPlanner.Stage::output).toList());
        assertEquals(List.of(16L,16L,16L),plan.stages().stream().map(ProductionDependencyPlanner.Stage::operations).toList());
    }

    @Test void sixteenFramesExpandThroughTwoDifferentDependencies() {
        var plan=ProductionDependencyPlanner.plan(liveWhitelistedChain(),"reinforced",16);
        assertEquals(Map.of("copper",160L,"iron_nugget",16L,"iron_ingot",32L,"obsidian",32L),plan.rawMaterials());
        assertEquals(32640L,plan.totalCue());
        assertEquals(3216L,plan.totalTicks());
        assertEquals(List.of("frame","plate","reinforced"),plan.stages().stream().map(ProductionDependencyPlanner.Stage::output).toList());
        assertEquals(32L,plan.stages().get(1).operations());
    }

    @Test void multiOutputBatchesDoNotMintOrForgetByproducts() {
        var chain=List.of(
                recipe("finish","final",1,9,4,Map.of("output",5)),
                recipe("chunks","output",4,2,3,Map.of("ore",1))
        );
        var plan=ProductionDependencyPlanner.plan(chain,"final",1);
        assertEquals(Map.of("ore",2L),plan.rawMaterials());
        assertEquals(2L,plan.stages().get(0).operations());
        assertEquals(13L,plan.totalCue());
    }

    @Test void cycleDuplicateAndBoundsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->ProductionDependencyPlanner.plan(
                List.of(recipe("a","a",1,1,1,Map.of("b",1)),
                        recipe("b","b",1,1,1,Map.of("a",1))),"a",1));
        assertThrows(IllegalArgumentException.class,()->ProductionDependencyPlanner.plan(
                List.of(recipe("a","x",1,1,1,Map.of("base",1)),
                        recipe("b","x",1,1,1,Map.of("base",1))),"x",1));
        assertThrows(IllegalArgumentException.class,()->ProductionDependencyPlanner.plan(List.of(),"ore",4097));
        assertThrows(IllegalArgumentException.class,()->ProductionDependencyPlanner.plan(List.of(),"ore",0));
        var chain=new ArrayList<ProductionDependencyPlanner.RecipeSpec>();
        for(int i=0;i<18;i++)
            chain.add(recipe("r"+i,"part"+i,1,1,1,Map.of(i==17?"ore":"part"+(i+1),1)));
        assertThrows(IllegalArgumentException.class,()->ProductionDependencyPlanner.plan(chain,"part0",1));
    }

    @Test void noSimulatedInventoryCountsAsOwnedStock() {
        assertEquals(Map.of("missing_ingot",7L),
                ProductionDependencyPlanner.plan(List.of(),"missing_ingot",7).rawMaterials());
        assertTrue(ProductionDependencyPlanner.plan(List.of(),"missing_ingot",7).stages().isEmpty());
    }
}
