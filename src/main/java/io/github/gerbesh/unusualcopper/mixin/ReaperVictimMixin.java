package io.github.gerbesh.unusualcopper.mixin;

import io.github.gerbesh.unusualcopper.gameplay.ReaperVictim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public abstract class ReaperVictimMixin implements ReaperVictim {
    // Accessors resolve the pinned official/SRG refmap at runtime in both environments.
    @Accessor("lastHurtByPlayer") public abstract Player unusualcopper$player();
    @Accessor("lastHurtByPlayer") public abstract void unusualcopper$player(Player value);
    @Accessor("lastHurtByPlayerTime") public abstract int unusualcopper$playerTime();
    @Accessor("lastHurtByPlayerTime") public abstract void unusualcopper$playerTime(int value);
    @Accessor("lastHurtByMob") public abstract LivingEntity unusualcopper$mob();
    @Accessor("lastHurtByMob") public abstract void unusualcopper$mob(LivingEntity value);
    @Accessor("lastHurtByMobTimestamp") public abstract int unusualcopper$mobTime();
    @Accessor("lastHurtByMobTimestamp") public abstract void unusualcopper$mobTime(int value);
    @Accessor("dead") public abstract boolean unusualcopper$dead();
    @Override public Credit unusualcopper$clearCredit() {
        var saved = new Credit(unusualcopper$player(), unusualcopper$playerTime(), unusualcopper$mob(), unusualcopper$mobTime());
        unusualcopper$player(null); unusualcopper$playerTime(0); unusualcopper$mob(null); unusualcopper$mobTime(0); return saved;
    }
    @Override public void unusualcopper$restoreCredit(Credit saved) { unusualcopper$player(saved.player()); unusualcopper$playerTime(saved.playerTicks()); unusualcopper$mob(saved.mob()); unusualcopper$mobTime(saved.mobTick()); }
    @Override public boolean unusualcopper$deathCommitted() { return unusualcopper$dead(); }
}
