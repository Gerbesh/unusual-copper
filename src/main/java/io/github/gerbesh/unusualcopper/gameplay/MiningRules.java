package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.*;

public final class MiningRules {
    public static final TagKey<Block> EXTRA=tag("mineable_ores"),EXCLUDED=tag("mining_excluded");
    private static TagKey<Block> tag(String id){return TagKey.create(Registries.BLOCK,new ResourceLocation("unusualcopper",id));}
    public static boolean pickaxe(ItemStack tool){return !tool.isEmpty()&&tool.getCount()==1&&tool.canPerformAction(ToolActions.PICKAXE_DIG);}
    public static boolean durable(ItemStack tool){return pickaxe(tool)&&(!tool.isDamageableItem()||tool.getMaxDamage()-tool.getDamageValue()>1);}
    public static boolean ore(BlockState state){return !state.isAir()&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()&&!state.is(EXCLUDED)&&(state.is(Tags.Blocks.ORES)||state.is(EXTRA)||state.getTags().anyMatch(t->{var p=t.location().getPath();return p.equals("ores")||p.startsWith("ores/")||p.equals("ore")||p.startsWith("ore/");}));}
    private static boolean host(BlockState state,String name){return state.is(tag("mining_hosts/"+name))||state.getTags().anyMatch(t->t.location().getPath().equals("ores_in_ground/"+name));}
    public static BlockState replacement(ServerLevel level,BlockPos pos,BlockState ore){
        if(host(ore,"deepslate"))return Blocks.DEEPSLATE.defaultBlockState();
        if(host(ore,"netherrack"))return Blocks.NETHERRACK.defaultBlockState();
        if(host(ore,"end_stone"))return Blocks.END_STONE.defaultBlockState();
        if(host(ore,"stone"))return Blocks.STONE.defaultBlockState();
        if(level.dimension()==Level.NETHER)return Blocks.NETHERRACK.defaultBlockState();
        if(level.dimension()==Level.END)return Blocks.END_STONE.defaultBlockState();
        return (pos.getY()<0?Blocks.DEEPSLATE:Blocks.STONE).defaultBlockState();
    }
    private MiningRules(){}
}
