package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.config.ServerConfig;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineStatus;
import io.github.gerbesh.unusualcopper.machine.logic.MiningLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/** Main-thread ticket lifecycle. Region tickets warm chunks without calling blocking getChunk/getChunkFuture. */
public final class MiningChunkLoading {
    private static final TicketType<BlockPos> WARMING = TicketType.create("unusualcopper:mining_warmup", BlockPos::compareTo);
    private static final Map<ServerLevel, World> worlds = new IdentityHashMap<>();
    private static final ArrayDeque<Lease> queue = new ArrayDeque<>();
    private static int warming;

    private static final class World {
        final Map<BlockPos, Lease> leases = new HashMap<>();
        final MiningChunkReservations<BlockPos> reservations = new MiningChunkReservations<>();
    }

    private record Settings(MiningFootprint area, BlockPos output, BlockPos partner, UUID owner, boolean enabled,
                            boolean faulted, boolean done, boolean redstone, boolean loading,
                            int ownerLimit, int levelLimit) {}

    private static final class Lease {
        final ServerLevel level;
        final CopperMachineBlockEntity machine;
        final World world;
        final BlockPos pos;
        final Map<Long, Boolean> held = new LinkedHashMap<>();
        Map<Long, Boolean> desired = Map.of();
        Settings settings;
        MiningChunkStatus result = MiningChunkStatus.IDLE;
        Long pending;
        long pendingSince, nextRefresh, retryAt;

        Lease(ServerLevel level, CopperMachineBlockEntity machine, World world) {
            this.level = level; this.machine = machine; this.world = world; pos = machine.getBlockPos().immutable();
        }
    }

    private static boolean backlog(CopperMachineBlockEntity m) {
        return !m.mining.recovery.empty() || m.boundCargoCount() > 0
                || !m.inventory.getStackInSlot(2).isEmpty() || !m.inventory.getStackInSlot(3).isEmpty();
    }

    private static Settings settings(CopperMachineBlockEntity m) {
        var s = m.mining;
        BlockPos partner = null;
        var output = m.boundInventory();
        if (output != null && m.getLevel() instanceof ServerLevel level
                && level.getChunkSource().getChunkNow(output.getX() >> 4, output.getZ() >> 4) != null) {
            var state = level.getBlockState(output);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
                partner = output.relative(ChestBlock.getConnectedDirection(state));
        }
        return new Settings(s.area, output, partner, m.owner(), s.enabled, s.faulted,
                s.finished && s.jobs.isEmpty() && s.targets.isEmpty() && !backlog(m), m.energyEnabled(),
                ServerConfig.miningChunkLoading(), ServerConfig.miningChunksOwner(), ServerConfig.miningChunksLevel());
    }

    public static void observe(ServerLevel level, CopperMachineBlockEntity m) {
        if (m.mining == null || m.isRemoved()) return;
        var world = worlds.computeIfAbsent(level, ignored -> new World());
        var old = world.leases.get(m.getBlockPos());
        if (old != null && old.machine == m) return;
        if (old != null) remove(old);
        var lease = new Lease(level, m, world);
        world.leases.put(lease.pos, lease); queue.addLast(lease);
        m.mining.chunkLoading = ServerConfig.miningChunkLoading() ? MiningChunkStatus.LOADING : MiningChunkStatus.DISABLED;
    }

    /** Called after explicit controls change, including pause and output/area reassignment. No chunk is loaded here. */
    public static void changed(ServerLevel level, CopperMachineBlockEntity m) {
        observe(level, m);
        var lease = worlds.get(level).leases.get(m.getBlockPos());
        if (lease == null) return;
        lease.nextRefresh = 0;
        refresh(lease, settings(m));
        publish(lease);
    }

    public static MachineStatus blockedStatus(ServerLevel level, CopperMachineBlockEntity m) {
        observe(level, m);
        var lease = worlds.get(level).leases.get(m.getBlockPos());
        if (lease == null) return MachineStatus.CHUNK_LOADING;
        if (!Objects.equals(lease.settings, settings(m))) changed(level, m);
        return switch (m.mining.chunkLoading) {
            case LOADING -> MachineStatus.CHUNK_LOADING;
            case LIMIT -> MachineStatus.CHUNK_LIMIT;
            case PROTECTED -> MachineStatus.PROTECTED;
            case FAILED -> MachineStatus.CHUNK_LOAD_FAILED;
            default -> null;
        };
    }

    private static void cancelWarmup(Lease lease) {
        if (lease.pending == null) return;
        lease.level.getChunkSource().removeRegionTicket(WARMING, new ChunkPos(lease.pending), 2, lease.pos, false);
        lease.pending = null; warming--;
    }

    private static void unforce(Lease lease, long chunk, boolean ticking) {
        var pos = new ChunkPos(chunk);
        ForgeChunkManager.forceChunk(lease.level, UnusualCopper.MOD_ID, lease.pos, pos.x, pos.z, false, ticking);
    }

    private static void reconcile(Lease lease, Map<Long, Boolean> desired) {
        if (lease.pending != null && !desired.containsKey(lease.pending)) cancelWarmup(lease);
        for (var entry : new ArrayList<>(lease.held.entrySet()))
            if (!Objects.equals(desired.get(entry.getKey()), entry.getValue())) {
                lease.held.remove(entry.getKey()); unforce(lease, entry.getKey(), entry.getValue());
            }
        lease.desired = desired;
    }

    private static void remove(Lease lease) {
        cancelWarmup(lease);
        reconcile(lease, Map.of());
        lease.world.reservations.release(lease.pos);
        lease.world.leases.remove(lease.pos, lease);
    }

    public static void removed(ServerLevel level, BlockPos pos) {
        var world = worlds.get(level);
        if (world != null) { var lease = world.leases.get(pos); if (lease != null) remove(lease); }
    }

    private static boolean allowed(Lease lease, Map<Long, Boolean> plan, UUID owner) {
        for (long packed : plan.keySet()) {
            var chunk = new ChunkPos(packed);
            if (!lease.level.getWorldBorder().isWithinBounds(chunk)
                    || !ProtectionPolicy.allowedChunk(lease.level, chunk.x, chunk.z, owner)) return false;
        }
        return true;
    }

    private static void refresh(Lease lease, Settings s) {
        lease.settings = s; lease.nextRefresh = lease.level.getGameTime() + 20;
        Map<Long, Boolean> desired = Map.of();
        lease.result = s.loading ? MiningChunkStatus.IDLE : MiningChunkStatus.DISABLED;
        if (s.loading && s.enabled && !s.faulted && !s.done && s.area != null && s.output != null) {
            if (s.owner == null || !MiningLogic.geometry(lease.level, lease.pos, s.area)
                    || lease.pos.distSqr(s.output) > 32 * 32
                    || lease.level.isOutsideBuildHeight(s.output)) lease.result = MiningChunkStatus.PROTECTED;
            else {
                var plan = s.redstone ? MiningChunkPlan.create(s.area, lease.pos.getX(), lease.pos.getZ(), s.output.getX(), s.output.getZ())
                        : MiningChunkPlan.controller(lease.pos.getX(), lease.pos.getZ());
                if (s.redstone && s.partner != null) plan = plan.withStoragePartner(s.partner.getX(), s.partner.getZ());
                desired = plan.chunks();
                if (!allowed(lease, desired, s.owner)) { desired = Map.of(); lease.result = MiningChunkStatus.PROTECTED; }
                else if (!lease.world.reservations.reserve(lease.pos, s.owner, desired.size(), s.ownerLimit, s.levelLimit)) {
                    lease.result = MiningChunkStatus.LIMIT;
                    desired = MiningChunkPlan.controller(lease.pos.getX(), lease.pos.getZ()).chunks();
                    if (!lease.world.reservations.reserve(lease.pos, s.owner, 1, s.ownerLimit, s.levelLimit)) desired = Map.of();
                } else lease.result = s.redstone ? MiningChunkStatus.READY : MiningChunkStatus.IDLE;
            }
        }
        if (desired.isEmpty()) lease.world.reservations.release(lease.pos);
        reconcile(lease, desired);
    }

    private static int loaded(Lease lease) {
        int ready = 0;
        for (var entry : lease.desired.entrySet()) {
            var pos = new ChunkPos(entry.getKey());
            if (Objects.equals(lease.held.get(entry.getKey()), entry.getValue()) && lease.level.getChunkSource().getChunkNow(pos.x, pos.z) != null) ready++;
        }
        return ready;
    }

    private static void publish(Lease lease) {
        var s = lease.machine.mining;
        s.chunksTotal = lease.desired.size(); s.chunksLoaded = loaded(lease);
        s.chunkLoading = lease.result == MiningChunkStatus.READY && s.chunksLoaded < s.chunksTotal ? MiningChunkStatus.LOADING : lease.result;
    }

    private static void failed(Lease lease, long chunk, RuntimeException error) {
        cancelWarmup(lease);
        lease.retryAt = lease.level.getGameTime() + 200;
        lease.result = MiningChunkStatus.FAILED;
        UnusualCopper.LOGGER.warn("Extractor chunk warm-up failed at {} for {}; retry in 200 ticks", lease.pos, new ChunkPos(chunk), error);
    }

    /** Returns whether this visit consumed one global admission/completion operation. */
    private static boolean step(Lease lease, boolean admission) {
        if (lease.machine.isRemoved() || lease.level.getChunkSource().getChunkNow(lease.pos.getX() >> 4, lease.pos.getZ() >> 4) == null
                || lease.level.getBlockEntity(lease.pos) != lease.machine) { remove(lease); return false; }
        long now = lease.level.getGameTime();
        var current = settings(lease.machine);
        if (!current.equals(lease.settings) || now >= lease.nextRefresh && now >= lease.retryAt) refresh(lease, current);
        if (now < lease.retryAt) { publish(lease); return false; }
        Long next = lease.pending;
        if (next != null && now - lease.pendingSince > 1200) { failed(lease, next, new IllegalStateException("Chunk warm-up timeout")); publish(lease); return false; }
        if (next == null) for (var entry : lease.desired.entrySet())
            if (!Objects.equals(lease.held.get(entry.getKey()), entry.getValue())) { next = entry.getKey(); break; }
        if (next == null || !admission) { publish(lease); return false; }
        var chunk = new ChunkPos(next);
        // A claim can change while a large plan is warming; recheck the exact chunk before issuing either ticket.
        if (!allowed(lease, Map.of(next, lease.desired.get(next)), current.owner)) {
            reconcile(lease, Map.of()); lease.world.reservations.release(lease.pos);
            lease.result = MiningChunkStatus.PROTECTED; publish(lease); return false;
        }
        try {
            if (lease.level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                boolean ticking = lease.desired.get(next);
                // getChunkNow established FULL availability, so Forge's synchronous getChunk will not wait for I/O/generation.
                if (!ForgeChunkManager.forceChunk(lease.level, UnusualCopper.MOD_ID, lease.pos, chunk.x, chunk.z, true, ticking))
                    throw new IllegalStateException("Could not acquire persistent extractor ticket");
                lease.held.put(next, ticking); cancelWarmup(lease);
            } else if (lease.pending == null && warming < ServerConfig.miningChunksWarming()) {
                lease.pending = next; lease.pendingSince = now; warming++;
                lease.level.getChunkSource().addRegionTicket(WARMING, chunk, 2, lease.pos, false);
            } else { publish(lease); return false; }
        } catch (RuntimeException error) { failed(lease, next, error); }
        publish(lease); return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long deadline = System.nanoTime() + 2_000_000L;
        int admissions = ServerConfig.miningChunksWarming(), visits = Math.min(16, queue.size());
        while (visits-- > 0 && !queue.isEmpty() && System.nanoTime() < deadline) {
            var lease = queue.removeFirst();
            if (lease.world.leases.get(lease.pos) != lease) continue;
            if (step(lease, admissions > 0)) admissions--;
            if (lease.world.leases.get(lease.pos) == lease) queue.addLast(lease);
        }
    }

    /** Restart keeps only a validated controller ticket; area/storage warm up through the bounded queue again. */
    public static void validateTickets(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        for (var entry : helper.getBlockTickets().entrySet()) {
            var pos = entry.getKey(); long self = new ChunkPos(pos).toLong();
            if (!ServerConfig.miningChunkLoading() || level.isOutsideBuildHeight(pos)
                    || !level.getWorldBorder().isWithinBounds(pos) || !entry.getValue().getSecond().contains(self)) {
                helper.removeAllTickets(pos); continue;
            }
            // The only synchronous load is the persisted controller used for validation, never its whole footprint.
            var entity = level.getChunkAt(pos).getBlockEntity(pos);
            if (!(entity instanceof CopperMachineBlockEntity m) || m.mining == null) { helper.removeAllTickets(pos); continue; }
            observe(level, m);
            var lease = worlds.get(level).leases.get(pos);
            refresh(lease, settings(m));
            if (!lease.desired.containsKey(self)) { helper.removeAllTickets(pos); continue; }
            for (long chunk : entry.getValue().getFirst()) helper.removeTicket(pos, chunk, false);
            for (long chunk : entry.getValue().getSecond()) if (chunk != self) helper.removeTicket(pos, chunk, true);
            lease.held.put(self, true); publish(lease);
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var world = worlds.remove(level);
        if (world == null) return;
        for (var lease : world.leases.values()) cancelWarmup(lease);
        queue.removeIf(lease -> lease.level == level);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (var world : worlds.values()) for (var lease : world.leases.values()) cancelWarmup(lease);
        // Do not remove persistent tickets: Forge saves/validates them on the next server start.
        worlds.clear(); queue.clear(); warming = 0;
    }

    private MiningChunkLoading() {}
}
