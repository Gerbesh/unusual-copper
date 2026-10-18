package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.*;
import io.github.gerbesh.unusualcopper.item.AreaWandItem;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.network.AreaNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated display fixtures: fixed client hover/selection and dock, no clicks or paid transfers. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class UtilityVisualClient {
    private static final BlockPos POS=new BlockPos(110,150,126),FIRST=new BlockPos(106,150,127);
    private static final String[] SHOTS={"creative-dock","area-live","area-too-large","mining-128","area-first-corner","creative-tooltip"};
    private static final AtomicBoolean READY=new AtomicBoolean(),SAVED=new AtomicBoolean();
    private static CompletableFuture<Void> reload;
    private static boolean started,captured;private static int ticks,stage,total;
    private static HitResult hover;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.utilityVisualClient"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++total>1400)throw new IllegalStateException("Utility display stalled");
        if(!started){started=true;mc.options.hideGui=false;mc.options.pauseOnLostFocus=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            String language=System.getProperty("unusualcopper.utilityVisualLanguage","ru_ru");mc.options.languageCode=language;mc.getLanguageManager().setSelected(language);reload=mc.reloadResourcePacks();
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);level.setWeatherParameters(0,0,false,false);
                level.setBlock(POS,ModContent.MACHINES.get(MachineKind.COPPER_CELL_III).get().defaultBlockState(),3);
                var player=level.getServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.getInventory().clearContent();
                for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
                player.teleportTo(level,110.5,156,116,0,25);READY.set(true);
            });return;
        }
        if(!READY.get()||!reload.isDone())return;
        if(ticks==0)display(stage);
        if(++ticks<(stage==0?100:40))return;
        if(!captured){captured=true;Screenshot.grab(mc.gameDirectory,"utility-"+SHOTS[stage]+".png",mc.getMainRenderTarget(),message->SAVED.set(true));return;}
        if(!SAVED.get())return;
        if(stage==SHOTS.length-1){UnusualCopper.LOGGER.info("UTILITY DISPLAY COMPLETE: six fixed display frames, no functional acceptance");mc.setScreen(null);mc.player.containerMenu=mc.player.inventoryMenu;AreaPreviewRenderer.clear();mc.stop();return;}
        stage++;ticks=0;captured=false;SAVED.set(false);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hover(RenderLevelStageEvent event){
        if(Boolean.getBoolean("unusualcopper.utilityVisualClient")&&started&&stage>0&&event.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)Minecraft.getInstance().hitResult=hover;
    }
    private static void display(int shot){
        var mc=Minecraft.getInstance();
        if(shot==0||shot==5){
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());final CopperMachineMenu menu;
            try{buffer.writeBlockPos(POS);menu=new CopperMachineMenu(0,mc.player.getInventory(),buffer);}finally{buffer.release();}
            menu.data.set(8,MachineKind.COPPER_CELL_III.ordinal());menu.data.set(45,1);menu.slots.get(40).set(new ItemStack(ModContent.CREATIVE_BATTERY.get()));
            menu.data.set(0,8_000_000&0xffff);menu.data.set(1,8_000_000>>>16);menu.data.set(2,16_000_000&0xffff);menu.data.set(3,16_000_000>>>16);
            menu.data.set(6,io.github.gerbesh.unusualcopper.machine.MachineStatus.CHARGING.ordinal());
            var screen=new CopperMachineScreen(menu,mc.player.getInventory(),Component.translatable("block.unusualcopper.copper_cell_iii")){
                @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,shot==5?(width-224)/2+20:-100,shot==5?(height-214)/2+71:-100,partial);}
            };mc.setScreen(screen);mc.player.containerMenu=menu;return;
        }
        mc.setScreen(null);mc.player.containerMenu=mc.player.inventoryMenu;
        var token=UUID.randomUUID();var wand=new ItemStack(ModContent.AREA_WAND.get());wand.getOrCreateTag().putUUID(AreaWandItem.PREVIEW_SESSION,token);
        mc.player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        boolean mining=shot==3;AreaPreviewRenderer.selection(new AreaNetwork.Selection(token,mc.level.dimension().location(),POS,FIRST,mining));
        var target=shot==2?new BlockPos(124,153,135):mining?new BlockPos(233,60,254):new BlockPos(113,153,135);
        hover=shot==4?BlockHitResult.miss(new Vec3(110,170,126),Direction.UP,BlockPos.ZERO)
                :new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
    }
}
