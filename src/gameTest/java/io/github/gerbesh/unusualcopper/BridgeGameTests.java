package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.compat.ssn.*;
import io.github.gerbesh.unusualcopper.registry.ModContent;
import io.github.gerbesh.unusualcopper.storage.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(UnusualCopper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BridgeGameTests {
    @GameTest(template="empty")
    public static void absentSsnLeavesBridgeUnavailableAndNormalStorageWorking(GameTestHelper h){
        var p=new BlockPos(1,2,1);h.setBlock(p,ModContent.SSN_BRIDGE.get());var pos=h.absolutePos(p);var bridge=(SsnBridgeBlockEntity)h.getBlockEntity(p);var owner=UUID.randomUUID();bridge.owner(owner);
        var endpoint=StorageEndpoints.at(h.getLevel(),pos,Direction.UP,owner);var key=ItemKey.of(new ItemStack(Items.DIAMOND));
        h.assertTrue(!SsnAccess.supported()&&!endpoint.isAvailable()&&!endpoint.count(key).complete()&&endpoint.simulateExtract(key,1)==0&&endpoint.extract(key,1).isEmpty()&&endpoint.insert(key.stack(1),false).getCount()==1,"Absent optional SSN cannot expose or invent any items");
        h.assertTrue(!bridge.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()&&!bridge.getCapability(ForgeCapabilities.ENERGY).isPresent(),"Bridge does not pretend to be an unrestricted item or FE handler");
        var chest=p.east(2);h.setBlock(chest,Blocks.BARREL);var barrel=(BarrelBlockEntity)h.getBlockEntity(chest);barrel.setItem(0,key.stack(7));var normal=StorageEndpoints.at(h.getLevel(),h.absolutePos(chest),Direction.UP,owner);
        h.assertTrue(normal.isAvailable()&&normal.count(key).complete()&&normal.count(key).amount()==7&&normal.extract(key,2).get(0).getCount()==2,"Normal factory-selected Forge storage works without SSN");h.succeed();
    }
    @GameTest(template="empty")
    public static void bridgeOwnershipSelectorRedstoneAndPrivatePersistence(GameTestHelper h){
        var p=new BlockPos(1,2,1);h.setBlock(p,ModContent.SSN_BRIDGE.get());var bridge=(SsnBridgeBlockEntity)h.getBlockEntity(p);var owner=UUID.randomUUID();bridge.owner(owner);bridge.redstoneMode=1;
        h.assertTrue(!bridge.enabled(),"Signal-required bridge is paused without redstone");h.setBlock(p.east(),Blocks.REDSTONE_BLOCK);h.assertTrue(bridge.enabled(),"Real redstone signal enables bridge");bridge.redstoneMode=2;h.assertTrue(!bridge.enabled(),"Inverted mode pauses on a real signal");
        var restored=(SsnBridgeBlockEntity)BlockEntity.loadStatic(h.absolutePos(p),bridge.getBlockState(),bridge.saveWithFullMetadata());h.assertTrue(restored!=null&&owner.equals(restored.owner())&&restored.redstoneMode==2&&!bridge.getUpdateTag().contains("Owner"),"Owner and mode persist without broadcasting private binding data");
        var foreign=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BridgeGuest"));var actor=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(owner,"BridgeOwner"));var before=bridge.getBlockState().getValue(SsnBridgeBlock.FACING);
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(p)),Direction.EAST,h.absolutePos(p),false);var tool=ModContent.FACE_SELECTOR.get();
        foreign.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));tool.useOn(new UseOnContext(foreign,InteractionHand.MAIN_HAND,hit));h.assertTrue(bridge.getBlockState().getValue(SsnBridgeBlock.FACING)==before,"Foreign selector cannot reconfigure bridge");
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));tool.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit));h.assertTrue(bridge.getBlockState().getValue(SsnBridgeBlock.FACING)==Direction.EAST,"Owner selector sets an actual six-direction network port");h.succeed();
    }
    @GameTest(template="empty")
    public static void routedEndpointRebindsAcrossBridgeAndNormalInventoryReplacement(GameTestHelper h){
        // The optional adapter must never stick to a removed bridge, nor expose the old barrel's stock.
        if(SsnAccess.supported()){h.succeed();return;}
        var p=new BlockPos(1,2,1);var owner=UUID.randomUUID();var key=ItemKey.of(new ItemStack(Items.COPPER_INGOT));
        h.setBlock(p,ModContent.SSN_BRIDGE.get());((SsnBridgeBlockEntity)h.getBlockEntity(p)).owner(owner);
        var endpoint=StorageEndpoints.at(h.getLevel(),h.absolutePos(p),Direction.UP,owner);
        h.assertTrue(!endpoint.isAvailable()&&!endpoint.count(key).complete(),"Optional bridge must be unavailable without SSN");
        h.setBlock(p,Blocks.BARREL);var barrel=(BarrelBlockEntity)h.getBlockEntity(p);barrel.setItem(0,key.stack(5));
        h.assertTrue(endpoint.isAvailable()&&endpoint.count(key).complete()&&endpoint.count(key).amount()==5,"Existing endpoint failed to select the newly placed physical barrel");
        h.assertTrue(endpoint.insert(key.stack(2),true).isEmpty()&&endpoint.count(key).amount()==5,"Insertion simulation changed physical stock after an adapter switch");
        h.assertTrue(endpoint.insert(key.stack(2),false).isEmpty()&&endpoint.count(key).amount()==7,"Real insertion failed after an adapter switch");
        h.setBlock(p,ModContent.SSN_BRIDGE.get());((SsnBridgeBlockEntity)h.getBlockEntity(p)).owner(owner);
        h.assertTrue(!endpoint.isAvailable()&&!endpoint.count(key).complete()&&endpoint.extract(key,1).isEmpty(),"Removed barrel remained exposed through the new unavailable bridge");
        h.setBlock(p,Blocks.BARREL);
        h.assertTrue(endpoint.isAvailable()&&endpoint.count(key).complete()&&endpoint.count(key).amount()==0,"Recreated barrel inherited the previous inventory's cached items");
        h.succeed();
    }
}
