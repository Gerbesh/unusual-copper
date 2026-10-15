package io.github.gerbesh.unusualcopper.machine;

public enum MachineKind {
    SOLAR_INFUSER("solar_infuser", 0),
    ESSENCE_SPLITTER("essence_splitter", 50_000),
    COPPER_CELL("copper_cell", 250_000),
    RESONANCE_SMELTER("resonance_smelter", 24_000),
    AURA_CULTIVATOR("aura_cultivator", 50_000),
    MAGNETIC_COLLECTOR("magnetic_collector", 24_000);

    public final String id;
    public final int capacity;
    MachineKind(String id, int capacity) { this.id = id; this.capacity = capacity; }
    public boolean produces() { return this == ESSENCE_SPLITTER || this == COPPER_CELL; }
    public boolean consumes() { return this == RESONANCE_SMELTER || this == COPPER_CELL || usesArea(); }
    public boolean hasInventory() { return this != COPPER_CELL && this != AURA_CULTIVATOR; }
    public boolean acceptsInputs() { return hasInventory() && this != MAGNETIC_COLLECTOR; }
    public boolean usesArea() { return this == AURA_CULTIVATOR || this == MAGNETIC_COLLECTOR; }
}
