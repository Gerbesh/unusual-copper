package io.github.gerbesh.unusualcopper.armor;

import net.minecraft.world.entity.EquipmentSlot;

/** Strict whitelist: module IDs cannot be supplied as arbitrary client NBT. */
public enum ArmorModuleType {
    OPTIC_VISOR("optic_visor", EquipmentSlot.HEAD, 1),
    AQUA_LUNGS("aqua_lungs", EquipmentSlot.HEAD, 3),
    STRIDE_DRIVE("stride_drive", EquipmentSlot.LEGS, 5),
    MINING_SERVO("mining_servo", EquipmentSlot.LEGS, 8),
    SHIELD_FIELD("shield_field", EquipmentSlot.CHEST, 6),
    FALL_DAMPER("fall_damper", EquipmentSlot.FEET, 2),
    JUMP_DRIVE("jump_drive", EquipmentSlot.FEET, 10),
    HOVER_DRIVE("hover_drive", EquipmentSlot.FEET, 80),
    FLIGHT_CORE_II("flight_core_ii", EquipmentSlot.CHEST, 200);

    public final String id;
    public final EquipmentSlot slot;
    public final int cue;
    ArmorModuleType(String id, EquipmentSlot slot, int cue) {
        this.id=id;this.slot=slot;this.cue=cue;
    }
    public static ArmorModuleType byId(String id) {
        for(var type:values())if(type.id.equals(id))return type;
        return null;
    }
}
