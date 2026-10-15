package io.github.gerbesh.unusualcopper.energy;

/** No Minecraft classes: the accounting contract can be tested independently. */
public final class CopperEnergyBuffer implements CopperEnergyHandler {
    private final int capacity;
    private final java.util.function.IntSupplier receiveLimit;
    private final java.util.function.IntSupplier extractLimit;
    private final Runnable changed;
    private int stored;
    private long produced, consumed;
    public long produced() { return produced; }
    public long consumed() { return consumed; }

    public CopperEnergyBuffer(int capacity, int receiveLimit, int extractLimit, Runnable changed) {
        this(capacity, () -> receiveLimit, () -> extractLimit, changed);
    }

    public CopperEnergyBuffer(int capacity, java.util.function.IntSupplier receiveLimit, java.util.function.IntSupplier extractLimit, Runnable changed) {
        if (capacity < 0 || receiveLimit.getAsInt() < 0 || extractLimit.getAsInt() < 0) throw new IllegalArgumentException("Negative energy limit");
        this.capacity = capacity;
        this.receiveLimit = receiveLimit;
        this.extractLimit = extractLimit;
        this.changed = changed;
    }

    @Override public int stored() { return stored; }
    @Override public int capacity() { return capacity; }
    @Override public int receive(int amount, boolean simulate) {
        return credit(Math.min(Math.max(0, amount), Math.max(0, receiveLimit.getAsInt())), simulate);
    }
    @Override public int extract(int amount, boolean simulate) {
        int taken = Math.min(Math.min(Math.max(0, amount), Math.max(0, extractLimit.getAsInt())), stored);
        if (!simulate && taken != 0) { stored -= taken; changed.run(); }
        return taken;
    }

    /** Internal production bypasses port rates, but never capacity. */
    public int add(int amount, boolean simulate) {
        int accepted = credit(amount, simulate);
        if (!simulate) produced += accepted;
        return accepted;
    }
    /** Return a failed transfer without reporting it as freshly generated CuE. */
    public int refund(int amount) { return credit(amount, false); }
    /** An aborted operation restores its payment without reporting production or consumption. */
    public int refundSpent(int amount) { int accepted=credit(amount,false);consumed=Math.max(0,consumed-accepted);return accepted; }
    private int credit(int amount, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), capacity - stored);
        if (!simulate && accepted != 0) { stored += accepted; changed.run(); }
        return accepted;
    }

    /** Internal operation payment bypasses output-port rates. */
    public boolean spend(int amount) {
        if (amount < 0 || amount > stored) return false;
        if (amount != 0) { stored -= amount; consumed += amount; changed.run(); }
        return true;
    }

    public void restore(int amount) { stored = Math.max(0, Math.min(capacity, amount)); }
}
