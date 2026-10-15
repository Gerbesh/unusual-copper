package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Explicit wand binding, with machine-owned escrow for every physically withdrawn item. */
public final class BoundStorageLogic {
    private BoundStorageLogic() {}
    public static void tick(CopperMachineBlockEntity machine, ServerLevel level) {
        if (!machine.kind.hasInventory() || machine.boundFaulted || !ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) return;
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
        for (int slot = 0; slot <= 1; slot++) {
            if(machine.kind==MachineKind.ENCHANTMENT_RESONATOR&&slot==0)continue;
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
    private static void deliver(CopperMachineBlockEntity machine, StorageEndpoint endpoint, List<ItemStack> cargo) {
        var stack = cargo.get(0); int count = Math.min(8, Math.min(stack.getCount(), stack.getMaxStackSize())); var offered = stack.copyWithCount(count);
        ItemStack rest;
        if (machine.boundCargoSlot >= 0) rest = machine.inventory.insertItem(machine.boundCargoSlot, offered, false);
        else if (endpoint != null && endpoint.isAvailable()) rest = endpoint.insert(offered, false);
        else return;
        int moved=count-rest.getCount();
        boolean showTransfer=moved>0 && machine.boundCargoSlot<0 && machine.kind==MachineKind.MOB_REAPER && machine.boundInventory()!=null && ItemKey.of(stack).matches(offered) && (rest.isEmpty() || ItemKey.of(stack).matches(rest));
        var after = new ArrayList<ItemStack>();
        if (stack.getCount() > count) after.add(stack.copyWithCount(stack.getCount() - count));
        if (!rest.isEmpty()) after.add(rest.copy()); after.addAll(cargo.subList(1, cargo.size())); machine.boundCargo(after);
        if (!rest.isEmpty() && (!ItemKey.of(stack).matches(rest) || rest.getCount() > count)) { machine.boundFaulted = true; machine.setChanged(); }
        if(showTransfer) io.github.gerbesh.unusualcopper.network.MachineEffects.reaperTransfer((ServerLevel)machine.getLevel(),machine,machine.boundInventory(),stack.copyWithCount(moved));
    }
}
