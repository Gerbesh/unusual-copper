package io.github.gerbesh.unusualcopper.machine;

public enum MachineStatus {
    IDLE, WORKING, NO_SUN, NO_LENS, OUTPUT_FULL, NO_ENERGY, INVALID_INPUT, REDSTONE_PAUSED, RECIPE_CHANGED, BUFFER_FULL,
    NO_AREA, NO_TARGETS, PROTECTED, UNLOADED, UNSUPPORTED, NO_GROWTH;
    public String translationKey() { return "status.unusualcopper." + name().toLowerCase(java.util.Locale.ROOT); }
}
