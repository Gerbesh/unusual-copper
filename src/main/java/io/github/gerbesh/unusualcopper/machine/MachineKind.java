package io.github.gerbesh.unusualcopper.machine;

public enum MachineKind {
    SOLAR_INFUSER("solar_infuser", 0),
    ESSENCE_SPLITTER("essence_splitter", 50_000),
    COPPER_CELL("copper_cell", 250_000),
    RESONANCE_SMELTER("resonance_smelter", 24_000),
    AURA_CULTIVATOR("aura_cultivator", 50_000),
    MAGNETIC_COLLECTOR("magnetic_collector", 24_000),
    GROWTH_SHEPHERD("growth_shepherd", 50_000),
    MOB_REAPER("mob_reaper", 50_000),
    ARBORIST("arboreal_reconstructor", 100_000),
    COPPER_SENTINEL("copper_sentinel", 100_000),
    AETHERIC_BREWER("aetheric_brewer", 100_000),
    ENCHANTMENT_RESONATOR("enchantment_resonator",2_000_000),
    COPPER_CELL_II("copper_cell_ii", 2_000_000),
    COPPER_CELL_III("copper_cell_iii", 16_000_000),
    ENERGY_DISTRIBUTOR("energy_distributor", 4096),
    ENERGY_MONITOR("energy_monitor", 0),
    REPAIR_CRYSTAL("repair_crystal",2_000_000),
    RESONANT_EXTRACTOR("resonant_extractor",2_000_000),
    RESONANCE_CRUSHER("resonance_crusher", 20_000),
    COPPER_PRESS("copper_press", 20_000);

    public final String id;
    public final int capacity;
    MachineKind(String id, int capacity) { this.id = id; this.capacity = capacity; }
    public boolean isCell() { return this == COPPER_CELL || this == COPPER_CELL_II || this == COPPER_CELL_III; }
    public int energyTier() { return this == COPPER_CELL ? 0 : this == COPPER_CELL_II ? 1 : 2; }
    public boolean energyControl() { return this == ENERGY_DISTRIBUTOR || this == ENERGY_MONITOR; }
    public boolean produces() { return this == ESSENCE_SPLITTER || this == ENERGY_DISTRIBUTOR || isCell(); }
    public boolean consumes() { return this == RESONANCE_SMELTER || this == ENERGY_DISTRIBUTOR || isCell() || this == ARBORIST || this == COPPER_SENTINEL || this == AETHERIC_BREWER || this == ENCHANTMENT_RESONATOR || this==REPAIR_CRYSTAL || this==RESONANT_EXTRACTOR || this==RESONANCE_CRUSHER || this==COPPER_PRESS || usesArea(); }
    public boolean hasInventory() { return !isCell() && !energyControl() && this != GROWTH_SHEPHERD && this != COPPER_SENTINEL; }
    public boolean acceptsInputs() { return hasInventory() && this != AURA_CULTIVATOR && this != MAGNETIC_COLLECTOR && this != MOB_REAPER; }
    public boolean usesArea() { return this == AURA_CULTIVATOR || this == MAGNETIC_COLLECTOR || this == GROWTH_SHEPHERD || this == MOB_REAPER; }
}
