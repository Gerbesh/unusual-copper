package io.github.gerbesh.unusualcopper.itempipe;

import io.github.gerbesh.unusualcopper.storage.ItemKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** One face's routing settings. Empty filters allow all items. */
public final class PipeRule {
    public PipeMode mode = PipeMode.AUTO;
    public boolean blacklist, exactNbt;
    public int priority, minimum, maximum = Integer.MAX_VALUE, reserve;
    private final ItemKey[] items = new ItemKey[8];
    private final List<ResourceLocation> tags = new ArrayList<>();
    private final Map<ItemKey, Boolean> filling = new LinkedHashMap<>();
    public ItemStack filter(int slot) { return items[slot] == null ? ItemStack.EMPTY : items[slot].stack(1); }
    public void filter(int slot, ItemStack stack) { items[slot] = stack.isEmpty() ? null : ItemKey.of(stack); filling.clear(); }
    public List<ResourceLocation> tags() { return List.copyOf(tags); }
    public boolean tag(ResourceLocation tag, boolean remove) {
        if (remove) return tags.remove(tag);
        if (tags.contains(tag) || tags.size() >= 8) return false;
        tags.add(tag); filling.clear(); return true;
    }
    public boolean accepts(ItemStack stack) {
        boolean any = !tags.isEmpty(), matches = false;
        for (var key : items) if (key != null) { any = true; matches |= exactNbt ? key.matches(stack) : stack.is(key.stack(1).getItem()); }
        for (var tag : tags) matches |= stack.is(TagKey.create(Registries.ITEM, tag));
        return !any || (blacklist ? !matches : matches);
    }
    public boolean restocking(ItemKey key, long stock) {
        if (stock >= maximum) { filling.put(key, false); return false; }
        if (minimum == 0) return true;
        if (filling.size() >= 64 && !filling.containsKey(key)) filling.remove(filling.keySet().iterator().next());
        boolean active = stock < minimum || filling.getOrDefault(key, false);
        filling.put(key, active); return active;
    }
    public boolean settings(int priority, int minimum, int maximum, int reserve) {
        if (priority < -100 || priority > 100 || minimum < 0 || minimum > 1_000_000 || maximum < minimum || (maximum > 1_000_000 && maximum != Integer.MAX_VALUE) || reserve < 0 || reserve > 1_000_000) return false;
        this.priority = priority; this.minimum = minimum; this.maximum = maximum; this.reserve = reserve; filling.clear(); return true;
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Mode", mode.ordinal()); tag.putBoolean("Blacklist", blacklist); tag.putBoolean("ExactNBT", exactNbt);
        tag.putInt("Priority", priority); tag.putInt("Minimum", minimum); tag.putInt("Maximum", maximum); tag.putInt("Reserve", reserve);
        var filters = new ListTag();
        for (int slot = 0; slot < items.length; slot++) if (items[slot] != null) { var value = items[slot].save(); value.putInt("Slot", slot); filters.add(value); }
        tag.put("Items", filters); var list = new ListTag(); tags.forEach(id -> list.add(StringTag.valueOf(id.toString()))); tag.put("Tags", list);
        var state = new ListTag(); filling.forEach((key, active) -> { var value = new CompoundTag(); value.put("Key", key.save()); value.putBoolean("Active", active); state.add(value); }); tag.put("Filling", state);
        return tag;
    }
    public void load(CompoundTag tag) {
        mode = PipeMode.values()[Math.floorMod(tag.getInt("Mode"), PipeMode.values().length)]; blacklist = tag.getBoolean("Blacklist"); exactNbt = tag.getBoolean("ExactNBT");
        int minimum = Math.max(0, Math.min(1_000_000, tag.getInt("Minimum")));
        int maximum = tag.contains("Maximum") ? Math.max(0, tag.getInt("Maximum")) : Integer.MAX_VALUE;
        if (maximum != Integer.MAX_VALUE) maximum = Math.max(minimum, Math.min(1_000_000, maximum));
        settings(Math.max(-100, Math.min(100, tag.getInt("Priority"))), minimum, maximum, Math.max(0, Math.min(1_000_000, tag.getInt("Reserve"))));
        Arrays.fill(items, null); tags.clear(); filling.clear();
        for (var raw : tag.getList("Items", Tag.TAG_COMPOUND)) { var value = (CompoundTag)raw; int slot = value.getInt("Slot"); if (slot >= 0 && slot < 8 && !ItemStack.of(value).isEmpty()) items[slot] = ItemKey.of(ItemStack.of(value)); }
        for (var raw : tag.getList("Tags", Tag.TAG_STRING)) { var id = ResourceLocation.tryParse(raw.getAsString()); if (id != null && tags.size() < 8 && !tags.contains(id)) tags.add(id); }
        for (var raw : tag.getList("Filling", Tag.TAG_COMPOUND)) { var value = (CompoundTag)raw; if (filling.size() < 64 && !ItemStack.of(value.getCompound("Key")).isEmpty()) filling.put(ItemKey.load(value.getCompound("Key")), value.getBoolean("Active")); }
    }
}
