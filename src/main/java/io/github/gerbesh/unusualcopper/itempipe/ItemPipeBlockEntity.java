package io.github.gerbesh.unusualcopper.itempipe;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.SideMode;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class ItemPipeBlockEntity extends BlockEntity implements MenuProvider {
    public enum Status { IDLE, TRANSFERRING, BUFFERED, NO_ROUTE, PAUSED, DENIED, FAULT }
    private UUID owner;
    private Direction configuredFace;
    public int redstoneMode;
    public Status status = Status.IDLE;
    public boolean faulted;
    private final EnumMap<Direction, PipeRule> rules = new EnumMap<>(Direction.class);
    private final List<ItemStack> cargo = new ArrayList<>();
    private Direction originSide;
    public long nextVisualTick;
    private BlockPos originInventory;
    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ITEM_PIPE_ENTITY.get(), pos, state);
        for (var direction : Direction.values()) rules.put(direction, new PipeRule());
    }
    public PipeRule rule(Direction direction) { return rules.get(direction); }
    /** UI focus only; changing it never changes the routing rules of another face. */
    public void configuredFace(Direction face) { configuredFace=face;setChanged(); }
    public Direction configuredFace() {
        if(configuredFace!=null)return configuredFace;
        if(level!=null) {
            // An external inventory is the useful default, rather than an arbitrary world north.
            for(boolean extractFirst:new boolean[]{true,false})for(var face:Direction.values()) {
                var pos=worldPosition.relative(face);if(!level.hasChunkAt(pos))continue;
                var neighbor=level.getBlockEntity(pos);
                if(neighbor!=null&&!(neighbor instanceof ItemPipeBlockEntity)
                        &&neighbor.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,face.getOpposite()).isPresent()
                        &&(!extractFirst||rule(face).mode==PipeMode.EXTRACT))return face;
            }
        }
        return Direction.NORTH;
    }
    public UUID owner() { return owner; }
    public void owner(UUID value) { owner = value; changed(); }
    public boolean canAccess(Player player) { return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2); }
    public PipeMode effective(Direction side) {
        PipeMode mode = rule(side).mode;
        if (mode != PipeMode.AUTO || level == null || !level.hasChunkAt(worldPosition.relative(side))) return mode;
        var neighbor = level.getBlockEntity(worldPosition.relative(side));
        if (neighbor instanceof ItemPipeBlockEntity) return PipeMode.AUTO;
        if (neighbor instanceof CopperMachineBlockEntity machine) {
            return machine.sideMode(side.getOpposite()) == SideMode.ITEM_OUTPUT ? PipeMode.EXTRACT
                    : machine.sideMode(side.getOpposite()) == SideMode.ITEM_INPUT ? PipeMode.INSERT : PipeMode.DISABLED;
        }
        return PipeMode.INSERT;
    }
    public boolean enabled() {
        if (isRemoved() || level == null) return false;
        boolean signal = level.hasNeighborSignal(worldPosition);
        return redstoneMode == 0 || (redstoneMode == 1 ? signal : !signal);
    }
    public List<ItemStack> cargo() { return cargo.stream().map(ItemStack::copy).toList(); }
    public long cargoCount() { return cargo.stream().mapToLong(ItemStack::getCount).sum(); }
    public BlockPos originInventory() { return originInventory; }
    public Direction originSide() { return originSide; }
    public void hold(List<ItemStack> stacks, BlockPos inventory, Direction side) {
        if (!cargo.isEmpty()) throw new IllegalStateException("Occupied transit buffer");
        for (var stack : stacks) if (!stack.isEmpty()) cargo.add(stack.copy());
        originInventory = inventory.immutable(); originSide = side; setChanged();
    }
    public void replaceCargo(List<ItemStack> stacks) {
        cargo.clear(); for (var stack : stacks) if (!stack.isEmpty()) cargo.add(stack.copy());
        if (cargo.isEmpty()) { originInventory = null; originSide = null; faulted = false; }
        setChanged();
    }
    public void recover(Player player) {
        if (!canAccess(player)) return;
        List<ItemStack> remaining = new ArrayList<>();
        for (var stored : cargo) {
            var offered = stored.copyWithCount(Math.min(stored.getCount(), stored.getMaxStackSize()));
            int amount = offered.getCount(); player.getInventory().add(offered);
            var rest = stored.copyWithCount(stored.getCount() - amount + offered.getCount());
            if (!rest.isEmpty()) remaining.add(rest);
        }
        replaceCargo(remaining);
    }
    public void changed() {
        setChanged();
        if (level != null) {
            ItemNetworks.dirty(level);
            if (getBlockState().getBlock() instanceof ItemPipeBlock block) block.refreshConnections(level, worldPosition);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag(); tag.putIntArray("Modes", Arrays.stream(Direction.values()).mapToInt(side -> rule(side).mode.ordinal()).toArray()); tag.putInt("Redstone", redstoneMode); return tag;
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag) { clientState(tag); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) { if (packet.getTag() != null) clientState(packet.getTag()); }
    private void clientState(CompoundTag tag) {
        int[] modes = tag.getIntArray("Modes");
        for (var side : Direction.values()) if (side.ordinal() < modes.length) rule(side).mode = PipeMode.values()[Math.floorMod(modes[side.ordinal()], PipeMode.values().length)];
        redstoneMode = Math.floorMod(tag.getInt("Redstone"), 3);
    }
    @Override public void onLoad() { super.onLoad(); ItemNetworks.add(level, worldPosition); }
    @Override public void onChunkUnloaded() { ItemNetworks.remove(level, worldPosition); super.onChunkUnloaded(); }
    @Override public void setRemoved() { ItemNetworks.remove(level, worldPosition); super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); if (owner != null) tag.putUUID("Owner", owner); tag.putInt("Redstone", redstoneMode); tag.putBoolean("Faulted", faulted);
        if(configuredFace!=null)tag.putInt("ConfiguredFace",configuredFace.ordinal());
        var settings = new CompoundTag(); rules.forEach((side, rule) -> settings.put(side.getName(), rule.save())); tag.put("Rules", settings);
        var buffer = new ListTag();
        for (var stack : cargo) { var value = new CompoundTag(); value.put("Stack", stack.copyWithCount(1).save(new CompoundTag())); value.putInt("Count", stack.getCount()); buffer.add(value); }
        tag.put("Cargo", buffer); if (originInventory != null) tag.putLong("Origin", originInventory.asLong()); if (originSide != null) tag.putInt("OriginSide", originSide.ordinal());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null; redstoneMode = Math.floorMod(tag.getInt("Redstone"), 3); faulted = tag.getBoolean("Faulted");
        configuredFace=tag.contains("ConfiguredFace")&&tag.getInt("ConfiguredFace")>=0&&tag.getInt("ConfiguredFace")<6?Direction.values()[tag.getInt("ConfiguredFace")]:null;
        rules.forEach((side, rule) -> rule.load(tag.getCompound("Rules").getCompound(side.getName()))); cargo.clear();
        for (var raw : tag.getList("Cargo", Tag.TAG_COMPOUND)) { var value = (CompoundTag)raw; var stack = ItemStack.of(value.getCompound("Stack")); stack.setCount(Math.max(0, value.getInt("Count"))); if (!stack.isEmpty()) cargo.add(stack); }
        originInventory = tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : null; originSide = tag.contains("OriginSide") ? Direction.values()[Math.floorMod(tag.getInt("OriginSide"), 6)] : null;
    }
    @Override public Component getDisplayName() { return Component.translatable("block.unusualcopper.copper_item_pipe"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new io.github.gerbesh.unusualcopper.menu.ItemPipeMenu(id, inventory, this); }
}
