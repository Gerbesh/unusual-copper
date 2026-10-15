package io.github.gerbesh.unusualcopper.client;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.network.MachineEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.ArrayDeque;

@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, value = Dist.CLIENT)
public final class ShepherdEffects {
    private static final ArrayDeque<ActiveWave> WAVES = new ArrayDeque<>();
    private static final DustParticleOptions AQUA = new DustParticleOptions(new Vector3f(.25F, 1F, .8F), .85F);
    private static final class ActiveWave {
        final MachineEffects.Wave wave; final long start; int spark;
        ActiveWave(MachineEffects.Wave wave, long start) { this.wave = wave; this.start = start; }
    }
    public static void accept(MachineEffects.Wave wave) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(wave.dimension())) return;
        while (WAVES.size() >= 8) WAVES.removeFirst(); WAVES.addLast(new ActiveWave(wave, level.getGameTime()));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level; if (level == null) { WAVES.clear(); return; }
        int budget = 64;
        var iterator = WAVES.iterator();
        while (iterator.hasNext()) {
            var active = iterator.next(); long age = level.getGameTime() - active.start;
            if (age < 0 || age >= 20 || !level.dimension().location().equals(active.wave.dimension())) { iterator.remove(); continue; }
            var pos = active.wave.origin(); double radius = active.wave.radius() * (age + 1) / 20.0;
            for (int point = 0; point < 8 && budget > 0; point++, budget--) {
                double angle = 2 * Math.PI * (point / 8.0 + (age % 4) / 32.0);
                level.addParticle(AQUA, pos.getX() + .5 + Math.cos(angle) * radius, pos.getY() + .12, pos.getZ() + .5 + Math.sin(angle) * radius, 0, .015, 0);
            }
            while (active.spark < active.wave.entities().length && budget > 0) {
                var entity = level.getEntity(active.wave.entities()[active.spark++]);
                if (entity instanceof Mob mob && mob.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 33 * 33) {
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() * .8, mob.getZ(), 0, .04, 0); budget--;
                }
            }
        }
    }
}
