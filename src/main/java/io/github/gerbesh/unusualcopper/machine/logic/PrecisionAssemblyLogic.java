package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineLogic;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.machine.OperationMath;
import io.github.gerbesh.unusualcopper.recipe.PrecisionAssemblyRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** E4 single-recipe precision assembly, with server-owned grid and exactly-once material commit. */
public final class PrecisionAssemblyLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m, ServerLevel level) {
        ItemStack card=m.inventory.getStackInSlot(0);
        if(!card.is(ModContent.PATTERN_CARD.get()) || card.hasTag()) {
            m.status(m.hasOperation()?MachineStatus.RECIPE_CHANGED:MachineStatus.INVALID_INPUT);
            return;
        }
        SimpleContainer container=new SimpleContainer(9);
        for(int i=0;i<9;i++)container.setItem(i,m.assemblyGrid.getStackInSlot(i));
        PrecisionAssemblyRecipe recipe=null;
        for(var candidate:level.getRecipeManager().getAllRecipesFor(ModContent.ASSEMBLY_RECIPE.get())) {
            if(candidate.matches(container,level) && (!m.hasOperation()||m.recipeId.equals(candidate.getId().toString()))) {
                recipe=candidate; break;
            }
        }
        if(recipe==null){m.status(m.hasOperation()?MachineStatus.RECIPE_CHANGED:MachineStatus.INVALID_INPUT);return;}
        ItemStack output=recipe.assemble(container,level.registryAccess());
        if(m.hasOperation() && (!m.inputMatches()||!m.assemblyInputsMatch()
                ||m.duration!=recipe.ticks()||m.operationCost!=recipe.cue()
                ||!ItemStack.matches(m.result,output))) {
            m.status(MachineStatus.RECIPE_CHANGED);return;
        }
        if(!m.hasOperation()){
            if(!m.canFitOutput(output)){m.status(MachineStatus.OUTPUT_FULL);return;}
            m.begin(recipe.getId().toString(),card.copyWithCount(1),output,recipe.ticks(),recipe.cue());
            for(int i=0;i<9;i++)m.assemblySnapshot.setStackInSlot(i,m.assemblyGrid.getStackInSlot(i).copyWithCount(1));
            m.setChanged();
        }
        if(!m.canFitOutput(m.result)){m.status(MachineStatus.OUTPUT_FULL);return;}
        if(m.progress>=m.duration){if(!m.finish())m.status(MachineStatus.RECIPE_CHANGED);return;}
        int due=OperationMath.payment(m.operationCost,m.progress+1,m.duration);
        if(!m.energy.spend(due)){m.status(MachineStatus.NO_ENERGY);return;}
        m.progress++;m.paid+=due;m.status(MachineStatus.WORKING);
        if(m.progress>=m.duration&&!m.finish())m.status(MachineStatus.RECIPE_CHANGED);
        m.setChanged();
    }
}
