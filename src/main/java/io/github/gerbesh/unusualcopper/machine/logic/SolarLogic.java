package io.github.gerbesh.unusualcopper.machine.logic;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import io.github.gerbesh.unusualcopper.config.ServerConfig;

public final class SolarLogic implements MachineLogic {
    @Override public void tick(CopperMachineBlockEntity m, ServerLevel level) {
        ItemStack input = m.inventory.getStackInSlot(0);
        ItemStack lens = m.inventory.getStackInSlot(1);
        if (input.isEmpty()) { m.status(MachineStatus.IDLE); return; }
        if (!lens.is(ModContent.SOLAR_LENS.get()) || lens.getDamageValue() >= lens.getMaxDamage()) { m.status(MachineStatus.NO_LENS); return; }
        var recipe = level.getRecipeManager().getRecipeFor(ModContent.SOLAR_RECIPE.get(), new SimpleContainer(input), level);
        if (recipe.isEmpty()) { m.status(m.hasOperation() ? MachineStatus.RECIPE_CHANGED : MachineStatus.INVALID_INPUT); return; }
        if (m.hasOperation() && (!m.recipeId.equals(recipe.get().getId().toString()) || !m.inputMatches() || m.result.getCount() != m.source.getCount() * recipe.get().multiplier())) {
            m.status(MachineStatus.RECIPE_CHANGED); return;
        }
        if (!m.hasOperation()) {
            int count = Math.min(input.getCount(), lens.getMaxDamage() - lens.getDamageValue());
            ItemStack result = new ItemStack(ModContent.ESSENCE.get(), count * recipe.get().multiplier());
            if (!m.canFitOutput(result)) { m.status(MachineStatus.OUTPUT_FULL); return; }
            int work = (int)(((long)count * ServerConfig.solarTicks() * 4 + 63) / 64);
            m.begin(recipe.get().getId().toString(), input.copyWithCount(count), result, work);
        }
        if (!m.canFitOutput(m.result)) { m.status(MachineStatus.OUTPUT_FULL); return; }
        long solarTime = level.dimensionType().fixedTime().orElse(level.getDayTime());
        // A transparent roof still obstructs open sky. This is independent of
        // cached skylight propagation and cannot be faked with a light source.
        boolean openSky = level.getHeight(Heightmap.Types.MOTION_BLOCKING, m.getBlockPos().getX(), m.getBlockPos().getZ()) <= m.getBlockPos().getY() + 1;
        int speed = SolarPolicy.rate(solarTime, level.dimensionType().hasSkyLight(), openSky, level.getLevelData().isRaining(), level.getLevelData().isThundering());
        if (speed == 0) { m.status(MachineStatus.NO_SUN); return; }
        m.progress = Math.min(m.duration, m.progress + speed);
        m.status(MachineStatus.WORKING);
        if (m.progress >= m.duration && m.inputMatches()) {
            int used = m.source.getCount();
            if (m.finish()) {
                lens.setDamageValue(lens.getDamageValue() + used);
                if (lens.getDamageValue() >= lens.getMaxDamage()) m.inventory.setStackInSlot(1, ItemStack.EMPTY);
                m.setChanged();
            }
        }
        m.setChanged();
    }
}
