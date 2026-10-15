package io.github.gerbesh.unusualcopper.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** All machine and pipe ports select their server-side storage adapter here. */
public final class StorageEndpoints {
    public static StorageEndpoint at(ServerLevel level,BlockPos pos,Direction side,UUID owner){
        return at(level,pos,side,owner,null);
    }
    public static StorageEndpoint at(ServerLevel level,BlockPos pos,Direction side,UUID owner,BlockPos identityHint){return new RoutedStorageEndpoint(level,pos,side,owner,identityHint);}
    private StorageEndpoints(){}
}
