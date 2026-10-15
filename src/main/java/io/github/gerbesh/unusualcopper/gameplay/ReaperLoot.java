package io.github.gerbesh.unusualcopper.gameplay;

import io.github.gerbesh.unusualcopper.machine.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Only genuine drops from this victim/source action, after their world spawn is accepted. */
public final class ReaperLoot {
    private static final Deque<Action> ACTIVE = new ArrayDeque<>();
    private static final class Action {
        final UUID id = UUID.randomUUID(), machineId;
        final WeakReference<CopperMachineBlockEntity> machine;
        final LivingEntity victim; final DamageSource source;
        WeakReference<Collection<ItemEntity>> drops;
        final Set<ItemEntity> known = Collections.newSetFromMap(new WeakHashMap<>());
        boolean deathSource;
        int captures;
        Action(CopperMachineBlockEntity machine, LivingEntity victim, DamageSource source) { this.machine = new WeakReference<>(machine); machineId = machine.runtimeIdentity; this.victim = victim; this.source = source; }
    }
    private ReaperLoot() {}
    public static boolean kill(CopperMachineBlockEntity machine, Mob victim, ServerLevel level, int price) {
        if (!machine.energy.spend(price)) return false;
        var access = (ReaperVictim)victim; var credit = access.unusualcopper$clearCredit(); float health = victim.getHealth();
        var source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MAGIC));
        var action = new Action(machine, victim, source); ACTIVE.push(action); boolean killed = false;
        try {
            victim.hurt(source, 1_000_000F);
            killed = access.unusualcopper$deathCommitted() && action.deathSource;
            return killed;
        } finally {
            ACTIVE.remove(action);
            // A downstream loot listener can throw after die() has already committed.
            killed = access.unusualcopper$deathCommitted() && action.deathSource;
            if (!killed) {
                if (!victim.isRemoved() && !access.unusualcopper$deathCommitted()) victim.setHealth(health);
                access.unusualcopper$restoreCredit(credit); machine.energy.add(price, false);
            }
            machine.setChanged();
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) {
        for (var action : ACTIVE) if (action.victim == event.getEntity()) { action.deathSource = event.getSource() == action.source; break; }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void drops(LivingDropsEvent event) {
        for (var action : ACTIVE) if (action.victim == event.getEntity() && event.getSource() == action.source) { action.drops = new WeakReference<>(event.getDrops()); break; }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void xp(LivingExperienceDropEvent event) {
        for (var action : ACTIVE) if (action.victim == event.getEntity() && action.deathSource && ((ReaperVictim)action.victim).unusualcopper$deathCommitted()) { event.setDroppedExperience(0); break; }
    }
    public static void accepted(Entity entity) {
        if (!(entity instanceof ItemEntity item) || entity.getClass() != ItemEntity.class || !(entity.level() instanceof ServerLevel level) || item.getItem().isEmpty()) return;
        for (var action : ACTIVE) {
            if (!action.deathSource || action.drops == null || action.captures >= 128 || !((ReaperVictim)action.victim).unusualcopper$deathCommitted()) continue;
            var drops = action.drops.get(); if (drops == null) continue;
            if (!action.known.contains(item)) { for (var drop : drops) { if (action.known.size() >= 256) break; if (drop.getClass() == ItemEntity.class) action.known.add(drop); } }
            if (!action.known.contains(item)) continue;
            var machine = action.machine.get();
            if (machine == null || machine.isRemoved() || machine.reaper == null || !machine.runtimeIdentity.equals(action.machineId) || !level.hasChunkAt(machine.getBlockPos()) || level.getBlockEntity(machine.getBlockPos()) != machine || !ProtectionPolicy.allowed(level, machine.getBlockPos(), machine.owner())) return;
            action.captures++;
            var remaining = machine.reaper.hold(machine.insertOutput(item.getItem()));
            if (!remaining.isEmpty() && remaining.getCount() > remaining.getMaxStackSize()) item.setItem(io.github.gerbesh.unusualcopper.item.CargoBundleItem.pack(List.of(remaining)));
            else item.setItem(remaining);
            if (remaining.isEmpty()) item.discard(); machine.setChanged(); return;
        }
    }
}
