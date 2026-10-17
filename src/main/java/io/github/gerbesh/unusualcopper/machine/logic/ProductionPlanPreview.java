package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.*;

/**
 * A read-only, server-authoritative planning preview. Resolves ONLY five explicit,
 * validated recipe IDs from the active server RecipeManager. Never withdraws materials
 * or queues phantom intermediate operations.
 */
public final class ProductionPlanPreview {
    private ProductionPlanPreview() {}

    public static boolean show(CopperMachineBlockEntity machine,ServerPlayer player) {
        if(machine.kind!=MachineKind.PRODUCTION_CONTROLLER || !machine.canAccess(player))return false;
        try {
            ServerLevel level=player.serverLevel();
            var specs=List.of(
                    crafting(level,"resonant_coil","unusualcopper:resonant_coil",MachineKind.PRODUCTION_CONTROLLER,240,80),
                    crafting(level,"reinforced_frame","unusualcopper:reinforced_frame",MachineKind.PRODUCTION_CONTROLLER,1800,120),
                    crafting(level,"copper_frame","unusualcopper:copper_frame",null,0,1),
                    press(level,"industrial/press_copper_wire","unusualcopper:copper_wire_spool"),
                    press(level,"industrial/press_copper_plate","unusualcopper:copper_plate")
            );
            int amount=Math.max(1,machine.productionQueued);
            String target=machine.productionPattern==0?"unusualcopper:resonant_coil":"unusualcopper:reinforced_frame";
            var plan=ProductionDependencyPlanner.plan(specs,target,amount);
            player.displayClientMessage(Component.translatable("production.unusualcopper.preview.header",
                    amount,item(target),plan.totalCue(),plan.totalTicks()),false);
            for(var stage:plan.stages())
                player.displayClientMessage(Component.translatable("production.unusualcopper.preview.stage",
                        stage.operations(),item(stage.output()),stage.machine(),stage.cue()),false);
            for(var raw:new TreeMap<>(plan.rawMaterials()).entrySet())
                player.displayClientMessage(Component.translatable("production.unusualcopper.preview.material",
                        raw.getValue(),item(raw.getKey())),false);
            for(String tool:new TreeSet<>(plan.reusableTools()))
                player.displayClientMessage(Component.translatable("production.unusualcopper.preview.tool",
                        item(tool)),false);
            player.displayClientMessage(Component.translatable("production.unusualcopper.preview.warning"),false);
            return true;
        }catch(RuntimeException failure){
            player.displayClientMessage(Component.translatable("production.unusualcopper.preview.failed",
                    failure.getMessage()==null?"invalid recipe":failure.getMessage()),false);
            return false;
        }
    }

    private static ProductionDependencyPlanner.RecipeSpec crafting(ServerLevel level,String id,String output,
                                                                     MachineKind machine,int cue,int ticks) {
        var entry=level.getRecipeManager().byKey(new ResourceLocation("unusualcopper",id));
        if(entry.isEmpty() || !(entry.get() instanceof ShapedRecipe recipe)
                || recipe.getWidth()!=3 || recipe.getHeight()!=3 || recipe.getIngredients().size()!=9)
            throw new IllegalArgumentException("Missing/changed crafting recipe: "+id);
        ItemStack result=recipe.getResultItem(level.registryAccess());
        validateOutput(result,output);
        var inputs=new TreeMap<String,Integer>();
        for(Ingredient ingredient:recipe.getIngredients())
            if(!ingredient.isEmpty())inputs.merge(singleItem(ingredient),1,Integer::sum);
        return new ProductionDependencyPlanner.RecipeSpec(id,output,result.getCount(),
                machine==null?"crafting_table":machine.id,cue,ticks,inputs,Set.of());
    }

    private static ProductionDependencyPlanner.RecipeSpec press(ServerLevel level,String id,String output) {
        var entry=level.getRecipeManager().byKey(new ResourceLocation("unusualcopper",id));
        if(entry.isEmpty() || !(entry.get() instanceof IndustrialRecipe recipe)
                || recipe.machine()!=MachineKind.COPPER_PRESS)
            throw new IllegalArgumentException("Missing/changed press recipe: "+id);
        validateOutput(recipe.getResultItem(level.registryAccess()),output);
        var inputs=recipe.getIngredients();
        if(inputs.size()!=2)throw new IllegalArgumentException("Unsupported press material or mold: "+id);
        return new ProductionDependencyPlanner.RecipeSpec(id,output,recipe.getResultItem(level.registryAccess()).getCount(),
                recipe.machine().id,recipe.cost(),recipe.ticks(),
                Map.of(singleItem(inputs.get(0)),recipe.inputCount()),Set.of(singleItem(inputs.get(1))));
    }

    /** Explicit whitelists refuse ambiguous tag alternatives and any NBT-bearing input. */
    private static String singleItem(Ingredient ingredient) {
        var choices=ingredient.getItems();
        if(choices.length==0)throw new IllegalArgumentException("Empty recipe ingredient");
        String key=null;
        for(ItemStack stack:choices) {
            if(stack.isEmpty()||stack.hasTag() || stack.getCount()!=1)
                throw new IllegalArgumentException("Unsafe recipe ingredient");
            String next=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if(key!=null&&!key.equals(next))
                throw new IllegalArgumentException("Ambiguous ingredient variants");
            key=next;
        }
        return key;
    }

    private static void validateOutput(ItemStack stack,String id) {
        if(stack.isEmpty()||stack.hasTag()||stack.getCount()<1||stack.getCount()>64
                ||!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id))
            throw new IllegalArgumentException("Changed or unsafe output: "+id);
    }

    private static Component item(String id) {
        ResourceLocation key=new ResourceLocation(id);
        return Component.translatable(BuiltInRegistries.ITEM.get(key).getDescriptionId());
    }
}
