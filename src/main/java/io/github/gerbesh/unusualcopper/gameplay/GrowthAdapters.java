package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit single-position adapters. Unknown overrides and multi-block generation are not executed. */
public final class GrowthAdapters {
    public enum Result { READY, MATURE, CONDITIONS, UNSUPPORTED }
    private GrowthAdapters() {}
    public static Result inspect(ServerLevel level, BlockPos pos, BlockState state) {
        var block = state.getBlock(); var type = block.getClass();
        boolean crop = type == CropBlock.class || type == CarrotBlock.class || type == PotatoBlock.class || type == BeetrootBlock.class;
        if (crop) {
            var plant = (CropBlock)block; if (plant.isMaxAge(state)) return Result.MATURE;
            var soil = level.getBlockState(pos.below());
            if (!soil.is(Blocks.FARMLAND) || soil.getValue(FarmBlock.MOISTURE) == 0 || level.getRawBrightness(pos, 0) < 9 || !state.canSurvive(level, pos)) return Result.CONDITIONS;
            return Result.READY;
        }
        if (type == StemBlock.class) {
            if (state.getValue(StemBlock.AGE) >= 7) return Result.MATURE;
            var soil = level.getBlockState(pos.below());
            return soil.is(Blocks.FARMLAND) && soil.getValue(FarmBlock.MOISTURE) > 0 && level.getRawBrightness(pos, 0) >= 9 && state.canSurvive(level, pos) ? Result.READY : Result.CONDITIONS;
        }
        if (type == SweetBerryBushBlock.class) {
            if (state.getValue(SweetBerryBushBlock.AGE) >= 3) return Result.MATURE;
            return level.getRawBrightness(pos, 0) >= 9 && state.canSurvive(level, pos) ? Result.READY : Result.CONDITIONS;
        }
        if (type == CaveVinesBlock.class || type == CaveVinesPlantBlock.class)
            return state.getValue(CaveVines.BERRIES) ? Result.MATURE : state.canSurvive(level, pos) ? Result.READY : Result.CONDITIONS;
        return Result.UNSUPPORTED;
    }
    public static boolean grow(ServerLevel level, BlockPos pos, BlockState before) {
        if (before.getBlock().getClass() == StemBlock.class) level.setBlock(pos, before.setValue(StemBlock.AGE, before.getValue(StemBlock.AGE) + 1), 3);
        else ((BonemealableBlock)before.getBlock()).performBonemeal(level, level.random, pos, before);
        return !level.getBlockState(pos).equals(before);
    }
}
