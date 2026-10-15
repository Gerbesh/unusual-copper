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
    private static BlockPos menuPos, pipeMenuPos, cultivatorPos, shepherdPos, reaperPos;
    private static final java.util.List<java.util.UUID> shepherdAnimals = new java.util.ArrayList<>();
    private static boolean settingsSent;
    private static boolean reaperFxStarted;
    private static final AtomicBoolean reaperFxReady=new AtomicBoolean(), reaperFxVerified=new AtomicBoolean();
    private static volatile java.util.UUID reaperVictim;
    private static BlockPos reaperStorage;
    private static long reaperWindupBaseline, reaperStrikeBaseline, reaperTransferBaseline;
    private static boolean reaperFlashCaptured;
    private static long reaperStressTick=Long.MIN_VALUE, reaperStressId;
    private static int reaperStressWaves;
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
        if (ready.get() && (stage == 0 || ((stage == 1 || stage == 4 || stage == 6 || stage == 7 || stage == 10) && mc.screen instanceof CopperMachineScreen) || (stage == 9 && mc.screen instanceof io.github.gerbesh.unusualcopper.client.ReaperScreen) || ((stage == 2 || stage == 5 || stage == 8 || stage>=11) && mc.screen == null) || (stage == 3 && mc.screen instanceof io.github.gerbesh.unusualcopper.client.ItemPipeScreen))) ticks++;
        if(stage>=11 && ticks>400) throw new IllegalStateException("Reaper visual fixture timed out at stage "+stage+", stats="+io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics());
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
        if (stage == 4 && ticks == 5) mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 2);
        if (stage == 4 && ticks >= 25 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-cultivator-menu.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE cultivator menu screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (stage == 5 && ticks >= 60 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-cultivator-area.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE cultivator area screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (stage == 6 && ticks >= 45 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-collector-menu.png", mc.getMainRenderTarget(), text -> {
                UnusualCopper.LOGGER.info("CuE collector menu screenshot: {}", text.getString()); saved.set(true);
            });
        }
        if (stage == 7 && ticks == 5) {
            mc.getToasts().clear(); mc.gui.getChat().clearMessages(true);
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 6);
        }
        if (stage == 7 && ticks >= 25 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-shepherd-menu.png", mc.getMainRenderTarget(), text -> { UnusualCopper.LOGGER.info("CuE shepherd menu screenshot: {}", text.getString()); saved.set(true); });
        }
        if (stage == 8 && ticks == 10) mc.getSingleplayerServer().execute(() -> {
            var level = serverLevel(mc); var shepherd = (CopperMachineBlockEntity)level.getBlockEntity(shepherdPos);
            for (var id : shepherdAnimals) if (level.getEntity(id) instanceof net.minecraft.world.entity.AgeableMob baby) baby.setAge(-5000);
            shepherd.energy.add(1200, false); shepherd.nextWaveTick = 0; io.github.gerbesh.unusualcopper.machine.logic.ShepherdLogic.process(shepherd, level);
            if (!shepherd.allowOwnTamed || shepherd.grown != 3 || shepherd.energy.stored() != 0) throw new IllegalStateException("Shepherd GUI permission or repeated client wave failed");
            UnusualCopper.LOGGER.info("CuE SHEPHERD CLIENT PASS: three calves each advanced 2400 ticks, paid 1200 CuE; real menu enabled own pets");
        });
        if (stage == 8 && ticks >= 20 && !captured) {
            captured = true;
            Screenshot.grab(mc.gameDirectory, "cue-shepherd-wave.png", mc.getMainRenderTarget(), text -> { UnusualCopper.LOGGER.info("CuE shepherd wave screenshot: {}", text.getString()); saved.set(true); });
        }
        if (stage == 9 && ticks >= 15 && mc.player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.ReaperMenu menu && menu.snapshot()!=null && !settingsSent) {
            settingsSent=true;
            io.github.gerbesh.unusualcopper.network.ReaperNetwork.settings(menu.containerId,new io.github.gerbesh.unusualcopper.gameplay.ReaperSettings(20,12,true,true,"minecraft:cow","","creature"));
        }
        if (stage == 9 && ticks >= 30 && !captured && mc.player.containerMenu instanceof io.github.gerbesh.unusualcopper.menu.ReaperMenu menu && menu.result==1) {
            if (menu.snapshot().trigger()!=20 || menu.snapshot().keep()!=12 || !menu.snapshot().passive()) throw new IllegalStateException("Reaper settings acknowledgement failed");
            captured=true;
            Screenshot.grab(mc.gameDirectory,"cue-reaper-settings.png",mc.getMainRenderTarget(),text -> { UnusualCopper.LOGGER.info("CuE REAPER CLIENT PASS: real C2S settings and S2C acknowledgement; screenshot {}",text.getString()); saved.set(true); });
        }
        if (stage == 10 && ticks >= 30 && !captured && mc.screen instanceof CopperMachineScreen) {
            captured=true;
            Screenshot.grab(mc.gameDirectory,"cue-reaper-menu.png",mc.getMainRenderTarget(),text -> { UnusualCopper.LOGGER.info("CuE reaper machine screenshot: {}",text.getString()); saved.set(true); });
        }
        if(stage==11 && reaperFxReady.get() && !reaperFxStarted) {
            reaperFxStarted=true;
            mc.getSingleplayerServer().execute(() -> ((CopperMachineBlockEntity)serverLevel(mc).getBlockEntity(reaperPos)).redstoneMode=0);
        }
        if(stage==11 && !captured && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().windups()>reaperWindupBaseline && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(0)>=4 && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(0)<10 && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().runeFrames()>0 && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().compressedModels()>0) {
            captured=true; Screenshot.grab(mc.gameDirectory,"cue-reaper-rune.png",mc.getMainRenderTarget(),text -> { UnusualCopper.LOGGER.info("CuE reaper rune screenshot: {}",text.getString()); saved.set(true); });
        }
        if(stage==12 && !captured && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().strikes()>reaperStrikeBaseline && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(1)>=5 && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(1)<13 && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().iconFrames()>0) {
            captured=true; Screenshot.grab(mc.gameDirectory,"cue-reaper-loot.png",mc.getMainRenderTarget(),text -> { UnusualCopper.LOGGER.info("CuE reaper loot screenshot: {}",text.getString()); saved.set(true); });
        }
        if(stage==12 && !reaperFlashCaptured && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(1)>=1 && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(1)<3) {
            reaperFlashCaptured=true; Screenshot.grab(mc.gameDirectory,"cue-reaper-flash.png",mc.getMainRenderTarget(),text -> UnusualCopper.LOGGER.info("CuE reaper flash screenshot: {}",text.getString()));
        }
        if(stage==13 && !captured && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().transfers()>reaperTransferBaseline && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(3)>=8 && io.github.gerbesh.unusualcopper.client.ReaperEffects.youngestAge(3)<14) {
            captured=true;
            mc.getSingleplayerServer().execute(() -> {
                var level=serverLevel(mc); var reaper=(CopperMachineBlockEntity)level.getBlockEntity(reaperPos); var victim=level.getEntity(reaperVictim); var barrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(reaperStorage);
                if(victim!=null && victim.isAlive() || reaper.energy.stored()!=0 || barrel.isEmpty() || !reaperFlashCaptured) throw new IllegalStateException("Reaper cosmetic event does not correspond to real paid loot transfer/flash");
                reaperFxVerified.set(true); UnusualCopper.LOGGER.info("CuE REAPER FX CLIENT PASS: actual ticker preparation, paid death, accepted loot icons and real barrel transfer; no client inventory authority");
            });
            Screenshot.grab(mc.gameDirectory,"cue-reaper-transfer.png",mc.getMainRenderTarget(),text -> { UnusualCopper.LOGGER.info("CuE reaper transfer screenshot: {}",text.getString()); saved.set(true); });
        }
        if(stage==14 && mc.level.getGameTime()!=reaperStressTick && reaperStressWaves<3) {
            reaperStressTick=mc.level.getGameTime(); reaperStressWaves++;
            var before=io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics();
            int[] icons={net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.BEEF),net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.LEATHER),net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.COPPER_INGOT),net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.REDSTONE)};
            for(int i=0;i<1000;i++) io.github.gerbesh.unusualcopper.client.ReaperEffects.accept(new io.github.gerbesh.unusualcopper.network.MachineEffects.Reap(mc.level.dimension().location(),reaperPos,new java.util.UUID(0,++reaperStressId),3,-1,new java.util.UUID(0,0),net.minecraft.world.phys.Vec3.atCenterOf(reaperPos),reaperStorage,icons));
            var after=io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics();
            if(after.transfers()-before.transfers()>8 || after.active()>16 || after.peakIcons()>24) throw new IllegalStateException("Client cosmetic budget exceeded: "+after);
        }
        if(stage==14 && reaperStressWaves==3 && io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics().peakIcons()==24) {
            var stats=io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics();
            if(stats.active()!=16) throw new IllegalStateException("Stress scene did not reach the bounded event cache");
            UnusualCopper.LOGGER.info("CuE REAPER FX BUDGET PASS: 3000 synthetic client events; accepted <=8/tick, active=16, peak drawn icons=24/frame; actual rune/model renders={},{}",stats.runeFrames(),stats.compressedModels()); mc.options.hideGui=false; mc.stop();
        }
        if (saved.get()) {
            if(stage==13 && !reaperFxVerified.get()) return;
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
            } else if (stage == 3) {
                stage = 4; captured = false; saved.set(false); ticks = 0;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var level = server.overworld(); var player = server.getPlayerList().getPlayer(mc.player.getUUID()); int y = menuPos.getY();
                    level.setDayTime(6000);
                    cultivatorPos = new BlockPos(47, y, 45);
                    level.setBlock(cultivatorPos, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(cultivatorPos, ModContent.MACHINES.get(MachineKind.AURA_CULTIVATOR).get().defaultBlockState(), 3);
                    var cultivator = (CopperMachineBlockEntity)level.getBlockEntity(cultivatorPos); cultivator.setOwner(player.getUUID()); cultivator.energy.add(4480, false);
                    for (int x = 44; x <= 50; x++) for (int z = 46; z <= 49; z++) {
                        level.setBlock(new BlockPos(x, y - 1, z), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
                        level.setBlock(new BlockPos(x, y, z), Blocks.WHEAT.defaultBlockState(), 3);
                    }
                    player.teleportTo(level, 47.5, y + 1.8, 42, 0, 20);
                    var wand = new ItemStack(ModContent.AREA_WAND.get()); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
                    for (var point : new BlockPos[]{cultivatorPos, new BlockPos(44, y, 46), new BlockPos(50, y, 49)}) {
                        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(point), net.minecraft.core.Direction.UP, point, false);
                        ModContent.AREA_WAND.get().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                    }
                    if (cultivator.workArea() == null || cultivator.workArea().volume() != 28) throw new IllegalStateException("Wand did not set the client fixture work area");
                    wand.getOrCreateTag().putInt("Mode", 2);
                    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(cultivatorPos), net.minecraft.core.Direction.UP, cultivatorPos, false);
                    ModContent.AREA_WAND.get().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                    UnusualCopper.LOGGER.info("CuE WAND SELECTION PASS: native server useOn selected 28 coordinates and opened the rules menu");
                });
            } else if (stage == 4) {
                stage = 5; captured = false; saved.set(false); ticks = 0; mc.options.hideGui = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID()); player.closeContainer();
                    player.teleportTo(server.overworld(), 47.5, cultivatorPos.getY() + 3.2, 41, 0, 30);
                    var cultivator = (CopperMachineBlockEntity)server.overworld().getBlockEntity(cultivatorPos);
                    io.github.gerbesh.unusualcopper.network.AreaNetwork.preview(player, cultivator.workArea().min(), cultivator.workArea().max(), true);
                });
            } else if (stage == 5) {
                stage = 6; captured = false; saved.set(false); ticks = 0; mc.options.hideGui = false;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var level = server.overworld(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    var pos = cultivatorPos.east(5); var target = pos.south(2); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos, ModContent.MACHINES.get(MachineKind.MAGNETIC_COLLECTOR).get().defaultBlockState(), 3);
                    var collector = (CopperMachineBlockEntity)level.getBlockEntity(pos); collector.setOwner(player.getUUID()); collector.energy.add(1000, false);
                    player.teleportTo(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() - 2, 0, 20);
                    if (!collector.setWorkArea(new io.github.gerbesh.unusualcopper.gameplay.WorkArea(target, target), player)) throw new IllegalStateException("Collector fixture selection failed");
                    var ground = new net.minecraft.world.entity.item.ItemEntity(level, target.getX() + .5, target.getY() + .5, target.getZ() + .5, new ItemStack(Items.IRON_INGOT, 16)); ground.setNoPickUpDelay(); ground.setNoGravity(true); level.addFreshEntity(ground);
                    io.github.gerbesh.unusualcopper.machine.logic.CollectorLogic.process(collector, level);
                    if (!ground.isRemoved() || collector.inventory.getStackInSlot(2).getCount() != 16) throw new IllegalStateException("Collector client fixture did not physically commit the stack");
                    NetworkHooks.openScreen(player, collector, pos);
                    UnusualCopper.LOGGER.info("CuE COLLECTOR CLIENT PASS: real sixteen-ingot entity became one output stack; {} CuE remain", collector.energy.stored());
                });
            } else if (stage == 6) {
                stage = 7; captured = false; saved.set(false); ticks = 0;
                mc.getSingleplayerServer().execute(() -> {
                    var level = serverLevel(mc); var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID()); int y = cultivatorPos.getY(); shepherdPos = new BlockPos(42, y, 45);
                    for (int x = 39; x <= 45; x++) for (int z = 45; z <= 50; z++) { level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 3); level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3); }
                    for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(39, y, 45, 46, y + 2, 51))) mob.discard();
                    level.setBlock(shepherdPos, ModContent.MACHINES.get(MachineKind.GROWTH_SHEPHERD).get().defaultBlockState(), 3); var shepherd = (CopperMachineBlockEntity)level.getBlockEntity(shepherdPos); shepherd.setOwner(player.getUUID()); shepherd.energy.add(1200, false);
                    player.teleportTo(level, 42.5, y + 1.8, 42, 0, 20);
                    if (!shepherd.setWorkArea(new io.github.gerbesh.unusualcopper.gameplay.WorkArea(new BlockPos(40, y, 48), new BlockPos(44, y + 1, 49)), player)) throw new IllegalStateException("Shepherd client area failed");
                    shepherdAnimals.clear();
                    for (int x : new int[]{40,42,44}) {
                        var calf = net.minecraft.world.entity.EntityType.COW.create(level); calf.setPos(x + .5, y + .1, 48.5); calf.setNoAi(true); calf.setNoGravity(true); calf.setAge(-5000); level.addFreshEntity(calf); shepherdAnimals.add(calf.getUUID());
                    }
                    for (var dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(38, y - 2, 33, 55, y + 3, 52))) dropped.discard();
                    player.getInventory().clearContent(); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModContent.AREA_WAND.get()));
                    io.github.gerbesh.unusualcopper.machine.logic.ShepherdLogic.process(shepherd, level);
                    if (shepherd.grown != 3 || shepherd.energy.stored() != 0) throw new IllegalStateException("Shepherd client initial wave failed");
                    NetworkHooks.openScreen(player, shepherd, shepherdPos);
                });
            } else if (stage == 7) {
                stage = 8; captured = false; saved.set(false); ticks = 0; mc.options.hideGui = true;
                io.github.gerbesh.unusualcopper.client.AreaPreviewRenderer.clear();
                mc.getSingleplayerServer().execute(() -> { var level = serverLevel(mc); var player = level.getServer().getPlayerList().getPlayer(mc.player.getUUID()); player.closeContainer(); player.teleportTo(level, 42.5, shepherdPos.getY() + 2.2, 42, 0, 34); });
            } else if (stage == 8) {
                stage=9; captured=false; saved.set(false); ticks=0; settingsSent=false; mc.options.hideGui=false;
                mc.getSingleplayerServer().execute(() -> {
                    var level=serverLevel(mc); var player=level.getServer().getPlayerList().getPlayer(mc.player.getUUID()); reaperPos=shepherdPos.east(4);
                    level.setBlock(reaperPos,ModContent.MACHINES.get(MachineKind.MOB_REAPER).get().defaultBlockState(),3);
                    var reaper=(CopperMachineBlockEntity)level.getBlockEntity(reaperPos); reaper.setOwner(player.getUUID()); reaper.energy.add(1500,false);
                    player.teleportTo(level,reaperPos.getX()+.5,reaperPos.getY()+1,reaperPos.getZ()-2,0,20);
                    io.github.gerbesh.unusualcopper.menu.ReaperMenu.open(player,reaper);
                });
            } else if (stage == 9) {
                stage=10; captured=false; saved.set(false); ticks=0;
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,0);
            } else if(stage==10) {
                stage=11; captured=false; saved.set(false); ticks=0; mc.options.hideGui=true;
                var stats=io.github.gerbesh.unusualcopper.client.ReaperEffects.statistics(); reaperWindupBaseline=stats.windups(); reaperStrikeBaseline=stats.strikes(); reaperTransferBaseline=stats.transfers();
                mc.getSingleplayerServer().execute(() -> {
                    var level=serverLevel(mc); var player=level.getServer().getPlayerList().getPlayer(mc.player.getUUID()); player.closeContainer();
                    var reaper=(CopperMachineBlockEntity)level.getBlockEntity(reaperPos); reaper.redstoneMode=1; reaper.clearBinding(); reaper.energy.spend(reaper.energy.stored()); reaper.energy.add(1500,false); reaper.nextWaveTick=0;
                    for(int slot=0;slot<4;slot++) reaper.inventory.setStackInSlot(slot,ItemStack.EMPTY);
                    var target=reaperPos.offset(2,0,3); var area=new io.github.gerbesh.unusualcopper.gameplay.WorkArea(target,target);
                    if(!reaper.setWorkArea(area,player)) throw new IllegalStateException("Reaper FX fixture area failed");
                    reaper.reaper.settings(new io.github.gerbesh.unusualcopper.gameplay.ReaperRule(true,true,java.util.Set.of(new net.minecraft.resources.ResourceLocation("minecraft","cow")),java.util.Set.of(),java.util.Set.of()),0,0);
                    for(var mob:level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(target))) mob.discard();
                    var cow=net.minecraft.world.entity.EntityType.COW.create(level); cow.setPos(target.getX()+.5,target.getY()+.1,target.getZ()+.5); cow.setNoAi(true); cow.setNoGravity(true); level.addFreshEntity(cow); reaperVictim=cow.getUUID();
                    reaperStorage=reaperPos.east(4); level.setBlock(reaperStorage,Blocks.AIR.defaultBlockState(),3); level.setBlock(reaperStorage,Blocks.BARREL.defaultBlockState(),3);
                    for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(reaperPos).inflate(8))) item.discard();
                    player.teleportTo(level,reaperPos.getX()+2.5,reaperPos.getY()+2.3,reaperPos.getZ()-4,0,22); reaperFxReady.set(true);
                });
            } else if(stage==11) {
                stage=12; captured=false; saved.set(false); ticks=0;
            } else if(stage==12) {
                stage=13; captured=false; saved.set(false); ticks=0;
                mc.getSingleplayerServer().execute(() -> {
                    var level=serverLevel(mc); var player=level.getServer().getPlayerList().getPlayer(mc.player.getUUID()); var reaper=(CopperMachineBlockEntity)level.getBlockEntity(reaperPos);
                    if(!reaper.bind(reaperStorage,net.minecraft.core.Direction.UP,player)) throw new IllegalStateException("Reaper FX barrel binding failed");
                });
            } else if(stage==13) {
                stage=14; captured=false; saved.set(false); ticks=0;
            } else { mc.options.hideGui = false; mc.stop(); }
        }
    }
    private static net.minecraft.server.level.ServerLevel serverLevel(Minecraft mc) { return mc.getSingleplayerServer().overworld(); }
}
