package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.energy.*;
import io.github.gerbesh.unusualcopper.machine.logic.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import java.util.EnumMap;
import java.util.UUID;

public final class CopperMachineBlockEntity extends BlockEntity implements MenuProvider {
    public final MachineKind kind;
    public final CopperEnergyBuffer energy;
    public final ItemStackHandler inventory;
    private final MachineLogic logic;
    private final EnumMap<Direction, SideMode> sides = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<IItemHandler>> itemCaps = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, LazyOptional<CopperEnergyHandler>> energyCaps = new EnumMap<>(Direction.class);
    private UUID owner;
    private long capabilityEpoch;
    private MachineStatus status = MachineStatus.IDLE;
    public int redstoneMode;
    public String recipeId = "";
    public ItemStack source = ItemStack.EMPTY;
    public ItemStack result = ItemStack.EMPTY;
    public int progress;
    public int duration;
    public int paid;
    public int operationCost;
    public final UUID runtimeIdentity = UUID.randomUUID();
    private io.github.gerbesh.unusualcopper.gameplay.WorkArea workArea;
    public int scanCursor, growthMode, inspected, grown;
    public long nextWaveTick;
    public boolean allowOwnTamed;
    public final ReaperState reaper;
    public final ArboristState arborist;
    private net.minecraft.core.BlockPos boundInventory;
    private Direction boundSide = Direction.UP;
    private io.github.gerbesh.unusualcopper.storage.HandlerStorageEndpoint boundEndpoint;
    public io.github.gerbesh.unusualcopper.storage.HandlerStorageEndpoint boundEndpoint(ServerLevel level) {
        if (boundInventory == null) return null;
        if (boundEndpoint == null) boundEndpoint = new io.github.gerbesh.unusualcopper.storage.HandlerStorageEndpoint(level, boundInventory, boundSide, owner);
        return boundEndpoint;
    }
    private final java.util.List<ItemStack> boundCargo = new java.util.ArrayList<>();
    public int boundCargoSlot = -1;
    public boolean boundFaulted;
    public BlockPos boundInventory() { return boundInventory; }
    public Direction boundSide() { return boundSide; }
    public java.util.List<ItemStack> boundCargo() { return boundCargo.stream().map(ItemStack::copy).toList(); }
    public int boundCargoCount() { return (int)Math.min(Integer.MAX_VALUE, boundCargo.stream().mapToLong(ItemStack::getCount).sum()); }
    public void boundCargo(java.util.List<ItemStack> items) {
        boundCargo.clear(); for (var item : items) if (!item.isEmpty()) boundCargo.add(item.copy());
        if (boundCargo.isEmpty()) { boundFaulted = false; boundCargoSlot = -1; } setChanged();
    }
    public boolean bind(BlockPos pos, Direction side, Player player) {
        if (!kind.hasInventory() || !canAccess(player) || !boundCargo.isEmpty() || !(level instanceof ServerLevel server) || pos.equals(worldPosition)
                || worldPosition.distSqr(pos) > 32 * 32 || !server.hasChunkAt(pos) || !io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server, worldPosition, player.getUUID())) return false;
        var endpoint = new io.github.gerbesh.unusualcopper.storage.HandlerStorageEndpoint(server, pos, side, owner == null ? player.getUUID() : owner);
        if (!endpoint.isAvailable()) return false;
        if (owner == null) setOwner(player.getUUID()); boundInventory = pos.immutable(); boundSide = side; boundEndpoint = null; setChanged(); return true;
    }
    public void clearBinding() { boundInventory = null; boundEndpoint = null; setChanged(); }
    public void recoverBinding(Player player) {
        if (!canAccess(player)) return;
        var remainder = new java.util.ArrayList<ItemStack>();
        for (var stored : boundCargo) {
            var offered = stored.copyWithCount(Math.min(stored.getCount(), stored.getMaxStackSize())); int before = offered.getCount(); player.getInventory().add(offered);
            var rest = stored.copyWithCount(stored.getCount() - before + offered.getCount()); if (!rest.isEmpty()) remainder.add(rest);
        }
        boundCargo(remainder);
    }

    public CopperMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY.get(), pos, state);
        kind = ((CopperMachineBlock)state.getBlock()).kind;
        reaper = kind == MachineKind.MOB_REAPER ? new ReaperState() : null;
        arborist = kind == MachineKind.ARBORIST ? new ArboristState() : null;
        energy = new CopperEnergyBuffer(kind.capacity, 256, 256, this::setChanged);
        inventory = new ItemStackHandler(4) {
            @Override protected void onContentsChanged(int slot) { setChanged(); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (slot >= 2 || !kind.acceptsInputs()) return false;
                if(arborist!=null) {
                    if(arborist.job!=null) return false;
                    return slot==1?io.github.gerbesh.unusualcopper.gameplay.TreeSpecies.axe(stack):io.github.gerbesh.unusualcopper.gameplay.TreeSpecies.item(stack)!=null;
                }
                if (slot == 1) return kind == MachineKind.SOLAR_INFUSER && stack.is(ModContent.SOLAR_LENS.get());
                if (kind == MachineKind.ESSENCE_SPLITTER) return stack.is(ModContent.ESSENCE.get());
                if (kind == MachineKind.RESONANCE_SMELTER) return stack.is(ModContent.SMELTABLE_ORES) && !stack.is(ModContent.SMELTING_BLACKLIST);
                return true;
            }
        };
        logic = switch (kind) {
            case SOLAR_INFUSER -> new SolarLogic();
            case ESSENCE_SPLITTER -> new SplitterLogic();
            case RESONANCE_SMELTER -> new SmelterLogic();
            case COPPER_CELL -> (m, level) -> m.status(MachineStatus.IDLE);
            case AURA_CULTIVATOR -> new CultivatorLogic();
            case MAGNETIC_COLLECTOR -> new CollectorLogic();
            case GROWTH_SHEPHERD -> new ShepherdLogic();
            case MOB_REAPER -> new ReaperLogic();
            case ARBORIST -> new ArboristLogic();
        };
        for (Direction side : Direction.values()) {
            SideMode mode = kind.usesArea() && !kind.hasInventory() ? SideMode.ENERGY_INPUT
                    : kind == MachineKind.COPPER_CELL ? (side == Direction.EAST ? SideMode.ENERGY_OUTPUT : SideMode.ENERGY_INPUT)
                    : side == Direction.UP && kind.acceptsInputs() ? SideMode.ITEM_INPUT : side == Direction.DOWN && kind.hasInventory() ? SideMode.ITEM_OUTPUT
                    : kind.produces() ? SideMode.ENERGY_OUTPUT : kind.consumes() ? SideMode.ENERGY_INPUT : SideMode.DISABLED;
            sides.put(side, mode);
        }
        rebuildCaps();
    }

    public void setOwner(UUID id) { owner = id; boundEndpoint = null; setChanged(); }
    public boolean togglePlantation(BlockPos pos,Player player) {
        if(arborist==null || !canAccess(player) || !(level instanceof ServerLevel server)) return false;
        for(var point:java.util.List.copyOf(arborist.points)) if(point.pos().equals(pos)||point.pos().equals(pos.above())) {
            arborist.points.remove(point); if(arborist.job!=null && arborist.job.roots.contains(point.pos())) arborist.cancel(); arborist.scan=null; setChanged(); return true;
        }
        if(arborist.points.size()>=16 || worldPosition.distSqr(pos)>32*32 || !server.hasChunkAt(pos) || server.isOutsideBuildHeight(pos) || !server.getWorldBorder().isWithinBounds(pos)) return false;
        if(!io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,pos,owner==null?player.getUUID():owner) || !io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy.allowed(server,worldPosition,owner==null?player.getUUID():owner)) return false;
        var species=io.github.gerbesh.unusualcopper.gameplay.TreeSpecies.at(server.getBlockState(pos)); if(species==null) return false;
        if(owner==null) setOwner(player.getUUID()); arborist.points.add(new ArboristState.Point(pos,species)); arborist.scan=null; arborist.retryAt=0; setChanged(); return true;
    }
    public UUID owner() { return owner; }
    public boolean allowsAutomation(UUID actor) { return owner == null || owner.equals(actor); }
    public boolean canAccess(Player player) { return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2); }
    public io.github.gerbesh.unusualcopper.gameplay.WorkArea workArea() { return workArea; }
    public boolean setWorkArea(io.github.gerbesh.unusualcopper.gameplay.WorkArea area, Player player) {
        if (!kind.usesArea() || !canAccess(player) || !(level instanceof ServerLevel server) || !io.github.gerbesh.unusualcopper.gameplay.AreaAccess.valid(server, worldPosition, area, owner == null ? player.getUUID() : owner)) return false;
        if (owner == null) setOwner(player.getUUID()); workArea = area; scanCursor = 0; inspected = grown = 0; setChanged(); return true;
    }
    public MachineStatus status() { return status; }
    public void status(MachineStatus value) { if (status != value) { status = value; setChanged(); } }
    public SideMode sideMode(Direction side) { return sides.get(side); }
    public boolean energyEnabled() {
        if (isRemoved()) return false;
        boolean signal = level != null && level.hasNeighborSignal(worldPosition);
        return !((redstoneMode == 1 && !signal) || (redstoneMode == 2 && signal));
    }
    public void cycleSide(Direction side) {
        SideMode next = sides.get(side).next();
        while (!supportsSideMode(next)) next = next.next();
        sides.put(side, next);
        invalidateCaps(); rebuildCaps(); setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            EnergyNetworks.dirty(level);
        }
    }
    public boolean supportsSideMode(SideMode mode) {
        return switch (mode) {
            case DISABLED -> true;
            case ITEM_INPUT -> kind.acceptsInputs();
            case ITEM_OUTPUT -> kind.hasInventory();
            case ENERGY_INPUT -> kind.consumes();
            case ENERGY_OUTPUT -> kind.produces();
        };
    }

    public boolean hasOperation() { return arborist!=null && arborist.job!=null || !recipeId.isEmpty() && duration > 0; }
    public boolean inputMatches() {
        ItemStack current = inventory.getStackInSlot(0);
        return ItemStack.isSameItemSameTags(current, source) && current.getCount() >= source.getCount();
    }
    public void begin(String id, ItemStack input, ItemStack output, int ticks) {
        begin(id, input, output, ticks, 0);
    }
    public void begin(String id, ItemStack input, ItemStack output, int ticks, int cost) {
        recipeId = id; source = input.copy(); result = output.copy(); duration = ticks; progress = 0; paid = 0; operationCost = cost; setChanged();
    }
    public void cancelOperation() {
        recipeId = ""; source = ItemStack.EMPTY; result = ItemStack.EMPTY; progress = 0; duration = 0; paid = 0; operationCost = 0; setChanged();
    }
    public boolean canFitOutput(ItemStack stack) { return outputPlan(stack) != null; }
    public ItemStack insertOutput(ItemStack stack) {
        var rest = stack.copy();
        for (int slot = 2; slot <= 3 && !rest.isEmpty(); slot++) {
            var stored = inventory.getStackInSlot(slot);
            if (!stored.isEmpty() && !io.github.gerbesh.unusualcopper.storage.ItemKey.of(rest).matches(stored)) continue;
            int count = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), inventory.getSlotLimit(slot)) - stored.getCount()); if (count <= 0) continue;
            inventory.setStackInSlot(slot, rest.copyWithCount(stored.getCount() + count)); rest.shrink(count);
        }
        return rest;
    }
    private ItemStack[] outputPlan(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemStack remaining = stack.copy();
        ItemStack[] plan = {inventory.getStackInSlot(2).copy(), inventory.getStackInSlot(3).copy()};
        for (int i = 0; i < plan.length && !remaining.isEmpty(); i++) {
            if (!plan[i].isEmpty() && !ItemStack.isSameItemSameTags(plan[i], remaining)) continue;
            int space = Math.min(inventory.getSlotLimit(i + 2), remaining.getMaxStackSize()) - plan[i].getCount();
            int moved = Math.min(space, remaining.getCount());
            if (moved <= 0) continue;
            if (plan[i].isEmpty()) plan[i] = remaining.copyWithCount(moved); else plan[i].grow(moved);
            remaining.shrink(moved);
        }
        return remaining.isEmpty() ? plan : null;
    }
    public boolean finish() {
        ItemStack[] outputs = outputPlan(result);
        if (outputs == null || !inputMatches()) return false;
        inventory.getStackInSlot(0).shrink(source.getCount());
        inventory.setStackInSlot(2, outputs[0]); inventory.setStackInSlot(3, outputs[1]);
        cancelOperation();
        return true;
    }

    /** One server-thread commit into owned slots; no external inventory or pickup callback runs here. */
    public boolean collectOutput(net.minecraft.world.entity.item.ItemEntity entity, int cost) {
        if (kind != MachineKind.MAGNETIC_COLLECTOR || !(level instanceof ServerLevel) || entity.level() != level || !entity.isAlive()) return false;
        var outputs = outputPlan(entity.getItem());
        if (outputs == null || !energy.spend(cost)) return false;
        inventory.setStackInSlot(2, outputs[0]); inventory.setStackInSlot(3, outputs[1]);
        entity.setItem(ItemStack.EMPTY); entity.discard(); setChanged(); return true;
    }

    public static void tick(ServerLevel level, BlockPos pos, BlockState state, CopperMachineBlockEntity machine) {
        boolean signal = level.hasNeighborSignal(pos);
        if ((machine.redstoneMode == 1 && !signal) || (machine.redstoneMode == 2 && signal)) { if(machine.reaper!=null) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(machine,level); machine.status(MachineStatus.REDSTONE_PAUSED); }
        else machine.logic.tick(machine, level);
        if (machine.energyEnabled() && level.getGameTime() % 20 == 0) io.github.gerbesh.unusualcopper.machine.logic.BoundStorageLogic.tick(machine, level);
        boolean active = machine.status == MachineStatus.WORKING || machine.status == MachineStatus.CHARGING;
        if (state.getValue(CopperMachineBlock.ACTIVE) != active) level.setBlock(pos, state.setValue(CopperMachineBlock.ACTIVE, active), 2);
        if (level.getGameTime() % 10 == 0) level.sendBlockUpdated(pos, machine.getBlockState(), machine.getBlockState(), 2);
    }

    private void rebuildCaps() {
        for (Direction side : Direction.values()) {
            itemCaps.put(side, LazyOptional.of(() -> new IItemHandler() {
                private final long epoch = capabilityEpoch;
                private boolean live() { return epoch == capabilityEpoch && !isRemoved(); }
                @Override public int getSlots() { return 4; }
                @Override public ItemStack getStackInSlot(int slot) { return live() ? inventory.getStackInSlot(slot).copy() : ItemStack.EMPTY; }
                @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
                @Override public boolean isItemValid(int slot, ItemStack stack) { return live() && sideMode(side) == SideMode.ITEM_INPUT && inventory.isItemValid(slot, stack); }
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    return isItemValid(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack;
                }
                @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
                    return live() && sideMode(side) == SideMode.ITEM_OUTPUT && slot >= 2 ? inventory.extractItem(slot, count, simulate) : ItemStack.EMPTY;
                }
            }));
            energyCaps.put(side, LazyOptional.of(() -> new CopperEnergyHandler() {
                private final long epoch = capabilityEpoch;
                private boolean live() { return epoch == capabilityEpoch && !isRemoved(); }
                @Override public int stored() { return live() ? energy.stored() : 0; }
                @Override public int capacity() { return energy.capacity(); }
                @Override public int receive(int amount, boolean simulate) {
                    return live() && energyEnabled() && kind.consumes() && sideMode(side) == SideMode.ENERGY_INPUT ? energy.receive(amount, simulate) : 0;
                }
                @Override public int extract(int amount, boolean simulate) {
                    return live() && energyEnabled() && kind.produces() && sideMode(side) == SideMode.ENERGY_OUTPUT ? energy.extract(amount, simulate) : 0;
                }
            }));
        }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (side != null && !isRemoved()) {
            if (cap == ForgeCapabilities.ITEM_HANDLER && (sideMode(side) == SideMode.ITEM_INPUT || sideMode(side) == SideMode.ITEM_OUTPUT)) return itemCaps.get(side).cast();
            if (cap == CopperCapabilities.ENERGY && energy.capacity() > 0 && (sideMode(side) == SideMode.ENERGY_INPUT || sideMode(side) == SideMode.ENERGY_OUTPUT)) return energyCaps.get(side).cast();
        }
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        capabilityEpoch++;
        super.invalidateCaps(); itemCaps.values().forEach(LazyOptional::invalidate); energyCaps.values().forEach(LazyOptional::invalidate);
    }
    @Override public void reviveCaps() { super.reviveCaps(); rebuildCaps(); }
    @Override public void onLoad() { super.onLoad(); if (!itemCaps.get(Direction.UP).isPresent()) rebuildCaps(); EnergyNetworks.dirty(level); }
    @Override public void onChunkUnloaded() { if(reaper!=null && level instanceof ServerLevel server) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(this,server); if(arborist!=null) {arborist.scan=null;arborist.actor=null;} invalidateCaps(); EnergyNetworks.dirty(level); super.onChunkUnloaded(); }
    @Override public void setRemoved() { if(reaper!=null && level instanceof ServerLevel server) io.github.gerbesh.unusualcopper.machine.logic.ReaperLogic.cancel(this,server); if(arborist!=null) {arborist.scan=null;arborist.actor=null;} super.setRemoved(); EnergyNetworks.dirty(level); }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("DataVersion", 2); tag.put("Inventory", inventory.serializeNBT()); tag.putInt("Energy", energy.stored());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Redstone", redstoneMode); saveSides(tag);
        tag.putString("Recipe", recipeId); tag.put("Source", source.save(new CompoundTag()));
        // ItemStack's vanilla Count is a signed byte: a solar batch can produce 128.
        tag.put("Result", (result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1)).save(new CompoundTag()));
        tag.putInt("ResultCount", result.getCount());
        tag.putInt("Progress", progress); tag.putInt("Duration", duration); tag.putInt("Paid", paid);
        tag.putInt("Cost", operationCost);
        if (workArea != null) tag.put("WorkArea", workArea.save());
        tag.putInt("ScanCursor", scanCursor); tag.putInt("GrowthMode", growthMode);
        tag.putLong("NextWaveTick", nextWaveTick); tag.putBoolean("AllowOwnTamed", allowOwnTamed);
        if (reaper != null) tag.put("Reaper", reaper.save());
        if (arborist != null) tag.put("Arborist", arborist.save());
        if (boundInventory != null) tag.putLong("BoundInventory", boundInventory.asLong()); tag.putInt("BoundSide", boundSide.ordinal()); tag.putInt("BoundCargoSlot", boundCargoSlot); tag.putBoolean("BoundFaulted", boundFaulted);
        var cargo = new net.minecraft.nbt.ListTag();
        for (var stack : boundCargo) { var entry = new CompoundTag(); entry.put("Stack", stack.copyWithCount(1).save(new CompoundTag())); entry.putInt("Count", stack.getCount()); cargo.add(entry); } tag.put("BoundCargo", cargo);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory")); energy.restore(tag.getInt("Energy"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        redstoneMode = Math.floorMod(tag.getInt("Redstone"), 3); loadSides(tag);
        recipeId = tag.getString("Recipe"); source = ItemStack.of(tag.getCompound("Source")); result = ItemStack.of(tag.getCompound("Result"));
        if (!result.isEmpty()) result.setCount(Math.max(1, Math.min(128, tag.getInt("ResultCount"))));
        duration = Math.max(0, tag.getInt("Duration")); progress = Math.max(0, Math.min(duration, tag.getInt("Progress"))); paid = Math.max(0, tag.getInt("Paid"));
        operationCost = tag.contains("Cost") ? Math.max(0, tag.getInt("Cost")) : kind == MachineKind.RESONANCE_SMELTER ? 240 : 0;
        if (source.isEmpty() || result.isEmpty() || duration == 0) cancelOperation();
        workArea = null;
        if (tag.contains("WorkArea")) try { workArea = io.github.gerbesh.unusualcopper.gameplay.WorkArea.load(tag.getCompound("WorkArea")); } catch (IllegalArgumentException ignored) {}
        scanCursor = workArea == null ? 0 : Math.floorMod(tag.getInt("ScanCursor"), workArea.volume()); growthMode = Math.floorMod(tag.getInt("GrowthMode"), 2);
        nextWaveTick = Math.max(0, tag.getLong("NextWaveTick")); allowOwnTamed = tag.getBoolean("AllowOwnTamed");
        if (reaper != null) reaper.load(tag.getCompound("Reaper"));
        if (arborist != null) arborist.load(tag.getCompound("Arborist"));
        boundInventory = tag.contains("BoundInventory") ? BlockPos.of(tag.getLong("BoundInventory")) : null; boundSide = Direction.values()[Math.floorMod(tag.getInt("BoundSide"), 6)]; boundEndpoint = null;
        boundCargoSlot = Math.max(-1, Math.min(1, tag.getInt("BoundCargoSlot"))); boundFaulted = tag.getBoolean("BoundFaulted"); boundCargo.clear();
        for (var raw : tag.getList("BoundCargo", net.minecraft.nbt.Tag.TAG_COMPOUND)) { var entry = (CompoundTag)raw; var stack = ItemStack.of(entry.getCompound("Stack")); stack.setCount(Math.max(0, entry.getInt("Count"))); if (!stack.isEmpty()) boundCargo.add(stack); }
    }
    private void saveSides(CompoundTag tag) { for (Direction side : Direction.values()) tag.putInt("Side" + side.ordinal(), sides.get(side).ordinal()); }
    private void loadSides(CompoundTag tag) {
        for (Direction side : Direction.values()) if (tag.contains("Side" + side.ordinal())) {
            var mode = SideMode.values()[Math.floorMod(tag.getInt("Side" + side.ordinal()), SideMode.values().length)]; sides.put(side, supportsSideMode(mode) ? mode : SideMode.DISABLED);
        }
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Energy", energy.stored()); tag.putInt("Status", status.ordinal()); saveSides(tag); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        energy.restore(tag.getInt("Energy")); status = MachineStatus.values()[Math.floorMod(tag.getInt("Status"), MachineStatus.values().length)]; loadSides(tag);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { if (packet.getTag() != null) handleUpdateTag(packet.getTag()); }
    @Override public Component getDisplayName() { return Component.translatable("block.unusualcopper." + kind.id); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return new CopperMachineMenu(id, playerInventory, this); }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override public int getCount() { return CopperMachineMenu.DATA_COUNT; }
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> energy.stored() & 0xffff; case 1 -> energy.stored() >>> 16;
                    case 2 -> energy.capacity() & 0xffff; case 3 -> energy.capacity() >>> 16;
                    case 4 -> progress & 0xffff; case 5 -> duration & 0xffff; case 6 -> status.ordinal();
                    case 7 -> redstoneMode; case 8 -> kind.ordinal(); case 9 -> paid;
                    case 10 -> progress >>> 16; case 11 -> duration >>> 16;
                    case 12 -> operationCost & 0xffff; case 13 -> operationCost >>> 16;
                    case 14 -> growthMode; case 15 -> workArea == null ? 0 : workArea.volume(); case 16 -> scanCursor; case 17 -> inspected; case 18 -> grown;
                    case 19 -> boundInventory == null ? 0 : 1; case 20 -> boundCargoCount() & 0xffff; case 21 -> boundCargoCount() >>> 16;
                    case 22 -> allowOwnTamed ? 1 : 0; case 23 -> level == null ? 0 : (int)Math.max(0, Math.min(32767, nextWaveTick - level.getGameTime()));
                    case 24 -> io.github.gerbesh.unusualcopper.config.ServerConfig.shepherdCost() & 0xffff;
                    case 25 -> io.github.gerbesh.unusualcopper.config.ServerConfig.shepherdCost() >>> 16;
                    case 26 -> io.github.gerbesh.unusualcopper.config.ServerConfig.shepherdAdvance();
                    case 27 -> reaper == null ? 0 : reaper.population & 0xffff;
                    case 28 -> reaper == null ? 0 : reaper.population >>> 16;
                    case 29 -> reaper == null ? 0 : reaper.lootCount() & 0xffff;
                    case 30 -> reaper == null ? 0 : reaper.lootCount() >>> 16;
                    case 31 -> reaper != null && reaper.active ? 1 : 0;
                    case 32 -> io.github.gerbesh.unusualcopper.config.ServerConfig.reaperCost() & 0xffff;
                    case 33 -> io.github.gerbesh.unusualcopper.config.ServerConfig.reaperCost() >>> 16;
                    case 34 -> arborist==null?0:arborist.points.size(); case 35 -> arborist!=null && arborist.job!=null?1:0;
                    case 36 -> arborist==null||arborist.job==null?0:arborist.job.logs.size()-arborist.job.cut;
                    case 37 -> arborist==null||arborist.job==null?0:arborist.job.roots.size()-arborist.job.planted;
                    case 38 -> arborist==null?0:arborist.recovery.count() & 0xffff; case 39 -> arborist==null?0:arborist.recovery.count()>>>16; default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
        };
    }
}
