package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.gameplay.ReaperSettings;
import io.github.gerbesh.unusualcopper.menu.ReaperMenu;
import io.github.gerbesh.unusualcopper.network.ReaperNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ReaperScreen extends AbstractContainerScreen<ReaperMenu> {
    private EditBox trigger,keep,types,tags,categories;
    private Button passiveButton,adultsButton,apply;
    private boolean passive,adults;
    private int observed=-1;
    private String message="reaper.unusualcopper.loading";
    public ReaperScreen(ReaperMenu menu,Inventory inventory,Component title) { super(menu,inventory,title); imageWidth=320; imageHeight=236; }
    private EditBox box(int x,int y,int width,int limit,String label) {
        var box=new EditBox(font,leftPos+x,topPos+y,width,18,Component.translatable("reaper.unusualcopper."+label)); box.setMaxLength(limit); addRenderableWidget(box); return box;
    }
    private Button button(int x,int y,int width,String key,Runnable action) { return addRenderableWidget(Button.builder(Component.translatable("reaper.unusualcopper."+key),b -> action.run()).bounds(leftPos+x,topPos+y,width,18).build()); }
    @Override protected void init() {
        super.init(); observed=-1;
        trigger=box(12,44,138,7,"trigger"); keep=box(170,44,138,7,"keep");
        passiveButton=button(12,66,146,"passive.0",() -> { passive=!passive; labels(); });
        adultsButton=button(162,66,146,"adults.1",() -> { adults=!adults; labels(); });
        types=box(12,100,296,1024,"types"); tags=box(12,135,296,1024,"tags"); categories=box(12,170,296,256,"categories");
        types.setTooltip(Tooltip.create(Component.translatable("reaper.unusualcopper.types_hint")));
        tags.setTooltip(Tooltip.create(Component.translatable("reaper.unusualcopper.tags_hint")));
        categories.setTooltip(Tooltip.create(Component.translatable("reaper.unusualcopper.categories_hint")));
        apply=button(12,195,146,"apply",this::apply);
        button(162,195,146,"back",() -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
    }
    private void labels() {
        passiveButton.setMessage(Component.translatable("reaper.unusualcopper.passive."+(passive?1:0)));
        adultsButton.setMessage(Component.translatable("reaper.unusualcopper.adults."+(adults?1:0)));
    }
    private void apply() {
        try {
            var values=new ReaperSettings(Integer.parseInt(trigger.getValue().trim()),Integer.parseInt(keep.getValue().trim()),passive,adults,types.getValue().trim(),tags.getValue().trim(),categories.getValue().trim());
            values.validate(); ReaperNetwork.settings(menu.containerId,values); message="reaper.unusualcopper.pending";
        } catch (IllegalArgumentException invalid) { message="reaper.unusualcopper.invalid"; }
    }
    private void refresh() {
        apply.active=menu.snapshot()!=null;
        if (observed==menu.snapshotSerial || menu.snapshot()==null) return;
        observed=menu.snapshotSerial; var v=menu.snapshot();
        trigger.setValue(Integer.toString(v.trigger())); keep.setValue(Integer.toString(v.keep()));
        types.setValue(v.types()); tags.setValue(v.tags()); categories.setValue(v.categories()); passive=v.passive(); adults=v.adults(); labels();
        message="reaper.unusualcopper."+(menu.result==1?"applied":menu.result==2?"invalid":menu.result==3?"throttled":"ready");
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffaa724d);
        g.fill(leftPos+5,topPos+18,leftPos+imageWidth-5,topPos+imageHeight-5,0xff243a36);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.drawString(font,title,8,6,0xff201b15,false);
        g.drawString(font,Component.translatable("reaper.unusualcopper.protected"),12,21,0xffb8efdf,false);
        g.drawString(font,Component.translatable("reaper.unusualcopper.trigger"),12,33,0xffe6f5ee,false);
        g.drawString(font,Component.translatable("reaper.unusualcopper.keep"),170,33,0xffe6f5ee,false);
        g.drawString(font,Component.translatable("reaper.unusualcopper.types"),12,89,0xffe6f5ee,false);
        g.drawString(font,Component.translatable("reaper.unusualcopper.tags"),12,124,0xffe6f5ee,false);
        g.drawString(font,Component.translatable("reaper.unusualcopper.categories"),12,159,0xffe6f5ee,false);
        g.drawString(font,Component.translatable(message),12,220,0xffb8efdf,false);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && key!=256) return box.keyPressed(key,scan,modifiers);
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) { refresh(); renderBackground(g); super.render(g,mouseX,mouseY,partial); renderTooltip(g,mouseX,mouseY); }
}
