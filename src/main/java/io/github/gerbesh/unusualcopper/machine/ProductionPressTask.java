package io.github.gerbesh.unusualcopper.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Persistent ownership of exactly one real E2 press batch. No virtual output or CuE. */
public record ProductionPressTask(UUID token, BlockPos press, int stage) {
    public static final int PLATE=1, WIRE=2;
    public ProductionPressTask {
        if(token==null||press==null||(stage!=PLATE&&stage!=WIRE))
            throw new IllegalArgumentException("Unsupported press batch");
        press=press.immutable();
    }
    public CompoundTag save() {
        var tag=new CompoundTag();
        tag.putUUID("Token",token);
        tag.putLong("Press",press.asLong());
        tag.putInt("Stage",stage);
        return tag;
    }
    public static ProductionPressTask load(CompoundTag tag) {
        if(!tag.hasUUID("Token")||!tag.contains("Press")||!tag.contains("Stage"))return null;
        try{return new ProductionPressTask(tag.getUUID("Token"),BlockPos.of(tag.getLong("Press")),tag.getInt("Stage"));}
        catch(IllegalArgumentException ex){return null;}
    }
}
