package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.AreaSelectionGeometry;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AreaSelectionGeometryTest {
    @Test void inclusiveReversedCornersAndOneBlockHaveCorrectDimensions() {
        var area=AreaSelectionGeometry.between(BlockPos.ZERO,new BlockPos(15,15,15),BlockPos.ZERO,false);
        assertTrue(area.valid());assertEquals(16,area.width());assertEquals(16,area.height());assertEquals(16,area.depth());
        var one=AreaSelectionGeometry.between(BlockPos.ZERO,BlockPos.ZERO,BlockPos.ZERO,false);
        assertEquals(1,one.width()*one.height()*one.depth());
        assertEquals(AreaSelectionGeometry.Problem.TOO_LARGE,AreaSelectionGeometry.between(BlockPos.ZERO,BlockPos.ZERO,new BlockPos(16,0,0),false).problem());
    }
    @Test void miningIgnoresClickedHeightAndAccepts128By128() {
        var area=AreaSelectionGeometry.between(new BlockPos(0,70,0),new BlockPos(0,-64,0),new BlockPos(127,310,127),true);
        assertTrue(area.valid());assertEquals(128,area.width());assertEquals(128,area.depth());
        assertEquals(70,area.min().getY());assertEquals(70,area.max().getY());assertEquals(1,area.height());
        assertEquals(AreaSelectionGeometry.Problem.TOO_LARGE,AreaSelectionGeometry.between(BlockPos.ZERO,BlockPos.ZERO,new BlockPos(128,0,0),true).problem());
    }
    @Test void reachChecksBothCornersAndLargeCoordinatesDoNotOverflow() {
        assertEquals(AreaSelectionGeometry.Problem.TOO_FAR,AreaSelectionGeometry.between(BlockPos.ZERO,new BlockPos(33,0,0),new BlockPos(34,0,0),false).problem());
        assertEquals(AreaSelectionGeometry.Problem.TOO_FAR,AreaSelectionGeometry.between(BlockPos.ZERO,new BlockPos(129,0,0),new BlockPos(130,0,0),true).problem());
        var remote=AreaSelectionGeometry.between(BlockPos.ZERO,new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0),true);
        assertEquals(4294967296L,remote.width());assertFalse(remote.valid());
    }
}
