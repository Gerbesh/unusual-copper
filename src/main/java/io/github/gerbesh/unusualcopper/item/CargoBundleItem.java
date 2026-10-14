package io.github.gerbesh.unusualcopper.item;

import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Recovery item keeps buffered resources safe when their pipe is broken. */
public final class CargoBundleItem extends Item {
    public CargoBundleItem() { super(new Properties().stacksTo(1)); }
    public static ItemStack pack(List<ItemStack> cargo) {
        var bundle = new ItemStack(io.github.gerbesh.unusualcopper.registry.ModContent.CARGO_BUNDLE.get());
        var contents = new ListTag();
        for (var stack : cargo) if (!stack.isEmpty()) {
            var entry = new CompoundTag(); entry.put("Stack", stack.copyWithCount(1).save(new CompoundTag())); entry.putInt("Count", stack.getCount()); contents.add(entry);
        }
        bundle.getOrCreateTag().put("Cargo", contents); return bundle;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var bundle = player.getItemInHand(hand);
        if (!level.isClientSide) {
            var contents = bundle.getOrCreateTag().getList("Cargo", Tag.TAG_COMPOUND); var remaining = new ListTag();
            for (int i = 0; i < contents.size(); i++) {
                var entry = contents.getCompound(i); var stored = ItemStack.of(entry.getCompound("Stack")); int count = Math.max(0, entry.getInt("Count"));
                if (stored.isEmpty() || count == 0) continue;
                if (i < 16) {
                    var offered = stored.copyWithCount(Math.min(count, stored.getMaxStackSize())); int original = offered.getCount();
                    player.getInventory().add(offered); count -= original - offered.getCount();
                }
                if (count > 0) { var copy = entry.copy(); copy.putInt("Count", count); remaining.add(copy); }
            }
            if (remaining.isEmpty()) bundle.shrink(1); else bundle.getOrCreateTag().put("Cargo", remaining);
        }
        return InteractionResultHolder.sidedSuccess(bundle, level.isClientSide);
    }
}
