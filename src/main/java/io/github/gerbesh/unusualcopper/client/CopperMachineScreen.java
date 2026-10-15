package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CopperMachineScreen extends AbstractContainerScreen<CopperMachineMenu> {
    private Button cancelButton;
    private Button growthButton, areaButton, clearBinding, recoverBinding, petButton;
    public CopperMachineScreen(CopperMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 224; imageHeight = 214; inventoryLabelY = 118;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.unusualcopper.redstone"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)).bounds(leftPos + 88, topPos + 91, 98, 18).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.unusualcopper.cancel"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .tooltip(Tooltip.create(Component.translatable("gui.unusualcopper.cancel_hint"))).bounds(leftPos + 188, topPos + 91, 28, 18).build());
        growthButton = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2)).bounds(leftPos + 13, topPos + 61, 198, 18).build());
        petButton = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 6)).bounds(leftPos + 13, topPos + 61, 198, 18).build());
        areaButton = addRenderableWidget(Button.builder(Component.translatable("gui.unusualcopper.preview"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3)).bounds(leftPos + 13, topPos + 91, 70, 18).build());
        clearBinding = addRenderableWidget(Button.builder(Component.literal("×"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 4)).tooltip(Tooltip.create(Component.translatable("gui.unusualcopper.unbind"))).bounds(leftPos + 184, topPos + 51, 28, 10).build());
        recoverBinding = addRenderableWidget(Button.builder(Component.literal("↓"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 5)).tooltip(Tooltip.create(Component.translatable("gui.unusualcopper.recover"))).bounds(leftPos + 188, topPos + 91, 28, 18).build());
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff243a36);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xffaa724d);
        g.fill(x + 7, y + 20, x + imageWidth - 7, y + 116, 0xff243a36);
        for (var slot : menu.slots) if (slot.isActive()) { g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff1d2825); g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xff52625c); }
        g.fill(x + 13, y + 43, x + 211, y + 49, 0xff142d2b);
        int bar = menu.capacity() == 0 ? 0 : (int)(198L * menu.energy() / menu.capacity());
        g.fill(x + 13, y + 43, x + 13 + bar, y + 49, 0xff3dd6d3);
        int duration = Math.max(1, menu.duration());
        int progress = (int)Math.min(56, 56L * menu.progress() / duration);
        if (menu.kind().acceptsInputs()) {
            g.fill(x + 69, y + 69, x + 125, y + 75, 0xff152d2a); g.fill(x + 69, y + 69, x + 69 + progress, y + 75, 0xff82f1e7);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 7, 0xff201b15, false);
        MachineStatus status = MachineStatus.values()[Math.floorMod(menu.data.get(6), MachineStatus.values().length)];
        g.drawString(font, Component.translatable(status.translationKey()), 13, 24, 0xffb8efdf, false);
        g.drawString(font, menu.energy() + " / " + menu.capacity() + " CuE", 13, 33, 0xffe6f5ee, false);
        if (menu.kind().acceptsInputs()) {
            g.drawString(font, Component.translatable("gui.unusualcopper.input"), 26, 52, 0xffe6f5ee, false);
        }
        if (menu.kind().hasInventory()) g.drawString(font, Component.translatable("gui.unusualcopper.output"), 133, 52, 0xffe6f5ee, false);
        if (menu.operationCost() > 0) g.drawString(font, menu.operationCost() + " CuE", 68, 59, 0xffe6f5ee, false);
        if (menu.kind().usesArea()) {
            g.drawString(font, Component.translatable("gui.unusualcopper.area_size", menu.data.get(15)), 13, 52, 0xffe6f5ee, false);
            if (menu.kind() == MachineKind.AURA_CULTIVATOR) g.drawString(font, Component.translatable("gui.unusualcopper.growth_stats", menu.data.get(17), menu.data.get(18)), 13, 81, 0xffe6f5ee, false);
            else if (menu.kind() == MachineKind.GROWTH_SHEPHERD) g.drawString(font, Component.translatable("gui.unusualcopper.shepherd_stats", menu.data.get(18), (menu.data.get(23) + 19) / 20), 13, 81, 0xffe6f5ee, false);
            else {
                g.drawString(font, Component.translatable("gui.unusualcopper.collection_checked", menu.data.get(17)), 13, 65, 0xffe6f5ee, false);
                g.drawString(font, Component.translatable("gui.unusualcopper.collection_taken", menu.data.get(18)), 13, 78, 0xffe6f5ee, false);
            }
        }
        if (!menu.kind().usesArea()) g.drawString(font, Component.translatable("gui.unusualcopper.redstone_mode." + menu.data.get(7)), 67, 82, 0xffe6f5ee, false);
        g.drawString(font, playerInventoryTitle, 31, 118, 0xff201b15, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        cancelButton.visible = menu.kind().acceptsInputs();
        cancelButton.active = menu.duration() > 0;
        growthButton.visible = menu.kind() == MachineKind.AURA_CULTIVATOR; areaButton.visible = menu.kind().usesArea();
        petButton.visible = menu.kind() == MachineKind.GROWTH_SHEPHERD;
        petButton.setMessage(Component.translatable("gui.unusualcopper.own_pets." + menu.data.get(22)));
        if (petButton.visible) petButton.setTooltip(Tooltip.create(Component.translatable("gui.unusualcopper.shepherd_hint", (menu.data.get(24) & 0xffff) | ((menu.data.get(25) & 0xffff) << 16), menu.data.get(26))));
        growthButton.setMessage(Component.translatable("gui.unusualcopper.growth_mode." + menu.data.get(14)));
        areaButton.active = menu.data.get(15) > 0; clearBinding.visible = menu.data.get(19) != 0; recoverBinding.visible = menu.data.get(20) != 0 || menu.data.get(21) != 0;
        if (recoverBinding.visible) cancelButton.visible = false;
        renderBackground(g); super.render(g, mouseX, mouseY, partialTick); renderTooltip(g, mouseX, mouseY);
    }
}
