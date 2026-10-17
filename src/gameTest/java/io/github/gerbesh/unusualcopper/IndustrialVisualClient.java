package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import java.util.concurrent.atomic.AtomicBoolean;

/** Screenshot-only display fixture in its own copied test world; never in release jars. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class IndustrialVisualClient {
    private static final MachineKind[] KINDS={MachineKind.RESONANCE_CRUSHER,MachineKind.COPPER_PRESS,
            MachineKind.VORTEX_PURIFIER,MachineKind.RESONANCE_CRYSTALLIZER,MachineKind.CIRCUIT_ETCHER,
            MachineKind.PRECISION_ASSEMBLER,MachineKind.COPPER_FLUID_PUMP,MachineKind.COPPER_FLUID_TANK,
            MachineKind.PRODUCTION_CONTROLLER,MachineKind.ARMOR_WORKBENCH};
    private static final String[] SHOTS={"front-idle","front-working-a","front-working-b","rear-working","night-working","close-processing","close-assembly","armor-front","armor-back","inventory","gear-inventory"};
    private static final AtomicBoolean ready=new AtomicBoolean(),saved=new AtomicBoolean();
    private static volatile int stage;
    private static boolean started,captured;
    private static int ticks,total;
    private static ServerLevel display;
    private static BlockPos pos(int index){return new BlockPos(102+(index%5)*3,150,110+(index/5)*4);}
    private static boolean enabled(){return Boolean.getBoolean("unusualcopper.industrialVisualClient");}

    @SubscribeEvent public static void server(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!enabled()||!ready.get()||display==null||stage>=7)return;
        // Force display poses only. Machines are redstone-paused and have no ingredients.
        for(int i=0;i<KINDS.length;i++)if(display.getBlockEntity(pos(i)) instanceof CopperMachineBlockEntity m){
            boolean active=stage>0;
            m.status(active?MachineStatus.WORKING:MachineStatus.REDSTONE_PAUSED);
            var state=m.getBlockState();if(state.getValue(CopperMachineBlock.ACTIVE)!=active)
                display.setBlock(pos(i),state.setValue(CopperMachineBlock.ACTIVE,active),2);
            if(display.getGameTime()%10==0)display.sendBlockUpdated(pos(i),m.getBlockState(),m.getBlockState(),2);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!enabled())return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(++total>1800)throw new IllegalStateException("Industrial screenshot fixture stalled");
        if(!started){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(70);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getSingleplayerServer().execute(()->{
                display=mc.getSingleplayerServer().overworld();var l=display;
                l.setDayTime(6000);l.setWeatherParameters(0,0,false,false);
                l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                for(int x=98;x<120;x++)for(int z=103;z<129;z++){
                    l.setBlock(new BlockPos(x,149,z),Blocks.POLISHED_DEEPSLATE.defaultBlockState(),2);
                    for(int y=150;y<158;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                var standArea=new net.minecraft.world.phys.AABB(98,149,118,120,154,125);
                for(var old:l.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,standArea))
                    if(old.getTags().contains("uc_visual_armor"))old.discard();
                for(int tier=1;tier<=3;tier++){
                    var stand=new net.minecraft.world.entity.decoration.ArmorStand(l,102.5+tier*3,150,121);
                    stand.addTag("uc_visual_armor");stand.setNoGravity(true);stand.setShowArms(true);stand.setNoBasePlate(true);stand.setYRot(180);
                    stand.setCustomName(Component.literal("Copper "+tier));stand.setCustomNameVisible(true);
                    for(var slot:new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET}){
                        String type=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";default->"boots";};
                        stand.setItemSlot(slot,new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_"+type+"_tier_"+tier).get()));
                    }
                    l.addFreshEntity(stand);
                }
                for(int i=0;i<KINDS.length;i++){
                    l.setBlock(pos(i),ModContent.MACHINES.get(KINDS[i]).get().defaultBlockState(),3);
                    var m=(CopperMachineBlockEntity)l.getBlockEntity(pos(i));m.redstoneMode=1;m.energy.restore(m.energy.capacity()/2);
                    if(KINDS[i]==MachineKind.VORTEX_PURIFIER||KINDS[i]==MachineKind.CIRCUIT_ETCHER)
                        m.water.setFluid(new FluidStack(Fluids.WATER,2800));
                    if(KINDS[i]==MachineKind.COPPER_FLUID_PUMP)m.water.setFluid(new FluidStack(Fluids.LAVA,10000));
                    if(KINDS[i]==MachineKind.COPPER_FLUID_TANK)m.water.setFluid(new FluidStack(Fluids.WATER,40000));
                    l.sendBlockUpdated(pos(i),m.getBlockState(),m.getBlockState(),2);
                }
                var p=l.getServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(GameType.SPECTATOR);
                p.teleportTo(l,108.5,153.3,103.8,0,16);ready.set(true);
            });return;
        }
        if(!ready.get())return;
        if(++ticks<(stage==0?120:45))return;
        if(!captured){
            captured=true;Screenshot.grab(mc.gameDirectory,"industrial-"+SHOTS[stage]+".png",mc.getMainRenderTarget(),message->{
                UnusualCopper.LOGGER.info("INDUSTRIAL VISUAL FRAME {}: {}",stage,message.getString());saved.set(true);
            });return;
        }
        if(!saved.get())return;
        if(stage==SHOTS.length-1){
            UnusualCopper.LOGGER.info("INDUSTRIAL VISUAL CAPTURE COMPLETE: ten devices, two motion phases, rear, night and item catalogue; screenshot fixture only");
            mc.options.hideGui=false;mc.stop();return;
        }
        stage++;ticks=0;captured=false;saved.set(false);
        if(stage==5)mc.options.fov().set(45);
        if(stage==9||stage==10){mc.setScreen(new Catalogue(stage==10));return;}
        ready.set(false);mc.getSingleplayerServer().execute(()->{
            var p=display.getServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(stage==3)p.teleportTo(display,108.5,153.3,121,180,16);
            if(stage==4){display.setDayTime(18000);p.teleportTo(display,108.5,153.3,103.8,0,16);}
            if(stage==5){display.setDayTime(6000);p.teleportTo(display,105.5,152,106.5,0,30);}
            if(stage==6)p.teleportTo(display,113,152,110.5,0,30);
            if(stage==7)p.teleportTo(display,108.5,152,115.5,0,14);
            if(stage==8)p.teleportTo(display,108.5,152,126.5,180,14);
            ready.set(true);
        });
    }
    private static final class Catalogue extends Screen {
        private static final String[] COMPONENTS={"copper_grit","iron_grit","gold_grit","washed_copper_dust","washed_iron_dust","washed_gold_dust",
                "inert_tailings","copper_plate","copper_wire_spool","resonant_coil","reinforced_frame","circuit_blank",
                "etched_circuit","resonant_crystal","shield_field_module","etching_reagent","precision_processor","module_blank_i",
                "plate_die","wire_die","pattern_card","prismatic_battery","sovereign_battery","flight_core_ii_module"};
        private final String[] ids;
        Catalogue(boolean armor){
            super(Component.literal(armor?"Unusual Copper — Programmable armor":"Unusual Copper — Industrial components"));
            if(armor){var list=new java.util.ArrayList<String>();
                for(int tier=1;tier<=3;tier++)for(var type:new String[]{"helmet","chestplate","leggings","boots"})list.add("programmable_"+type+"_tier_"+tier);
                for(var module:io.github.gerbesh.unusualcopper.armor.ArmorModuleType.values())list.add(module.id+"_module");
                ids=list.toArray(String[]::new);
            }else ids=COMPONENTS;
        }
        @Override public void render(GuiGraphics g,int x,int y,float partial){
            g.fill(0,0,width,height,0xff111c20);g.drawCenteredString(font,title,width/2,9,0xffc9e4db);
            int cell=Math.min((width-20)/6,(height-34)/4),left=(width-cell*6)/2,top=29;
            for(int i=0;i<ids.length;i++){
                int xx=left+(i%6)*cell,yy=top+(i/6)*cell;g.fill(xx+1,yy+1,xx+cell-2,yy+cell-2,0xff243b39);
                var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("unusualcopper",ids[i])));
                float scale=Math.min(3,(cell-17)/16F);g.pose().pushPose();g.pose().translate(xx+(cell-16*scale)/2,yy+3,0);g.pose().scale(scale,scale,1);g.renderItem(stack,0,0);g.pose().popPose();
                String label=ids[i].replace("programmable_","").replace("_tier_"," ").replace("_module","");
                g.drawString(font,font.plainSubstrByWidth(label,cell-6),xx+3,yy+cell-11,0xffbad3c7,false);
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
