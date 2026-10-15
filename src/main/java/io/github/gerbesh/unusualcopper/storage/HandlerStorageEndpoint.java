package io.github.gerbesh.unusualcopper.storage;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;

/** Loaded physical inventory only. No strong block-entity or capability reference survives an operation. */
public final class HandlerStorageEndpoint implements StorageEndpoint {
    public static final int SLOT_BUDGET = 256;
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction side;
    private final UUID owner;
    private WeakReference<IItemHandler> previous = new WeakReference<>(null);
    private int scanCursor, extractCursor, insertCursor;
    private int simulatedInsertion = -1;
    private int spaceCursor, spaceVisited;
    private long snapshotTick = Long.MIN_VALUE;
    private int snapshotLimit;
    private View rawSnapshot;
    private final Map<ItemKey, Integer> hints = new LinkedHashMap<>();
    private final InventoryCensus census=new InventoryCensus();
    private InventoryChanges.Token primaryToken,partnerToken;
    private long primaryRevision,partnerRevision;
    public HandlerStorageEndpoint(ServerLevel level, BlockPos pos, Direction side, UUID owner) {
        this.level = level; this.pos = pos.immutable(); this.side = side; this.owner = owner;
    }
    private BlockPos partner() {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                ? pos.relative(ChestBlock.getConnectedDirection(state)) : pos;
    }
    private IItemHandler resolve() {
        if (!level.hasChunkAt(pos) || !ProtectionPolicy.allowed(level, pos, owner)) return null;
        var partner = partner();
        if (!level.hasChunkAt(partner) || !ProtectionPolicy.allowed(level, partner, owner)) return null;
        var entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved()) return null;
        if (entity instanceof CopperMachineBlockEntity machine && !machine.allowsAutomation(owner)) return null;
        IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        if (previous.get() != handler) { previous = new WeakReference<>(handler); hints.clear(); scanCursor = extractCursor = insertCursor = spaceCursor = spaceVisited = 0; simulatedInsertion = -1; rawSnapshot = null; census.reset(); }
        var other=level.getBlockEntity(partner);if(other==null)return null;
        var a=InventoryChanges.watch(level,entity);var b=InventoryChanges.watch(level,other);
        if(primaryToken!=a||partnerToken!=b||primaryRevision!=a.revision()||partnerRevision!=b.revision())rawSnapshot=null;
        primaryToken=a;partnerToken=b;primaryRevision=a.revision();partnerRevision=b.revision();
        return handler;
    }
    @Override public boolean isAvailable() { return resolve() != null; }
    /** Potential insertion capacity, not a reservation for an unknown future loot type. */
    public Space space() {
        var handler = resolve(); if (handler == null) return Space.UNAVAILABLE;
        int slots = Math.max(0, handler.getSlots()); if (slots == 0) return Space.FULL;
        spaceCursor = Math.floorMod(spaceCursor, slots);
        for (int i = 0; i < Math.min(slots, SLOT_BUDGET); i++) {
            int slot = spaceCursor; spaceCursor = (spaceCursor + 1) % slots; spaceVisited++;
            var stack = handler.getStackInSlot(slot); int limit = handler.getSlotLimit(slot);
            if (limit > 0 && (stack.isEmpty() || (stack.getCount() < Math.min(limit, stack.getMaxStackSize()) && handler.insertItem(slot, stack.copyWithCount(1), true).isEmpty()))) { spaceVisited = 0; return Space.AVAILABLE; }
            if (spaceVisited >= slots) { spaceVisited = 0; return Space.FULL; }
        }
        return Space.SCANNING;
    }
    @Override public StorageIdentity identity() {
        BlockPos canonical = pos;
        if (level.hasChunkAt(pos)) {
            var partner = partner();
            if (partner.asLong() < canonical.asLong()) canonical = partner;
        }
        return new StorageIdentity(level.dimension(), canonical, side);
    }
    @Override public View snapshot(Predicate<ItemStack> filter, int limit) {
        var handler = resolve();
        if (handler == null) return new View(List.of(), 0, false);
        int slots = Math.max(0, handler.getSlots()), budget = Math.min(Math.max(0, limit), SLOT_BUDGET);
        if (slots == 0) return new View(List.of(), 0, true);
        if (rawSnapshot != null && snapshotTick == level.getGameTime() && snapshotLimit == budget)
            return new View(rawSnapshot.entries().stream().filter(entry -> filter.test(entry.key().stack(entry.count()))).toList(), rawSnapshot.nextSlot(), rawSnapshot.complete());
        scanCursor = Math.floorMod(scanCursor, slots);
        int start = scanCursor, checked = Math.min(budget, slots - start);
        List<Entry> entries = new ArrayList<>();
        for (int slot = start; slot < start + checked; slot++) {
            var stack = handler.getStackInSlot(slot).copy();
            if (stack.isEmpty()) continue;
            var key = ItemKey.of(stack); entries.add(new Entry(key, stack.getCount(), slot));
            if (hints.size() >= 64) hints.remove(hints.keySet().iterator().next());
            hints.put(key, slot);
        }
        scanCursor = start + checked >= slots ? 0 : start + checked;
        rawSnapshot = new View(entries, scanCursor, start == 0 && checked == slots); snapshotTick = level.getGameTime(); snapshotLimit = budget;
        return new View(entries.stream().filter(entry -> filter.test(entry.key().stack(entry.count()))).toList(), scanCursor, rawSnapshot.complete());
    }
    private List<Integer> extractionSlots(IItemHandler handler, ItemKey key) {
        int slots = Math.max(0, handler.getSlots());
        if (slots == 0) return List.of();
        List<Integer> result = new ArrayList<>();
        Integer hint = hints.get(key);
        if (hint != null && hint >= 0 && hint < slots) result.add(hint);
        int budget = Math.min(slots, SLOT_BUDGET - result.size());
        for (int i = 0; i < budget; i++) {
            int slot = (int)(((long)extractCursor + i) % slots);
            if (!result.contains(slot)) result.add(slot);
        }
        extractCursor = (int)(((long)extractCursor + Math.min(slots, SLOT_BUDGET)) % slots);
        return result;
    }
    @Override public int simulateExtract(ItemKey key, int amount) {
        var handler = resolve();
        if (handler == null || amount <= 0) return 0;
        int remaining = Math.min(amount, key.stack(1).getMaxStackSize());
        int requested = remaining;
        for (int slot : extractionSlots(handler, key)) {
            if (!key.matches(handler.getStackInSlot(slot))) continue;
            var simulated = handler.extractItem(slot, remaining, true);
            if (key.matches(simulated)) {
                if (hints.size() >= 64 && !hints.containsKey(key)) hints.remove(hints.keySet().iterator().next());
                hints.put(key, slot);
                remaining -= Math.min(remaining, simulated.getCount());
            }
            if (remaining == 0) break;
        }
        return requested - remaining;
    }
    @Override public List<ItemStack> extract(ItemKey key, int amount) {
        rawSnapshot = null;
        var handler = resolve();
        if (handler == null || amount <= 0) return List.of();
        int remaining = Math.min(amount, key.stack(1).getMaxStackSize());
        List<ItemStack> extracted = new ArrayList<>();
        for (int slot : extractionSlots(handler, key)) {
            if (!key.matches(handler.getStackInSlot(slot))) continue;
            var actual = handler.extractItem(slot, remaining, false);
            if (actual.isEmpty()) continue;
            extracted.add(actual.copy());
            if (!key.matches(actual) || actual.getCount() > remaining) break;
            remaining -= actual.getCount();
            if (remaining == 0) break;
        }
        return extracted;
    }
    @Override public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!simulate) rawSnapshot = null;
        var remaining = stack.copy(); var handler = resolve();
        if (handler == null || remaining.isEmpty()) return remaining;
        int slots = Math.max(0, handler.getSlots());
        if (slots == 0) return remaining;
        int start = !simulate && simulatedInsertion >= 0 ? Math.floorMod(simulatedInsertion, slots) : insertCursor;
        for (int i = 0; i < Math.min(slots, SLOT_BUDGET); i++) {
            int slot = (int)(((long)start + i) % slots);
            remaining = handler.insertItem(slot, remaining, simulate);
            if (remaining.isEmpty()) break;
        }
        if (simulate && remaining.getCount() < stack.getCount()) simulatedInsertion = start;
        else { insertCursor = (int)(((long)start + Math.min(slots, SLOT_BUDGET)) % slots); simulatedInsertion = -1; }
        return remaining;
    }
    @Override public Count count(ItemKey key) {
        var handler = resolve();
        if (handler == null) return new Count(0, false);
        long count = 0; int slots = Math.max(0, handler.getSlots());
        if(slots>SLOT_BUDGET){
            return census.count(handler,key,primaryToken,partnerToken,level.getGameTime());
        }
        for (int i = 0; i < Math.min(slots, SLOT_BUDGET); i++) {
            var stack = handler.getStackInSlot(i);
            if (key.matches(stack)) count += stack.getCount();
        }
        return new Count(count, slots <= SLOT_BUDGET);
    }
}
