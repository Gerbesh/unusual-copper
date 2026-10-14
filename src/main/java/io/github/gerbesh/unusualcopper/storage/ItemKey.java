package io.github.gerbesh.unusualcopper.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Immutable identity includes the item, its complete NBT and serialized Forge capabilities. */
public final class ItemKey {
    private final CompoundTag encoded;
    private final int hash;
    private ItemKey(CompoundTag encoded) { this.encoded = encoded.copy(); hash = this.encoded.hashCode(); }
    public static ItemKey of(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty item identity");
        return new ItemKey(stack.copyWithCount(1).save(new CompoundTag()));
    }
    public static ItemKey load(CompoundTag encoded) { return of(ItemStack.of(encoded)); }
    public CompoundTag save() { return encoded.copy(); }
    public ItemStack stack(int count) { return ItemStack.of(encoded.copy()).copyWithCount(count); }
    public boolean matches(ItemStack stack) { return !stack.isEmpty() && equals(of(stack)); }
    @Override public boolean equals(Object other) { return other instanceof ItemKey key && encoded.equals(key.encoded); }
    @Override public int hashCode() { return hash; }
}
