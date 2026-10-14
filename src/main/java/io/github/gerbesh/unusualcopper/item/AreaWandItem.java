package io.github.gerbesh.unusualcopper.item;

import io.github.gerbesh.unusualcopper.gameplay.WorkArea;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.network.AreaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;

public final class AreaWandItem extends Item {
    public AreaWandItem() { super(new Properties().stacksTo(1)); }
    private static void message(Player player, String key) { player.displayClientMessage(Component.translatable("wand.unusualcopper." + key), true); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isCrouching()) { var tag = stack.getOrCreateTag(); tag.putInt("Mode", (Math.floorMod(tag.getInt("Mode"), 3) + 1) % 3); clearSelection(tag); message(player, "mode." + tag.getInt("Mode")); }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    private static void clearSelection(CompoundTag tag) { tag.remove("Machine"); tag.remove("Dimension"); tag.remove("Instance"); tag.remove("First"); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var player = context.getPlayer(); var pos = context.getClickedPos();
        if (player == null || !level.hasChunkAt(pos)) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.FAIL;
        var tag = context.getItemInHand().getOrCreateTag(); int mode = Math.floorMod(tag.getInt("Mode"), 3);
        var clicked = level.getBlockEntity(pos);
        if (mode == 2) {
            if (clicked instanceof CopperMachineBlockEntity machine && machine.canAccess(player)) { NetworkHooks.openScreen(server, machine, pos); return InteractionResult.SUCCESS; }
            message(player, "denied"); return InteractionResult.FAIL;
        }
        if (clicked instanceof CopperMachineBlockEntity machine && (mode == 0 ? machine.kind.usesArea() : machine.kind.hasInventory())) {
            if (!machine.canAccess(player)) { message(player, "denied"); return InteractionResult.FAIL; }
            tag.putLong("Machine", pos.asLong()); tag.putString("Dimension", level.dimension().location().toString()); tag.putUUID("Instance", machine.runtimeIdentity); tag.remove("First");
            message(player, mode == 0 ? "first" : "storage"); return InteractionResult.SUCCESS;
        }
        if (!tag.contains("Machine") || !tag.getString("Dimension").equals(level.dimension().location().toString())) { clearSelection(tag); message(player, "select_machine"); return InteractionResult.FAIL; }
        var anchor = BlockPos.of(tag.getLong("Machine"));
        if (!level.hasChunkAt(anchor) || !(level.getBlockEntity(anchor) instanceof CopperMachineBlockEntity machine) || !machine.canAccess(player) || !tag.hasUUID("Instance") || !machine.runtimeIdentity.equals(tag.getUUID("Instance"))) { clearSelection(tag); message(player, "stale"); return InteractionResult.FAIL; }
        if (anchor.distSqr(pos) > 32 * 32) { message(player, "too_far"); return InteractionResult.FAIL; }
        if (mode == 1) {
            if (!machine.bind(pos, context.getClickedFace(), player)) { message(player, "denied"); return InteractionResult.FAIL; }
            message(player, "bound"); clearSelection(tag); return InteractionResult.SUCCESS;
        }
        if (!tag.contains("First")) { tag.putLong("First", pos.asLong()); AreaNetwork.preview(server, pos, pos, true); message(player, "second"); return InteractionResult.SUCCESS; }
        var first = BlockPos.of(tag.getLong("First"));
        try {
            var area = WorkArea.between(first, pos);
            if (!machine.setWorkArea(area, player)) { AreaNetwork.preview(server, area.min(), area.max(), false); message(player, "denied"); return InteractionResult.FAIL; }
            AreaNetwork.preview(server, area.min(), area.max(), true); tag.remove("First"); message(player, "selected"); return InteractionResult.SUCCESS;
        } catch (IllegalArgumentException invalid) { AreaNetwork.preview(server, first, pos, false); message(player, "too_large"); return InteractionResult.FAIL; }
    }
    @Override public CompoundTag getShareTag(ItemStack stack) { var tag = new CompoundTag(); tag.putInt("Mode", Math.floorMod(stack.getOrCreateTag().getInt("Mode"), 3)); return tag; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("wand.unusualcopper.mode." + Math.floorMod(stack.getOrCreateTag().getInt("Mode"), 3)));
        tooltip.add(Component.translatable("wand.unusualcopper.tooltip"));
    }
}
