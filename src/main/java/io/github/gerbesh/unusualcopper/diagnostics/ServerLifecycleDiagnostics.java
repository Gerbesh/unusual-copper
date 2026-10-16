package io.github.gerbesh.unusualcopper.diagnostics;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Event-only diagnostics: no ticking, retained player/server references or shutdown interception. */
public final class ServerLifecycleDiagnostics {
    private record Transition(long timeNanos, String from, String to) {}
    private static volatile Transition lastTransition;

    private ServerLifecycleDiagnostics() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer()) lastTransition = null;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (event.getServer().isDedicatedServer()) lastTransition = null;
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getServer() == null || !player.getServer().isDedicatedServer()) return;
        Transition transition = new Transition(System.nanoTime(), event.getFrom().location().toString(),
                event.getTo().location().toString());
        lastTransition = transition;
        UnusualCopper.LOGGER.info("[CuE lifecycle] Dimension transfer: {} -> {}",
                transition.from(), transition.to());
    }

    public static void shutdown(MinecraftServer server, String entryPoint) {
        if (!server.isDedicatedServer()) return;
        Transition transition = lastTransition;
        String recent = transition == null ? "none recorded this session"
                : transition.from() + " -> " + transition.to() + ", "
                + ((System.nanoTime() - transition.timeNanos()) / 1_000_000L) + " ms ago";
        UnusualCopper.LOGGER.warn("[CuE lifecycle] Shutdown entry: " + entryPoint
                + "; thread=" + Thread.currentThread().getName() + "; last dimension transfer=" + recent
                + ". Diagnostic caller trace follows; this also appears on normal shutdown.",
                new Throwable("Shutdown caller trace (diagnostic, not a crash)"));
    }
}
