package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class CopperMachineMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 14;
    private final CopperMachineBlockEntity machine;
    private final BlockPos pos;
    public final ContainerData data;
    public CopperMachineMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos(), new ItemStackHandler(4), new SimpleContainerData(DATA_COUNT));
    }
    public CopperMachineMenu(int id, Inventory inventory, CopperMachineBlockEntity machine) {
        this(id, inventory, machine, machine.getBlockPos(), machine.inventory, machine.menuData());
    }
    private CopperMachineMenu(int id, Inventory playerInventory, CopperMachineBlockEntity machine, BlockPos pos, IItemHandler slots, ContainerData data) {
        super(ModContent.MACHINE_MENU.get(), id); this.machine = machine; this.pos = pos; this.data = data;
        int[][] positions = {{44, 63}, {44, 91}, {134, 63}, {152, 63}};
        for (int index = 0; index < 4; index++) {
            final int slotIndex = index;
            addSlot(new SlotItemHandler(slots, index, positions[index][0], positions[index][1]) {
                @Override public boolean isActive() { return data.get(8) != MachineKind.COPPER_CELL.ordinal() && (slotIndex != 1 || data.get(8) == MachineKind.SOLAR_INFUSER.ordinal()); }
                @Override public boolean mayPickup(Player player) { return machine == null || slotIndex >= 2 || !machine.hasOperation(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 31 + col * 18, 130 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 31 + col * 18, 188));
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player player) {
        return machine == null || (player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) == machine && machine.canAccess(player) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64);
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (machine == null || !stillValid(player)) return false;
        if (button == 0) { machine.redstoneMode = (machine.redstoneMode + 1) % 3; machine.setChanged(); return true; }
        if (button == 1) { machine.cancelOperation(); return true; }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player) || !stillValid(player)) return ItemStack.EMPTY;
        ItemStack current = slot.getItem(); ItemStack original = current.copy();
        if (index < 4) { if (!moveItemStackTo(current, 4, 40, true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(current, 0, 2, false)) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, current); return original;
    }
    public int energy() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    public int capacity() { return (data.get(2) & 0xffff) | ((data.get(3) & 0xffff) << 16); }
    public int progress() { return (data.get(4) & 0xffff) | ((data.get(10) & 0xffff) << 16); }
    public int duration() { return (data.get(5) & 0xffff) | ((data.get(11) & 0xffff) << 16); }
    public int operationCost() { return (data.get(12) & 0xffff) | ((data.get(13) & 0xffff) << 16); }
}
