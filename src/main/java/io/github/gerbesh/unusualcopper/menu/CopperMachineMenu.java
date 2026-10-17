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
    public static final int DATA_COUNT = 54;
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
        int[][] positions = kind()==MachineKind.PRECISION_ASSEMBLER || kind()==MachineKind.PRODUCTION_CONTROLLER
                ? new int[][]{{185,63},{44,91},{134,63},{152,63}}
                : new int[][]{{44,63},{44,91},{134,63},{152,63}};
        for (int index = 0; index < 4; index++) {
            final int slotIndex = index;
            addSlot(new SlotItemHandler(slots, index, positions[index][0], positions[index][1]) {
                @Override public boolean isActive() { return slotIndex >= 2 ? kind().hasInventory() : kind().acceptsInputs() && (slotIndex != 1 || kind() == MachineKind.SOLAR_INFUSER || kind() == MachineKind.COPPER_PRESS || kind() == MachineKind.VORTEX_PURIFIER || kind() == MachineKind.CIRCUIT_ETCHER || kind() == MachineKind.RESONANCE_CRYSTALLIZER || kind() == MachineKind.ARMOR_WORKBENCH || kind() == MachineKind.ARBORIST || kind()==MachineKind.AETHERIC_BREWER); }
                @Override public boolean mayPickup(Player player) { return machine == null || slotIndex >= 2 || !machine.hasOperation(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 31 + col * 18, 130 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 31 + col * 18, 188));
        var dock=machine==null?new ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot,ItemStack stack) { return stack.getItem() instanceof io.github.gerbesh.unusualcopper.item.CopperBatteryItem; }
        }:machine.battery;
        addSlot(new SlotItemHandler(dock,0,13,64) {
            @Override public boolean isActive() { return kind().isCell(); }
        });
        var extra=machine==null?new ItemStackHandler(1):machine.industrialExtra;
        addSlot(new SlotItemHandler(extra,0,69,91) {
            @Override public boolean isActive() { return kind()==MachineKind.RESONANCE_CRYSTALLIZER || kind()==MachineKind.CIRCUIT_ETCHER; }
            @Override public boolean mayPickup(Player player) { return machine==null || !machine.hasOperation(); }
        });
        var grid=machine==null?new ItemStackHandler(9):machine.assemblyGrid;
        for(int i=0;i<9;i++){
            final int slotIndex=i;
            addSlot(new SlotItemHandler(grid,i,13+(i%3)*18,61+(i/3)*18){
                @Override public boolean isActive(){return kind()==MachineKind.PRECISION_ASSEMBLER || kind()==MachineKind.PRODUCTION_CONTROLLER;}
                @Override public boolean mayPickup(Player player){return machine==null||!machine.hasOperation();}
            });
        }
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player player) {
        return machine == null || (player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) == machine && machine.canAccess(player) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64);
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (machine == null || !stillValid(player)) return false;
        if(machine.kind==MachineKind.ARMOR_WORKBENCH) {
            if(button==16)return io.github.gerbesh.unusualcopper.machine.logic.ArmorWorkbenchLogic.install(machine);
            if(button==17)return io.github.gerbesh.unusualcopper.machine.logic.ArmorWorkbenchLogic.recoverModule(machine);
            if(button==18)return io.github.gerbesh.unusualcopper.machine.logic.ArmorWorkbenchLogic.recoverBattery(machine);
            if(button==19)return io.github.gerbesh.unusualcopper.machine.logic.ArmorWorkbenchLogic.cycleProfile(machine);
        }
        if(machine.kind==MachineKind.PRODUCTION_CONTROLLER) {
            if(button==12){machine.queueProduction(1);return true;}
            if(button==13){machine.queueProduction(16);return true;}
            if(button==14 || button==1){machine.cancelProduction();return true;}
            if(button==15){machine.nextProductionPattern();return true;}
        }
        if(button==11 && machine.kind.isCell()) { machine.batteryMode=(machine.batteryMode+1)%3;machine.setChanged();return true; }
        if(button==10&&player instanceof net.minecraft.server.level.ServerPlayer server&&(machine.kind.capacity>0||machine.kind.energyControl())){EnergyControlMenu.open(server,machine);return true;}
        if (button == 0) { machine.redstoneMode = (machine.redstoneMode + 1) % 3; machine.setChanged(); return true; }
        if (button == 1) { if(machine.arborist!=null) machine.arborist.cancel(); if(machine.brewer!=null)machine.brewer.cancel(); machine.cancelOperation(); machine.setChanged(); return true; }
        if (button == 2 && machine.kind == MachineKind.AURA_CULTIVATOR) { machine.growthMode = (machine.growthMode + 1) % 3; machine.setChanged(); return true; }
        if (button == 3 && machine.workArea() != null && player instanceof net.minecraft.server.level.ServerPlayer server) { io.github.gerbesh.unusualcopper.network.AreaNetwork.preview(server, machine.workArea().min(), machine.workArea().max(), true); return true; }
        if (button == 4) { machine.clearBinding(); return true; }
        if (button == 5) { machine.recoverBinding(player); return true; }
        if (button == 6 && machine.kind == MachineKind.GROWTH_SHEPHERD) { machine.allowOwnTamed = !machine.allowOwnTamed; machine.setChanged(); return true; }
        if (button == 7 && machine.reaper != null && player instanceof net.minecraft.server.level.ServerPlayer server) { ReaperMenu.open(server,machine); return true; }
        if(button==9&&machine.brewer!=null&&player instanceof net.minecraft.server.level.ServerPlayer server){BrewerMenu.open(server,machine);return true;}
        if(button==8 && machine.arborist!=null && !machine.arborist.points.isEmpty() && player instanceof net.minecraft.server.level.ServerPlayer server) {
            var point=machine.arborist.points.get(Math.floorMod(plantationPreview++,machine.arborist.points.size()));
            io.github.gerbesh.unusualcopper.network.AreaNetwork.preview(server,point.pos(),point.pos(),true);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("arborist.unusualcopper.point",net.minecraft.network.chat.Component.translatable(point.species().sapling.getDescriptionId()),point.pos().getX(),point.pos().getY(),point.pos().getZ()),true); return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.isActive() || !slot.hasItem() || !slot.mayPickup(player) || !stillValid(player)) return ItemStack.EMPTY;
        ItemStack current = slot.getItem(); ItemStack original = current.copy();
        if (index < 4 || index==40 || index==41 || index>=42) { if (!moveItemStackTo(current, 4, 40, true)) return ItemStack.EMPTY; }
        else if(kind().isCell()) { if(!moveItemStackTo(current,40,41,false))return ItemStack.EMPTY; }
        else if((kind()==MachineKind.PRECISION_ASSEMBLER || kind()==MachineKind.PRODUCTION_CONTROLLER) && !current.is(ModContent.PATTERN_CARD.get())) {
            if(!moveItemStackTo(current,42,51,false))return ItemStack.EMPTY;
        }
        else if ((kind()==MachineKind.RESONANCE_CRYSTALLIZER && current.is(ModContent.ESSENCE.get()))
                || (kind()==MachineKind.CIRCUIT_ETCHER && current.is(net.minecraft.world.item.Items.WATER_BUCKET))) {
            if(!moveItemStackTo(current,41,42,false))return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(current, 0, 2, false)) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, current); return original;
    }
    public int energy() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    private int plantationPreview;
    public int capacity() { return (data.get(2) & 0xffff) | ((data.get(3) & 0xffff) << 16); }
    public int progress() { return (data.get(4) & 0xffff) | ((data.get(10) & 0xffff) << 16); }
    public int duration() { return (data.get(5) & 0xffff) | ((data.get(11) & 0xffff) << 16); }
    public int operationCost() { return (data.get(12) & 0xffff) | ((data.get(13) & 0xffff) << 16); }
    public MachineKind kind() { return MachineKind.values()[Math.floorMod(data.get(8), MachineKind.values().length)]; }
}
