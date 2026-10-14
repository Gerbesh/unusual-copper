package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.WorkArea;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class WorkAreaTest {
    @Test void inclusiveSelectionCoversEveryCoordinateWithoutDeadZones() {
        var area = WorkArea.between(new BlockPos(15, 15, 15), BlockPos.ZERO); var visited = new HashSet<BlockPos>();
        for (int index = 0; index < area.volume(); index++) { var pos = area.at(index); assertTrue(area.contains(pos)); assertTrue(visited.add(pos)); }
        assertEquals(4096, visited.size()); assertEquals(area.at(0), area.at(area.volume())); assertEquals(area.max(), area.at(-1));
    }
    @Test void hostileAndReversedBoundsCannotWrapAroundTheLimit() {
        assertThrows(IllegalArgumentException.class, () -> new WorkArea(new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> WorkArea.between(BlockPos.ZERO, new BlockPos(16, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new WorkArea(new BlockPos(1, 0, 0), BlockPos.ZERO));
    }
    @Test void savedSelectionIsAnImmutableCopy() {
        var min = new BlockPos.MutableBlockPos(2, 3, 4); var area = new WorkArea(min, new BlockPos(3, 4, 5)); min.set(100, 100, 100);
        assertEquals(new BlockPos(2, 3, 4), area.min()); assertEquals(area, WorkArea.load(area.save()));
    }
}
