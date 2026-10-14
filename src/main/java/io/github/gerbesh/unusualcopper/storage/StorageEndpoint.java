package io.github.gerbesh.unusualcopper.storage;

import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.Predicate;

public interface StorageEndpoint {
    record Entry(ItemKey key, int count, int slot) {}
    record View(List<Entry> entries, int nextSlot, boolean complete) {
        public View { entries = List.copyOf(entries); }
    }
    record Count(long amount, boolean complete) {}
    View snapshot(Predicate<ItemStack> filter, int limit);
    int simulateExtract(ItemKey key, int amount);
    /** Every physically extracted stack is returned, including unexpected results from a changing handler. */
    List<ItemStack> extract(ItemKey key, int amount);
    ItemStack insert(ItemStack stack, boolean simulate);
    Count count(ItemKey key);
    boolean isAvailable();
    StorageIdentity identity();
}
