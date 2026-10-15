package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Explicit vanilla adapters: logs/wood tags alone never authorize forestry. */
public enum TreeSpecies {
    OAK(Blocks.OAK_LOG,Blocks.OAK_LEAVES,Blocks.OAK_SAPLING,false,false),
    BIRCH(Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES,Blocks.BIRCH_SAPLING,false,false),
    SPRUCE(Blocks.SPRUCE_LOG,Blocks.SPRUCE_LEAVES,Blocks.SPRUCE_SAPLING,true,false),
    JUNGLE(Blocks.JUNGLE_LOG,Blocks.JUNGLE_LEAVES,Blocks.JUNGLE_SAPLING,true,false),
    ACACIA(Blocks.ACACIA_LOG,Blocks.ACACIA_LEAVES,Blocks.ACACIA_SAPLING,false,false),
    DARK_OAK(Blocks.DARK_OAK_LOG,Blocks.DARK_OAK_LEAVES,Blocks.DARK_OAK_SAPLING,true,true),
    CHERRY(Blocks.CHERRY_LOG,Blocks.CHERRY_LEAVES,Blocks.CHERRY_SAPLING,false,false);
    public final Block log,leaves,sapling; public final boolean large,requiresFour;
    TreeSpecies(Block log,Block leaves,Block sapling,boolean large,boolean requiresFour) { this.log=log; this.leaves=leaves; this.sapling=sapling; this.large=large; this.requiresFour=requiresFour; }
    public static TreeSpecies at(BlockState state) { return Arrays.stream(values()).filter(s -> state.is(s.log)||state.is(s.sapling)).findFirst().orElse(null); }
    public static TreeSpecies item(ItemStack stack) { return Arrays.stream(values()).filter(s -> stack.is(s.sapling.asItem())).findFirst().orElse(null); }
    public static boolean axe(ItemStack stack) {
        boolean vanilla=stack.is(Items.WOODEN_AXE)||stack.is(Items.STONE_AXE)||stack.is(Items.IRON_AXE)||stack.is(Items.GOLDEN_AXE)||stack.is(Items.DIAMOND_AXE)||stack.is(Items.NETHERITE_AXE);
        return vanilla && net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack).keySet().stream().allMatch(enchantment -> {
            var id=net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(enchantment); return id!=null && id.getNamespace().equals("minecraft");
        });
    }
}
