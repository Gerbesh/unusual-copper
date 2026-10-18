package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningChunkLoadingGameTests {
    @GameTest(template="empty") public static void pauseReleasesAndResumeReacquiresTickets(GameTestHelper h) {
        var f=MiningGameTests.fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine();
        h.assertTrue(m.mining.chunkLoading==MiningChunkStatus.READY&&m.mining.chunksLoaded>0,"Fixture owns ready tickets");
        m.mining.enabled=false;MiningChunkLoading.changed(h.getLevel(),m);
        h.assertTrue(m.mining.chunksTotal==0&&m.mining.chunksLoaded==0,"Pause releases the whole plan");
        m.mining.enabled=true;MiningGameTests.settleTickets(h,m);
        h.assertTrue(m.mining.chunksTotal==m.mining.chunksLoaded,"Resume re-admits the plan");
        m.mining.enabled=false;MiningChunkLoading.changed(h.getLevel(),m);h.succeed();
    }
    @GameTest(template="empty") public static void destroyedStationReleasesItsTickets(GameTestHelper h) {
        var f=MiningGameTests.fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine();
        var plan=MiningChunkPlan.create(m.mining.area,m.getBlockPos().getX(),m.getBlockPos().getZ(),m.boundInventory().getX(),m.boundInventory().getZ());
        h.getLevel().setBlock(m.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(h.getLevel().getBlockEntity(m.getBlockPos())==null,"Machine removed");
        for(var entry:plan.chunks().entrySet()){
            var chunk=new net.minecraft.world.level.ChunkPos(entry.getKey());
            h.assertTrue(!net.minecraftforge.common.world.ForgeChunkManager.forceChunk(h.getLevel(),UnusualCopper.MOD_ID,m.getBlockPos(),chunk.x,chunk.z,false,entry.getValue()),"Every ticket was already removed by block removal");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void completionReleasesTicketsOnlyAfterFlightsAndLootAreDrained(GameTestHelper h) {
        var f=MiningGameTests.fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine();
        m.mining.jobs.clear();m.inventory.setStackInSlot(2,new ItemStack(Items.RAW_COPPER));MiningChunkLoading.changed(h.getLevel(),m);
        h.assertTrue(m.mining.chunksTotal>0,"Last owned loot keeps the route loaded");
        m.inventory.setStackInSlot(2,ItemStack.EMPTY);MiningChunkLoading.changed(h.getLevel(),m);
        h.assertTrue(m.mining.chunksTotal==0&&m.mining.chunkLoading==MiningChunkStatus.IDLE,"Exhausted and drained station releases all tickets");h.succeed();
    }
    @GameTest(template="empty") public static void newAreaWaitsForAdmissionBeforeSpendingOrAdvancingFlights(GameTestHelper h) {
        var f=MiningGameTests.fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine();var pos=m.getBlockPos();
        m.mining.area=new MiningFootprint(pos.getX(),pos.getZ(),pos.getX()+32,pos.getZ()+32);MiningChunkLoading.changed(h.getLevel(),m);
        int energy=m.energy.stored(),progress=m.mining.jobs.get(0).progress;
        io.github.gerbesh.unusualcopper.machine.logic.MiningLogic.process(m,h.getLevel(),64,Long.MAX_VALUE);
        h.assertTrue(m.status()==io.github.gerbesh.unusualcopper.machine.MachineStatus.CHUNK_LOADING&&m.energy.stored()==energy&&m.mining.mined==0&&m.mining.jobs.get(0).progress==progress,"Unadmitted area cannot advance or pay for mining");
        m.mining.enabled=false;MiningChunkLoading.changed(h.getLevel(),m);h.succeed();
    }
    @GameTest(template="empty") public static void actualDoubleChestPartnerGetsItsOwnTickingTicket(GameTestHelper h) {
        var f=MiningGameTests.fixture(h,new ItemStack(Items.DIAMOND_PICKAXE));var m=f.machine();var pos=m.getBlockPos();
        var left=new net.minecraft.core.BlockPos((pos.getX()>>4)*16+15,pos.getY(),pos.getZ()+2);var right=left.east();
        var facing=net.minecraft.core.Direction.NORTH;
        h.getLevel().setBlock(left,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,facing).setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT),2);
        h.getLevel().setBlock(right,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,facing).setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT),2);
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(m.owner(),"MiningFixture"));
        h.assertTrue(m.bind(left,net.minecraft.core.Direction.UP,actor),"Double chest bound");MiningGameTests.settleTickets(h,m);
        var chunk=new net.minecraft.world.level.ChunkPos(right);
        h.assertTrue(net.minecraftforge.common.world.ForgeChunkManager.forceChunk(h.getLevel(),UnusualCopper.MOD_ID,pos,chunk.x,chunk.z,false,true),"Other chest half owns a ticking ticket");
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(h.getLevel(),UnusualCopper.MOD_ID,pos,chunk.x,chunk.z,true,true);
        m.mining.enabled=false;MiningChunkLoading.changed(h.getLevel(),m);h.succeed();
    }
}
