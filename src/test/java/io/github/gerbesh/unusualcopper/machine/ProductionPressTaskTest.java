package io.github.gerbesh.unusualcopper.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** The controller job token and exact press address survive save/reload. */
class ProductionPressTaskTest {
    @Test void roundTripAndInvalidDataFailClosed() {
        var task=new ProductionPressTask(UUID.fromString("00000000-0000-0000-0000-000000000042"),
                new BlockPos(-3,72,16),ProductionPressTask.PLATE);
        var restored=ProductionPressTask.load(task.save());
        assertEquals(task,restored);
        assertEquals(ProductionPressTask.PLATE,restored.stage());
        var invalid=task.save();
        invalid.putInt("Stage",99);
        assertNull(ProductionPressTask.load(invalid));
        assertNull(ProductionPressTask.load(new CompoundTag()));
        assertThrows(IllegalArgumentException.class,
                ()->new ProductionPressTask(UUID.randomUUID(),new BlockPos(0,0,0),0));
    }
}
