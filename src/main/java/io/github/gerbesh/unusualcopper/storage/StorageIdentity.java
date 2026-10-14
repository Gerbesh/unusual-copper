package io.github.gerbesh.unusualcopper.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record StorageIdentity(ResourceKey<Level> dimension, BlockPos pos, Direction side) {
    public StorageIdentity { pos = pos.immutable(); }
    public boolean sameInventory(StorageIdentity other) { return dimension.equals(other.dimension) && pos.equals(other.pos); }
}
