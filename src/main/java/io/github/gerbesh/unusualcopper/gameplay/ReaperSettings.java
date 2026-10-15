package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Bounded wire/editor values. Invalid input must never silently replace a targeting rule. */
public record ReaperSettings(int trigger, int keep, boolean passive, boolean adults, String types, String tags, String categories) {
    public static ReaperSettings of(ReaperRule rule, int trigger, int keep) {
        return new ReaperSettings(trigger, keep, rule.allowPassive(), rule.adultsOnly(), join(rule.types()), join(rule.tags()),
                String.join(",", rule.categories().stream().map(MobCategory::getName).sorted().toList()));
    }
    private static String join(Set<ResourceLocation> ids) { return String.join(",", ids.stream().map(Object::toString).sorted().toList()); }
    private static Set<ResourceLocation> ids(String text) {
        if (text.length() > 1024) throw new IllegalArgumentException("Filter too long");
        var result = new HashSet<ResourceLocation>();
        if (!text.isBlank()) for (String raw : text.split(",", -1)) {
            String value = raw.trim();
            if (value.length() > 128 || !value.contains(":")) throw new IllegalArgumentException("Use namespace:id");
            var id = ResourceLocation.tryParse(value);
            if (id == null || !result.add(id)) throw new IllegalArgumentException("Invalid or duplicate entity filter");
        }
        if (result.size() > 8) throw new IllegalArgumentException("Too many entity filters");
        return Set.copyOf(result);
    }
    public ReaperRule validate() {
        if (trigger < 0 || trigger > 1_000_000 || keep < 0 || keep > trigger) throw new IllegalArgumentException("Invalid thresholds");
        var types = ids(this.types); var tags = ids(this.tags);
        for (var type : types) if (!ForgeRegistries.ENTITY_TYPES.containsKey(type)) throw new IllegalArgumentException("Unknown entity type");
        if (categories.length() > 256) throw new IllegalArgumentException("Category filter too long");
        var selected = EnumSet.noneOf(MobCategory.class);
        if (!categories.isBlank()) for (String raw : categories.split(",", -1)) {
            var category = Arrays.stream(MobCategory.values()).filter(candidate -> candidate.getName().equals(raw.trim())).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown entity category"));
            if (!selected.add(category)) throw new IllegalArgumentException("Duplicate entity category");
        }
        return new ReaperRule(passive, adults, types, tags, selected);
    }
}
