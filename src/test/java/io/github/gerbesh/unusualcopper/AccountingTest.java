package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.energy.CopperEnergyBuffer;
import io.github.gerbesh.unusualcopper.machine.OperationMath;
import io.github.gerbesh.unusualcopper.machine.SolarPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountingTest {
    @Test void sunPolicyUsesDaytimeRatherThanWeatherDarkness() {
        assertEquals(4, SolarPolicy.rate(6000, true, true, false, false));
        assertEquals(2, SolarPolicy.rate(6000, true, true, true, false));
        assertEquals(1, SolarPolicy.rate(6000, true, true, true, true));
        assertEquals(0, SolarPolicy.rate(18000, true, true, false, false));
        assertEquals(0, SolarPolicy.rate(6000, true, false, false, false));
        assertEquals(0, SolarPolicy.rate(6000, false, true, false, false));
        assertEquals(4, SolarPolicy.rate(30000, true, true, false, false));
    }
    @Test void simulationDoesNotMintOrSpendEnergy() {
        var energy = new CopperEnergyBuffer(50_000, 256, 256, () -> {});
        assertEquals(500, energy.add(500, true));
        assertEquals(0, energy.stored());
        energy.add(500, false);
        assertEquals(256, energy.extract(500, true));
        assertEquals(500, energy.stored());
        assertFalse(energy.spend(501));
        assertEquals(500, energy.stored());
        assertTrue(energy.spend(500));
        assertEquals(0, energy.stored());
    }
    @Test void capacityAndNegativeInputsNeverOverflow() {
        var energy = new CopperEnergyBuffer(50_000, 256, 256, () -> {});
        assertEquals(50_000, energy.add(Integer.MAX_VALUE, false));
        assertEquals(0, energy.add(-1, false));
        assertEquals(0, energy.extract(-1, false));
        assertEquals(0, energy.receive(Integer.MAX_VALUE, false));
        assertFalse(energy.spend(-1));
        energy.restore(Integer.MAX_VALUE);
        assertEquals(50_000, energy.stored());
    }
    @Test void fullStackConversionNeedsARealExternalBuffer() {
        var splitter = new CopperEnergyBuffer(50_000, 256, 256, () -> {});
        var cell = new CopperEnergyBuffer(250_000, 256, 256, () -> {});
        int remainingEssence = 128;
        while (remainingEssence > 0 || splitter.stored() > 0) {
            if (remainingEssence > 0 && splitter.add(500, true) == 500) { splitter.add(500, false); remainingEssence--; }
            int amount = Math.min(splitter.extract(256, true), cell.receive(256, true));
            assertEquals(amount, cell.receive(splitter.extract(amount, false), false));
        }
        assertEquals(64_000, cell.stored());
    }
    @Test void allDurationsChargeExactlyOneOperation() {
        for (int duration : new int[]{1, 7, 20, 30, 300, 100_000}) {
            int paid = 0;
            for (int t = 1; t <= duration; t++) paid += OperationMath.payment(240, t, duration);
            assertEquals(240, paid, "duration=" + duration);
        }
        assertEquals(20, OperationMath.smeltTicks(200));
        assertEquals(30, OperationMath.smeltTicks(300));
        assertEquals(1, OperationMath.smeltTicks(0));
        assertEquals(214_748_365, OperationMath.smeltTicks(Integer.MAX_VALUE));
    }
}
