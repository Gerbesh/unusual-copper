package io.github.gerbesh.unusualcopper.machine;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Only upgrades and the paid cooldown persist; locks/targets are revalidated after loading. */
public final class SentinelState {
    public long nextShot, nextScan;
    public UUID target;
    public boolean focused, active;
    public CompoundTag save() { var t=new CompoundTag(); t.putLong("NextShot",nextShot); t.putBoolean("Focused",focused); return t; }
    public void load(CompoundTag t) { nextShot=Math.max(0,t.getLong("NextShot")); focused=t.getBoolean("Focused"); nextScan=0; target=null; active=false; }
}
