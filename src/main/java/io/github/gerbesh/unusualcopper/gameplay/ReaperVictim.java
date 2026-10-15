package io.github.gerbesh.unusualcopper.gameplay;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Transaction access implemented by the pinned LivingEntity mixin. */
public interface ReaperVictim {
    record Credit(Player player, int playerTicks, LivingEntity mob, int mobTick) {}
    Credit unusualcopper$clearCredit();
    void unusualcopper$restoreCredit(Credit credit);
    boolean unusualcopper$deathCommitted();
}
