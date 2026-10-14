package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs only in the isolated, explicitly selected development server profile. */
@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID)
public final class ServerSmokeTest {
    private static MinecraftServer server;
    private static CopperMachineBlockEntity solar, splitter, cell, smelter;
    private static int ticks;
    private static net.minecraft.world.level.block.entity.BarrelBlockEntity input, output;
    private static final java.util.List<io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity> itemPipes = new java.util.ArrayList<>();
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> fixtureChunks = new java.util.HashSet<>();

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("unusualcopper.smokeServer")) return;
        server = event.getServer();
        if (!server.isDedicatedServer()) throw new IllegalStateException("Smoke profile requires a dedicated server");
        var level = server.overworld();
        level.setDayTime(6000); level.setWeatherParameters(0, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        BlockPos origin = level.getSharedSpawnPos().above();
        for (int x = -2; x <= 8; x++) {
            var chunk = new net.minecraft.world.level.ChunkPos(origin.offset(x, 0, 0));
            if (fixtureChunks.add(chunk)) level.setChunkForced(chunk.x, chunk.z, true);
            level.setBlock(origin.offset(x, -1, 0), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        solar = place(origin, MachineKind.SOLAR_INFUSER);
        splitter = place(origin.offset(3, 0, 0), MachineKind.ESSENCE_SPLITTER);
        cell = place(origin.offset(6, 0, 0), MachineKind.COPPER_CELL);
        smelter = place(origin.offset(8, 0, 0), MachineKind.RESONANCE_SMELTER);
        for (int x : new int[]{4, 5, 7}) level.setBlock(origin.offset(x, 0, 0), ModContent.ENERGY_PIPE.get().defaultBlockState(), 3);
        solar.cycleSide(net.minecraft.core.Direction.WEST);
        while (solar.sideMode(net.minecraft.core.Direction.WEST) != io.github.gerbesh.unusualcopper.machine.SideMode.ITEM_INPUT) solar.cycleSide(net.minecraft.core.Direction.WEST);
        while (splitter.sideMode(net.minecraft.core.Direction.WEST) != io.github.gerbesh.unusualcopper.machine.SideMode.ITEM_INPUT) splitter.cycleSide(net.minecraft.core.Direction.WEST);
        input = barrel(origin.offset(-2, 0, 0)); input.setItem(0, new ItemStack(Items.COPPER_INGOT, 64));
        var inputPipe = itemPipe(origin.offset(-1, 0, 0)); inputPipe.rule(net.minecraft.core.Direction.WEST).mode = io.github.gerbesh.unusualcopper.itempipe.PipeMode.EXTRACT; inputPipe.changed();
        for (int x = 0; x <= 2; x++) itemPipe(origin.offset(x, -1, 0));
        itemPipe(origin.offset(2, 0, 0));
        itemPipe(origin.offset(8, -1, 0)); itemPipe(origin.offset(8, -1, 1)); itemPipe(origin.offset(8, 0, 1));
        output = barrel(origin.offset(8, 0, 2));
        solar.inventory.setStackInSlot(1, new ItemStack(ModContent.SOLAR_LENS.get()));
        smelter.inventory.setStackInSlot(0, new ItemStack(Items.RAW_IRON));
        UnusualCopper.LOGGER.info("CuE dedicated smoke started: real world ticks, ordinary server launch, 64 copper input");
    }

    private static CopperMachineBlockEntity place(BlockPos pos, MachineKind kind) {
        var level = server.overworld();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModContent.MACHINES.get(kind).get().defaultBlockState(), 3);
        return (CopperMachineBlockEntity)level.getBlockEntity(pos);
    }

    private static void keepChunk(BlockPos pos) {
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        if (fixtureChunks.add(chunk)) server.overworld().setChunkForced(chunk.x, chunk.z, true);
    }
    private static net.minecraft.world.level.block.entity.BarrelBlockEntity barrel(BlockPos pos) {
        keepChunk(pos); var level = server.overworld();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        return (net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(pos);
    }
    private static io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity itemPipe(BlockPos pos) {
        keepChunk(pos); var level = server.overworld();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos, ModContent.ITEM_PIPE.get().defaultBlockState(), 3);
        var pipe = (io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity)level.getBlockEntity(pos); itemPipes.add(pipe); return pipe;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks % 200 == 0) UnusualCopper.LOGGER.info("CuE dedicated smoke: tick={}, solarProgress={}, essence={}, storedCuE={}", ticks, solar.progress, splitter.inventory.getStackInSlot(0).getCount(),
                splitter.energy.stored() + cell.energy.stored() + smelter.energy.stored());
        boolean ironReady = output.getItem(0).is(Items.IRON_INGOT) && output.getItem(0).getCount() == 1;
        if (input.isEmpty() && solar.inventory.getStackInSlot(0).isEmpty() && solar.inventory.getStackInSlot(2).isEmpty() && solar.inventory.getStackInSlot(3).isEmpty()
                && splitter.inventory.getStackInSlot(0).isEmpty() && splitter.energy.stored() == 0 && ironReady && itemPipes.stream().allMatch(pipe -> pipe.cargoCount() == 0)) {
            int stored = cell.energy.stored() + smelter.energy.stored();
            if (stored != 63_760 || !solar.inventory.getStackInSlot(0).isEmpty() || solar.inventory.getStackInSlot(1).getDamageValue() != 64)
                throw new IllegalStateException("Dedicated chain accounting failed: remaining=" + stored);
            UnusualCopper.LOGGER.info("CuE DEDICATED SMOKE PASS: fully automatic chest/item-pipe chain; 64 copper -> 128 essence -> 64000 CuE; one iron operation paid 240, remaining 63760; ticks={}", ticks);
            for (var chunk : fixtureChunks) server.overworld().setChunkForced(chunk.x, chunk.z, false);
            fixtureChunks.clear();
            server.halt(false); server = null;
        } else if (ticks >= 1800) throw new IllegalStateException("Dedicated chain did not finish within 1800 ticks");
    }
}
