package io.github.gerbesh.unusualcopper.ssnprobe;

import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.block.main.TileMain;
import com.lothrazar.storagenetwork.block.request.ContainerNetworkCraftingTable;
import com.lothrazar.storagenetwork.registry.SsnRegistry;
import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.machine.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.lang.reflect.*;
import java.util.*;

/** Probe-only instrumentation of the archived Progressive 1.2.4 server gateway. */
final class ProgressiveProbe {
    private static ServerPlayer player;private static CopperMachineBlockEntity smelter;private static StorageEndpoint endpoint;
    private static Class<?> manager;private static Constructor<?> request;private static Method send;private static boolean submitted;
    private static Object field(Object object,String name)throws ReflectiveOperationException{var f=(object instanceof Class<?> c?c:object.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class<?>?null:object);}
    static void start(ServerLevel level,TileMain master,BlockPos main,BlockPos bridge,UUID owner,StorageEndpoint storage){
        try{
            endpoint=storage;var source=(BarrelBlockEntity)level.getBlockEntity(main.east().south(2));source.setItem(0,new ItemStack(Items.OAK_LOG));source.setItem(1,new ItemStack(Items.IRON_INGOT,4));source.setItem(2,new ItemStack(Items.RAW_IRON,3));
            var p=main.west(6).north(3);level.getChunkAt(p);level.setBlock(p,Blocks.AIR.defaultBlockState(),3);level.setBlock(p,ModContent.MACHINES.get(MachineKind.RESONANCE_SMELTER).get().defaultBlockState(),3);smelter=(CopperMachineBlockEntity)level.getBlockEntity(p);smelter.setOwner(owner);smelter.energy.add(720,false);
            var profile=new com.mojang.authlib.GameProfile(owner,"SSNBridgeOwner");level.getServer().getProfileCache().add(profile);player=net.minecraftforge.common.util.FakePlayerFactory.get(level,profile);player.getInventory().clearContent();player.setPos(p.getX()+.5,p.getY()+.5,p.getZ()+.5);check(smelter.bind(bridge,Direction.UP,player),"Concurrent smelter failed to bind SSN");
            var terminal=main.north();level.setBlock(terminal,Blocks.AIR.defaultBlockState(),3);level.setBlock(terminal,SsnRegistry.Blocks.REQUEST.get().defaultBlockState(),3);master.getNetwork().doRefresh(new DimPos(level,main));player.setPos(terminal.getX()+.5,terminal.getY()+.5,terminal.getZ()+.5);
            var menu=new ContainerNetworkCraftingTable(95,level,terminal,player.getInventory(),player);player.containerMenu=menu;check(menu.getTileMain()==master&&menu.stillValid(player),"Real Progressive crafting terminal does not resolve the same native master");
            manager=Class.forName("ru.pashvera.ssn.ServerManager");var packet=Class.forName("ru.pashvera.ssn.Packets$Request");request=packet.getConstructor(int.class,int.class,long.class,String.class,int.class);send=manager.getMethod("request",ServerPlayer.class,packet);send.invoke(null,player,request.newInstance(0,95,0L,"",0));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Progressive 1.2.4 probe gateway unavailable",failure);}
    }
    static boolean tick(){
        try{
            var sessions=(Map<?,?>)field(manager,"sessions");var session=sessions.get(player.getUUID());check(session!=null,"Progressive discarded the real terminal session");
            if(!submitted){var index=field(session,"index");if(index==null)return false;var byId=index.getClass().getDeclaredMethod("byId");byId.setAccessible(true);check(((Map<?,?>)byId.invoke(index)).containsKey("minecraft:iron_sword"),"Native Progressive catalogue omitted the vanilla sword recipe");send.invoke(null,player,request.newInstance(2,95,(long)field(session,"token"),"minecraft:iron_sword",2));submitted=true;return false;}
            int swords=0;for(var stack:player.getInventory().items)if(stack.is(Items.IRON_SWORD))swords+=stack.getCount();if(swords!=2||smelter.energy.stored()!=0||!smelter.inventory.getStackInSlot(0).isEmpty()||!smelter.inventory.getStackInSlot(2).isEmpty()||smelter.boundCargoCount()!=0)return false;
            var iron=endpoint.count(ItemKey.of(new ItemStack(Items.IRON_INGOT)));if(!iron.complete())return false;check(iron.amount()==3,"Native crafting and concurrent smelter did not preserve the four-input/three-new-iron balance: "+iron);
            send.invoke(null,player,request.newInstance(4,95,(long)field(session,"token"),"",0));UnusualCopper.LOGGER.info("CuE SSN PROGRESSIVE PASS: actual 1.2.4 terminal/server request, two recursively crafted iron swords; concurrent bridge smelter processed three real raw iron for 720 CuE and returned exactly three iron to the same network");return true;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Progressive probe session failed",failure);}
    }
    private static void check(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
    private ProgressiveProbe(){}
}
