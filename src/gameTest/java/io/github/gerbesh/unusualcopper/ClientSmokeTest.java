package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.machine.CopperMachineBlock;
import io.github.gerbesh.unusualcopper.machine.CopperMachineBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.network.NetworkHooks;
import io.github.gerbesh.unusualcopper.client.CopperMachineScreen;

/** Development-only integration scene; this class is absent from the release jar. */
@Mod.EventBusSubscriber(modid = UnusualCopper.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static boolean requested;
    private static boolean captured;
    private static final AtomicBoolean ready = new AtomicBoolean();
    private static final AtomicBoolean saved = new AtomicBoolean();
    private static int ticks;
    private static int stage;
    private static BlockPos menuPos, pipeMenuPos;
    private static boolean settingsSent;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("unusualcopper.smokeClient")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (!requested) {
            requested = true;
            if (Boolean.getBoolean("unusualcopper.smokeClientRu")) {
                mc.options.languageCode = "ru_ru"; mc.getLanguageManager().setSelected("ru_ru"); mc.reloadResourcePacks();
            }
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 40, 40);
                level.setDayTime(6000); level.setWeatherParameters(0, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                for (int x = 39; x <= 53; x++) for (int z = 34; z <= 47; z++) level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                MachineKind[] kinds = {MachineKind.SOLAR_INFUSER, MachineKind.ESSENCE_SPLITTER, MachineKind.RESONANCE_SMELTER, MachineKind.COPPER_CELL};
                int[] positions = {41, 45, 48, 51};
                menuPos = new BlockPos(51, y, 42);
                for (int i = 0; i < kinds.length; i++) {
                    BlockPos pos = new BlockPos(positions[i], y, 42);
                    if (level.getBlockEntity(pos) instanceof CopperMachineBlockEntity previous)
                        for (int slot = 0; slot < 4; slot++) previous.inventory.setStackInSlot(slot, ItemStack.EMPTY);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos, ModContent.MACHINES.get(kinds[i]).get().defaultBlockState(), 3);
                    var machine = (CopperMachineBlockEntity)level.getBlockEntity(pos);
                    if (kinds[i] == MachineKind.SOLAR_INFUSER) {
                        machine.inventory.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 64)); machine.inventory.setStackInSlot(1, new ItemStack(ModContent.SOLAR_LENS.get()));
                    } else if (kinds[i] == MachineKind.ESSENCE_SPLITTER) machine.inventory.setStackInSlot(0, new ItemStack(ModContent.ESSENCE.get(), 64));
                    else if (kinds[i] == MachineKind.RESONANCE_SMELTER) { machine.inventory.setStackInSlot(0, new ItemStack(Items.RAW_GOLD, 64)); machine.energy.add(24_000, false); }
                    else if (kinds[i] == MachineKind.COPPER_CELL) machine.energy.add(123_456, false);
                }
                var splitter = (CopperMachineBlockEntity)level.getBlockEntity(new BlockPos(45, y, 42));
                while (splitter.sideMode(net.minecraft.core.Direction.WEST) != io.github.gerbesh.unusualcopper.machine.SideMode.ITEM_INPUT) splitter.cycleSide(net.minecraft.core.Direction.WEST);
                for (int x = 41; x <= 44; x++) {
                    var pos = new BlockPos(x, y - 1, 42);
                    level.setBlock(pos, ModContent.ITEM_PIPE.get().defaultBlockState(), 3);
                    ((io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity)level.getBlockEntity(pos)).owner(mc.player.getUUID());
                }
                pipeMenuPos = new BlockPos(44, y, 42);
                level.setBlock(pipeMenuPos, ModContent.ITEM_PIPE.get().defaultBlockState(), 3);
                var routingPipe = (io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity)level.getBlockEntity(pipeMenuPos);
                routingPipe.owner(mc.player.getUUID()); routingPipe.rule(net.minecraft.core.Direction.EAST).filter(0, new ItemStack(ModContent.ESSENCE.get())); routingPipe.changed();
                for (int x : new int[]{46, 47, 49, 50}) level.setBlock(new BlockPos(x, y, 42), ModContent.ENERGY_PIPE.get().defaultBlockState(), 3);
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) {
                    player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.teleportTo(level, 46.5, y + 2.2, 36, 0, 28);
                }
                UnusualCopper.LOGGER.info("CuE client smoke scene prepared at height {}", y);
                ready.set(true);
            });
        }
        if (ready.get() && (stage == 0 || (stage == 1 && mc.screen instanceof CopperMachineScreen) || (stage == 2 && mc.screen == null) || (stage == 3 && mc.screen instanceof io.github.gerbesh.unusualcopper.client.ItemPipeScreen))) ticks++;
        if (stage == 0 && ticks == 140) mc.options.hideGui = true;
        if (stage == 0 && ready.get() && ticks >= 160 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-smoke.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE client smoke screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (stage == 1 && ticks == 5) mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 0);
        if (stage == 1 && ticks >= 20 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-menu.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE menu smoke screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (stage == 2 && ticks >= 80 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-night.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE night smoke screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (stage == 3 && ticks == 5) mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, net.minecraft.core.Direction.EAST.ordinal());
        if (stage == 3 && ticks >= 12 && !settingsSent && mc.player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.ItemPipeMenu menu && menu.selected() == net.minecraft.core.Direction.EAST) {
            settingsSent = true;
            io.github.gerbesh.unusualcopper.network.PipeNetwork.settings(new io.github.gerbesh.unusualcopper.network.PipeNetwork.Settings(menu.containerId, net.minecraft.core.Direction.EAST.ordinal(), 20, 8, 16, 0, "forge:ingots/copper", false));
        }
        if (stage == 3 && ticks >= 40 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-item-pipe-menu.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE item pipe menu screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (saved.get()) {
            if (stage == 0) {
                stage = 1; captured = false; saved.set(false); ticks = 0; mc.options.hideGui = false;
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var machine = (CopperMachineBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(menuPos);
                    if (player != null && machine != null) {
                        player.teleportTo(serverLevel(mc), menuPos.getX() + .5, menuPos.getY() + 1, menuPos.getZ() - 2, 0, 20);
                        NetworkHooks.openScreen(player, machine, menuPos);
                    }
                });
            } else if (stage == 1) {
                stage = 2; captured = false; saved.set(false); ticks = 0; mc.options.hideGui = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (player != null) {
                        player.closeContainer();
                        player.teleportTo(server.overworld(), 46.5, menuPos.getY() + 2.2, 36, 0, 28);
                    }
                    server.overworld().setDayTime(18000);
                });
            } else if (stage == 2) {
                stage = 3; captured = false; saved.set(false); ticks = 0; mc.options.hideGui = false;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    var pipe = (io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity)server.overworld().getBlockEntity(pipeMenuPos);
                    if (player != null && pipe != null) {
                        player.teleportTo(server.overworld(), pipeMenuPos.getX() + .5, pipeMenuPos.getY() + 1, pipeMenuPos.getZ() - 2, 0, 20);
                        NetworkHooks.openScreen(player, pipe, pipeMenuPos);
                    }
                });
            } else { mc.options.hideGui = false; mc.stop(); }
        }
    }
    private static net.minecraft.server.level.ServerLevel serverLevel(Minecraft mc) { return mc.getSingleplayerServer().overworld(); }
}
