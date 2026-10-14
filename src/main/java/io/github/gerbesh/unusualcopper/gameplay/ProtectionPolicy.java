package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional providers are linked lazily. Failure never grants access. */
public final class ProtectionPolicy {
    private static boolean initialized, unknownProvider, failed;
    private static Method getApi, claims, claimAt, protection, chunkAccess;
    private ProtectionPolicy() {}
    private static void initialize() {
        if (initialized) return;
        initialized = true;
        var mods = ModList.get();
        unknownProvider = java.util.stream.Stream.of("ftbchunks", "flan", "griefdefender", "minecolonies").anyMatch(mods::isLoaded);
        if (!mods.isLoaded("openpartiesandclaims")) return;
        try {
            var api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var manager = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            var policy = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            getApi = api.getMethod("get", MinecraftServer.class);
            claims = api.getMethod("getServerClaimsManager");
            claimAt = manager.getMethod("get", ResourceLocation.class, int.class, int.class);
            protection = api.getMethod("getChunkProtection");
            chunkAccess = policy.getMethod("hasChunkAccess", UUID.class, ResourceLocation.class, int.class, int.class);
        } catch (ReflectiveOperationException error) {
            failed = true; UnusualCopper.LOGGER.error("Cannot initialize Open Parties and Claims adapter; automation is denied", error);
        }
    }
    public static boolean allowed(ServerLevel level, BlockPos pos, UUID owner) {
        if (!level.hasChunkAt(pos)) return false;
        initialize();
        if (unknownProvider || failed) return false;
        if (getApi == null) return true;
        try {
            var api = getApi.invoke(null, level.getServer());
            int x = pos.getX() >> 4, z = pos.getZ() >> 4;
            var claim = claimAt.invoke(claims.invoke(api), level.dimension().location(), x, z);
            if (claim == null) return true;
            return owner != null && (boolean)chunkAccess.invoke(protection.invoke(api), owner, level.dimension().location(), x, z);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!failed) UnusualCopper.LOGGER.error("Claims API failed; automation is denied", error);
            failed = true; return false;
        }
    }
}
