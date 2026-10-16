package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.CopperMachineScreen;
import io.github.gerbesh.unusualcopper.item.AreaWandItem;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.menu.CopperMachineMenu;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class HarvestClientSmoke {
    private static final BlockPos MACHINE=new BlockPos(88,90,76),STORAGE=MACHINE.east(3),SMELTER=MACHINE.offset(4,0,2),MIN=MACHINE.offset(1,0,1),MAX=MACHINE.offset(2,0,2);
    private static final AtomicBoolean verified=new AtomicBoolean(),saved=new AtomicBoolean();
    private static volatile int stage;
    private static int ticks,total;
    private static void use(AreaWandItem wand,net.minecraft.server.level.ServerPlayer player,BlockPos pos) {
        if(!wand.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))).consumesAction())throw new IllegalStateException("Real wand use failed at "+pos);
    }
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("unusualcopper.harvestClient") || !(event.level instanceof ServerLevel level) || stage<2 || verified.get())return;
        var m=(CopperMachineBlockEntity)level.getBlockEntity(MACHINE);if(m==null)return;
        if(level.getGameTime()%100==0)UnusualCopper.LOGGER.info("CuE HARVEST CLIENT STATE: mode={}, energy={}, status={}, cursor={}, harvested={}, cargo={}, output={},{}",m.growthMode,m.energy.stored(),m.status(),m.scanCursor,m.harvest.count(),m.boundCargoCount(),m.inventory.getStackInSlot(2),m.inventory.getStackInSlot(3));
        if(m.growthMode!=2)return;
        m.redstoneMode=0;
        var chest=(BarrelBlockEntity)level.getBlockEntity(STORAGE);var furnace=(CopperMachineBlockEntity)level.getBlockEntity(SMELTER);if(chest==null || furnace==null)return;
        int wheat=0;for(int i=0;i<27;i++)if(chest.getItem(i).is(Items.WHEAT))wheat+=chest.getItem(i).getCount();
        boolean replanted=true;for(var pos:BlockPos.betweenClosed(MIN,MAX)){var state=level.getBlockState(pos);replanted &= state.getBlock() instanceof CropBlock crop && crop.getAge(state)==0;}
        if(wheat==2 && replanted && m.energy.stored()==0 && m.energy.consumed()==560 && m.harvest.empty() && m.boundCargoCount()==0 && m.inventory.getStackInSlot(2).isEmpty() && m.inventory.getStackInSlot(3).isEmpty() && furnace.inventory.getStackInSlot(2).is(Items.BAKED_POTATO) && furnace.energy.stored()==0) {
            verified.set(true);UnusualCopper.LOGGER.info("CuE HARVEST CLIENT SERVER PASS: real wand area/storage; four real mature crops replanted; two wheat exported; exactly 560 CuE; furnace recipe cooked potato for 240 CuE");
        }
    }
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("unusualcopper.harvestClient"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        if(++total>1200)throw new IllegalStateException("Harvest client smoke timed out at "+stage);
        if(stage==0) {
            mc.options.pauseOnLostFocus=false;mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            var server=mc.getSingleplayerServer();server.execute(()->{
                var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
                for(var p:BlockPos.betweenClosed(MACHINE.offset(-1,-1,-3),MACHINE.offset(5,3,3)))level.setBlock(p,p.getY()==89?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                level.setBlock(MACHINE,ModContent.MACHINES.get(MachineKind.AURA_CULTIVATOR).get().defaultBlockState(),3);
                level.setBlock(STORAGE,Blocks.BARREL.defaultBlockState(),3);((BarrelBlockEntity)level.getBlockEntity(STORAGE)).setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));
                for(var p:BlockPos.betweenClosed(MIN,MAX)) {
                    level.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),3);level.setBlock(p.above(),Blocks.GLOWSTONE.defaultBlockState(),3);
                    level.setBlock(p,(p.getX()==MIN.getX()?Blocks.WHEAT:Blocks.POTATOES).defaultBlockState().setValue(CropBlock.AGE,7),3);
                }
                var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level,MACHINE.getX()+.5,MACHINE.getY()+1,MACHINE.getZ()-2,0,20);
                var wand=(AreaWandItem)ModContent.AREA_WAND.get();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(wand));
                use(wand,player,MACHINE);use(wand,player,MIN);use(wand,player,MAX);player.setShiftKeyDown(true);player.setPose(net.minecraft.world.entity.Pose.CROUCHING);wand.use(level,player,InteractionHand.MAIN_HAND);player.setShiftKeyDown(false);player.setPose(net.minecraft.world.entity.Pose.STANDING);use(wand,player,MACHINE);use(wand,player,STORAGE);
                var machine=(CopperMachineBlockEntity)level.getBlockEntity(MACHINE);machine.energy.add(560,false);machine.redstoneMode=1;
                if(!STORAGE.equals(machine.boundInventory()))throw new IllegalStateException("Wand fixture did not bind the selected chest");
                level.setBlock(SMELTER,ModContent.MACHINES.get(MachineKind.RESONANCE_SMELTER).get().defaultBlockState(),3);var furnace=(CopperMachineBlockEntity)level.getBlockEntity(SMELTER);furnace.energy.add(240,false);
                if(!furnace.inventory.insertItem(0,new ItemStack(Items.POTATO),false).isEmpty())throw new IllegalStateException("Furnace rejected a potato");
                NetworkHooks.openScreen(player,machine,MACHINE);
            });stage=1;return;
        }
        if(stage==1 && mc.screen instanceof CopperMachineScreen && mc.player.containerMenu instanceof CopperMachineMenu menu && ++ticks>40) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId,2);mc.gameMode.handleInventoryButtonClick(menu.containerId,2);stage=2;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);
        } else if(stage==2 && verified.get() && mc.screen instanceof CopperMachineScreen && mc.player.containerMenu instanceof CopperMachineMenu menu && menu.data.get(14)==2 && menu.energy()==0) {
            mc.getToasts().clear();mc.gui.getChat().clearMessages(true);stage=3;ticks=0;
        } else if(stage==3 && ++ticks>10) {
            Screenshot.grab(mc.gameDirectory,"cue-cultivator-harvest-ru.png",mc.getMainRenderTarget(),text->saved.set(true));stage=4;
        } else if(stage==4 && saved.get()) {
            UnusualCopper.LOGGER.info("CuE HARVEST CLIENT PASS: actual C2S mode buttons; Russian harvest menu; scheduled harvest/replant/storage and cooked potato verified");mc.stop();
        }
    }
}
