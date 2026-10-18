package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.EnchanterScreen;
import io.github.gerbesh.unusualcopper.gameplay.*;
import io.github.gerbesh.unusualcopper.menu.EnchanterMenu;
import io.github.gerbesh.unusualcopper.network.EnchanterNetwork;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed client quotes/catalog pages only; no select, confirm, XP or resource transfer. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class EnchanterVisualClient {
    private static final String[] SHOTS={"loading","catalog","reach-quote","search","mod-tooltip","curse-book"};
    private static final BlockPos POS=new BlockPos(110,150,126);
    private static final AtomicBoolean READY=new AtomicBoolean(),SAVED=new AtomicBoolean();
    private static boolean started,captured;private static int ticks,stage,total;
    private EnchanterVisualClient(){}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.enchanterVisualClient"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(++total>1600)throw new IllegalStateException("Enchanter display stalled");
        if(!started){started=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);level.setWeatherParameters(0,0,false,false);
                level.setBlock(POS,ModContent.MACHINES.get(MachineKind.ENCHANTMENT_RESONATOR).get().defaultBlockState(),3);
                var p=level.getServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.teleportTo(level,110.5,151.5,122.5,0,10);READY.set(true);
            });return;
        }
        if(!READY.get())return;
        if(ticks==0)screen(stage);
        if(++ticks<(stage==0?100:45))return;
        if(!captured){captured=true;Screenshot.grab(mc.gameDirectory,"enchanter-compat-"+SHOTS[stage]+".png",mc.getMainRenderTarget(),message->{UnusualCopper.LOGGER.info("ENCHANTER DISPLAY FRAME {}: {}",stage,message.getString());SAVED.set(true);});return;}
        if(!SAVED.get())return;
        if(stage==SHOTS.length-1){UnusualCopper.LOGGER.info("ENCHANTER DISPLAY COMPLETE: fixed client pages and quotes; no paid transaction acceptance");mc.setScreen(null);mc.player.containerMenu=mc.player.inventoryMenu;mc.options.hideGui=false;mc.stop();return;}
        stage++;ticks=0;captured=false;SAVED.set(false);
    }
    private static void screen(int shot){
        var mc=Minecraft.getInstance();var runtime=UUID.randomUUID();var catalog=UUID.randomUUID();
        var ids=ForgeRegistries.ENCHANTMENTS.getKeys().stream().filter(id->id.getNamespace().equals("uc_enchanter_fixture")).sorted().toList();
        var offers=ids.stream().map(id->new EnchantmentPolicy.Offer(id,id.getPath().endsWith("reach")?3:1,id.getPath().endsWith("reach")?113:38,80000)).toList();
        var source=new ItemStack(ForgeRegistries.ITEMS.getValue(EnchanterCompatFixtures.id("gloves")));
        EnchanterNetwork.Quote quote=null;
        if(shot>=2&&shot<=4){var offer=offers.stream().filter(o->o.enchantment().equals(EnchanterCompatFixtures.id("reach"))).findFirst().orElseThrow();quote=new EnchanterNetwork.Quote(UUID.randomUUID(),offer,EnchantmentPolicy.apply(source,offer),600);}
        if(shot==5){source=new ItemStack(Items.BOOK);offers=List.of(new EnchantmentPolicy.Offer(new ResourceLocation("minecraft","binding_curse"),1,94,80000),new EnchantmentPolicy.Offer(new ResourceLocation("minecraft","mending"),1,75,80000),new EnchantmentPolicy.Offer(EnchanterCompatFixtures.id("reach"),3,113,80000));quote=new EnchanterNetwork.Quote(UUID.randomUUID(),offers.get(0),EnchantmentPolicy.apply(source,offers.get(0)),600);}
        var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());final EnchanterMenu menu;
        try{buffer.writeBlockPos(POS);menu=new EnchanterMenu(0,mc.player.getInventory(),buffer);}finally{buffer.release();}
        menu.getSlot(0).set(source);menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,64));
        int first=Math.min(offers.size(),EnchantmentCatalog.PAGE_SIZE);
        menu.receive(new EnchanterNetwork.Reply(0,POS,runtime,offers.subList(0,first),quote,5000,2000000,64,quote==null?EnchanterNetwork.Result.NONE:EnchanterNetwork.Result.QUOTED,0,true,catalog,offers.size()));
        if(shot!=0)for(int start=first;start<offers.size();start+=EnchantmentCatalog.PAGE_SIZE)menu.receivePage(new EnchanterNetwork.Page(0,POS,runtime,catalog,start,offers.size(),offers.subList(start,Math.min(offers.size(),start+EnchantmentCatalog.PAGE_SIZE))));
        var screen=new EnchanterScreen(menu,mc.player.getInventory(),Component.translatable("block.unusualcopper.enchantment_resonator")){
            @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,shot==4?(width-340)/2+160:-1,shot==4?(height-234)/2+55:-1,partial);}
        };
        mc.setScreen(screen);mc.player.containerMenu=menu;
        if(shot==3||shot==4)screen.children().stream().filter(child->child instanceof EditBox).map(child->(EditBox)child).findFirst().ifPresent(search->search.setValue(Component.translatable("enchantment.uc_enchanter_fixture.reach").getString()));
    }
}
