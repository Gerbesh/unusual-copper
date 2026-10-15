package io.github.gerbesh.unusualcopper.energy;

import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

public final class EnergyPipeBlockEntity extends BlockEntity {
    // Appearance only: never persisted, never exposed as stored CuE or a capability.
    private int fill, incoming, outgoing, pendingIn, pendingOut;
    private float previousFill;
    private long visualChanged;
    public EnergyPipeBlockEntity(BlockPos pos, BlockState state) { super(ModContent.PIPE_ENTITY.get(), pos, state); }
    public void transferred(Direction entry, Direction exit) { pendingIn |= 1 << entry.ordinal(); pendingOut |= 1 << exit.ordinal(); }
    public void clearAppearance() { pendingIn=pendingOut=0;sampleAppearance(0); }
    public void sampleAppearance(int charge) {
        int nextFill = pendingOut != 0 ? 15 : charge;
        if (fill != nextFill || incoming != pendingIn || outgoing != pendingOut) {
            fill = nextFill; incoming = pendingIn; outgoing = pendingOut;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        pendingIn = pendingOut = 0;
    }
    public float visibleFill(float partial) {
        float blend = Math.min(1, (level.getGameTime() - visualChanged + partial) / 8F);
        return previousFill + (fill / 15F - previousFill) * blend;
    }
    public boolean flowing() { return outgoing != 0; }
    public boolean incoming(Direction side) { return (incoming & 1 << side.ordinal()) != 0; }
    public boolean outgoing(Direction side) { return (outgoing & 1 << side.ordinal()) != 0; }
    public int visualCharge() { return fill; }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag(); tag.putInt("Fill", fill); tag.putInt("In", incoming); tag.putInt("Out", outgoing); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        previousFill = level == null ? 0 : visibleFill(0);
        fill = Math.max(0, Math.min(15, tag.getInt("Fill"))); incoming = tag.getInt("In") & 63; outgoing = tag.getInt("Out") & 63;
        visualChanged = level == null ? 0 : level.getGameTime();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { if (packet.getTag() != null) handleUpdateTag(packet.getTag()); }
    @Override public void onLoad() { super.onLoad(); EnergyNetworks.add(level, worldPosition); }
    @Override public void onChunkUnloaded() { EnergyNetworks.remove(level, worldPosition); super.onChunkUnloaded(); }
    @Override public void setRemoved() { EnergyNetworks.remove(level, worldPosition); super.setRemoved(); }
}
