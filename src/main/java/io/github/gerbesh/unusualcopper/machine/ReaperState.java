package io.github.gerbesh.unusualcopper.machine;

import io.github.gerbesh.unusualcopper.gameplay.ReaperRule;
import io.github.gerbesh.unusualcopper.storage.ItemKey;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Owned emergency loot, independent of external storage simulation. */
public final class ReaperState {
    public ReaperRule rule = ReaperRule.defaults();
    public int trigger, keep, population;
    public boolean active;
    private final List<ItemStack> loot = new ArrayList<>();
    public List<ItemStack> loot() { return loot.stream().map(ItemStack::copy).toList(); }
    public int lootCount() { return (int)Math.min(Integer.MAX_VALUE, loot.stream().mapToLong(ItemStack::getCount).sum()); }
    public void settings(ReaperRule rule, int trigger, int keep) {
        if (trigger < 0 || trigger > 1_000_000 || keep < 0 || keep > trigger) throw new IllegalArgumentException("Invalid population thresholds");
        this.rule = rule; this.trigger = trigger; this.keep = keep; active = false;
    }
    public ItemStack hold(ItemStack original) {
        var rest = original.copy(); if (rest.isEmpty()) return ItemStack.EMPTY; var key = ItemKey.of(rest);
        for (var stored : loot) if (key.matches(stored)) { int count = Math.min(rest.getCount(), Math.max(0, stored.getMaxStackSize() - stored.getCount())); stored.grow(count); rest.shrink(count); if (rest.isEmpty()) return ItemStack.EMPTY; }
        while (!rest.isEmpty() && loot.size() < 64) { int count = Math.min(rest.getCount(), rest.getMaxStackSize()); loot.add(rest.copyWithCount(count)); rest.shrink(count); }
        return rest;
    }
    public void drain(CopperMachineBlockEntity machine) {
        if (loot.isEmpty()) return;
        var rest = machine.insertOutput(loot.get(0)); if (rest.isEmpty()) loot.remove(0); else loot.set(0, rest); machine.setChanged();
    }
    public CompoundTag save() {
        var data = new CompoundTag(); data.put("Rule", rule.save()); data.putInt("Trigger", trigger); data.putInt("Keep", keep); data.putBoolean("Active", active);
        var items = new ListTag(); for (var stack : loot) { var item = new CompoundTag(); item.put("Stack", stack.copyWithCount(1).save(new CompoundTag())); item.putInt("Count", stack.getCount()); items.add(item); } data.put("Loot", items); return data;
    }
    public void load(CompoundTag data) {
        rule = data.contains("Rule") ? ReaperRule.load(data.getCompound("Rule")) : ReaperRule.defaults(); trigger = Math.max(0, Math.min(1_000_000, data.getInt("Trigger"))); keep = Math.max(0, Math.min(trigger, data.getInt("Keep"))); active = data.getBoolean("Active"); loot.clear();
        for (var raw : data.getList("Loot", Tag.TAG_COMPOUND)) { var item = (CompoundTag)raw; var stack = ItemStack.of(item.getCompound("Stack")); stack.setCount(Math.max(0, item.getInt("Count"))); if (!stack.isEmpty()) loot.add(stack); }
    }
}
