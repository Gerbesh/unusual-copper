package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Inclusive, bounded selection; dimension and loaded-chunk permissions are validated by its user. */
public record WorkArea(BlockPos min, BlockPos max) {
    public WorkArea {
        min = min.immutable(); max = max.immutable();
        long x = (long)max.getX() - min.getX() + 1, y = (long)max.getY() - min.getY() + 1, z = (long)max.getZ() - min.getZ() + 1;
        if (x < 1 || y < 1 || z < 1 || x > 16 || y > 16 || z > 16 || x * y * z > 4096) throw new IllegalArgumentException("Area exceeds 16x16x16");
    }
    public static WorkArea between(BlockPos a, BlockPos b) {
        return new WorkArea(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }
    public int volume() { return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1); }
    public BlockPos at(int index) {
        int x = max.getX() - min.getX() + 1, z = max.getZ() - min.getZ() + 1; int offset = Math.floorMod(index, volume());
        return min.offset(offset % x, offset / (x * z), (offset / x) % z);
    }
    public boolean contains(BlockPos pos) { return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ(); }
    public CompoundTag save() { var tag = new CompoundTag(); tag.put("Min", NbtUtils.writeBlockPos(min)); tag.put("Max", NbtUtils.writeBlockPos(max)); return tag; }
    public static WorkArea load(CompoundTag tag) { return new WorkArea(NbtUtils.readBlockPos(tag.getCompound("Min")), NbtUtils.readBlockPos(tag.getCompound("Max"))); }
}
