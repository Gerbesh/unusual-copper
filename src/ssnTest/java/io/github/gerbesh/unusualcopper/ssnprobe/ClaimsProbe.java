package io.github.gerbesh.unusualcopper.ssnprobe;

import io.github.gerbesh.unusualcopper.UnusualCopper;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.lang.reflect.Method;
import java.util.*;

/** Actual OpenPAC source/port/master revocation; probe-only fixture claims are always removed. */
final class ClaimsProbe {
    private static final UUID STRANGER=UUID.fromString("ceee088d-08b1-4b97-969d-327660ad3e4b");
    private static final ItemKey KEY=ItemKey.of(new ItemStack(Items.EMERALD));
    private static ServerLevel level;private static BlockPos main,bridge,far;private static UUID owner;private static StorageEndpoint endpoint;private static ProbeInventory inventory;private static int phase;
    private static Object manager;private static Method claim,unclaim;
    static void start(ServerLevel world,BlockPos master,BlockPos bridgePos,UUID actor,StorageEndpoint storage){
        level=world;main=master;bridge=bridgePos;owner=actor;endpoint=storage;far=main.east(19).south(2);inventory=(ProbeInventory)level.getBlockEntity(far);inventory.inventory.setStackInSlot(511,new ItemStack(Items.EMERALD,7));
        for(int i=0;i<18;i++){var barrel=(BarrelBlockEntity)level.getBlockEntity(main.east(i+1).south(2));for(int slot=0;slot<27;slot++)barrel.setItem(slot,new ItemStack(Items.STONE,64));}
        level.getServer().getProfileCache().add(new com.mojang.authlib.GameProfile(owner,"SSNBridgeOwner"));level.getServer().getProfileCache().add(new com.mojang.authlib.GameProfile(STRANGER,"SSNBridgeGuest"));
        try{var api=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");var claims=Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");manager=api.getMethod("getServerClaimsManager").invoke(api.getMethod("get",MinecraftServer.class).invoke(null,level.getServer()));claim=claims.getMethod("claim",ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class);unclaim=claims.getMethod("unclaim",ResourceLocation.class,int.class,int.class);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Native OpenPAC probe API missing",failure);}
    }
    private static void set(BlockPos pos,UUID actor)throws ReflectiveOperationException{unclaim.invoke(manager,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4);claim.invoke(manager,level.dimension().location(),actor,-1,pos.getX()>>4,pos.getZ()>>4,false);}
    private static void clear(BlockPos pos)throws ReflectiveOperationException{unclaim.invoke(manager,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4);}
    static boolean tick(){
        try{
            if(phase==0){var cached=endpoint.count(KEY);if(!cached.complete())return false;check(cached.amount()==7,"Native source claim fixture did not cache seven exact items");
                set(far,STRANGER);int reads=inventory.reads;check(!endpoint.count(KEY).complete(),"Foreign source claim leaked a completed cached count");
                for(int i=0;i<4;i++){check(endpoint.simulateExtract(KEY,1)==0&&endpoint.extract(KEY,1).isEmpty(),"Foreign source claim allowed real or simulated extraction");check(endpoint.insert(new ItemStack(Items.REDSTONE,3),true).getCount()==3&&endpoint.insert(new ItemStack(Items.REDSTONE,3),false).getCount()==3,"Foreign source claim accepted an insertion after the full public ports");}
                check(inventory.reads==reads,"Denied source claim still read its physical slots");check(inventory.inventory.getStackInSlot(511).getCount()==7,"Foreign source stock changed");clear(far);
                set(main,STRANGER);check(!endpoint.isAvailable()&&!endpoint.count(KEY).complete()&&endpoint.extract(KEY,1).isEmpty(),"Claimed native master remained usable from an unclaimed bridge");clear(main);
                set(bridge,STRANGER);check(!endpoint.isAvailable()&&endpoint.snapshot(stack->true,32).entries().isEmpty()&&!endpoint.count(KEY).complete(),"Bridge claim leaked a previous private network view");clear(bridge);phase=1;
            }
            var restored=endpoint.count(KEY);if(!restored.complete())return false;check(restored.amount()==7,"Restored native claims did not recover the exact unchanged stock");
            UnusualCopper.LOGGER.info("CuE SSN CLAIMS PASS: actual OpenPAC 0.30.1 source/master/bridge revocation; no cached count, denied slot reads, fees or transfer; restored owner still has seven exact items");return true;
        }catch(ReflectiveOperationException|RuntimeException failure){cleanup();throw new IllegalStateException("Native SSN/OpenPAC claim verification failed",failure);}
    }
    static void cleanup(){if(manager==null)return;for(var pos:List.of(far,main,bridge))try{clear(pos);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Could not clear the owned fixture claim",failure);}}
    private static void check(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
    private ClaimsProbe(){}
}
