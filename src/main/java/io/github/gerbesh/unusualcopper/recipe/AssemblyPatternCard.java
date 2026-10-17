package io.github.gerbesh.unusualcopper.recipe;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A single physical pattern card stores only a server-whitelisted recipe ID. */
public final class AssemblyPatternCard {
    private static final String KEY = "UCAssemblyPattern";
    private AssemblyPatternCard() {}

    public static boolean valid(ItemStack card) {
        if (!card.is(ModContent.PATTERN_CARD.get())) return false;
        if (!card.hasTag()) return true;
        CompoundTag tag = card.getTag();
        return tag != null && tag.getAllKeys().size() == 1
                && tag.contains(KEY, Tag.TAG_STRING) && recipe(card) != null;
    }

    public static ResourceLocation recipe(ItemStack card) {
        if (!card.is(ModContent.PATTERN_CARD.get()) || !card.hasTag()) return null;
        CompoundTag tag = card.getTag();
        if (tag == null || !tag.contains(KEY, Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY));
        return id != null && "unusualcopper".equals(id.getNamespace()) ? id : null;
    }

    public static boolean accepts(ItemStack card, ResourceLocation recipe) {
        if (!valid(card)) return false;
        ResourceLocation bound = recipe(card);
        return bound == null || bound.equals(recipe);
    }

    public static ItemStack engraved(ItemStack card, ResourceLocation recipe) {
        if (!valid(card) || card.getCount() != 1 || recipe == null
                || !"unusualcopper".equals(recipe.getNamespace())) return ItemStack.EMPTY;
        ItemStack single = card.copyWithCount(1);
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, recipe.toString());
        single.setTag(tag);
        return single;
    }
}
