package io.github.gerbesh.unusualcopper.machine;

public enum SideMode {
    DISABLED, ITEM_INPUT, ITEM_OUTPUT, ENERGY_INPUT, ENERGY_OUTPUT;
    public SideMode next() { return values()[(ordinal() + 1) % values().length]; }
    public String translationKey() { return "side.unusualcopper." + name().toLowerCase(java.util.Locale.ROOT); }
}
