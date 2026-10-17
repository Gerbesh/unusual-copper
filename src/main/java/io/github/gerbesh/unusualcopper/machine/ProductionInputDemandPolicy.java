package io.github.gerbesh.unusualcopper.machine;

/** Validates the entire 3x3 recipe, including legitimate non-press ingredients.
 * Unrelated correctly placed redstone, iron or obsidian must not inhibit E2 jobs.
 * Negative result means an invalid/mutated grid, not demand for manufacturing.
 */
public final class ProductionInputDemandPolicy {
    private ProductionInputDemandPolicy() {}
    public static int missing(int queued,boolean[] occupiedInRecipe,boolean[] matching,
                              boolean[] targetIngredient,int[] heldCounts) {
        if(queued<1||occupiedInRecipe.length!=9||matching.length!=9
                ||targetIngredient.length!=9||heldCounts.length!=9)
            throw new IllegalArgumentException("Invalid production grid");
        int target=Math.min(queued,64);
        int missing=0;
        for(int i=0;i<9;i++) {
            if(heldCounts[i]<0 || heldCounts[i]>64 || targetIngredient[i]&&!occupiedInRecipe[i])
                return -1;
            if(!occupiedInRecipe[i] && heldCounts[i]>0 || heldCounts[i]>0 && !matching[i])
                return -1;
            if(targetIngredient[i] && heldCounts[i]<target)missing++;
        }
        return missing;
    }
}
