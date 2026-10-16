package io.github.gerbesh.unusualcopper.storage;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** One machine owns every stack here; overflow remains with the caller. */
public final class OwnedItems {
    private final List<ItemStack> stacks=new ArrayList<>();
    public List<ItemStack> items() { return stacks.stream().map(ItemStack::copy).toList(); }
    public int count() { return (int)Math.min(Integer.MAX_VALUE,stacks.stream().mapToLong(ItemStack::getCount).sum()); }
    public boolean empty() { return stacks.isEmpty(); }
    public boolean consumeOne(ItemStack template) {
        var key=ItemKey.of(template);
        var iterator=stacks.iterator();while(iterator.hasNext()) {var stack=iterator.next();if(key.matches(stack)){stack.shrink(1);if(stack.isEmpty())iterator.remove();return true;}}
        return false;
    }
    public void retain(ItemStack alreadyOwned) { if(!alreadyOwned.isEmpty()) stacks.add(alreadyOwned.copy()); }
    public ItemStack hold(ItemStack original) {
        var rest=original.copy(); if(rest.isEmpty()) return ItemStack.EMPTY; var key=ItemKey.of(rest);
        for(var stored:stacks) if(key.matches(stored)) { int n=Math.min(rest.getCount(),Math.max(0,stored.getMaxStackSize()-stored.getCount())); stored.grow(n); rest.shrink(n); if(rest.isEmpty()) return ItemStack.EMPTY; }
        while(!rest.isEmpty() && stacks.size()<64) { int n=Math.min(rest.getCount(),rest.getMaxStackSize()); stacks.add(rest.copyWithCount(n)); rest.shrink(n); }
        return rest;
    }
    public void drain(CopperMachineBlockEntity machine) {
        drain(machine,1);
    }
    public void drain(CopperMachineBlockEntity machine,int limit) {
        for(int i=0;i<Math.min(64,limit) && !stacks.isEmpty();i++) {
            var rest=machine.insertOutput(stacks.get(0));
            if(rest.isEmpty())stacks.remove(0);else stacks.set(0,rest);
            machine.setChanged();if(!rest.isEmpty())break;
        }
    }
    public ListTag save() {
        var result=new ListTag(); for(var stack:stacks) { var row=new CompoundTag(); row.put("Stack",stack.copyWithCount(1).save(new CompoundTag())); row.putInt("Count",stack.getCount()); result.add(row); } return result;
    }
    public void load(ListTag data) {
        stacks.clear(); for(var raw:data) { var row=(CompoundTag)raw; var stack=ItemStack.of(row.getCompound("Stack")); stack.setCount(Math.max(0,row.getInt("Count"))); if(!stack.isEmpty()) stacks.add(stack); }
    }
}
