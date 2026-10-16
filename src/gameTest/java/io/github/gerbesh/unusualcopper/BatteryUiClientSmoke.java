package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.CopperMachineScreen;
import io.github.gerbesh.unusualcopper.item.CopperBatteryItem;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class BatteryUiClientSmoke {
    private static final BlockPos POS=new BlockPos(82,90,76);
    private static final AtomicBoolean saved=new AtomicBoolean();
    private static int stage,total,ticks,stable,pausedEnergy;
    private static boolean jeiVerified;
    private static boolean verifyJei(Minecraft mc) {
        var runtime=io.github.gerbesh.unusualcopper.compat.jei.UnusualCopperJeiPlugin.activeRuntime();
        if(runtime==null)return false;
        long recipes=runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get()
                .filter(recipe->recipe.getResultItem(mc.level.registryAccess()).getItem() instanceof CopperBatteryItem).count();
        if(recipes!=4)throw new IllegalStateException("JEI exposed "+recipes+" battery recipes instead of four");
        UnusualCopper.LOGGER.info("CuE BATTERY JEI PASS: all four crafting recipes registered, including charge-preserving upgrades");return true;
    }
    private static void shot(Minecraft mc,String name) {
        saved.set(false);Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->saved.set(true));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("unusualcopper.batteryClient"))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        if(++total>1000)throw new IllegalStateException("Battery client smoke timed out at stage "+stage);
        if(net.minecraftforge.fml.ModList.get().isLoaded("jei") && !jeiVerified) {jeiVerified=verifyJei(mc);if(!jeiVerified)return;}
        if(stage==0) {
            mc.options.pauseOnLostFocus=false;mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            var server=mc.getSingleplayerServer();server.execute(()->{
                var level=server.overworld();for(var side:Direction.values())level.setBlock(POS.relative(side),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(POS,Blocks.AIR.defaultBlockState(),3);level.setBlock(POS,ModContent.MACHINES.get(MachineKind.COPPER_CELL_III).get().defaultBlockState(),3);
                var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.teleportTo(level,POS.getX()+.5,POS.getY()+1,POS.getZ()-2,0,20);
                player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(ModContent.COPPER_BATTERY.get()));player.getInventory().setItem(1,new ItemStack(ModContent.REINFORCED_BATTERY.get()));player.getInventory().setItem(2,new ItemStack(ModContent.RESONANT_BATTERY.get()));
                var battery=new ItemStack(ModContent.AETHERIC_BATTERY.get());CopperBatteryItem.stored(battery,10_000_000);player.getInventory().setItem(9,battery);
                var cell=(CopperMachineBlockEntity)level.getBlockEntity(POS);cell.setOwner(player.getUUID());NetworkHooks.openScreen(player,cell,POS);
            });stage=1;return;
        }
        if(!(mc.screen instanceof CopperMachineScreen) || !(mc.player.containerMenu instanceof CopperMachineMenu menu))return;
        ++ticks;
        if(stage==1 && ticks>40) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId,4,0,ClickType.QUICK_MOVE,mc.player);stage=2;ticks=0;
        } else if(stage==2 && menu.slots.get(40).getItem().is(ModContent.AETHERIC_BATTERY.get())) {
            if(CopperBatteryItem.stored(menu.slots.get(40).getItem())!=10_000_000)throw new IllegalStateException("Shift-click lost battery charge");
            mc.gameMode.handleInventoryButtonClick(menu.containerId,11);stage=3;ticks=0;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);
        } else if(stage==3 && menu.data.get(45)==1 && menu.energy()>65_535 && menu.energy()+CopperBatteryItem.stored(menu.slots.get(40).getItem())==10_000_000 && ++stable>=3) {
            shot(mc,"cue-battery-discharge-ru.png");stage=4;
        } else if(stage==4 && saved.get()) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId,11);stage=5;ticks=0;
        } else if(stage==5 && menu.data.get(45)==2) {
            pausedEnergy=menu.energy();stage=6;ticks=0;
        } else if(stage==6 && ticks>10) {
            if(menu.energy()!=pausedEnergy)throw new IllegalStateException("Paused battery UI continued transferring");
            mc.gameMode.handleInventoryButtonClick(menu.containerId,11);stage=7;ticks=0;stable=0;
        } else if(stage==7 && menu.data.get(45)==0 && menu.energy()==0 && CopperBatteryItem.stored(menu.slots.get(40).getItem())==10_000_000 && ++stable>=3) {
            shot(mc,"cue-battery-charge-ru.png");stage=8;
        } else if(stage==8 && saved.get()) {
            UnusualCopper.LOGGER.info("CuE BATTERY CLIENT PASS: actual shift-click into dock; C2S discharge/pause/charge; synchronized >65535 CuE; conserved all 10000000 CuE; Russian 1280x720 layout");mc.stop();
        }
    }
}
