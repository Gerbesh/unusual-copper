package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Collection;

/** Legacy modules are level I; malformed or future data stays preserved but inert. */
public final class ArmorModuleLevels {
    private ArmorModuleLevels() {}

    public static int read(CompoundTag root, ArmorModuleType module) {
        if (!root.contains("ModuleLevels")) return 1;
        if (!root.contains("ModuleLevels", Tag.TAG_COMPOUND)) return 0;
        var levels = root.getCompound("ModuleLevels");
        if (!levels.contains(module.id)) return 1;
        if (!levels.contains(module.id, Tag.TAG_INT)) return 0;
        int value = levels.getInt(module.id);
        return value >= 1 && value <= module.maxLevel() ? value : 0;
    }

    public static boolean valid(CompoundTag root, int tier, Collection<ArmorModuleType> installed) {
        if (tier < 1 || tier > 3) return false;
        if (!root.contains("ModuleLevels")) return true;
        if (!root.contains("ModuleLevels", Tag.TAG_COMPOUND)) return false;
        var levels = root.getCompound("ModuleLevels");
        if (levels.size() > installed.size()) return false;
        for (String id : levels.getAllKeys()) {
            var module = ArmorModuleType.byId(id);
            if (module == null || !installed.contains(module)) return false;
            int level = read(root, module);
            if (level < 1 || level > tier) return false;
        }
        return true;
    }
}
