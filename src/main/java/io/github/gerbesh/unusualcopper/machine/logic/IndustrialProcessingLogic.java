package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineLogic;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.machine.OperationMath;
import io.github.gerbesh.unusualcopper.recipe.IndustrialRecipe;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Purely server-authoritative operation, persisted in the existing machine NBT. */
public final class IndustrialProcessingLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m, ServerLevel level) {
        ItemStack input=m.inventory.getStackInSlot(0);
        if (input.isEmpty()) { m.status(m.hasOperation()?MachineStatus.RECIPE_CHANGED:MachineStatus.IDLE); return; }
        var container=new SimpleContainer(input,m.inventory.getStackInSlot(1));
        IndustrialRecipe recipe=null;
        // Check ID during an in-progress operation; never switch recipes after CuE was paid.
        for (var candidate : level.getRecipeManager().getAllRecipesFor(ModContent.INDUSTRIAL_RECIPE.get())) {
            if (candidate.machine()==m.kind && candidate.matches(container,level)
                    && (!m.hasOperation() || m.recipeId.equals(candidate.getId().toString()))) {
                recipe=candidate; break;
            }
        }
        if (recipe==null) { m.status(m.hasOperation()?MachineStatus.RECIPE_CHANGED:MachineStatus.INVALID_INPUT); return; }
        ItemStack result=recipe.assemble(container,level.registryAccess());
        if (m.hasOperation() && (!m.inputMatches() || m.duration!=recipe.ticks()
                || m.operationCost!=recipe.cost() || !ItemStack.matches(m.result,result))) {
            m.status(MachineStatus.RECIPE_CHANGED); return;
        }
        if (!m.hasOperation()) {
            if (!m.canFitOutput(result)) { m.status(MachineStatus.OUTPUT_FULL); return; }
            m.begin(recipe.getId().toString(),input.copyWithCount(recipe.inputCount()),result,recipe.ticks(),recipe.cost());
        }
        if (!m.canFitOutput(m.result)) { m.status(MachineStatus.OUTPUT_FULL); return; }
        if (m.progress>=m.duration) { m.finish(); return; }
        int due=OperationMath.payment(m.operationCost,m.progress+1,m.duration);
        if (!m.energy.spend(due)) { m.status(MachineStatus.NO_ENERGY); return; }
        m.progress++;
        m.paid+=due;
        m.status(MachineStatus.WORKING);
        if (m.progress>=m.duration) m.finish();
        m.setChanged();
    }
}
