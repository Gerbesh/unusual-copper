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
    private Button cancelButton, redstoneButton,brewerButton,energyButton,batteryButton;
    private Button growthButton, areaButton, clearBinding, recoverBinding, petButton, reaperButton, plantationButton;
    private Button orderOne, orderBatch, orderPattern, orderPreview, patternEngrave;
    private Button armorInstall, armorRemove, armorBattery, armorProfile, armorUpgrade;
    public CopperMachineScreen(CopperMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 224; imageHeight = 214; inventoryLabelY = 118;
    }
    @Override protected void init() {
        super.init();
        batteryButton=addRenderableWidget(Button.builder(Component.empty(),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,11)).bounds(leftPos+40,topPos+61,172,18).tooltip(Tooltip.create(Component.translatable("battery.unusualcopper.dock_hint"))).build());
        energyButton=addRenderableWidget(Button.builder(Component.literal("E"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,10)).bounds(leftPos+188,topPos+21,28,18).tooltip(Tooltip.create(Component.translatable("energy.unusualcopper.settings"))).build());
        brewerButton=addRenderableWidget(Button.builder(Component.literal("B"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,9)).bounds(leftPos+184,topPos+63,28,18).tooltip(Tooltip.create(Component.translatable("brewer.unusualcopper.settings"))).build());
        plantationButton=addRenderableWidget(Button.builder(Component.literal("P"),b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,8)).bounds(leftPos+184,topPos+63,28,18).tooltip(Tooltip.create(Component.translatable("arborist.unusualcopper.hint"))).build());
        reaperButton = addRenderableWidget(Button.builder(Component.translatable("reaper.unusualcopper.settings"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 7)).bounds(leftPos + 13, topPos + 63, 112, 18).build());
        redstoneButton=addRenderableWidget(Button.builder(Component.translatable("gui.unusualcopper.redstone"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)).bounds(leftPos + 88, topPos + 91, 98, 18).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.unusualcopper.cancel"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .tooltip(Tooltip.create(Component.translatable("gui.unusualcopper.cancel_hint"))).bounds(leftPos + 188, topPos + 91, 28, 18).build());
        growthButton = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2)).bounds(leftPos + 13, topPos + 61, 112, 18).tooltip(Tooltip.create(Component.translatable("cultivator.unusualcopper.harvest_hint"))).build());
        petButton = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 6)).bounds(leftPos + 13, topPos + 61, 198, 18).build());
        areaButton = addRenderableWidget(Button.builder(Component.translatable("gui.unusualcopper.preview"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3)).bounds(leftPos + 13, topPos + 91, 70, 18).build());
        clearBinding = addRenderableWidget(Button.builder(Component.literal("×"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 4)).tooltip(Tooltip.create(Component.translatable("gui.unusualcopper.unbind"))).bounds(leftPos + 184, topPos + 51, 28, 10).build());
        recoverBinding = addRenderableWidget(Button.builder(Component.literal("↓"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 5)).tooltip(Tooltip.create(Component.translatable("gui.unusualcopper.recover"))).bounds(leftPos + 188, topPos + 91, 28, 18).build());
        patternEngrave=addRenderableWidget(Button.builder(Component.translatable("assembly.unusualcopper.engrave"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,20))
                .tooltip(Tooltip.create(Component.translatable("assembly.unusualcopper.engrave_hint"))).bounds(leftPos+70,topPos+91,55,18).build());
        orderOne=addRenderableWidget(Button.builder(Component.literal("+1"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,12))
                .tooltip(Tooltip.create(Component.translatable("production.unusualcopper.queue_one"))).bounds(leftPos+75,topPos+91,30,18).build());
        orderBatch=addRenderableWidget(Button.builder(Component.literal("+16"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,13))
                .tooltip(Tooltip.create(Component.translatable("production.unusualcopper.queue_batch"))).bounds(leftPos+108,topPos+91,34,18).build());
        armorInstall=addRenderableWidget(Button.builder(Component.literal("+"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,16))
                .tooltip(Tooltip.create(Component.translatable("armor.unusualcopper.install"))).bounds(leftPos+73,topPos+91,28,18).build());
        armorRemove=addRenderableWidget(Button.builder(Component.literal("-"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,17))
                .tooltip(Tooltip.create(Component.translatable("armor.unusualcopper.remove"))).bounds(leftPos+104,topPos+91,28,18).build());
        armorBattery=addRenderableWidget(Button.builder(Component.literal("B"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,18))
                .tooltip(Tooltip.create(Component.translatable("armor.unusualcopper.eject_battery"))).bounds(leftPos+135,topPos+91,28,18).build());
        armorProfile=addRenderableWidget(Button.builder(Component.literal("P"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,19))
                .tooltip(Tooltip.create(Component.translatable("armor.unusualcopper.change_profile"))).bounds(leftPos+166,topPos+91,28,18).build());
        armorUpgrade=addRenderableWidget(Button.builder(Component.literal("↑"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,21))
                .tooltip(Tooltip.create(Component.translatable("armor.unusualcopper.upgrade"))).bounds(leftPos+196,topPos+91,22,18).build());
        orderPreview=addRenderableWidget(Button.builder(Component.literal("DAG"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,22))
                .tooltip(Tooltip.create(Component.translatable("production.unusualcopper.preview.hint"))).bounds(leftPos+185,topPos+112,33,14).build());
        orderPattern=addRenderableWidget(Button.builder(Component.translatable("production.unusualcopper.pattern"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,15))
                .tooltip(Tooltip.create(Component.translatable("production.unusualcopper.pattern_hint"))).bounds(leftPos+145,topPos+91,39,18).build());
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
        String statusKey=menu.kind().isCell() && status==MachineStatus.CHARGING?"battery.unusualcopper.transferring":status.translationKey();
        g.drawString(font, Component.translatable(statusKey), 13, 24, 0xffb8efdf, false);
        g.drawString(font, menu.energy() + " / " + menu.capacity() + " CuE", 13, 33, 0xffe6f5ee, false);
        if (menu.kind().acceptsInputs()) {
            g.drawString(font, Component.translatable("gui.unusualcopper.input"), 26, 52, 0xffe6f5ee, false);
        }
        if (menu.kind().hasInventory()) g.drawString(font, Component.translatable("gui.unusualcopper.output"), 133, 52, 0xffe6f5ee, false);
        if(menu.kind()==MachineKind.VORTEX_PURIFIER || menu.kind()==MachineKind.CIRCUIT_ETCHER || menu.kind()==MachineKind.COPPER_FLUID_PUMP || menu.kind()==MachineKind.COPPER_FLUID_TANK)
            g.drawString(font,Component.translatable(menu.kind()==MachineKind.COPPER_FLUID_TANK?"gui.unusualcopper.fluid_tank":menu.kind()==MachineKind.COPPER_FLUID_PUMP?"gui.unusualcopper.pump_fluid":"gui.unusualcopper.water_tank",menu.data.get(47)),68,81,0xffe6f5ee,false);
        if (menu.operationCost() > 0 && menu.kind()!=MachineKind.PRODUCTION_CONTROLLER) g.drawString(font, menu.operationCost() + " CuE", 68, 59, 0xffe6f5ee, false);
        if(menu.kind()==MachineKind.ARMOR_WORKBENCH){
            g.drawString(font,Component.translatable("armor.unusualcopper.sockets",menu.data.get(51),menu.data.get(52)),70,59,0xffe6f5ee,false);
            g.drawString(font,Component.translatable("armor.unusualcopper.profile."+Math.floorMod(menu.data.get(53),4)),70,79,0xffe6f5ee,false);
        }
        if(menu.kind()==MachineKind.PRECISION_ASSEMBLER){
            var card=menu.slots.get(0).getItem();
            var recipe=io.github.gerbesh.unusualcopper.recipe.AssemblyPatternCard.recipe(card);
            g.drawString(font,recipe==null?Component.translatable("assembly.unusualcopper.blank")
                    :Component.translatable("assembly.unusualcopper.bound",recipe.getPath()),68,81,0xffe6f5ee,false);
        }
        if(menu.kind()==MachineKind.PRODUCTION_CONTROLLER){
            g.drawString(font,Component.translatable("production.unusualcopper.orders",menu.data.get(48),menu.data.get(49)),70,59,0xffe6f5ee,false);
            g.drawString(font,Component.translatable("production.unusualcopper.target."+menu.data.get(50)),70,79,0xffe6f5ee,false);
        }
        if(menu.kind()==MachineKind.ARBORIST) g.drawString(font,Component.translatable("arborist.unusualcopper.stats",menu.data.get(34),menu.data.get(36)),69,81,0xffe6f5ee,false);
        if(menu.kind()==MachineKind.COPPER_SENTINEL) {
            g.drawString(font,Component.translatable("sentinel.unusualcopper.stats",menu.data.get(40),menu.data.get(18)),13,57,0xffe6f5ee,false);
            g.drawString(font,Component.translatable("sentinel.unusualcopper.price",(menu.data.get(41)&0xffff)|((menu.data.get(44)&0xffff)<<16),menu.data.get(42)),13,70,0xffe6f5ee,false);
        }
        if (menu.kind().usesArea()) {
            g.drawString(font, Component.translatable("gui.unusualcopper.area_size", menu.data.get(15)), 13, 52, 0xffe6f5ee, false);
            if (menu.kind() == MachineKind.AURA_CULTIVATOR) g.drawString(font, Component.translatable("cultivator.unusualcopper.stats", menu.data.get(17), menu.data.get(18),menu.data.get(46)), 13, 81, 0xffe6f5ee, false);
            else if (menu.kind() == MachineKind.GROWTH_SHEPHERD) g.drawString(font, Component.translatable("gui.unusualcopper.shepherd_stats", menu.data.get(18), (menu.data.get(23) + 19) / 20), 13, 81, 0xffe6f5ee, false);
            else if (menu.kind() == MachineKind.MOB_REAPER) {
                int population=(menu.data.get(27)&0xffff)|((menu.data.get(28)&0xffff)<<16);
                int loot=(menu.data.get(29)&0xffff)|((menu.data.get(30)&0xffff)<<16);
                g.drawString(font, Component.translatable("reaper.unusualcopper.stats", population, loot), 13, 81, 0xffe6f5ee, false);
            } else {
                g.drawString(font, Component.translatable("gui.unusualcopper.collection_checked", menu.data.get(17)), 13, 65, 0xffe6f5ee, false);
                g.drawString(font, Component.translatable("gui.unusualcopper.collection_taken", menu.data.get(18)), 13, 78, 0xffe6f5ee, false);
            }
        }
        if(menu.kind().isCell()) {
            g.drawString(font,Component.translatable("battery.unusualcopper.slot"),13,52,0xffe6f5ee,false);
            var portable=menu.slots.get(40).getItem();
            var charge=portable.isEmpty()?Component.translatable("battery.unusualcopper.empty"):Component.translatable("battery.unusualcopper.charge",io.github.gerbesh.unusualcopper.item.CopperBatteryItem.amount(io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(portable)),io.github.gerbesh.unusualcopper.item.CopperBatteryItem.amount(io.github.gerbesh.unusualcopper.item.CopperBatteryItem.capacity(portable)));
            g.drawString(font,charge,40,81,0xffe6f5ee,false);
        }
        if (!menu.kind().usesArea() && menu.kind()!=MachineKind.ARBORIST && !menu.kind().isCell()
                && menu.kind()!=MachineKind.COPPER_FLUID_PUMP && menu.kind()!=MachineKind.COPPER_FLUID_TANK) g.drawString(font, Component.translatable("gui.unusualcopper.redstone_mode." + menu.data.get(7)), 67, 82, 0xffe6f5ee, false);
        g.drawString(font, playerInventoryTitle, 31, 118, 0xff201b15, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        batteryButton.visible=menu.kind().isCell();
        batteryButton.setMessage(Component.translatable("battery.unusualcopper.mode."+Math.floorMod(menu.data.get(45),3)));
        energyButton.visible=menu.kind().capacity>0;
        redstoneButton.visible=menu.kind()!=MachineKind.COPPER_FLUID_TANK && menu.kind()!=MachineKind.PRODUCTION_CONTROLLER && menu.kind()!=MachineKind.ARMOR_WORKBENCH;
        boolean armorBench=menu.kind()==MachineKind.ARMOR_WORKBENCH;
        armorInstall.visible=armorBench;armorRemove.visible=armorBench;armorBattery.visible=armorBench;armorProfile.visible=armorBench;armorUpgrade.visible=armorBench;
        boolean assembler=menu.kind()==MachineKind.PRECISION_ASSEMBLER;
        patternEngrave.visible=assembler;
        patternEngrave.active=assembler && menu.duration()==0 && !menu.slots.get(0).getItem().isEmpty();
        redstoneButton.setX(leftPos+(assembler?129:88));
        redstoneButton.setWidth(assembler?57:98);
        redstoneButton.setMessage(assembler?Component.literal("R"):Component.translatable("gui.unusualcopper.redstone"));
        boolean controller=menu.kind()==MachineKind.PRODUCTION_CONTROLLER;
        orderOne.visible=controller;orderBatch.visible=controller;orderPattern.visible=controller;orderPreview.visible=controller;
        orderPattern.active=controller && menu.data.get(48)==0 && menu.duration()==0;
        brewerButton.visible=menu.kind()==MachineKind.AETHERIC_BREWER;
        cancelButton.visible = menu.kind().acceptsInputs() && menu.kind()!=MachineKind.ARMOR_WORKBENCH;
        redstoneButton.setTooltip(Tooltip.create(Component.translatable("gui.unusualcopper.redstone_mode."+menu.data.get(7))));
        reaperButton.visible = menu.kind() == MachineKind.MOB_REAPER;
        if (reaperButton.visible) reaperButton.setTooltip(Tooltip.create(Component.translatable("reaper.unusualcopper.cost", (menu.data.get(32)&0xffff)|((menu.data.get(33)&0xffff)<<16))));
        cancelButton.active = menu.duration() > 0 || menu.kind()==MachineKind.ARBORIST || (menu.kind()==MachineKind.PRODUCTION_CONTROLLER && menu.data.get(48)>0);
        plantationButton.visible=menu.kind()==MachineKind.ARBORIST; plantationButton.active=menu.data.get(34)>0;
        if(plantationButton.visible) cancelButton.setTooltip(Tooltip.create(Component.translatable("arborist.unusualcopper.cancel")));
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
