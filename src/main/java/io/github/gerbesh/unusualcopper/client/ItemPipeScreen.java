package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.itempipe.*;
import io.github.gerbesh.unusualcopper.menu.ItemPipeMenu;
import io.github.gerbesh.unusualcopper.network.PipeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Arrays;

public final class ItemPipeScreen extends AbstractContainerScreen<ItemPipeMenu> {
    private final Button[] faces = new Button[6];
    private Button mode, filter, exact, redstone;
    private EditBox priority, minimum, maximum, reserve, tag;
    private int[] observed = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, -1};
    private String error = "";
    public ItemPipeScreen(ItemPipeMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 300; imageHeight = 236; inventoryLabelY = 142; }
    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable(key), ignored -> action.run()).bounds(leftPos + x, topPos + y, width, 18).build());
    }
    private EditBox box(int x, int width, int y, int limit) {
        var box = new EditBox(font, leftPos + x, topPos + y, width, 18, Component.empty()); box.setMaxLength(limit); addRenderableWidget(box); return box;
    }
    private void click(int id) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    @Override protected void init() {
        super.init();
        for (var side : Direction.values()) faces[side.ordinal()] = button("direction.unusualcopper." + side.getName(), 10 + side.ordinal() * 46, 18, 44, () -> click(side.ordinal()));
        mode = button("pipe.unusualcopper.mode.auto", 10, 50, 80, () -> click(10));
        filter = button("pipe.unusualcopper.whitelist", 92, 50, 92, () -> click(11));
        exact = button("pipe.unusualcopper.exact_off", 186, 50, 104, () -> click(12));
        exact.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.exact_hint")));
        redstone = button("gui.unusualcopper.redstone", 166, 70, 124, () -> click(13));
        priority = box(10, 46, 100, 5); minimum = box(70, 46, 100, 8); maximum = box(130, 46, 100, 8); reserve = box(190, 46, 100, 8);
        minimum.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.minimum_hint")));
        maximum.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.maximum_hint")));
        reserve.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.reserve_hint")));
        button("pipe.unusualcopper.apply", 248, 100, 42, () -> apply("", false));
        tag = box(10, 174, 120, 128);
        button("pipe.unusualcopper.tag_add", 190, 120, 42, () -> apply(tag.getValue().trim(), false));
        button("pipe.unusualcopper.tag_remove", 236, 120, 54, () -> apply(tag.getValue().trim(), true));
        observed = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, -1};
    }
    private void apply(String tag, boolean remove) {
        try {
            int p = Integer.parseInt(priority.getValue().trim()), min = Integer.parseInt(minimum.getValue().trim()), max = maximum.getValue().trim().equals("*") ? Integer.MAX_VALUE : Integer.parseInt(maximum.getValue().trim()), keep = Integer.parseInt(reserve.getValue().trim());
            if (p < -100 || p > 100 || min < 0 || min > 1_000_000 || max < min || (max != Integer.MAX_VALUE && max > 1_000_000) || keep < 0 || keep > 1_000_000 || (!tag.isEmpty() && net.minecraft.resources.ResourceLocation.tryParse(tag) == null)) throw new IllegalArgumentException();
            PipeNetwork.settings(new PipeNetwork.Settings(menu.containerId, menu.selected().ordinal(), p, min, max, keep, tag, remove)); error = "";
        } catch (IllegalArgumentException invalid) { error = "pipe.unusualcopper.invalid_settings"; }
    }
    private void refresh() {
        for (var side : Direction.values()) {
            faces[side.ordinal()].active = side != menu.selected();
            faces[side.ordinal()].setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.face_target",Component.translatable("direction.unusualcopper."+side.getName()),neighbor(side))));
        }
        mode.setMessage(Component.translatable(PipeMode.values()[Math.floorMod(menu.data.get(1), PipeMode.values().length)].translationKey()));
        mode.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.mode_hint",neighbor(menu.selected()))));
        filter.setMessage(Component.translatable("pipe.unusualcopper." + (menu.data.get(2) == 0 ? "whitelist" : "blacklist")));
        filter.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.filter_hint")));
        exact.setMessage(Component.translatable("pipe.unusualcopper.exact_" + (menu.data.get(3) == 0 ? "off" : "on")));
        redstone.setMessage(Component.translatable("gui.unusualcopper.redstone_mode." + menu.data.get(11)));
        int[] values = {menu.data.get(4), menu.number(5), menu.number(7), menu.number(9), menu.data.get(0)};
        if (!Arrays.equals(observed, values)) {
            EditBox[] boxes = {priority, minimum, maximum, reserve};
            for (int i = 0; i < 4; i++) if (!boxes[i].isFocused() || observed[4] != values[4]) boxes[i].setValue(i == 2 && values[i] == Integer.MAX_VALUE ? "*" : Integer.toString(values[i]));
            observed = values;
        }
        tag.setTooltip(Tooltip.create(Component.translatable("pipe.unusualcopper.tags_hint", menu.tags().isEmpty() ? "—" : String.join(", ", menu.tags().stream().map(Object::toString).toList()))));
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff243a36); g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xffaa724d);
        g.fill(x + 7, y + 16, x + imageWidth - 7, y + 140, 0xff243a36);
        for (var slot : menu.slots) { g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff1d2825); g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff52625c); }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xff201b15, false);
        var mode=PipeMode.values()[Math.floorMod(menu.data.get(1),PipeMode.values().length)];
        var target=Component.translatable("pipe.unusualcopper.binding."+mode.name().toLowerCase(java.util.Locale.ROOT),Component.translatable("direction.unusualcopper."+menu.selected().getName()),neighbor(menu.selected()));
        int selectedX=10+menu.selected().ordinal()*46;
        g.fill(selectedX,37,selectedX+44,38,0xff80fff0);
        g.drawString(font,font.plainSubstrByWidth(target.getString(),280),10,39,0xffe6f5ee,false);
        var status = ItemPipeBlockEntity.Status.values()[Math.floorMod(menu.data.get(14), ItemPipeBlockEntity.Status.values().length)];
        g.drawString(font, Component.translatable(error.isEmpty() ? "pipe.unusualcopper.status." + status.name().toLowerCase(java.util.Locale.ROOT) : error), 184, 6, error.isEmpty() ? 0xff201b15 : 0xffffa070, false);
        for (int i = 0; i < 4; i++) g.drawString(font, Component.translatable("pipe.unusualcopper." + new String[]{"priority", "minimum", "maximum", "reserve"}[i]), 10 + i * 60, 89, 0xffe6f5ee, false);
        g.drawString(font, playerInventoryTitle, 69, 142, 0xff201b15, false);
    }
    private Component neighbor(Direction side) {
        var level=minecraft.level;var pos=menu.pos.relative(side);
        if(level==null||!level.hasChunkAt(pos))return Component.translatable("pipe.unusualcopper.neighbor_unloaded");
        var state=level.getBlockState(pos);
        return state.isAir()?Component.translatable("pipe.unusualcopper.neighbor_empty"):state.getBlock().getName();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) { refresh(); renderBackground(g); super.render(g, mouseX, mouseY, partialTick); renderTooltip(g, mouseX, mouseY); }
}
