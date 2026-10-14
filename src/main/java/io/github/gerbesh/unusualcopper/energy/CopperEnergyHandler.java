package io.github.gerbesh.unusualcopper.energy;

/** CuE is deliberately separate from Forge Energy. Calls run on the server thread. */
public interface CopperEnergyHandler {
    int stored();
    int capacity();
    int receive(int amount, boolean simulate);
    int extract(int amount, boolean simulate);
}
