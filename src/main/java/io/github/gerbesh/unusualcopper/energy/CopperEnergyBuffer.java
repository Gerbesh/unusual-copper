package io.github.gerbesh.unusualcopper.energy;

/** No Minecraft classes: the accounting contract can be tested independently. */
public final class CopperEnergyBuffer implements CopperEnergyHandler {
    private final int capacity;
    private final int receiveLimit;
    private final int extractLimit;
    private final Runnable changed;
    private int stored;

    public CopperEnergyBuffer(int capacity, int receiveLimit, int extractLimit, Runnable changed) {
        if (capacity < 0 || receiveLimit < 0 || extractLimit < 0) throw new IllegalArgumentException("Negative energy limit");
        this.capacity = capacity;
        this.receiveLimit = receiveLimit;
        this.extractLimit = extractLimit;
        this.changed = changed;
    }

    @Override public int stored() { return stored; }
    @Override public int capacity() { return capacity; }
    @Override public int receive(int amount, boolean simulate) {
        return add(Math.min(Math.max(0, amount), receiveLimit), simulate);
    }
    @Override public int extract(int amount, boolean simulate) {
        int taken = Math.min(Math.min(Math.max(0, amount), extractLimit), stored);
        if (!simulate && taken != 0) { stored -= taken; changed.run(); }
        return taken;
    }

    /** Internal production/refund bypasses port rates, but never capacity. */
    public int add(int amount, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), capacity - stored);
        if (!simulate && accepted != 0) { stored += accepted; changed.run(); }
        return accepted;
    }

    /** Internal operation payment bypasses output-port rates. */
    public boolean spend(int amount) {
        if (amount < 0 || amount > stored) return false;
        if (amount != 0) { stored -= amount; changed.run(); }
        return true;
    }

    public void restore(int amount) { stored = Math.max(0, Math.min(capacity, amount)); }
}
