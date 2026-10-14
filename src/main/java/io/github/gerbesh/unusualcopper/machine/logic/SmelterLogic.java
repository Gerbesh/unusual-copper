package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.ItemStack;
import io.github.gerbesh.unusualcopper.config.ServerConfig;

public final class SmelterLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m, ServerLevel level) {
        ItemStack input = m.inventory.getStackInSlot(0);
        if (m.hasOperation() && !m.inputMatches()) m.cancelOperation();
        if (input.isEmpty()) { m.status(MachineStatus.IDLE); return; }
        if (!input.is(ModContent.SMELTABLE_ORES) || input.is(ModContent.SMELTING_BLACKLIST)) { m.status(MachineStatus.INVALID_INPUT); return; }
        var container = new SimpleContainer(input);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, container, level);
        if (recipe.isEmpty()) { m.status(m.hasOperation() ? MachineStatus.RECIPE_CHANGED : MachineStatus.INVALID_INPUT); return; }
        ItemStack output = recipe.get().assemble(container, level.registryAccess());
        int ticks = OperationMath.smeltTicks(recipe.get().getCookingTime(), ServerConfig.smeltDivisor());
        if (m.hasOperation() && (!m.recipeId.equals(recipe.get().getId().toString()) || !m.inputMatches()
                || m.duration != ticks || !ItemStack.matches(m.result, output))) { m.status(MachineStatus.RECIPE_CHANGED); return; }
        if (!m.hasOperation()) {
            if (!m.canFitOutput(output)) { m.status(MachineStatus.OUTPUT_FULL); return; }
            m.begin(recipe.get().getId().toString(), input.copyWithCount(1), output, ticks, ServerConfig.smeltCost());
        }
        if (!m.canFitOutput(m.result)) { m.status(MachineStatus.OUTPUT_FULL); return; }
        if (m.progress >= m.duration) { m.finish(); return; }
        int charge = OperationMath.payment(m.operationCost, m.progress + 1, m.duration);
        if (!m.energy.spend(charge)) { m.status(MachineStatus.NO_ENERGY); return; }
        m.paid += charge;
        m.progress++;
        m.status(MachineStatus.WORKING);
        if (m.progress >= m.duration) m.finish();
        m.setChanged();
    }
}
