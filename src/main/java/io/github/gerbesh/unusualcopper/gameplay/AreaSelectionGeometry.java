package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.core.BlockPos;

/** Local geometry only. The second server-side click still checks ownership and permissions. */
public final class AreaSelectionGeometry {
    public enum Problem { NONE, TOO_LARGE, TOO_FAR }
    public record Candidate(BlockPos min,BlockPos max,long width,long height,long depth,boolean mining,Problem problem) {
        public boolean valid(){return problem==Problem.NONE;}
    }
    private AreaSelectionGeometry() {}
    public static Candidate between(BlockPos anchor,BlockPos first,BlockPos second,boolean mining) {
        var min=new BlockPos(Math.min(first.getX(),second.getX()),mining?anchor.getY():Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));
        var max=new BlockPos(Math.max(first.getX(),second.getX()),mining?anchor.getY():Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));
        long w=(long)max.getX()-min.getX()+1,h=(long)max.getY()-min.getY()+1,d=(long)max.getZ()-min.getZ()+1;
        boolean reach=mining?MiningFootprint.reachable(anchor.getX(),anchor.getZ(),first.getX(),first.getZ())
                &&MiningFootprint.reachable(anchor.getX(),anchor.getZ(),second.getX(),second.getZ())
                :anchor.distSqr(first)<=1024&&anchor.distSqr(second)<=1024;
        int limit=mining?MiningFootprint.MAX_SIDE:16;
        Problem problem=w>limit||d>limit||(!mining&&h>16)?Problem.TOO_LARGE:!reach?Problem.TOO_FAR:Problem.NONE;
        return new Candidate(min,max,w,h,d,mining,problem);
    }
}
