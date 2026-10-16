package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.ItemPipeScreen;
import io.github.gerbesh.unusualcopper.itempipe.ItemPipeBlockEntity;
import io.github.gerbesh.unusualcopper.itempipe.PipeMode;
import io.github.gerbesh.unusualcopper.menu.ItemPipeMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real C2S buttons, close/reopen and Russian rendering in the repository-owned smoke world. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class PipeUiClientSmoke {
    private static final BlockPos POS=new BlockPos(76,90,76);
    private static final AtomicBoolean saved=new AtomicBoolean();
    private static int stage,ticks,total;
    private static void open(Minecraft mc) {
        var server=mc.getSingleplayerServer();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(mc.player.getUUID());
            var pipe=(ItemPipeBlockEntity)server.overworld().getBlockEntity(POS);
            NetworkHooks.openScreen(player,pipe,POS);
        });
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("unusualcopper.pipeUiClient"))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        if(++total>900)throw new IllegalStateException("Pipe UI smoke timed out at stage "+stage);
        if(stage==0) {
            mc.options.pauseOnLostFocus=false;
            mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var server=mc.getSingleplayerServer();
            server.execute(()->{
                var level=server.overworld();
                for(var side:Direction.values())level.setBlock(POS.relative(side),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(POS,Blocks.AIR.defaultBlockState(),3);
                level.setBlock(POS,ModContent.ITEM_PIPE.get().defaultBlockState(),3);
                level.setBlock(POS.east(),Blocks.CHEST.defaultBlockState(),3);
                var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                player.teleportTo(level,POS.getX()+.5,POS.getY()+1,POS.getZ()-2,0,20);
                ((ItemPipeBlockEntity)level.getBlockEntity(POS)).owner(player.getUUID());
                NetworkHooks.openScreen(player,(ItemPipeBlockEntity)level.getBlockEntity(POS),POS);
            });
            stage=1;return;
        }
        if(stage==1 && mc.screen instanceof ItemPipeScreen && mc.player.containerMenu instanceof ItemPipeMenu menu && ++ticks>40) {
            if(menu.selected()!=Direction.EAST)throw new IllegalStateException("Fresh UI did not select its east chest");
            mc.gameMode.handleInventoryButtonClick(menu.containerId,Direction.NORTH.ordinal());
            mc.gameMode.handleInventoryButtonClick(menu.containerId,10);
            mc.gameMode.handleInventoryButtonClick(menu.containerId,Direction.EAST.ordinal());
            mc.gameMode.handleInventoryButtonClick(menu.containerId,10);
            stage=2;ticks=0;
        } else if(stage==2 && ++ticks>15 && mc.player.containerMenu instanceof ItemPipeMenu menu) {
            if(menu.selected()!=Direction.EAST || menu.data.get(1)!=PipeMode.EXTRACT.ordinal())throw new IllegalStateException("C2S face/mode buttons failed to synchronize");
            mc.player.closeContainer();stage=3;ticks=0;
        } else if(stage==3 && mc.screen==null && ++ticks>10) {
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);
            open(mc);stage=4;ticks=0;
        } else if(stage==4 && mc.screen instanceof ItemPipeScreen && mc.player.containerMenu instanceof ItemPipeMenu menu && ++ticks>40) {
            if(menu.selected()!=Direction.EAST || menu.data.get(1)!=PipeMode.EXTRACT.ordinal())throw new IllegalStateException("Reopening lost the selected east extraction rule");
            Screenshot.grab(mc.gameDirectory,"cue-pipe-ui-ru.png",mc.getMainRenderTarget(),text->saved.set(true));
            UnusualCopper.LOGGER.info("CuE PIPE UI CLIENT PASS: east chest selected automatically; real C2S face/mode buttons synchronized; close/reopen retained east extraction");
            stage=5;
        } else if(stage==5 && saved.get())mc.stop();
    }
}
