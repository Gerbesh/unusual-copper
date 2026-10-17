package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Explicit wand binding, with machine-owned escrow for every physically withdrawn item. */
public final class BoundStorageLogic {
    private BoundStorageLogic() {}
    public static void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (!machine.kind.hasInventory() || machine.boundFaulted || machine.pressLeased()
                || !ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) return;
        var pos = machine.boundInventory();
        StorageEndpoint endpoint = pos == null || machine.getBlockPos().distSqr(pos) > 32 * 32 ? null : machine.boundEndpoint(level);
        var cargo = machine.boundCargo();
        if (!cargo.isEmpty()) { deliver(machine, endpoint, cargo); return; }
        if (endpoint == null || !endpoint.isAvailable()) return;
        for (int slot = 2; slot <= 3; slot++) {
            var stack = machine.inventory.getStackInSlot(slot); if (stack.isEmpty()) continue;
            int count = Math.min(8, stack.getCount());
            if (endpoint.insert(stack.copyWithCount(count), true).getCount() == count) continue;
            var extracted = machine.inventory.extractItem(slot, count, false);
            machine.boundCargo(List.of(extracted)); machine.boundCargoSlot = -1; deliver(machine, endpoint, machine.boundCargo()); return;
        }
        if (machine.kind == MachineKind.PRODUCTION_CONTROLLER && machine.productionQueued>0) {
            replenishProductionGrid(machine,level,endpoint);
            return;
        }
        if(!machine.kind.acceptsInputs())return;
        for (int slot = 0; slot <= 1; slot++) {
            if(machine.mining!=null)continue;
            if((machine.kind==MachineKind.ENCHANTMENT_RESONATOR||machine.kind==MachineKind.REPAIR_CRYSTAL)&&slot==0)continue;
            final int input = slot;
            var view = endpoint.snapshot(stack -> machine.inventory.isItemValid(input, stack), 32);
            for (var entry : view.entries()) {
                int count = Math.min(8, Math.min(entry.count(), entry.key().stack(1).getMaxStackSize()));
                if(machine.brewer!=null)count=Math.min(count,BrewerLogic.request(machine,input));
                count -= machine.inventory.insertItem(input, entry.key().stack(count), true).getCount();
                if (count <= 0) continue;
                count = Math.min(count, endpoint.simulateExtract(entry.key(), count)); if (count <= 0) continue;
                var actual = endpoint.extract(entry.key(), count); if (actual.isEmpty()) continue;
                machine.boundCargo(actual); machine.boundCargoSlot = input;
                machine.boundFaulted = actual.stream().anyMatch(stack -> !entry.key().matches(stack)) || actual.stream().mapToLong(ItemStack::getCount).sum() > count;
                if (!machine.boundFaulted) deliver(machine, endpoint, machine.boundCargo());
                machine.setChanged(); return;
            }
        }
    }
    /**
     * E5B: physically withdraw one bounded batch from the player's explicitly bound
     * chest or optional SSN bridge. The existing persisted BoundCargo is the escrow:
     * simulation never counts as a reservation and no materials are promised twice.
     * This method never crafts intermediate components or touches another controller.
     */
    private static void replenishProductionGrid(CopperMachineBlockEntity m,ServerLevel level,StorageEndpoint endpoint) {
        if(m.boundCargoCount()!=0 || m.boundFaulted || m.hasOperation() && !m.productionInputsMatch())return;
        var plan=ProductionOrderPolicy.plan(m.productionPattern);
        var loaded=level.getRecipeManager().byKey(new ResourceLocation("unusualcopper",plan.recipe()));
        if(loaded.isEmpty() || !(loaded.get() instanceof ShapedRecipe recipe)
                || recipe.getWidth()!=3 || recipe.getHeight()!=3 || recipe.getIngredients().size()!=9)return;
        // Refuse a server datapack replacement which changes the recipe's output.
        var expected=m.productionPattern==0?io.github.gerbesh.unusualcopper.registry.ModContent.RESONANT_COIL.get()
                :io.github.gerbesh.unusualcopper.registry.ModContent.REINFORCED_FRAME.get();
        if(!recipe.getResultItem(level.registryAccess()).is(expected)
                || recipe.getResultItem(level.registryAccess()).hasTag()
                || recipe.getResultItem(level.registryAccess()).getCount()!=1)return;
        for(int slot=0;slot<9;slot++) {
            Ingredient ingredient=recipe.getIngredients().get(slot);
            ItemStack held=m.assemblyGrid.getStackInSlot(slot);
            if(ingredient.isEmpty()) {
                // Never try to clear player-supplied items automatically.
                if(!held.isEmpty())return;
                continue;
            }
            if(!held.isEmpty() && (!ingredient.test(held) || held.hasTag()))return;
            int target=Math.min(m.assemblyGrid.getSlotLimit(slot),m.productionQueued);
            if(held.getCount()>=target)continue;
            final int gridSlot=slot;
            var census=endpoint.snapshot(stack -> !stack.isEmpty() && !stack.hasTag()
                    && !(stack.getItem() instanceof io.github.gerbesh.unusualcopper.item.CopperBatteryItem)
                    && ingredient.test(stack) && (held.isEmpty() || ItemKey.of(held).matches(stack)),64);
            for(var candidate:census.entries()) {
                int count=ProductionSupplyPolicy.needed(m.productionQueued,held.getCount(),
                        m.assemblyGrid.getSlotLimit(gridSlot),candidate.count());
                if(count<=0)continue;
                ItemStack keyStack=candidate.key().stack(count);
                if(!ingredient.test(keyStack) || !m.assemblyGrid.isItemValid(gridSlot,keyStack))continue;
                count-=m.assemblyGrid.insertItem(gridSlot,keyStack,true).getCount();
                count=Math.min(count,endpoint.simulateExtract(candidate.key(),count));
                if(count<=0)continue;
                var actual=endpoint.extract(candidate.key(),count);
                if(actual.isEmpty())continue;
                // Persist all returned items, even a badly behaved external handler's
                // unexpected stacks. Faults can be recovered through the existing wand UI.
                m.boundCargo(actual);
                m.boundCargoSlot=ProductionSupplyPolicy.cargoSlot(gridSlot);
                m.boundFaulted=actual.stream().anyMatch(item -> !candidate.key().matches(item))
                        || actual.stream().mapToLong(ItemStack::getCount).sum()>count;
                if(!m.boundFaulted)deliver(m,endpoint,m.boundCargo());
                m.setChanged();
                return;
            }
        }
    }

    private static void deliver(CopperMachineBlockEntity machine, StorageEndpoint endpoint, List<ItemStack> cargo) {
        var stack = cargo.get(0); int count = Math.min(8, Math.min(stack.getCount(), stack.getMaxStackSize())); var offered = stack.copyWithCount(count);
        ItemStack rest;
        if(machine.kind==MachineKind.PRODUCTION_CONTROLLER && machine.boundCargoSlot==13)
            rest=ProductionUpstreamLogic.deliverInput(machine,offered);
        else if(machine.kind==MachineKind.PRODUCTION_CONTROLLER
                && machine.boundCargoSlot>=14 && machine.boundCargoSlot<=22)
            rest=machine.productionSubGrid.insertItem(machine.boundCargoSlot-14,offered,false);
        else if(machine.kind==MachineKind.PRODUCTION_CONTROLLER && ProductionSupplyPolicy.gridCargoSlot(machine.boundCargoSlot))
            rest=machine.assemblyGrid.insertItem(machine.boundCargoSlot-4,offered,false);
        else if (machine.boundCargoSlot >= 0 && machine.boundCargoSlot < 2)
            rest=machine.inventory.insertItem(machine.boundCargoSlot, offered, false);
        else if (machine.boundCargoSlot >= 0) return;
        else if (endpoint != null && endpoint.isAvailable()) rest = endpoint.insert(offered, false);
        else return;
        int moved=count-rest.getCount();
        boolean showTransfer=moved>0 && machine.boundCargoSlot<0 && (machine.kind==MachineKind.MOB_REAPER || machine.kind==MachineKind.AURA_CULTIVATOR) && machine.boundInventory()!=null && ItemKey.of(stack).matches(offered) && (rest.isEmpty() || ItemKey.of(stack).matches(rest));
        var after = new ArrayList<ItemStack>();
        if (stack.getCount() > count) after.add(stack.copyWithCount(stack.getCount() - count));
        if (!rest.isEmpty()) after.add(rest.copy()); after.addAll(cargo.subList(1, cargo.size())); machine.boundCargo(after);
        if (!rest.isEmpty() && (!ItemKey.of(stack).matches(rest) || rest.getCount() > count)) { machine.boundFaulted = true; machine.setChanged(); }
        if(showTransfer) {
            if(machine.kind==MachineKind.AURA_CULTIVATOR)io.github.gerbesh.unusualcopper.network.MachineEffects.cultivatorDelivery((ServerLevel)machine.getLevel(),machine,machine.boundInventory(),stack.copyWithCount(moved));
            else io.github.gerbesh.unusualcopper.network.MachineEffects.reaperTransfer((ServerLevel)machine.getLevel(),machine,machine.boundInventory(),stack.copyWithCount(moved));
        }
    }
}
