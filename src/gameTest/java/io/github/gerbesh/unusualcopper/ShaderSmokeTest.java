package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlock;
import io.github.gerbesh.unusualcopper.compat.ssn.SsnBridgeBlockEntity;
import io.github.gerbesh.unusualcopper.machine.MachineKind;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short, development-only screenshot fixture for the owner's actual Oculus Medium profile. */
@Mod.EventBusSubscriber(modid=UnusualCopper.MOD_ID,value=Dist.CLIENT)
public final class ShaderSmokeTest {
    private static volatile boolean ready,daySaved,nightSaved;
    private static boolean requested,dayRequested,nightRequested,verified;
    private static int phase,ticks;
    private static BlockPos scene;

    private static boolean shaderActive() {
        try {
            var type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var instance=type.getMethod("getInstance").invoke(null);
            if(!(boolean)type.getMethod("isShaderPackInUse").invoke(instance))return false;
            var iris=Class.forName("net.irisshaders.iris.Iris");
            var name=(String)iris.getMethod("getCurrentPackName").invoke(null);
            if(!"Better MC - Medium".equals(name))
                throw new IllegalStateException("Wrong shader profile: expected Better MC - Medium, active "+name);
            return true;
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Oculus IrisApi is missing; shader rendering was not verified",failure);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("unusualcopper.smokeShaders"))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(!requested){
            requested=true;
            mc.options.hideGui=true;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();
                var level=server.overworld();
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,132,132);
                scene=new BlockPos(132,y,132);
                level.setDayTime(6000);
                level.setWeatherParameters(0,0,false,false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                for(int dx=-10;dx<=10;dx++)for(int dz=-10;dz<=10;dz++){
                    var p=scene.offset(dx,-1,dz);
                    level.setBlock(p,Blocks.STONE_BRICKS.defaultBlockState(),3);
                    for(int above=0;above<5;above++)level.setBlock(p.above(above+1),Blocks.AIR.defaultBlockState(),3);
                }
                var directions=Direction.values();
                for(int i=0;i<directions.length;i++){
                    var p=scene.offset((i%3-1)*3,0,(i/3)*3);
                    level.setBlock(p,ModContent.SSN_BRIDGE.get().defaultBlockState().setValue(SsnBridgeBlock.FACING,directions[i]),3);
                    ((SsnBridgeBlockEntity)level.getBlockEntity(p)).owner(mc.player.getUUID());
                }
                level.setBlock(scene.offset(-6,0,1),ModContent.MACHINES.get(MachineKind.AETHERIC_BREWER).get().defaultBlockState(),3);
                level.setBlock(scene.offset(-6,0,4),ModContent.MACHINES.get(MachineKind.COPPER_CELL).get().defaultBlockState(),3);
                level.setBlock(scene.offset(6,0,1),ModContent.MACHINES.get(MachineKind.SOLAR_INFUSER).get().defaultBlockState(),3);
                level.setBlock(scene.offset(6,0,4),ModContent.MACHINES.get(MachineKind.ENCHANTMENT_RESONATOR).get().defaultBlockState(),3);
                for(int z=0;z<5;z++) {
                    level.setBlock(scene.offset(-8,0,z),ModContent.ENERGY_PIPE.get().defaultBlockState(),3);
                    level.setBlock(scene.offset(8,0,z),ModContent.ITEM_PIPE.get().defaultBlockState(),3);
                }
                for(int x=-9;x<=9;x+=9)for(int z=-3;z<=6;z+=9)
                    level.setBlock(scene.offset(x,0,z),Blocks.GLOWSTONE.defaultBlockState(),3);
                var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                if(player==null)throw new IllegalStateException("Shader fixture needs a real integrated-server player");
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(level,scene.getX()+.5,scene.getY()+3.5,scene.getZ()-10.5,0,17);
                UnusualCopper.LOGGER.info("CuE SHADER CLIENT SCENE READY: six SSN orientations, pipes, brewer glass and machines; position={}",scene);
                ready=true;
            });
            return;
        }
        if(!ready)return;
        if(++ticks>500)throw new IllegalStateException("Oculus Medium screenshot smoke timed out at phase "+phase);
        if(ticks>=100&&!verified){
            if(!shaderActive())throw new IllegalStateException("The required Better MC - Medium shader is not actively rendering");
            verified=true;
            UnusualCopper.LOGGER.info("CuE SHADER CLIENT ACTIVE PASS: Oculus IrisApi confirms a shader pack is rendering");
        }
        if(phase==0 && verified && ticks>=160&&!dayRequested){
            dayRequested=true;
            Screenshot.grab(mc.gameDirectory,"cue-shaders-medium-day.png",mc.getMainRenderTarget(),message->{
                UnusualCopper.LOGGER.info("CuE SHADER CLIENT DAY SCREENSHOT: {}",message.getString());
                daySaved=true;
            });
        }
        if(phase==0&&daySaved){
            phase=1;ticks=0;
            mc.getSingleplayerServer().execute(()->{
                mc.getSingleplayerServer().overworld().setDayTime(18000);
                UnusualCopper.LOGGER.info("CuE SHADER CLIENT NIGHT SCENE: real world lighting and shader shadow pass");
            });
        }
        if(phase==1 && ticks>=120&&!nightRequested){
            if(!shaderActive())throw new IllegalStateException("Shader deactivated before the night scene");
            nightRequested=true;
            Screenshot.grab(mc.gameDirectory,"cue-shaders-medium-night.png",mc.getMainRenderTarget(),message->{
                UnusualCopper.LOGGER.info("CuE SHADER CLIENT NIGHT SCREENSHOT: {}",message.getString());
                nightSaved=true;
            });
        }
        if(phase==1&&nightSaved){
            phase=2;
            UnusualCopper.LOGGER.info("CuE SHADER CLIENT PASS: active Oculus shader with day and night screenshots, six bridge orientations and glass machines");
            mc.options.hideGui=false;
            mc.stop();
        }
    }
    private ShaderSmokeTest(){}
}
