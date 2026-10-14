package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

public final class AreaAccess {
    private AreaAccess() {}
    public static boolean geometryValid(ServerLevel level, BlockPos machine, WorkArea area) {
        if (area.min().getY() < level.getMinBuildHeight() || area.max().getY() >= level.getMaxBuildHeight()) return false;
        double dx = Math.max(Math.abs((double)machine.getX() - area.min().getX()), Math.abs((double)machine.getX() - area.max().getX()));
        double dy = Math.max(Math.abs((double)machine.getY() - area.min().getY()), Math.abs((double)machine.getY() - area.max().getY()));
        double dz = Math.max(Math.abs((double)machine.getZ() - area.min().getZ()), Math.abs((double)machine.getZ() - area.max().getZ()));
        if (dx * dx + dy * dy + dz * dz > 32 * 32) return false;
        if (!level.getWorldBorder().isWithinBounds(area.min()) || !level.getWorldBorder().isWithinBounds(area.max())) return false;
        return true;
    }
    public static boolean valid(ServerLevel level, BlockPos machine, WorkArea area, UUID owner) {
        if (!geometryValid(level, machine, area) || !level.hasChunkAt(machine) || !ProtectionPolicy.allowed(level, machine, owner)) return false;
        for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4; x++) for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) {
            var pos = new BlockPos(x << 4, area.min().getY(), z << 4);
            if (!level.hasChunkAt(pos) || !ProtectionPolicy.allowed(level, pos, owner)) return false;
        }
        return true;
    }
}
