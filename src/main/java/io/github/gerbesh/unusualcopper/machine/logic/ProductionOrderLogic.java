package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

/**
 * E5A: server-owned queued assembly of two deliberately whitelisted vanilla-shaped recipes.
 * No external inventory is ever treated as an escrow. The machine-owned 3x3 grid is
 * physically reserved for the duration of each paid job.
 */
public final class ProductionOrderLogic implements MachineLogic {

    private static CraftingContainer snapshot(CopperMachineBlockEntity machine) {
        AbstractContainerMenu dummy = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return false; }
        };
        CraftingContainer grid = new TransientCraftingContainer(dummy, 3, 3);
        for (int i = 0; i < 9; i++) grid.setItem(i, machine.assemblyGrid.getStackInSlot(i).copy());
        return grid;
    }

    @Override public void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (machine.productionQueued == 0) {
            machine.status(MachineStatus.IDLE);
            return;
        }
        ItemStack card = machine.inventory.getStackInSlot(0);
        if (!card.is(ModContent.PATTERN_CARD.get()) || card.hasTag()) {
            machine.status(MachineStatus.INVALID_INPUT);
            return;
        }
        var plan=ProductionOrderPolicy.plan(machine.productionPattern);
        int index = machine.productionPattern;
        ResourceLocation id = new ResourceLocation("unusualcopper", plan.recipe());
        var entry = level.getRecipeManager().byKey(id);
        if (entry.isEmpty() || !(entry.get() instanceof CraftingRecipe recipe)) {
            machine.status(MachineStatus.RECIPE_CHANGED);
            return;
        }
        CraftingContainer grid = snapshot(machine);
        if (!recipe.matches(grid, level)) {
            machine.status(machine.hasOperation()?MachineStatus.RECIPE_CHANGED
                    :machine.boundInventory()!=null?MachineStatus.WAITING_MATERIALS:MachineStatus.INVALID_INPUT);
            return;
        }
        // No modded capabilities, enchantments or container-return recipes in this first whitelist.
        NonNullList<ItemStack> returned = recipe.getRemainingItems(grid);
        for (ItemStack item : returned) if (!item.isEmpty()) {
            machine.status(MachineStatus.INVALID_INPUT);
            return;
        }
        ItemStack output = recipe.assemble(grid, level.registryAccess());
        if (output.hasTag() || output.getCount() != 1
                || (index == 0 && !output.is(ModContent.RESONANT_COIL.get()))
                || (index == 1 && !output.is(ModContent.REINFORCED_FRAME.get()))) {
            machine.status(MachineStatus.RECIPE_CHANGED);
            return;
        }
        if (machine.hasOperation() && (!machine.recipeId.equals(id.toString())
                || !machine.inputMatches() || !machine.productionInputsMatch()
                || machine.duration != plan.ticks() || machine.operationCost != plan.cue()
                || !ItemStack.matches(machine.result, output))) {
            machine.status(MachineStatus.RECIPE_CHANGED);
            return;
        }
        if (!machine.hasOperation()) {
            if (!machine.canFitOutput(output)) {
                machine.status(MachineStatus.OUTPUT_FULL);
                return;
            }
            machine.begin(id.toString(), card.copyWithCount(1), output, plan.ticks(), plan.cue());
            for (int i = 0; i < 9; i++) {
                ItemStack stack = grid.getItem(i);
                machine.assemblySnapshot.setStackInSlot(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            }
            machine.setChanged();
        }
        if (!machine.canFitOutput(machine.result)) {
            machine.status(MachineStatus.OUTPUT_FULL);
            return;
        }
        int charge = OperationMath.payment(machine.operationCost, machine.progress + 1, machine.duration);
        if (!machine.energy.spend(charge)) {
            machine.status(MachineStatus.NO_ENERGY);
            return;
        }
        machine.progress++;
        machine.paid += charge;
        machine.status(MachineStatus.WORKING);
        if (machine.progress >= machine.duration && !machine.finish()) machine.status(MachineStatus.RECIPE_CHANGED);
        machine.setChanged();
    }
}


