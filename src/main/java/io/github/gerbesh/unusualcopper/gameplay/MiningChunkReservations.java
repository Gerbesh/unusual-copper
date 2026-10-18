package io.github.gerbesh.unusualcopper.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Atomic quota admission for whole plans, including chunks still warming up. Counts ticket references. */
public final class MiningChunkReservations<K> {
    private record Reservation(UUID owner, int count) {}
    private final Map<K, Reservation> reservations = new HashMap<>();

    public boolean reserve(K key, UUID owner, int count, int ownerLimit, int levelLimit) {
        if (count < 0 || count > MiningChunkPlan.MAX_TICKETS || owner == null) return false;
        int total = count, owned = count;
        for (var entry : reservations.entrySet()) {
            if (entry.getKey().equals(key)) continue;
            total += entry.getValue().count;
            if (owner.equals(entry.getValue().owner)) owned += entry.getValue().count;
        }
        if (total > levelLimit || owned > ownerLimit) return false;
        if (count == 0) reservations.remove(key);
        else reservations.put(key, new Reservation(owner, count));
        return true;
    }

    public void release(K key) { reservations.remove(key); }
    public int total() { return reservations.values().stream().mapToInt(Reservation::count).sum(); }
}
