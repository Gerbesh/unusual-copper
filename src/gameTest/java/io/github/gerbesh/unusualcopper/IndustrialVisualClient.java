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
    private static final String[] SHOTS={"front-idle","front-working-a","front-working-b","rear-working","night-working","close-processing","close-assembly","armor-front","armor-back","inventory","gear-inventory","armor-console-head","armor-console-chest","armor-console-boots","armor-hud","armor-hud-expired","armor-faces","visor-dark","visor-lit","scanner-ports","visor-expired","scanner-console"};
    private static volatile int displayedPorts;
    private static final BlockPos SENSE_TARGET=new BlockPos(108,139,111);
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
        if(stage>=11&&stage<17||stage==21)io.github.gerbesh.unusualcopper.client.ArmorClientState.receive(displayArmor(stage));
        if(stage>=17&&stage<20)io.github.gerbesh.unusualcopper.client.ArmorSenseClient.receive(displaySenses(stage));
        if(++ticks<(stage==0?120:45))return;
        if(!captured){
            if(stage>=17&&stage<=20&&Boolean.getBoolean("unusualcopper.verifyShaderSenseUniform"))verifyShaderUniform(stage);
            captured=true;Screenshot.grab(mc.gameDirectory,"industrial-"+SHOTS[stage]+".png",mc.getMainRenderTarget(),message->{
                UnusualCopper.LOGGER.info("INDUSTRIAL VISUAL FRAME {}: {}",stage,message.getString());saved.set(true);
            });return;
        }
        if(!saved.get())return;
        if(stage==SHOTS.length-1){
            UnusualCopper.LOGGER.info("INDUSTRIAL VISUAL CAPTURE COMPLETE: ten devices, two motion phases, rear, night and item catalogue; screenshot fixture only");
            mc.options.hideGui=false;mc.stop();return;
        }
        stage=stage==0&&Boolean.getBoolean("unusualcopper.armorSenseVisualOnly")?17:stage+1;ticks=0;captured=false;saved.set(false);
        if(stage==5)mc.options.fov().set(45);
        if(stage==9||stage==10){mc.setScreen(new Catalogue(stage==10));return;}
        if(stage==16){mc.options.hideGui=true;mc.setScreen(new ArmorFaces());return;}
        if(stage==21){
            io.github.gerbesh.unusualcopper.client.ArmorClientState.receive(displayArmor(stage));mc.options.hideGui=true;
            mc.setScreen(new io.github.gerbesh.unusualcopper.client.ArmorScreen(0){
                @Override protected void init(){
                    super.init();
                    // Pagination alone changes client display state; no owner-setting action is sent.
                    for(int click=0;click<2;click++)for(var widget:new java.util.ArrayList<>(children()))
                        if(widget instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("›")){button.onPress();break;}
                }
                @Override public void render(GuiGraphics g,int x,int y,float partial){io.github.gerbesh.unusualcopper.client.ArmorClientState.receive(displayArmor(stage));super.render(g,-1,-1,partial);}
            });return;
        }
        if(stage>=17){
            mc.options.hideGui=stage!=19;mc.setScreen(null);
            if(stage==17){ready.set(false);mc.getSingleplayerServer().execute(()->{
                var p=display.getServer().getPlayerList().getPlayer(mc.player.getUUID());
                prepareSenseRoom(p);ready.set(true);
            });}
            return;
        }
        if(stage>=11){
            io.github.gerbesh.unusualcopper.client.ArmorClientState.receive(displayArmor(stage));
            mc.options.hideGui=stage<14;
            mc.setScreen(stage>=14?null:new io.github.gerbesh.unusualcopper.client.ArmorScreen(stage==13?3:stage==12?1:0){
                @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,-1,-1,partial);}
            });return;
        }
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
    private static void verifyShaderUniform(int frame){
        try{
            var method=Class.forName("net.irisshaders.iris.uniforms.CommonUniforms").getDeclaredMethod("getNightVision");method.setAccessible(true);
            float actual=(float)method.invoke(null),expected=frame==18||frame==19?1F:0F;
            if(!Float.isFinite(actual)||Math.abs(actual-expected)>.001F)throw new IllegalStateException("Unexpected night-vision uniform: "+actual+", expected "+expected);
            UnusualCopper.LOGGER.info("ARMOR SHADER UNIFORM frame={} actual={} expected={}",frame,actual,expected);
        }catch(ReflectiveOperationException incompatible){throw new IllegalStateException("Cannot verify optional Oculus vision uniform",incompatible);}
    }
    /** Display-only gear on a spectator. The server runtime refuses all paid abilities in this mode. */
    private static void prepareSenseRoom(net.minecraft.server.level.ServerPlayer player){
        display.setDayTime(18000);
        for(int x=102;x<=115;x++)for(int y=136;y<=144;y++)for(int z=102;z<=113;z++){
            boolean wall=x==102||x==115||y==136||y==144||z==102||z==113;
            display.setBlock(new BlockPos(x,y,z),(wall?Blocks.STONE_BRICKS:Blocks.AIR).defaultBlockState(),2);
        }
        display.setBlock(SENSE_TARGET.below(),Blocks.POLISHED_DEEPSLATE.defaultBlockState(),2);
        display.setBlock(SENSE_TARGET,ModContent.MACHINES.get(MachineKind.COPPER_PRESS).get().defaultBlockState(),3);
        var machine=(CopperMachineBlockEntity)display.getBlockEntity(SENSE_TARGET);machine.redstoneMode=1;machine.setOwner(player.getUUID());machine.energy.restore(4800);machine.status(MachineStatus.REDSTONE_PAUSED);
        displayedPorts=0;for(var side:net.minecraft.core.Direction.values())displayedPorts|=machine.sideMode(side).ordinal()<<(side.ordinal()*3);
        display.sendBlockUpdated(SENSE_TARGET,machine.getBlockState(),machine.getBlockState(),2);
        for(var pos:java.util.List.of(new BlockPos(105,139,114),new BlockPos(109,140,114),new BlockPos(112,138,114)))display.setBlock(pos,Blocks.DIAMOND_ORE.defaultBlockState(),2);
        for(var type:net.minecraft.world.item.ArmorItem.Type.values()){
            var item=new ItemStack(ModContent.PROGRAMMABLE_ARMOR.get("programmable_"+type.getName()+"_tier_3").get());
            if(type==net.minecraft.world.item.ArmorItem.Type.HELMET){
                io.github.gerbesh.unusualcopper.armor.ArmorData.install(item,io.github.gerbesh.unusualcopper.armor.ArmorModuleType.OPTIC_VISOR,2);
                io.github.gerbesh.unusualcopper.armor.ArmorData.install(item,io.github.gerbesh.unusualcopper.armor.ArmorModuleType.FIELD_SCANNER,2);
                io.github.gerbesh.unusualcopper.armor.ArmorData.install(item,io.github.gerbesh.unusualcopper.armor.ArmorModuleType.AQUA_LUNGS,2);
            }
            if(type==net.minecraft.world.item.ArmorItem.Type.CHESTPLATE){
                var cell=new ItemStack(ModContent.AETHERIC_BATTERY.get());io.github.gerbesh.unusualcopper.item.CopperBatteryItem.stored(cell,1_000_000);
                io.github.gerbesh.unusualcopper.armor.ArmorData.installBattery(item,cell);io.github.gerbesh.unusualcopper.armor.ArmorData.profile(item,3);
            }
            player.setItemSlot(((io.github.gerbesh.unusualcopper.armor.ProgrammableArmorItem)item.getItem()).getEquipmentSlot(),item);
        }
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.teleportTo(display,108.5,138,108.5,0,5);
    }
    private static io.github.gerbesh.unusualcopper.network.ArmorSenseSnapshot displaySenses(int frame){
        boolean on=frame>=18,diagnostics=frame==19;
        var ores=diagnostics?java.util.List.of(new BlockPos(105,139,114),new BlockPos(109,140,114),new BlockPos(112,138,114)):java.util.List.<BlockPos>of();
        var target=diagnostics?new io.github.gerbesh.unusualcopper.network.ArmorSenseSnapshot.Target(SENSE_TARGET,"unusualcopper:copper_press",4800,20_000,MachineStatus.REDSTONE_PAUSED.ordinal(),displayedPorts):null;
        return new io.github.gerbesh.unusualcopper.network.ArmorSenseSnapshot("minecraft:overworld",3,on?2:0,diagnostics?2:0,0,
                on?io.github.gerbesh.unusualcopper.network.ArmorSenseSnapshot.ScanState.READY:io.github.gerbesh.unusualcopper.network.ArmorSenseSnapshot.ScanState.OFF,on?925:0,on?925:0,ores,target);
    }
    /** Synthetic display snapshot only: this does not configure, equip or pay for real armor. */
    private static io.github.gerbesh.unusualcopper.network.ArmorNetwork.Snapshot displayArmor(int frame){
        var pieces=new java.util.ArrayList<io.github.gerbesh.unusualcopper.network.ArmorNetwork.Piece>();
        var types=new String[]{"helmet","chestplate","leggings","boots"};
        var slots=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET};
        for(int i=0;i<4;i++){
            var rows=new java.util.ArrayList<io.github.gerbesh.unusualcopper.network.ArmorNetwork.Row>();
            for(var module:io.github.gerbesh.unusualcopper.armor.ArmorModuleType.values())if(module.slot==slots[i]){
                var trigger=module==io.github.gerbesh.unusualcopper.armor.ArmorModuleType.AQUA_LUNGS?io.github.gerbesh.unusualcopper.armor.ArmorModuleSettings.Trigger.UNDERWATER:
                        module==io.github.gerbesh.unusualcopper.armor.ArmorModuleType.FLIGHT_CORE_II?io.github.gerbesh.unusualcopper.armor.ArmorModuleSettings.Trigger.MANUAL:io.github.gerbesh.unusualcopper.armor.ArmorModuleSettings.Trigger.AUTO;
                rows.add(new io.github.gerbesh.unusualcopper.network.ArmorNetwork.Row(module,new io.github.gerbesh.unusualcopper.armor.ArmorModuleSettings(true,trigger,10,false,
                        io.github.gerbesh.unusualcopper.armor.ArmorPowerScheduler.defaultPriority(module)),module.maxLevel()));
            }
            pieces.add(new io.github.gerbesh.unusualcopper.network.ArmorNetwork.Piece("unusualcopper:programmable_"+types[i]+"_tier_3",3,i==1?4:3,rows));
        }
        return new io.github.gerbesh.unusualcopper.network.ArmorNetwork.Snapshot(new java.util.UUID(0,42),1,true,1,frame>=14?800000:3400000,10000000,4440,36,1L<<io.github.gerbesh.unusualcopper.armor.ArmorModuleType.SHIELD_FIELD.ordinal(),4,new io.github.gerbesh.unusualcopper.armor.ArmorRuntime.FlightStatus(true,frame==15?0:820,39),pieces,io.github.gerbesh.unusualcopper.network.ArmorNetwork.Result.OK);
    }
    /** Client-only mannequins: never inserted into the world or given working modules/cells. */
    private static final class ArmorFaces extends Screen {
        private final net.minecraft.client.player.RemotePlayer[] mannequins=new net.minecraft.client.player.RemotePlayer[3];
        ArmorFaces(){
            super(Component.literal("Unusual Copper — Open-face copper armor I / II / III"));
            for(int tier=1;tier<=3;tier++){
                var dummy=new net.minecraft.client.player.RemotePlayer(Minecraft.getInstance().level,new com.mojang.authlib.GameProfile(new java.util.UUID(0,100+tier),"CuE display "+tier));
                for(var type:net.minecraft.world.item.ArmorItem.Type.values()){
                    var armor=ModContent.PROGRAMMABLE_ARMOR.get("programmable_"+type.getName()+"_tier_"+tier).get();
                    dummy.setItemSlot(((io.github.gerbesh.unusualcopper.armor.ProgrammableArmorItem)armor).getEquipmentSlot(),new ItemStack(armor));
                }
                mannequins[tier-1]=dummy;
            }
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xff172527);g.drawCenteredString(font,title,width/2,15,0xffe5cfab);
            int scale=Math.min((height-60)/2,110),bottom=height-35;
            for(int i=0;i<3;i++){
                int x=width*(i*2+1)/6;
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsAngle(g,x,bottom,scale,0,0,mannequins[i]);
                g.drawCenteredString(font,"Copper "+(i+1),x,height-20,0xff82dad0);
            }
            super.render(g,-1,-1,partial);
        }
        @Override public boolean isPauseScreen(){return false;}
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
                for(var module:io.github.gerbesh.unusualcopper.armor.ArmorModuleType.values())for(int level=1;level<=module.maxLevel();level++)list.add(module.itemId(level));
                ids=list.toArray(String[]::new);
            }else ids=COMPONENTS;
        }
        @Override public void render(GuiGraphics g,int x,int y,float partial){
            g.fill(0,0,width,height,0xff111c20);g.drawCenteredString(font,title,width/2,9,0xffc9e4db);
            int rows=(ids.length+5)/6;
            int cell=Math.min((width-20)/6,(height-34)/rows),left=(width-cell*6)/2,top=29;
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
