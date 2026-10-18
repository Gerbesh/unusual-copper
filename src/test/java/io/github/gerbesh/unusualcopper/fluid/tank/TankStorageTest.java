package io.github.gerbesh.unusualcopper.fluid.tank;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TankStorageTest {
    @Test void incompatibleFluidsCompeteForEmptyCubesDeterministically(){
        var entries=new ArrayList<>(List.of(Map.entry(new BlockPos(0,0,0),"water"),Map.entry(new BlockPos(4,0,0),"lava")));
        List<List<BlockPos>> expected=null;
        for(int seed=0;seed<20;seed++){
            Collections.shuffle(entries,new Random(seed));var nodes=new LinkedHashMap<BlockPos,String>();
            for(var e:entries)nodes.put(e.getKey(),e.getValue());for(int x=1;x<4;x++)nodes.put(new BlockPos(x,0,0),null);
            var groups=TankTopology.partition(nodes);if(expected==null)expected=groups;else assertEquals(expected,groups);
            assertEquals(2,groups.size());assertEquals(5,groups.stream().mapToInt(List::size).sum());
            assertTrue(groups.get(0).contains(new BlockPos(2,0,0)));assertFalse(groups.get(0).contains(new BlockPos(4,0,0)));
        }
    }
    @Test void emptyConnectedCubesAndVerticalLayersShareOneLevel(){
        var nodes=new HashMap<BlockPos,String>();
        for(int y=0;y<3;y++)for(int x=0;x<2;x++)nodes.put(new BlockPos(x,y,0),null);
        var members=TankTopology.partition(nodes).get(0);assertEquals(6,members.size());
        assertEquals(1,TankVolumes.fillAt(members,new BlockPos(0,0,0),48_000));
        assertEquals(.5F,TankVolumes.fillAt(members,new BlockPos(0,1,0),48_000));
        assertEquals(.5F,TankVolumes.fillAt(members,new BlockPos(1,1,0),48_000));
        assertEquals(0,TankVolumes.fillAt(members,new BlockPos(0,2,0),48_000));
    }
    @Test void oldFullTankSurvivesMergeDrainSplitAndNewCapacity(){
        int[] old={64_000};assertEquals(0,TankVolumes.fill(old,1000).moved());assertEquals(64_000,TankVolumes.total(old));
        int[] joined={64_000,0,0,0,0};var added=TankVolumes.fill(joined,30_000);
        assertEquals(16_000,added.moved());assertEquals(80_000,TankVolumes.total(added.amounts()));
        var drained=TankVolumes.drain(added.amounts(),1000);assertEquals(79_000,TankVolumes.total(drained.amounts()));
        // Splitting keeps each physical cube's actual quantity, including an old overfull one.
        assertEquals(79_000,Arrays.stream(drained.amounts()).sum());assertEquals(63_000,drained.amounts()[0]);
        assertEquals(0,TankVolumes.fill(new int[]{63_000},1000).moved());assertArrayEquals(new int[]{64_000,0,0,0,0},joined);
    }
    @Test void randomTransfersConserveFluidAndNeverGrowAnOverfullLegacyCube(){
        var random=new Random(51);int[] amounts={64_000,0,15_999,0,5000,0,0};
        for(int step=0;step<10_000;step++){
            int before=TankVolumes.total(amounts),offered=random.nextInt(70_000);boolean filling=random.nextBoolean();
            var plan=filling?TankVolumes.fill(amounts,offered):TankVolumes.drain(amounts,offered);
            assertTrue(plan.moved()>=0&&plan.moved()<=offered);
            assertEquals(before+(filling?plan.moved():-plan.moved()),TankVolumes.total(plan.amounts()));
            int[] after=plan.amounts();for(int i=0;i<after.length;i++)assertTrue(after[i]>=0&&after[i]<=Math.max(16_000,amounts[i]));amounts=after;
        }
    }
    @Test void surfaceAndShapeAreSharedAcrossMembersAndRejectMalformedMetadata(){
        var members=List.of(new BlockPos(0,150,0),new BlockPos(1,150,0),new BlockPos(0,151,0));
        assertEquals(150.5,TankVolumes.surface(members,16_000));assertEquals(151.5,TankVolumes.surface(members,40_000));
        var shuffled=new ArrayList<>(members);Collections.reverse(shuffled);assertEquals(TankTopology.shape(members),TankTopology.shape(shuffled));
        var view=new TankView(40_000,3,3,15,true,.5F,"minecraft:water",TankView.State.READY,new UUID(0,44),TankTopology.shape(members),20,151.5);
        var tag=new net.minecraft.nbt.CompoundTag();view.write(tag);assertEquals(view,TankView.read(tag));
        tag.getCompound("UCFluidTankView").putFloat("Fill",Float.NaN);assertEquals(TankView.EMPTY,TankView.read(tag));
    }
    @Test void sharedFacesHaveOnlyBoundaryCornersIncludingConcaveSupports(){
        var square=List.of(new BlockPos(0,0,0),new BlockPos(1,0,0),new BlockPos(0,0,1),new BlockPos(1,0,1));
        assertEquals(4,square.stream().mapToInt(p->Integer.bitCount(TankTopology.corners(p,square))).sum());
        var elbow=square.subList(0,3);assertEquals(6,elbow.stream().mapToInt(p->Integer.bitCount(TankTopology.corners(p,elbow))).sum());
        assertNotEquals(0,TankTopology.corners(BlockPos.ZERO,elbow)&1<<3);
    }
}
