package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.gameplay.ProtectionPolicy;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Ordinary dedicated server supplies the profile cache required by OpenPAC's lifecycle. */
@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID)
public final class OpenPacServerSmoke {
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("unusualcopper.opacSmoke")) return;
        var server = event.getServer(); var level = server.overworld(); var pos = new BlockPos(1024, 70, 512);
        var owner = UUID.fromString("7f9d4e0e-73b9-4d0c-89ae-e50a232fa21e"); var stranger = UUID.fromString("17b844a5-3ef8-4b52-a376-cdc1d76cd392");
        server.getProfileCache().add(new com.mojang.authlib.GameProfile(owner, "CuEOwner")); server.getProfileCache().add(new com.mojang.authlib.GameProfile(stranger, "CuEGuest"));
        // Explicit setup in the isolated fixture world. Runtime automation never loads chunks.
        level.getChunkAt(pos); level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity)level.getBlockEntity(pos); barrel.setItem(0, new ItemStack(Items.COPPER_INGOT, 8));
        Object manager = null;
        try {
            var apiType = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var managerType = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            var api = apiType.getMethod("get", MinecraftServer.class).invoke(null, server);
            manager = apiType.getMethod("getServerClaimsManager").invoke(api);
            managerType.getMethod("claim", ResourceLocation.class, UUID.class, int.class, int.class, int.class, boolean.class)
                    .invoke(manager, level.dimension().location(), owner, -1, pos.getX() >> 4, pos.getZ() >> 4, false);
            check(ProtectionPolicy.allowed(level, pos, owner), "Actual claim did not allow its owner");
            check(!ProtectionPolicy.allowed(level, pos, stranger) && !ProtectionPolicy.allowed(level, pos, null), "Actual claim allowed a foreign or ownerless actor");
            var denied = new HandlerStorageEndpoint(level, pos, Direction.UP, stranger); var key = ItemKey.of(new ItemStack(Items.COPPER_INGOT));
            check(!denied.isAvailable() && denied.simulateExtract(key, 8) == 0 && denied.extract(key, 8).isEmpty() && barrel.getItem(0).getCount() == 8, "Denied endpoint modified inventory");
            var allowed = new HandlerStorageEndpoint(level, pos, Direction.UP, owner);
            check(allowed.extract(key, 8).stream().mapToInt(ItemStack::getCount).sum() == 8 && barrel.getItem(0).isEmpty(), "Owner endpoint could not perform the real extraction");
            UnusualCopper.LOGGER.info("CuE OPENPAC CLAIM PASS: actual 0.30.1 owner allowed, foreign and null-owner inventory automation denied");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("OpenPAC fixture API failed", error); }
        finally {
            if (manager != null) try { Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI").getMethod("unclaim", ResourceLocation.class, int.class, int.class).invoke(manager, level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not clear fixture claim", error); }
        }
        server.halt(false);
    }
}
