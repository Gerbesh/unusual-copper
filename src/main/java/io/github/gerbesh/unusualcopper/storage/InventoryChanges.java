package io.github.gerbesh.unusualcopper.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.ref.WeakReference;
import java.util.*;

/** Dirty notifications for watched inventories only; no inventory scan runs from the hook. */
public final class InventoryChanges {
    private static final int LIMIT=16384;
    private static final Map<ServerLevel,LinkedHashMap<BlockPos,Token>> WORLDS=new WeakHashMap<>();
    public static final class Token {
        private final WeakReference<BlockEntity> entity;
        private long revision;
        private boolean valid=true;
        private Token(BlockEntity entity){this.entity=new WeakReference<>(entity);}
        public long revision(){return revision;}
        public boolean valid(){return valid&&entity.get()!=null;}
    }
    public static Token watch(ServerLevel level,BlockEntity entity){
        var tokens=WORLDS.computeIfAbsent(level,ignored->new LinkedHashMap<>(16,.75F,true));var pos=entity.getBlockPos();var token=tokens.get(pos);
        if(token!=null&&token.entity.get()==entity&&token.valid)return token;
        if(token!=null)token.valid=false;
        if(tokens.size()>=LIMIT){var oldest=tokens.entrySet().iterator();var entry=oldest.next();entry.getValue().valid=false;oldest.remove();}
        token=new Token(entity);tokens.put(pos.immutable(),token);return token;
    }
    public static void changed(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server))return;var tokens=WORLDS.get(server);if(tokens==null)return;var token=tokens.get(pos);if(token!=null)token.revision++;
    }
    private InventoryChanges(){}
}
