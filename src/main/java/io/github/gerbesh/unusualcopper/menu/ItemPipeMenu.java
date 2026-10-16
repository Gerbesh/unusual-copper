package io.github.gerbesh.unusualcopper.menu;

import io.github.gerbesh.unusualcopper.itempipe.*;
import io.github.gerbesh.unusualcopper.network.PipeNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class ItemPipeMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 15;
    public final BlockPos pos;
    public final ContainerData data;
    private final ItemPipeBlockEntity pipe;
    private final SimpleContainer ghosts = new SimpleContainer(8);
    private final Player player;
    private Direction selected = Direction.NORTH;
    private int lastFace = -1;
    private long lastSettingsTick = Long.MIN_VALUE;
    private List<ResourceLocation> sentTags = List.of();
    private List<ResourceLocation> clientTags = List.of();
    public ItemPipeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, null, buffer.readBlockPos()); }
    public ItemPipeMenu(int id, Inventory inventory, ItemPipeBlockEntity pipe) { this(id, inventory, pipe, pipe.getBlockPos()); }
    private ItemPipeMenu(int id, Inventory inventory, ItemPipeBlockEntity pipe, BlockPos pos) {
        super(ModContent.PIPE_MENU.get(), id); this.pipe = pipe; this.pos = pos; this.player = inventory.player;
        if(pipe!=null)selected=pipe.configuredFace();
        data = pipe == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) {}
            @Override public int get(int index) {
                var rule = pipe.rule(selected); int count = (int)Math.min(Integer.MAX_VALUE, pipe.cargoCount());
                return switch (index) {
                    case 0 -> selected.ordinal(); case 1 -> rule.mode.ordinal(); case 2 -> rule.blacklist ? 1 : 0; case 3 -> rule.exactNbt ? 1 : 0; case 4 -> rule.priority;
                    case 5 -> rule.minimum & 0xffff; case 6 -> rule.minimum >>> 16; case 7 -> rule.maximum & 0xffff; case 8 -> rule.maximum >>> 16;
                    case 9 -> rule.reserve & 0xffff; case 10 -> rule.reserve >>> 16; case 11 -> pipe.redstoneMode; case 12 -> count & 0xffff; case 13 -> count >>> 16; case 14 -> pipe.status.ordinal(); default -> 0;
                };
            }
        };
        for (int slot = 0; slot < 8; slot++) addSlot(new Slot(ghosts, slot, 10 + slot * 18, 70) {
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, row * 9 + col + 9, 69 + col * 18, 153 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 69 + col * 18, 211));
        refreshGhosts(); addDataSlots(data);
    }
    public Direction selected() { return Direction.values()[Math.floorMod(data.get(0), 6)]; }
    public int number(int low) { return (data.get(low) & 0xffff) | ((data.get(low + 1) & 0xffff) << 16); }
    public List<ResourceLocation> tags() { return clientTags; }
    public void tags(int face, List<ResourceLocation> tags) { if (face == data.get(0)) clientTags = List.copyOf(tags); }
    private void refreshGhosts() {
        if (pipe == null) return;
        for (int slot = 0; slot < 8; slot++) { var template = pipe.rule(selected).filter(slot); if (!ItemStack.matches(ghosts.getItem(slot), template)) ghosts.setItem(slot, template); }
    }
    @Override public boolean stillValid(Player player) {
        return pipe == null || (!pipe.isRemoved() && player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) == pipe && pipe.canAccess(player) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64);
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (pipe == null || !stillValid(player)) return false;
        if (button >= 0 && button < 6) { selected = Direction.values()[button]; pipe.configuredFace(selected); refreshGhosts(); return true; }
        var rule = pipe.rule(selected);
        switch (button) {
            case 10 -> rule.mode = rule.mode.next();
            case 11 -> rule.blacklist = !rule.blacklist;
            case 12 -> rule.exactNbt = !rule.exactNbt;
            case 13 -> pipe.redstoneMode = (pipe.redstoneMode + 1) % 3;
            default -> { return false; }
        }
        pipe.changed(); return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < 8) {
            if (pipe != null && stillValid(player) && type == ClickType.PICKUP) { pipe.rule(selected).filter(slot, button == 1 ? ItemStack.EMPTY : getCarried()); pipe.changed(); refreshGhosts(); }
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) {
        if (pipe == null || !stillValid(player) || slot < 8 || slot >= slots.size()) return ItemStack.EMPTY;
        var stack = slots.get(slot).getItem();
        if (!stack.isEmpty()) for (int i = 0; i < 8; i++) if (pipe.rule(selected).filter(i).isEmpty()) { pipe.rule(selected).filter(i, stack); pipe.changed(); refreshGhosts(); break; }
        return ItemStack.EMPTY;
    }
    public void configure(PipeNetwork.Settings settings, ServerPlayer player) {
        if (pipe == null || !stillValid(player) || settings.face() != selected.ordinal()) return;
        long now = player.level().getGameTime();
        if (lastSettingsTick != Long.MIN_VALUE && now - lastSettingsTick < 2) return;
        lastSettingsTick = now;
        var rule = pipe.rule(selected);
        if (!rule.settings(settings.priority(), settings.minimum(), settings.maximum(), settings.reserve())) return;
        if (!settings.tag().isEmpty()) { var id = ResourceLocation.tryParse(settings.tag()); if (id != null) rule.tag(id, settings.removeTag()); }
        pipe.changed();
    }
    @Override public void broadcastChanges() {
        refreshGhosts(); super.broadcastChanges();
        if (pipe != null && player instanceof ServerPlayer server) {
            var tags = pipe.rule(selected).tags();
            if (lastFace != selected.ordinal() || !sentTags.equals(tags)) {
                lastFace = selected.ordinal(); sentTags = tags;
                PipeNetwork.tags(server, new PipeNetwork.Tags(containerId, pos, selected.ordinal(), tags));
            }
        }
    }
}
