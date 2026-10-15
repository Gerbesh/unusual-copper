package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Immutable server-side target filter; passive mode never overrides protected entity exclusions. */
public record ReaperRule(boolean allowPassive, boolean adultsOnly, Set<ResourceLocation> types, Set<ResourceLocation> tags, Set<MobCategory> categories) {
    public ReaperRule {
        types = Set.copyOf(types); tags = Set.copyOf(tags); categories = Set.copyOf(categories);
        if (types.size() > 8 || tags.size() > 8) throw new IllegalArgumentException("Too many entity filters");
    }
    public static ReaperRule defaults() { return new ReaperRule(false, true, Set.of(), Set.of(), Set.of(MobCategory.MONSTER)); }
    public boolean matches(Mob mob) {
        if (!mob.isAlive() || mob instanceof AbstractVillager || mob.hasCustomName() || mob.isLeashed() || mob.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) return false;
        if (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null) return false;
        if (mob instanceof TamableAnimal pet && (pet.isTame() || pet.getOwnerUUID() != null)) return false;
        if (mob instanceof AbstractHorse horse && (horse.isTamed() || horse.getOwnerUUID() != null) && !(mob.getClass() == net.minecraft.world.entity.animal.camel.Camel.class && horse.getOwnerUUID() == null)) return false;
        if (adultsOnly && mob.isBaby()) return false;
        boolean hostile = mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER;
        if (!allowPassive && !hostile) return false;
        var type = mob.getType();
        return types.contains(ForgeRegistries.ENTITY_TYPES.getKey(type)) || categories.contains(type.getCategory()) || tags.stream().anyMatch(tag -> type.is(TagKey.create(Registries.ENTITY_TYPE, tag)));
    }
    public CompoundTag save() {
        var data = new CompoundTag(); data.putBoolean("AllowPassive", allowPassive); data.putBoolean("AdultsOnly", adultsOnly);
        var typeList = new ListTag(); types.stream().sorted().forEach(id -> typeList.add(StringTag.valueOf(id.toString()))); data.put("Types", typeList);
        var tagList = new ListTag(); tags.stream().sorted().forEach(id -> tagList.add(StringTag.valueOf(id.toString()))); data.put("Tags", tagList);
        var categoryList = new ListTag(); categories.stream().sorted().forEach(category -> categoryList.add(StringTag.valueOf(category.getName()))); data.put("Categories", categoryList); return data;
    }
    public static ReaperRule load(CompoundTag data) {
        try {
            var types = new HashSet<ResourceLocation>(); for (var raw : data.getList("Types", Tag.TAG_STRING)) types.add(new ResourceLocation(raw.getAsString()));
            var tags = new HashSet<ResourceLocation>(); for (var raw : data.getList("Tags", Tag.TAG_STRING)) tags.add(new ResourceLocation(raw.getAsString()));
            var categories = new HashSet<MobCategory>(); for (var raw : data.getList("Categories", Tag.TAG_STRING)) for (var category : MobCategory.values()) if (category.getName().equals(raw.getAsString())) categories.add(category);
            return new ReaperRule(data.getBoolean("AllowPassive"), !data.contains("AdultsOnly") || data.getBoolean("AdultsOnly"), types, tags, categories);
        } catch (RuntimeException invalid) { return defaults(); }
    }
}
