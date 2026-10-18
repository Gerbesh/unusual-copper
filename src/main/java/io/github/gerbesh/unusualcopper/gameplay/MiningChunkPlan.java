package io.github.gerbesh.unusualcopper.gameplay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact horizontal footprint, plus the controller and storage service chunks. No world access. */
public record MiningChunkPlan(Map<Long, Boolean> chunks) {
    public static final int MAX_TICKETS = 84; // 9x9 unaligned area + controller + two chest halves.

    public MiningChunkPlan {
        if (chunks.size() > MAX_TICKETS) throw new IllegalArgumentException("Too many mining chunks");
        chunks = Collections.unmodifiableMap(new LinkedHashMap<>(chunks));
    }

    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    public static MiningChunkPlan controller(int x, int z) {
        return new MiningChunkPlan(Map.of(key(x >> 4, z >> 4), true));
    }

    public static MiningChunkPlan create(MiningFootprint area, int x, int z, int outputX, int outputZ) {
        var chunks = new LinkedHashMap<Long, Boolean>();
        chunks.put(key(x >> 4, z >> 4), true);
        chunks.put(key(outputX >> 4, outputZ >> 4), true);
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++)
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++)
                chunks.putIfAbsent(key(cx, cz), false);
        return new MiningChunkPlan(chunks);
    }

    /** Only the actual other half is admitted; a barrel on a claim edge must not load foreign neighbours. */
    public MiningChunkPlan withStoragePartner(int x, int z) {
        var copy = new LinkedHashMap<>(chunks);
        copy.put(key(x >> 4, z >> 4), true);
        return new MiningChunkPlan(copy);
    }
}
